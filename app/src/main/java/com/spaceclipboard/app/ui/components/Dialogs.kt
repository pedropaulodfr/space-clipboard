package com.spaceclipboard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.DeepSpace
import com.spaceclipboard.app.ui.theme.Error
import com.spaceclipboard.app.ui.theme.FocusRing
import com.spaceclipboard.app.ui.theme.Outline
import com.spaceclipboard.app.ui.theme.OutlineStrong
import com.spaceclipboard.app.ui.theme.Panel
import com.spaceclipboard.app.ui.theme.PanelRaised
import com.spaceclipboard.app.ui.theme.SpaceShapes
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SpaceDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace.copy(alpha = 0.76f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Surface(
                modifier = modifier.widthIn(max = 620.dp),
                shape = SpaceShapes.large,
                color = Panel,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineStrong),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        cancelFocus.requestFocus()
    }
    SpaceDialog(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            SpaceButton(
                text = "Cancelar",
                onClick = onDismiss,
                focusRequester = cancelFocus,
            )
            SpaceButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant = if (danger) ButtonVariant.Danger else ButtonVariant.Primary,
            )
        }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = 48,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }
    SpaceDialog(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) value = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = spaceTextFieldColors(),
        )
        if (helper != null) {
            Text(helper, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            SpaceButton(text = "Cancelar", onClick = onDismiss)
            SpaceButton(
                text = confirmLabel,
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                variant = ButtonVariant.Primary,
                enabled = value.isNotBlank(),
            )
        }
    }
}

@Composable
fun spaceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = PanelRaised,
    unfocusedContainerColor = PanelRaised,
    cursorColor = Cyan,
    focusedBorderColor = FocusRing,
    unfocusedBorderColor = Outline,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = TextMuted,
    errorBorderColor = Error,
    errorLabelColor = Error,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted,
)
