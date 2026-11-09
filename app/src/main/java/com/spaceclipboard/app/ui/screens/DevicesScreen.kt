package com.spaceclipboard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaceclipboard.app.R
import com.spaceclipboard.app.domain.model.DiscoveredPeer
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.ui.DeviceCheckUi
import com.spaceclipboard.app.ui.MainViewModel
import com.spaceclipboard.app.ui.components.ButtonVariant
import com.spaceclipboard.app.ui.components.ConfirmDialog
import com.spaceclipboard.app.ui.components.EmptyState
import com.spaceclipboard.app.ui.components.PairingDialog
import com.spaceclipboard.app.ui.components.ScreenTitle
import com.spaceclipboard.app.ui.components.SectionLabel
import com.spaceclipboard.app.ui.components.SpaceButton
import com.spaceclipboard.app.ui.components.SpacePanel
import com.spaceclipboard.app.ui.components.StatusPill
import com.spaceclipboard.app.ui.components.TextPromptDialog
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.Error
import com.spaceclipboard.app.ui.theme.Success
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import com.spaceclipboard.app.ui.theme.Warning
import com.spaceclipboard.app.util.formatDateTime
import kotlinx.coroutines.delay

@Composable
fun DevicesScreen(viewModel: MainViewModel) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val peers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val discoveryError by viewModel.discoveryError.collectAsStateWithLifecycle()
    val checks by viewModel.deviceChecks.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    var showPairing by rememberSaveable { mutableStateOf(false) }
    var pairingHost by rememberSaveable { mutableStateOf("") }
    var pairingPort by rememberSaveable { mutableStateOf("8765") }
    var renameTarget by remember { mutableStateOf<PairedDevice?>(null) }
    var removeTarget by remember { mutableStateOf<PairedDevice?>(null) }
    var focusApplied by rememberSaveable { mutableStateOf(false) }
    val addFocus = remember { FocusRequester() }
    val testRequesters = remember { mutableMapOf<String, FocusRequester>() }

    fun openPairing(host: String = "", port: String = "8765") {
        viewModel.dismissPairing()
        pairingHost = host
        pairingPort = port
        showPairing = true
    }

    LaunchedEffect(Unit) {
        viewModel.startScan()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }
    LaunchedEffect(devices, scanning) {
        if (focusApplied) return@LaunchedEffect
        if (devices.isEmpty()) {
            delay(200)
            addFocus.requestFocus()
            focusApplied = true
        } else {
            delay(200)
            testRequesters[devices.first().id]?.requestFocus()
            focusApplied = true
        }
    }

    val pairedIds = devices.map { it.id }.toSet()
    val unpaired = peers.filter { it.id !in pairedIds }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "Dispositivos",
            subtitle = "Pareie uma vez. Depois, compartilhe em poucos toques ou pelo D-Pad.",
            modifier = Modifier.fillMaxWidth(),
            actions = {
                SpaceButton(
                    text = if (scanning) "Procurando..." else "Procurar na rede",
                    onClick = viewModel::startScan,
                    icon = painterResource(R.drawable.ic_refresh),
                    enabled = !scanning,
                )
                SpaceButton(
                    text = "Adicionar",
                    onClick = { openPairing() },
                    icon = painterResource(R.drawable.ic_add),
                    variant = ButtonVariant.Primary,
                    focusRequester = addFocus,
                )
            },
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
        ) {
            if (devices.isEmpty()) {
                item(key = "empty-paired") {
                    EmptyState(
                        title = "Nenhum dispositivo pareado",
                        message = "Abra o Space Clipboard no outro dispositivo, exiba o QR Code e adicione aqui. A mesma rede Wi-Fi é necessária.",
                        icon = painterResource(R.drawable.ic_devices),
                        action = {
                            SpaceButton(
                                text = "Parear dispositivo",
                                onClick = { openPairing() },
                                icon = painterResource(R.drawable.ic_qr),
                                variant = ButtonVariant.Primary,
                            )
                        },
                    )
                }
            } else {
                item(key = "paired-title") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("DISPOSITIVOS PAREADOS")
                        Text(
                            text = "${devices.size} conectado${if (devices.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }
                items(devices, key = { it.id }) { device ->
                    val requester = remember(device.id) { FocusRequester() }
                    androidx.compose.runtime.SideEffect {
                        testRequesters[device.id] = requester
                    }
                    DeviceCard(
                        device = device,
                        check = checks[device.id] ?: DeviceCheckUi.Idle,
                        testFocus = requester,
                        onTest = { viewModel.testDevice(device) },
                        onRename = { renameTarget = device },
                        onRemove = { removeTarget = device },
                    )
                }
            }

            item(key = "network-title") {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("ENCONTRADOS NA REDE", color = if (scanning) Cyan else TextMuted)
                    if (scanning) {
                        Text("Procurando...", style = MaterialTheme.typography.bodySmall, color = Cyan)
                    }
                }
            }
            if (unpaired.isEmpty()) {
                item(key = "network-empty") {
                    Text(
                        text = if (scanning) {
                            "Procurando dispositivos Space Clipboard nesta rede..."
                        } else {
                            "Nenhum dispositivo novo encontrado. Verifique se o receptor está aberto e na mesma rede Wi-Fi."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(unpaired, key = { "peer-${it.id}" }) { peer ->
                    DiscoveredPeerCard(
                        peer = peer,
                        onPair = { openPairing(host = peer.host, port = peer.port.toString()) },
                    )
                }
            }

            if (!discoveryError.isNullOrBlank()) {
                item(key = "discovery-error") {
                    Text(
                        text = discoveryError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (!scanning && unpaired.isEmpty() && devices.isNotEmpty()) {
                item(key = "ap-isolation") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Warning.copy(alpha = 0.08f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wifi),
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Se nada aparecer, a rede Wi-Fi pode ter isolamento entre clientes (AP Isolation). Nesse caso, use o QR Code ou desative o isolamento no roteador.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }

    if (showPairing) {
        PairingDialog(
            state = pairingState,
            hasCamera = viewModel.hasCamera,
            onDismiss = {
                showPairing = false
                viewModel.dismissPairing()
            },
            onScanResult = viewModel::pairFromQr,
            onManualPair = { host, port, code -> viewModel.pairManual(host, port, code) },
            initialHost = pairingHost,
            initialPort = pairingPort,
        )
    }

    renameTarget?.let { device ->
        TextPromptDialog(
            title = "Renomear dispositivo",
            label = "Nome",
            initialValue = device.name,
            confirmLabel = "Salvar nome",
            onConfirm = { newName ->
                viewModel.renameDevice(device, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
            helper = "O nome é usado apenas neste dispositivo.",
        )
    }

    removeTarget?.let { device ->
        ConfirmDialog(
            title = "Remover ${device.name}?",
            message = "A credencial deste dispositivo será revogada. Será necessário parear novamente para enviar ou receber.",
            confirmLabel = "Remover",
            danger = true,
            onDismiss = { removeTarget = null },
            onConfirm = {
                viewModel.removeDevice(device)
                removeTarget = null
            },
        )
    }
}

@Composable
private fun DeviceCard(
    device: PairedDevice,
    check: DeviceCheckUi,
    testFocus: FocusRequester,
    onTest: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    SpacePanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 580.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeviceInfo(device = device, check = check)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SpaceButton(
                            text = "Testar",
                            onClick = onTest,
                            icon = painterResource(R.drawable.ic_refresh),
                            focusRequester = testFocus,
                            enabled = check != DeviceCheckUi.Checking,
                            modifier = Modifier.weight(1f),
                        )
                        SpaceButton(
                            text = "Renomear",
                            onClick = onRename,
                            variant = ButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SpaceButton(
                        text = "Remover dispositivo",
                        onClick = onRemove,
                        variant = ButtonVariant.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DeviceInfo(device = device, check = check, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SpaceButton(
                            text = "Testar",
                            onClick = onTest,
                            icon = painterResource(R.drawable.ic_refresh),
                            focusRequester = testFocus,
                            enabled = check != DeviceCheckUi.Checking,
                        )
                        SpaceButton(
                            text = "Renomear",
                            onClick = onRename,
                            variant = ButtonVariant.Ghost,
                        )
                        SpaceButton(
                            text = "Remover",
                            onClick = onRemove,
                            variant = ButtonVariant.Danger,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfo(
    device: PairedDevice,
    check: DeviceCheckUi,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            BoxedDeviceIcon()
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (check) {
                is DeviceCheckUi.Online -> StatusPill("Online", Success)
                is DeviceCheckUi.Offline -> StatusPill("Offline", Error)
                DeviceCheckUi.Checking -> StatusPill("Testando", Cyan)
                DeviceCheckUi.Idle -> Unit
            }
        }
        Text(
            text = "${device.host}:${device.port} · protocolo v${device.protocol}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = device.lastSeen?.let { "Visto em ${formatDateTime(it)}" } ?: "Ainda não testado",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        when (check) {
            is DeviceCheckUi.Online -> Text(
                check.message,
                style = MaterialTheme.typography.bodySmall,
                color = Success,
            )
            is DeviceCheckUi.Offline -> Text(
                check.message,
                style = MaterialTheme.typography.bodySmall,
                color = Error,
            )
            DeviceCheckUi.Checking -> Text(
                "Verificando conexão...",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            DeviceCheckUi.Idle -> Unit
        }
    }
}

@Composable
private fun DiscoveredPeerCard(peer: DiscoveredPeer, onPair: () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Cyan.copy(alpha = 0.035f))
            .padding(14.dp),
    ) {
        val compact = maxWidth < 560.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PeerSummary(peer)
                SpaceButton(
                    text = "Usar código",
                    onClick = onPair,
                    icon = painterResource(R.drawable.ic_qr),
                    variant = ButtonVariant.Secondary,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PeerSummary(peer, modifier = Modifier.weight(1f))
                SpaceButton(
                    text = "Usar código",
                    onClick = onPair,
                    icon = painterResource(R.drawable.ic_qr),
                    variant = ButtonVariant.Secondary,
                )
            }
        }
    }
}

@Composable
private fun PeerSummary(peer: DiscoveredPeer, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxedDeviceIcon(tint = Cyan)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                peer.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${peer.host}:${peer.port} · na mesma rede",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxedDeviceIcon(tint: androidx.compose.ui.graphics.Color = Cyan) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(35.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_devices),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}
