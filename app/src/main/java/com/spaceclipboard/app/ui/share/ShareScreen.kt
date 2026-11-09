package com.spaceclipboard.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaceclipboard.app.R
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.ui.components.ButtonVariant
import com.spaceclipboard.app.ui.components.EmptyState
import com.spaceclipboard.app.ui.components.PairingDialog
import com.spaceclipboard.app.ui.components.SectionLabel
import com.spaceclipboard.app.ui.components.SpaceButton
import com.spaceclipboard.app.ui.components.SpaceFocusable
import com.spaceclipboard.app.ui.components.SpacePanel
import com.spaceclipboard.app.ui.components.TypeBadge
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.DeepSpace
import com.spaceclipboard.app.ui.theme.Error
import com.spaceclipboard.app.ui.theme.Outline
import com.spaceclipboard.app.ui.theme.PanelRaised
import com.spaceclipboard.app.ui.theme.Success
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    var showPairing by rememberSaveable { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val pairFocus = remember { FocusRequester() }
    val firstDeviceFocus = remember { FocusRequester() }

    LaunchedEffect(state.phase) {
        delay(160)
        when (state.phase) {
            SharePhase.SUCCESS -> doneFocus.requestFocus()
            SharePhase.ERROR, SharePhase.INVALID -> retryFocus.requestFocus()
            SharePhase.NO_DEVICES -> if (showPairing) closeFocus.requestFocus() else pairFocus.requestFocus()
            SharePhase.CHOOSING -> {
                if (state.devices.size > 1) firstDeviceFocus.requestFocus()
                else closeFocus.requestFocus()
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .background(
                Brush.radialGradient(
                    colors = listOf(Cyan.copy(alpha = 0.07f), androidx.compose.ui.graphics.Color.Transparent),
                    radius = 1300f,
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ShareHeader(onClose = onClose, closeFocus = closeFocus)
            Spacer(Modifier.height(16.dp))
            ContentPreview(state)
            Spacer(Modifier.height(14.dp))
            when (state.phase) {
                SharePhase.PREPARING -> PreparingPanel()
                SharePhase.CHOOSING -> DeviceChooser(
                    devices = state.devices,
                    selectedId = state.selectedId,
                    onSelect = viewModel::select,
                    onSend = {
                        state.devices.firstOrNull { it.id == state.selectedId }?.let(viewModel::sendTo)
                    },
                    firstDeviceFocus = firstDeviceFocus,
                )
                SharePhase.SENDING -> SendingPanel(state.sendingTo ?: "dispositivo")
                SharePhase.SUCCESS -> SuccessPanel(
                    deviceName = state.successDevice ?: "dispositivo",
                    duplicate = state.message != null,
                    doneFocus = doneFocus,
                    onDone = onClose,
                    onOpenApp = onOpenApp,
                )
                SharePhase.ERROR -> ErrorPanel(
                    message = state.message ?: "Não foi possível enviar o conteúdo.",
                    retryFocus = retryFocus,
                    onRetry = viewModel::retry,
                    onChoose = viewModel::chooseAnother,
                    canChoose = state.devices.size > 1,
                    onPair = { showPairing = true },
                )
                SharePhase.NO_DEVICES -> NoDevicesPanel(
                    pairFocus = pairFocus,
                    onPair = { showPairing = true },
                    onOpenApp = onOpenApp,
                    hasCamera = viewModel.hasCamera,
                )
                SharePhase.INVALID -> InvalidPanel(
                    message = state.message ?: "O conteúdo compartilhado não é um texto válido.",
                    retryFocus = retryFocus,
                    onClose = onClose,
                )
            }
            Spacer(Modifier.height(20.dp))
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
        )
    }
}

@Composable
private fun ShareHeader(onClose: () -> Unit, closeFocus: FocusRequester) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Cyan.copy(alpha = 0.12f))
                    .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clipboard),
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Space Clipboard",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Compartilhar conteúdo",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SpaceButton(
            text = "Fechar",
            onClick = onClose,
            variant = ButtonVariant.Ghost,
            focusRequester = closeFocus,
        )
    }
}

@Composable
private fun ContentPreview(state: ShareUiState) {
    SpacePanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("CONTEÚDO COMPARTILHADO")
            TypeBadge(state.type)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = state.text.ifBlank { "Nenhum texto válido recebido." },
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreparingPanel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(PanelRaised)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = null,
            tint = Cyan,
            modifier = Modifier.size(20.dp),
        )
        Text("Buscando dispositivos pareados...", color = TextSecondary)
    }
}

