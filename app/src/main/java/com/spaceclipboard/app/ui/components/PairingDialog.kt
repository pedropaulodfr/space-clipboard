package com.spaceclipboard.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.spaceclipboard.app.R
import com.spaceclipboard.app.ui.PairingUiState
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.Error
import com.spaceclipboard.app.ui.theme.Success
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun PairingDialog(
    state: PairingUiState,
    hasCamera: Boolean,
    onDismiss: () -> Unit,
    onScanResult: (String) -> Unit,
    onManualPair: (host: String, port: String, code: String) -> Unit,
    initialHost: String = "",
    initialPort: String = "8765",
) {
    if (state is PairingUiState.Success) {
        LaunchedEffect(Unit) {
            delay(450)
            onDismiss()
        }
        return
    }
    val context = LocalContext.current
    var host by rememberSaveable(initialHost) { mutableStateOf(initialHost) }
    var port by rememberSaveable(initialPort) { mutableStateOf(initialPort) }
    var code by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val busy = state is PairingUiState.Pairing
    val manualFocus = remember { FocusRequester() }
    val scanFocus = remember { FocusRequester() }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            onScanResult(result.contents)
        }
    }
    fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Aponte para o QR Code exibido no receptor")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) launchScanner() else localError = "Permissão de câmera necessária para ler o QR Code."
    }

    LaunchedEffect(hasCamera) {
        delay(140)
        if (hasCamera && !busy) scanFocus.requestFocus() else manualFocus.requestFocus()
    }

    SpaceDialog(
        onDismiss = { if (!busy) onDismiss() },
        dismissOnBackPress = !busy,
        dismissOnClickOutside = !busy,
    ) {
        Text(
            text = "Parear dispositivo",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Text(
            text = "Leia o QR Code exibido no outro dispositivo ou informe o endereço e o código de 6 dígitos.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        if (hasCamera) {
            SpaceButton(
                text = if (permissionGranted) "Ler QR Code" else "Permitir câmera e ler QR",
                onClick = {
                    localError = null
                    if (permissionGranted) launchScanner()
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                icon = painterResource(R.drawable.ic_qr),
                variant = ButtonVariant.Primary,
                enabled = !busy,
                focusRequester = scanFocus,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Este dispositivo não tem câmera. Use o código exibido no receptor.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.take(64) },
                label = { Text("IP do receptor") },
                placeholder = { Text("192.168.0.10") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier
                    .weight(1.6f)
                    .then(if (!hasCamera) Modifier.focusRequester(manualFocus) else Modifier),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = spaceTextFieldColors(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { value -> port = value.filter(Char::isDigit).take(5) },
                label = { Text("Porta") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.weight(0.8f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = spaceTextFieldColors(),
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
            label = { Text("Código de pareamento") },
            placeholder = { Text("000000") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = spaceTextFieldColors(),
        )

        val error = (state as? PairingUiState.Error)?.message ?: localError
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = Error)
        }
        if (busy) {
            Text("Pareando com segurança...", color = TextSecondary)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.End),
        ) {
            SpaceButton(text = "Cancelar", onClick = onDismiss, enabled = !busy)
            SpaceButton(
                text = "Parear",
                onClick = {
                    localError = null
                    when {
                        host.isBlank() -> localError = "Informe o IP do receptor."
                        code.length != 6 -> localError = "Informe o código de 6 dígitos."
                        else -> onManualPair(host.trim(), port, code)
                    }
                },
                icon = painterResource(R.drawable.ic_shield),
                variant = ButtonVariant.Primary,
                enabled = !busy && host.isNotBlank() && code.length == 6,
            )
        }

        if (state is PairingUiState.Pairing) {
            Text("Mantenha os dois dispositivos na mesma rede.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
