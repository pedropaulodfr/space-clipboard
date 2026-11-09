package com.spaceclipboard.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaceclipboard.app.R
import com.spaceclipboard.app.domain.model.HistoryItem
import com.spaceclipboard.app.domain.model.ReceiverPhase
import com.spaceclipboard.app.domain.model.ReceiverUiState
import com.spaceclipboard.app.ui.MainViewModel
import com.spaceclipboard.app.ui.components.ButtonVariant
import com.spaceclipboard.app.ui.components.CodeDisplay
import com.spaceclipboard.app.ui.components.FeedbackBanner
import com.spaceclipboard.app.ui.components.LabeledValue
import com.spaceclipboard.app.ui.components.SectionLabel
import com.spaceclipboard.app.ui.components.SpaceButton
import com.spaceclipboard.app.ui.components.SpacePanel
import com.spaceclipboard.app.ui.components.StatusPill
import com.spaceclipboard.app.ui.components.TypeBadge
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.Error
import com.spaceclipboard.app.ui.theme.Outline
import com.spaceclipboard.app.ui.theme.Panel
import com.spaceclipboard.app.ui.theme.PanelRaised
import com.spaceclipboard.app.ui.theme.Success
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import com.spaceclipboard.app.ui.theme.Warning
import com.spaceclipboard.app.util.formatDateTime
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onOpenSend: () -> Unit,
) {
    val state by viewModel.receiverState.collectAsStateWithLifecycle()
    val historyFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var focusApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.phase, state.lastReceived?.id) {
        if (focusApplied) return@LaunchedEffect
        when (state.phase) {
            ReceiverPhase.ACTIVE -> {
                delay(300)
                if (state.lastReceived != null) copyFocus.requestFocus() else historyFocus.requestFocus()
                focusApplied = true
            }
            ReceiverPhase.NO_NETWORK, ReceiverPhase.ERROR -> {
                delay(300)
                retryFocus.requestFocus()
                focusApplied = true
            }
            else -> Unit
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 660.dp && maxHeight >= 430.dp
        if (wide) {
            Column(Modifier.fillMaxSize()) {
                HomeHeader(state, onOpenSend)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatusPanel(
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        copyFocus = copyFocus,
                        historyFocus = historyFocus,
                        retryFocus = retryFocus,
                        onCopyLast = { state.lastReceived?.let(viewModel::copyHistory) },
                        onOpenHistory = onOpenHistory,
                        onRetry = viewModel::retry,
                        expanded = true,
                    )
                    PairingPanel(
                        state = state,
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                LastReceivedPanel(
                    item = state.lastReceived,
                    onCopy = { state.lastReceived?.let(viewModel::copyHistory) },
                    onOpenHistory = onOpenHistory,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HomeHeader(state, onOpenSend)
                StatusPanel(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    copyFocus = copyFocus,
                    historyFocus = historyFocus,
                    retryFocus = retryFocus,
                    onCopyLast = { state.lastReceived?.let(viewModel::copyHistory) },
                    onOpenHistory = onOpenHistory,
                    onRetry = viewModel::retry,
                    expanded = false,
                )
                PairingPanel(state = state, modifier = Modifier.fillMaxWidth())
                LastReceivedPanel(
                    item = state.lastReceived,
                    onCopy = { state.lastReceived?.let(viewModel::copyHistory) },
                    onOpenHistory = onOpenHistory,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(state: ReceiverUiState, onOpenSend: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 560.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Space Clipboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Text(
                    text = "Texto e links entre dispositivos, sem internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    StatusPill(text = statusPill(state.phase), color = statusColor(state.phase))
                    SpaceButton(
                        text = "Enviar",
                        onClick = onOpenSend,
                        icon = painterResource(R.drawable.ic_send),
                        variant = ButtonVariant.Primary,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Space ",
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Clipboard",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Cyan,
                        )
                    }
                    Text(
                        text = "Texto e links entre dispositivos, sem passar pela internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatusPill(text = statusPill(state.phase), color = statusColor(state.phase))
                    SpaceButton(
                        text = "Enviar",
                        onClick = onOpenSend,
                        icon = painterResource(R.drawable.ic_send),
                        variant = ButtonVariant.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(
    state: ReceiverUiState,
    modifier: Modifier,
    copyFocus: FocusRequester,
    historyFocus: FocusRequester,
    retryFocus: FocusRequester,
    onCopyLast: () -> Unit,
    onOpenHistory: () -> Unit,
    onRetry: () -> Unit,
    expanded: Boolean,
) {
    val color = statusColor(state.phase)
    SpacePanel(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("Receptor")
                Spacer(Modifier.height(7.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(color),
                        )
                    }
                    Text(
                        text = statusTitle(state.phase),
                        style = if (expanded) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = statusDescription(state.phase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.phase == ReceiverPhase.STARTING) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        state.feedback?.let {
            FeedbackBanner(message = it.message, success = it.success)
            Spacer(Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledValue(
                label = "Dispositivo",
                value = state.deviceName.ifBlank { "Space Clipboard" },
                modifier = Modifier.weight(1f),
            )
            LabeledValue(
                label = "Rede",
                value = if (state.network.connected) {
                    "${state.network.transportLabel} · ${state.network.interfaceName ?: "—"}"
                } else {
                    "Sem conexão"
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledValue(
                label = "Endereço IP",
                value = state.network.ipAddress ?: "Não disponível",
                modifier = Modifier.weight(1f),
            )
            LabeledValue(
                label = "Porta",
                value = state.port?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        if (expanded) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(14.dp))
        if (state.feedback == null && expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("COMO USAR", color = TextMuted)
                Text(
                    "1  Abra o Space Clipboard no aparelho que vai enviar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    "2  Toque em Compartilhar e escolha Space Clipboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    "3  Selecione este dispositivo. O texto é copiado aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (state.phase == ReceiverPhase.ACTIVE) {
                if (state.lastReceived != null) {
                    SpaceButton(
                        text = "Copiar último",
                        onClick = onCopyLast,
                        icon = painterResource(R.drawable.ic_copy),
                        variant = ButtonVariant.Primary,
                        focusRequester = copyFocus,
                    )
                }
                SpaceButton(
                    text = "Histórico",
                    onClick = onOpenHistory,
                    icon = painterResource(R.drawable.ic_history),
                    focusRequester = if (state.lastReceived == null) historyFocus else null,
                )
            } else if (state.phase != ReceiverPhase.STARTING) {
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
}

@Composable
private fun PairingPanel(state: ReceiverUiState, modifier: Modifier) {
    SpacePanel(
        modifier = modifier,
        color = PanelRaised.copy(alpha = 0.78f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("Pareamento")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Escaneie o QR Code",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        val bitmap = state.qrBitmap
        if (state.phase == ReceiverPhase.ACTIVE && bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code de pareamento do Space Clipboard",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(162.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Panel)
                    .border(1.dp, Outline, RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("CÓDIGO MANUAL", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                CodeDisplay(code = state.pairCode ?: "------")
                Text(
                    "No remetente, informe IP e código",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Válido por 10 minutos.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr),
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (state.phase) {
                        ReceiverPhase.STARTING -> "Preparando o QR Code..."
                        else -> "O QR Code aparece quando o receptor estiver ativo."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun LastReceivedPanel(
    item: HistoryItem?,
    onCopy: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    SpacePanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 520.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LastReceivedSummary(item)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        if (item != null) {
                            SpaceButton(
                                text = "Copiar",
                                onClick = onCopy,
                                icon = painterResource(R.drawable.ic_copy),
                            )
                        }
                        SpaceButton(
                            text = if (item != null) "Ver todos" else "Abrir histórico",
                            onClick = onOpenHistory,
                            icon = painterResource(R.drawable.ic_history),
                            variant = ButtonVariant.Ghost,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LastReceivedSummary(item, modifier = Modifier.weight(1f))
                    if (item != null) {
                        SpaceButton(
                            text = "Copiar",
                            onClick = onCopy,
                            icon = painterResource(R.drawable.ic_copy),
                        )
                        SpaceButton(
                            text = "Ver todos",
                            onClick = onOpenHistory,
                            icon = painterResource(R.drawable.ic_history),
                            variant = ButtonVariant.Ghost,
                        )
                    } else {
                        SpaceButton(
                            text = "Abrir histórico",
                            onClick = onOpenHistory,
                            icon = painterResource(R.drawable.ic_history),
                            variant = ButtonVariant.Ghost,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LastReceivedSummary(item: HistoryItem?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("Último conteúdo recebido")
        if (item != null) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                TypeBadge(item.type)
                Text(
                    text = "${item.senderName} · ${formatDateTime(item.receivedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = "Nenhum conteúdo recebido ainda.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
            )
        }
    }
}

private fun statusColor(phase: ReceiverPhase): Color = when (phase) {
    ReceiverPhase.ACTIVE -> Success
    ReceiverPhase.STARTING -> Cyan
    ReceiverPhase.NO_NETWORK -> Warning
    ReceiverPhase.ERROR -> Error
    ReceiverPhase.STOPPED -> TextMuted
}

private fun statusPill(phase: ReceiverPhase): String = when (phase) {
    ReceiverPhase.ACTIVE -> "Servidor ativo"
    ReceiverPhase.STARTING -> "Iniciando"
    ReceiverPhase.NO_NETWORK -> "Sem rede"
    ReceiverPhase.ERROR -> "Erro"
    ReceiverPhase.STOPPED -> "Inativo"
}

private fun statusTitle(phase: ReceiverPhase): String = when (phase) {
    ReceiverPhase.ACTIVE -> "Pronto para receber"
    ReceiverPhase.STARTING -> "Iniciando receptor"
    ReceiverPhase.NO_NETWORK -> "Dispositivo sem rede"
    ReceiverPhase.ERROR -> "Servidor indisponível"
    ReceiverPhase.STOPPED -> "Receptor inativo"
}

private fun statusDescription(phase: ReceiverPhase): String = when (phase) {
    ReceiverPhase.ACTIVE -> "Aguardando novo compartilhamento..."
    ReceiverPhase.STARTING -> "Identificando a rede e abrindo a porta local."
    ReceiverPhase.NO_NETWORK -> "Conecte-se à mesma rede Wi-Fi do dispositivo remetente."
    ReceiverPhase.ERROR -> "Verifique a rede e tente iniciar o servidor novamente."
    ReceiverPhase.STOPPED -> "Abra o aplicativo para iniciar o receptor."
}
