package com.spaceclipboard.app.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaceclipboard.app.R
import com.spaceclipboard.app.domain.model.HistoryItem
import com.spaceclipboard.app.ui.MainViewModel
import com.spaceclipboard.app.ui.components.ButtonVariant
import com.spaceclipboard.app.ui.components.ConfirmDialog
import com.spaceclipboard.app.ui.components.EmptyState
import com.spaceclipboard.app.ui.components.ScreenTitle
import com.spaceclipboard.app.ui.components.SpaceButton
import com.spaceclipboard.app.ui.components.SpacePanel
import com.spaceclipboard.app.ui.components.TypeBadge
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import com.spaceclipboard.app.util.formatDateTime
import kotlinx.coroutines.delay

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var focusApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(history) {
        if (history.isEmpty() || focusApplied) return@LaunchedEffect
        val targetId = viewModel.lastFocusedHistoryId?.takeIf { id -> history.any { it.id == id } }
            ?: history.first().id
        val index = history.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        listState.scrollToItem(index)
        delay(180)
        requesters[targetId]?.requestFocus()
        focusApplied = true
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            title = "Histórico",
            subtitle = "Os últimos 20 conteúdos recebidos ficam somente neste dispositivo.",
            modifier = Modifier.fillMaxWidth(),
            actions = {
                if (history.isNotEmpty()) {
                    SpaceButton(
                        text = "Limpar histórico",
                        onClick = { showClearDialog = true },
                        icon = painterResource(R.drawable.ic_delete),
                        variant = ButtonVariant.Danger,
                    )
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    title = "Nada por aqui ainda",
                    message = "Abra o receptor e compartilhe um texto ou link. O último conteúdo também é copiado automaticamente.",
                    icon = painterResource(R.drawable.ic_history),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                items(history, key = { it.id }) { item ->
                    val requester = remember(item.id) { FocusRequester() }
                    SideEffect { requesters[item.id] = requester }
                    HistoryCard(
                        item = item,
                        copyFocus = requester,
                        onCopy = { viewModel.copyHistory(item) },
                        onDelete = { viewModel.deleteHistory(item) },
                        modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) viewModel.markHistoryFocus(item.id)
                        },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Limpar todo o histórico?",
            message = "Os 20 registros locais serão apagados. Esta ação não pode ser desfeita.",
            confirmLabel = "Limpar tudo",
            danger = true,
            onDismiss = { showClearDialog = false },
            onConfirm = {
                showClearDialog = false
                viewModel.clearHistory()
            },
        )
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    copyFocus: FocusRequester,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpacePanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TypeBadge(item.type)
            Text(
                text = item.senderName,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDateTime(item.receivedAt),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            SpaceButton(
                text = "Copiar",
                onClick = onCopy,
                icon = painterResource(R.drawable.ic_copy),
                focusRequester = copyFocus,
                variant = ButtonVariant.Ghost,
            )
            SpaceButton(
                text = "Excluir",
                onClick = onDelete,
                icon = painterResource(R.drawable.ic_delete),
                variant = ButtonVariant.Ghost,
            )
        }
    }
}
