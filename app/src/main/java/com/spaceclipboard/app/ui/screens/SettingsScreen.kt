package com.spaceclipboard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaceclipboard.app.BuildConfig
import com.spaceclipboard.app.R
import com.spaceclipboard.app.domain.model.ReceiverPhase
import com.spaceclipboard.app.ui.MainViewModel
import com.spaceclipboard.app.ui.components.ButtonVariant
import com.spaceclipboard.app.ui.components.ConfirmDialog
import com.spaceclipboard.app.ui.components.InfoLine
import com.spaceclipboard.app.ui.components.SectionLabel
import com.spaceclipboard.app.ui.components.SpaceButton
import com.spaceclipboard.app.ui.components.SpacePanel
import com.spaceclipboard.app.ui.components.StatusPill
import com.spaceclipboard.app.ui.components.spaceTextFieldColors
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.Error
import com.spaceclipboard.app.ui.theme.Success
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import com.spaceclipboard.app.ui.theme.Warning
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenDevices: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val receiver by viewModel.receiverState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("8765") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val nameSaveFocus = remember { FocusRequester() }

    LaunchedEffect(settings) {
        if (!initialized && settings != null) {
            name = settings!!.deviceName
            port = settings!!.serverPort.toString()
            initialized = true
        }
    }
    LaunchedEffect(Unit) {
        delay(180)
        nameSaveFocus.requestFocus()
    }

    Column(Modifier.fillMaxSize()) {
        Text("Ajustes", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        Text(
            "Configuração do receptor e informações de segurança.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
        ) {
            item(key = "identity") {
                SpacePanel(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("ESTE DISPOSITIVO")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "O nome aparece no QR Code e para os dispositivos pareados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 48) name = it },
                        label = { Text("Nome do dispositivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = spaceTextFieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SpaceButton(
                            text = "Salvar nome",
                            onClick = { viewModel.saveDeviceName(name) },
                            focusRequester = nameSaveFocus,
                            enabled = name.isNotBlank(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { value -> port = value.filter(Char::isDigit).take(5) },
                        label = { Text("Porta do servidor") },
                        supportingText = {
                            Text(
                                "Padrão 8765. Se estiver ocupada, o receptor escolhe outra automaticamente.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = spaceTextFieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SpaceButton(
                            text = "Aplicar porta",
                            onClick = { viewModel.savePort(port) },
                            enabled = port.isNotBlank(),
                        )
                    }
                }
            }

            item(key = "receiver") {
                SpacePanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            SectionLabel("RECEPTOR")
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Servidor HTTP local",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                        }
                        StatusPill(
                            text = when (receiver.phase) {
                                ReceiverPhase.ACTIVE -> "Ativo"
                                ReceiverPhase.STARTING -> "Iniciando"
                                ReceiverPhase.NO_NETWORK -> "Sem rede"
                                ReceiverPhase.ERROR -> "Erro"
                                ReceiverPhase.STOPPED -> "Inativo"
                            },
                            color = when (receiver.phase) {
                                ReceiverPhase.ACTIVE -> Success
                                ReceiverPhase.STARTING -> Cyan
                                ReceiverPhase.NO_NETWORK -> Warning
                                ReceiverPhase.ERROR -> Error
                                ReceiverPhase.STOPPED -> TextMuted
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    InfoLine("Rede", receiver.network.transportLabel)
                    InfoLine("Interface", receiver.network.interfaceName ?: "—")
                    InfoLine("Endereço IP", receiver.network.ipAddress ?: "Não disponível")
                    InfoLine("Porta ativa", receiver.port?.toString() ?: "—")
                    InfoLine("Protocolo", "Space Clipboard v1")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "O servidor existe somente enquanto este aplicativo está em primeiro plano. Não há serviço em segundo plano.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SpaceButton(
                            text = "Gerenciar dispositivos",
                            onClick = onOpenDevices,
                            icon = painterResource(R.drawable.ic_devices),
                        )
                        if (receiver.phase != ReceiverPhase.ACTIVE) {
                            SpaceButton(
                                text = "Reiniciar",
                                onClick = viewModel::retry,
                                icon = painterResource(R.drawable.ic_refresh),
                                variant = ButtonVariant.Ghost,
                            )
                        }
                    }
                }
            }

            item(key = "history") {
                SpacePanel(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("HISTÓRICO")
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Até 20 conteúdos recebidos, armazenados apenas neste aparelho.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    SpaceButton(
                        text = "Limpar histórico",
                        onClick = { showClearDialog = true },
                        icon = painterResource(R.drawable.ic_delete),
                        variant = ButtonVariant.Danger,
                    )
                }
            }

            item(key = "security") {
                SpacePanel(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("SEGURANÇA", color = Cyan)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cada dispositivo recebe uma credencial própria, cifrada com AES-GCM. Quando o aparelho oferece um Android Keystore funcional, a chave fica no Keystore; em projetores com Keymaster limitado, a chave fica no sandbox privado do aplicativo. O QR Code usa um código temporário de 10 minutos e uso único.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O tráfego é HTTP na rede local, sem internet. Em redes Wi-Fi públicas ou não confiáveis, prefira uma rede privada: alguém com acesso à mesma rede pode observar o tráfego. TLS local não foi ativado para manter compatibilidade com Android 8.1 e projetores de baixo custo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning,
                    )
                }
            }

            item(key = "permissions") {
                SpacePanel(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("PERMISSÕES")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Internet e rede: comunicação local e descoberta mDNS.\n" +
                            "Wi-Fi multicast: anúncio e descoberta na rede.\n" +
                            "Câmera: opcional, apenas para ler o QR Code de pareamento.\n" +
                            "Nenhuma permissão de localização é solicitada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            item(key = "about") {
                SpacePanel(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("SOBRE")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Space Clipboard", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("Protocolo v1", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text("Android ${android.os.Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Feito para compartilhar texto e links na mesma rede, sem nuvem, sem contas e sem serviços externos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Limpar todo o histórico?",
            message = "Os registros locais serão apagados permanentemente.",
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
