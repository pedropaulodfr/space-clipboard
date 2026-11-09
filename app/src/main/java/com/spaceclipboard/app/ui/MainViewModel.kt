package com.spaceclipboard.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spaceclipboard.app.di.AppContainer
import com.spaceclipboard.app.domain.ClipboardValidator
import com.spaceclipboard.app.domain.TextValidation
import com.spaceclipboard.app.domain.model.AppSettings
import com.spaceclipboard.app.domain.model.HistoryItem
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.domain.model.SendOutcome
import com.spaceclipboard.app.pairing.PairingDecodeResult
import com.spaceclipboard.app.pairing.PairingPayloadCodec
import com.spaceclipboard.app.pairing.PairingResult
import com.spaceclipboard.app.sender.ConnectionTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Pairing : PairingUiState
    data class Success(val deviceName: String) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

sealed interface DeviceCheckUi {
    data object Idle : DeviceCheckUi
    data object Checking : DeviceCheckUi
    data class Online(val message: String) : DeviceCheckUi
    data class Offline(val message: String) : DeviceCheckUi
}

data class ManualSendUiState(
    val text: String = "",
    val selectedDeviceId: String? = null,
    val sending: Boolean = false,
    val resultMessage: String? = null,
    val resultSuccess: Boolean? = null,
)

