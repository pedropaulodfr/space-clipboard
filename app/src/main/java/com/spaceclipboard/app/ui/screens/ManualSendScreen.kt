package com.spaceclipboard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaceclipboard.app.R
import com.spaceclipboard.app.domain.ClipboardValidator
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.ui.MainViewModel
import com.spaceclipboard.app.ui.ManualSendUiState
import com.spaceclipboard.app.ui.components.ButtonVariant
import com.spaceclipboard.app.ui.components.EmptyState
import com.spaceclipboard.app.ui.components.FeedbackBanner
import com.spaceclipboard.app.ui.components.PairingDialog
import com.spaceclipboard.app.ui.components.ScreenTitle
import com.spaceclipboard.app.ui.components.SectionLabel
import com.spaceclipboard.app.ui.components.SpaceButton
import com.spaceclipboard.app.ui.components.SpaceFocusable
import com.spaceclipboard.app.ui.components.SpacePanel
import com.spaceclipboard.app.ui.components.TypeBadge
import com.spaceclipboard.app.ui.components.spaceTextFieldColors
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.Outline
import com.spaceclipboard.app.ui.theme.PanelRaised
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.nio.charset.StandardCharsets
import java.util.Locale

@Composable
fun ManualSendScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val manual by viewModel.manualSend.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    var showPairing by rememberSaveable { mutableStateOf(false) }
    val textFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(250)
        textFocus.requestFocus()
    }

    fun closePairing() {
        showPairing = false
        viewModel.dismissPairing()
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ScreenTitle(
            title = "Enviar texto",
            subtitle = "Digite ou cole um texto e envie para um dispositivo pareado.",
            modifier = Modifier.fillMaxWidth(),
            actions = {
                SpaceButton(
                    text = "Voltar",
                    onClick = onBack,
                    icon = painterResource(R.drawable.ic_arrow_back),
                    variant = ButtonVariant.Ghost,
                )
            },
        )
        Spacer(Modifier.height(14.dp))

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EditorPanel(
                        manual = manual,
                        textFocus = textFocus,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onTextChange = viewModel::updateManualText,
                        onPaste = viewModel::pasteIntoManualText,
                        onClear = { viewModel.updateManualText("") },
                    )
                    DestinationPanel(
                        manual = manual,
                        devices = devices,
                        modifier = Modifier
                            .width(330.dp)
                            .fillMaxHeight(),
                        onSelect = viewModel::selectManualDevice,
                        onSend = viewModel::sendManualText,
                        onPair = {
                            viewModel.dismissPairing()
                            showPairing = true
                        },
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EditorPanel(
                        manual = manual,
                        textFocus = textFocus,
                        modifier = Modifier.fillMaxWidth(),
                        onTextChange = viewModel::updateManualText,
                        onPaste = viewModel::pasteIntoManualText,
                        onClear = { viewModel.updateManualText("") },
                    )
                    DestinationPanel(
                        manual = manual,
                        devices = devices,
                        modifier = Modifier.fillMaxWidth(),
                        onSelect = viewModel::selectManualDevice,
                        onSend = viewModel::sendManualText,
                        onPair = {
                            viewModel.dismissPairing()
                            showPairing = true
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    if (showPairing) {
        PairingDialog(
            state = pairingState,
            hasCamera = viewModel.hasCamera,
            onDismiss = ::closePairing,
            onScanResult = viewModel::pairFromQr,
            onManualPair = { host, port, code -> viewModel.pairManual(host, port, code) },
        )
    }
}

@Composable
private fun EditorPanel(
    manual: ManualSendUiState,
    textFocus: FocusRequester,
    modifier: Modifier,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    val bytes = manual.text.toByteArray(StandardCharsets.UTF_8).size
    val type = if (manual.text.isBlank()) ContentType.TEXT else ClipboardValidator.detectType(manual.text)
    SpacePanel(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("TEXTO PARA ENVIAR")
            TypeBadge(type)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = manual.text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 170.dp)
                .focusRequester(textFocus),
            placeholder = {
                Text(
                    "Cole um link ou escreva qualquer texto para enviar ao projetor...",
                    color = TextMuted,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            minLines = 5,
            maxLines = 10,
            colors = spaceTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatCount(bytes)} / ${formatCount(ClipboardValidator.MAX_TEXT_BYTES)} bytes",
                style = MaterialTheme.typography.bodySmall,
                color = if (bytes > ClipboardValidator.MAX_TEXT_BYTES) {
                    MaterialTheme.colorScheme.error
                } else {
                    TextMuted
                },
            )
            Text(
                text = if (type == ContentType.URL) "Link detectado" else "Texto simples",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
        ) {
            SpaceButton(
                text = "Colar",
                onClick = onPaste,
                icon = painterResource(R.drawable.ic_copy),
                variant = ButtonVariant.Secondary,
            )
            SpaceButton(
                text = "Limpar",
                onClick = onClear,
                variant = ButtonVariant.Ghost,
                enabled = manual.text.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun DestinationPanel(
    manual: ManualSendUiState,
    devices: List<PairedDevice>,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onSend: () -> Unit,
    onPair: () -> Unit,
) {
    val bytes = manual.text.toByteArray(StandardCharsets.UTF_8).size
    val overLimit = bytes > ClipboardValidator.MAX_TEXT_BYTES
    SpacePanel(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        color = PanelRaised.copy(alpha = 0.78f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("DESTINO")
            Text(
                "${devices.size} pareado${if (devices.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            EmptyState(
                title = "Nenhum dispositivo pareado",
                message = "Abra o receptor e pareie para poder enviar textos daqui.",
                icon = painterResource(R.drawable.ic_devices),
                action = {
                    SpaceButton(
                        text = "Parear dispositivo",
                        onClick = onPair,
                        icon = painterResource(R.drawable.ic_qr),
                        variant = ButtonVariant.Primary,
                    )
                },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                devices.forEach { device ->
                    val selected = device.id == manual.selectedDeviceId
                    SpaceFocusable(
                        onClick = { onSelect(device.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(20.dp)
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check_circle),
                                    contentDescription = "Destino selecionado",
                                    tint = Cyan,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SpaceButton(
                text = "Parear outro dispositivo",
                onClick = onPair,
                icon = painterResource(R.drawable.ic_add),
                variant = ButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        manual.resultMessage?.let { message ->
            Spacer(Modifier.height(10.dp))
            FeedbackBanner(
                message = message,
                success = manual.resultSuccess == true,
            )
        }

        Spacer(Modifier.weight(1f))
        SpaceButton(
            text = if (manual.sending) "Enviando..." else "Enviar agora",
            onClick = onSend,
            icon = painterResource(R.drawable.ic_send),
            variant = ButtonVariant.Primary,
            enabled = devices.isNotEmpty() && manual.text.isNotBlank() && !overLimit && !manual.sending,
            modifier = Modifier.fillMaxWidth(),
        )
        if (manual.text.isBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Digite ou cole um texto para habilitar o envio.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        } else if (overLimit) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Reduza o texto para o limite de 64 KB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatCount(value: Int): String =
    String.format(Locale.forLanguageTag("pt-BR"), "%,d", value)