@Composable
private fun DeviceChooser(
    devices: List<PairedDevice>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onSend: () -> Unit,
    firstDeviceFocus: FocusRequester,
) {
    SpacePanel(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("ESCOLHA O DESTINO")
        Spacer(Modifier.height(7.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEachIndexed { index, device ->
                val selected = device.id == selectedId
                SpaceFocusable(
                    onClick = { onSelect(device.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp),
                    focusRequester = if (index == 0) firstDeviceFocus else null,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .border(
                                    if (selected) 6.dp else 1.dp,
                                    if (selected) Cyan else Outline,
                                    RoundedCornerShape(999.dp),
                                ),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                device.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${device.host}:${device.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                        if (selected) {
                            Text("SELEcionado".uppercase(), style = MaterialTheme.typography.labelSmall, color = Cyan)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            SpaceButton(
                text = "Enviar",
                onClick = onSend,
                icon = painterResource(R.drawable.ic_send),
                variant = ButtonVariant.Primary,
                enabled = selectedId != null && devices.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun SendingPanel(deviceName: String) {
    SpacePanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text("Enviando para $deviceName", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Comunicação direta pela rede local.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SuccessPanel(
    deviceName: String,
    duplicate: Boolean,
    doneFocus: FocusRequester,
    onDone: () -> Unit,
    onOpenApp: () -> Unit,
) {
    SpacePanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(38.dp),
            )
            Column {
                Text("Enviado para $deviceName", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    if (duplicate) "O receptor já tinha este conteúdo." else "Recebido e copiado para a área de transferência.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
        ) {
            SpaceButton(
                text = "Abrir Space Clipboard",
                onClick = onOpenApp,
                variant = ButtonVariant.Ghost,
            )
            SpaceButton(
                text = "Concluir",
                onClick = onDone,
                icon = painterResource(R.drawable.ic_check_circle),
                variant = ButtonVariant.Primary,
                focusRequester = doneFocus,
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    onChoose: () -> Unit,
    canChoose: Boolean,
    onPair: () -> Unit,
) {
    SpacePanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = Error,
                modifier = Modifier.size(28.dp),
            )
            Text(message, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Verifique se o Space Clipboard está aberto no receptor e se os dois estão na mesma rede Wi-Fi.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            SpaceButton(text = "Parear outro", onClick = onPair, variant = ButtonVariant.Ghost)
            if (canChoose) {
                SpaceButton(text = "Escolher outro", onClick = onChoose)
            }
            SpaceButton(
                text = "Tentar novamente",
                onClick = onRetry,
                icon = painterResource(R.drawable.ic_refresh),
                variant = ButtonVariant.Primary,
                focusRequester = retryFocus,
            )
        }
    }
}

@Composable
private fun NoDevicesPanel(
    pairFocus: FocusRequester,
    onPair: () -> Unit,
    onOpenApp: () -> Unit,
    hasCamera: Boolean,
) {
    SpacePanel(modifier = Modifier.fillMaxWidth()) {
        EmptyState(
            title = "Nenhum dispositivo pareado",
            message = if (hasCamera) {
                "Abra o receptor no outro dispositivo e leia o QR Code para parear. Depois o envio é automático."
            } else {
                "Abra o receptor no outro dispositivo e informe o IP e o código de 6 dígitos."
            },
            icon = painterResource(R.drawable.ic_qr),
            action = {
                SpaceButton(
                    text = "Parear dispositivo",
                    onClick = onPair,
                    icon = painterResource(R.drawable.ic_qr),
                    variant = ButtonVariant.Primary,
                    focusRequester = pairFocus,
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SpaceButton(
                text = "Abrir o app",
                onClick = onOpenApp,
                variant = ButtonVariant.Ghost,
            )
        }
    }
}

@Composable
private fun InvalidPanel(message: String, retryFocus: FocusRequester, onClose: () -> Unit) {
    SpacePanel(modifier = Modifier.fillMaxWidth()) {
        Text("Conteúdo inválido", style = MaterialTheme.typography.headlineSmall, color = Error)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            SpaceButton(
                text = "Fechar",
                onClick = onClose,
                focusRequester = retryFocus,
            )
        }
    }
}