class MainViewModel(
    private val container: AppContainer,
    val hasCamera: Boolean,
) : ViewModel() {
    val receiverState = container.receiverController.state
    val history = container.historyRepository.recent.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val devices = container.deviceRepository.devices.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val settings: StateFlow<AppSettings?> = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val discoveredPeers = container.discoveryController.peers
    val scanning = container.discoveryController.scanning
    val discoveryError = container.discoveryController.error

    private val _pairingState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val pairingState = _pairingState.asStateFlow()
    private val _deviceChecks = MutableStateFlow<Map<String, DeviceCheckUi>>(emptyMap())
    val deviceChecks = _deviceChecks.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    private val _manualSend = MutableStateFlow(ManualSendUiState())
    val manualSend = _manualSend.asStateFlow()

    var lastFocusedHistoryId: String? = null

    init {
        viewModelScope.launch {
            container.deviceRepository.devices.collect { list ->
                val selected = _manualSend.value.selectedDeviceId
                if (selected == null || list.none { it.id == selected }) {
                    _manualSend.update { it.copy(selectedDeviceId = list.firstOrNull()?.id) }
                }
            }
        }
    }

    fun updateManualText(text: String) {
        _manualSend.update {
            it.copy(
                text = text.take(ClipboardValidator.MAX_TEXT_BYTES),
                resultMessage = null,
                resultSuccess = null,
            )
        }
    }

    fun selectManualDevice(deviceId: String) {
        _manualSend.update { it.copy(selectedDeviceId = deviceId) }
    }

    fun pasteIntoManualText() {
        viewModelScope.launch {
            container.clipboardWriter.readText()
                .onSuccess { value ->
                    _manualSend.update {
                        it.copy(
                            text = value.take(ClipboardValidator.MAX_TEXT_BYTES),
                            resultMessage = null,
                            resultSuccess = null,
                        )
                    }
                    _messages.emit("Texto colado da área de transferência.")
                }
                .onFailure { _messages.emit(it.message ?: "Não foi possível colar.") }
        }
    }

    fun sendManualText() {
        val current = _manualSend.value
        if (current.sending) return
        val validation = ClipboardValidator.validate(current.text)
        if (validation is TextValidation.Invalid) {
            _manualSend.update {
                it.copy(resultMessage = validation.message, resultSuccess = false)
            }
            return
        }
        val valid = validation as TextValidation.Valid
        val device = devices.value.firstOrNull { it.id == current.selectedDeviceId }
        if (device == null) {
            _manualSend.update {
                it.copy(
                    resultMessage = "Escolha um dispositivo pareado para enviar.",
                    resultSuccess = false,
                )
            }
            return
        }
        _manualSend.update {
            it.copy(sending = true, resultMessage = null, resultSuccess = null)
        }
        viewModelScope.launch {
            when (val outcome = container.deviceService.send(device, valid.text, valid.type)) {
                is SendOutcome.Success -> _manualSend.update {
                    it.copy(
                        sending = false,
                        resultSuccess = true,
                        resultMessage = if (outcome.duplicate) {
                            "O receptor já tinha este conteúdo."
                        } else {
                            "Enviado para ${outcome.deviceName}."
                        },
                    )
                }
                is SendOutcome.Failure -> _manualSend.update {
                    it.copy(
                        sending = false,
                        resultSuccess = false,
                        resultMessage = outcome.message,
                    )
                }
            }
        }
    }

    fun copyText(text: String, successMessage: String = "Conteúdo copiado para a área de transferência.") {
        viewModelScope.launch {
            container.clipboardWriter.copy(text)
                .onSuccess { _messages.emit(successMessage) }
                .onFailure { _messages.emit("Não foi possível copiar o conteúdo.") }
        }
    }

    fun copyHistory(item: HistoryItem) = copyText(item.text)

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch {
            if (container.historyRepository.delete(item.id)) {
                _messages.emit("Registro excluído.")
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.historyRepository.clear()
            _messages.emit("Histórico limpo.")
        }
    }

    fun saveDeviceName(name: String) {
        viewModelScope.launch {
            runCatching { container.settingsRepository.updateDeviceName(name) }
                .onSuccess {
                    container.receiverController.restart()
                    _messages.emit("Nome do dispositivo atualizado.")
                }
                .onFailure { _messages.emit(it.message ?: "Nome inválido.") }
        }
    }

    fun savePort(portText: String) {
        val port = portText.toIntOrNull()
        if (port == null) {
            viewModelScope.launch { _messages.emit("Informe uma porta entre 1024 e 65535.") }
            return
        }
        viewModelScope.launch {
            runCatching { container.settingsRepository.updateServerPort(port) }
                .onSuccess {
                    container.receiverController.restart()
                    _messages.emit("Porta atualizada. Servidor reiniciando.")
                }
                .onFailure { _messages.emit(it.message ?: "Porta inválida.") }
        }
    }

    fun renameDevice(device: PairedDevice, newName: String) {
        viewModelScope.launch {
            runCatching { container.deviceService.rename(device, newName) }
                .onSuccess { _messages.emit("Dispositivo renomeado.") }
                .onFailure { _messages.emit(it.message ?: "Não foi possível renomear.") }
        }
    }

    fun removeDevice(device: PairedDevice) {
        viewModelScope.launch {
            container.deviceService.remove(device)
            _deviceChecks.value = _deviceChecks.value - device.id
            _messages.emit("Dispositivo removido e credencial revogada.")
        }
    }

    fun testDevice(device: PairedDevice) {
        if (_deviceChecks.value[device.id] == DeviceCheckUi.Checking) return
        _deviceChecks.value = _deviceChecks.value + (device.id to DeviceCheckUi.Checking)
        viewModelScope.launch {
            val result = container.deviceService.test(device)
            val state = when (result) {
                is ConnectionTest.Online -> DeviceCheckUi.Online(
                    if (result.relocated) "Online. Endereço atualizado." else "Online e autenticado.",
                )
                is ConnectionTest.Failure -> DeviceCheckUi.Offline(result.message)
            }
            _deviceChecks.value = _deviceChecks.value + (device.id to state)
            if (result is ConnectionTest.Failure) _messages.emit(result.message)
        }
    }

    fun retry() {
        container.receiverController.retry()
    }

    fun startScan() {
        container.discoveryController.startScan()
    }

    fun stopScan() {
        container.discoveryController.stop()
    }

    fun pairFromQr(raw: String) {
        when (val decoded = PairingPayloadCodec.decode(raw)) {
            is PairingDecodeResult.Error -> {
                _pairingState.value = PairingUiState.Error(decoded.message)
            }
            is PairingDecodeResult.Success -> {
                launchPairing { container.pairingManager.pairFromQr(decoded.payload) }
            }
        }
    }

    fun pairManual(host: String, portText: String, code: String) {
        val port = portText.toIntOrNull()
        if (port == null) {
            _pairingState.value = PairingUiState.Error("Porta inválida.")
            return
        }
        launchPairing { container.pairingManager.pairManually(host, port, code) }
    }

    private fun launchPairing(block: suspend () -> PairingResult) {
        _pairingState.value = PairingUiState.Pairing
        viewModelScope.launch {
            when (val result = block()) {
                is PairingResult.Success -> {
                    _pairingState.value = PairingUiState.Success(result.device.name)
                    _messages.emit("Pareado com ${result.device.name}.")
                }
                is PairingResult.Failure -> {
                    _pairingState.value = PairingUiState.Error(result.message)
                }
            }
        }
    }

    fun dismissPairing() {
        _pairingState.value = PairingUiState.Idle
    }

    fun markHistoryFocus(id: String) {
        lastFocusedHistoryId = id
    }
}

class MainViewModelFactory(
    private val container: AppContainer,
    private val hasCamera: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(container, hasCamera) as T
}
