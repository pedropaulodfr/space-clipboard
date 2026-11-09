package com.spaceclipboard.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spaceclipboard.app.di.AppContainer
import com.spaceclipboard.app.domain.ClipboardValidator
import com.spaceclipboard.app.domain.TextValidation
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.domain.model.SendOutcome
import com.spaceclipboard.app.pairing.PairingDecodeResult
import com.spaceclipboard.app.pairing.PairingPayloadCodec
import com.spaceclipboard.app.pairing.PairingResult
import com.spaceclipboard.app.ui.PairingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SharePhase {
    PREPARING,
    CHOOSING,
    SENDING,
    SUCCESS,
    ERROR,
    NO_DEVICES,
    INVALID,
}

data class ShareUiState(
    val phase: SharePhase = SharePhase.PREPARING,
    val devices: List<PairedDevice> = emptyList(),
    val selectedId: String? = null,
    val text: String = "",
    val type: ContentType = ContentType.TEXT,
    val sendingTo: String? = null,
    val successDevice: String? = null,
    val message: String? = null,
    val autoSent: Boolean = false,
)

class ShareViewModel(
    private val container: AppContainer,
    sharedText: String?,
    val hasCamera: Boolean,
) : ViewModel() {
    private val validation = ClipboardValidator.validate(sharedText)
    private val validText: String = (validation as? TextValidation.Valid)?.text.orEmpty()
    private val contentType: ContentType = (validation as? TextValidation.Valid)?.type ?: ContentType.TEXT
    private val _state = MutableStateFlow(
        ShareUiState(
            phase = SharePhase.PREPARING,
            text = validText,
            type = contentType,
            message = (validation as? TextValidation.Invalid)?.message,
        ),
    )
    val state = _state.asStateFlow()
    private val _pairingState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val pairingState = _pairingState.asStateFlow()
    private var initialized = false

    init {
        if (validation is TextValidation.Invalid) {
            _state.update { it.copy(phase = SharePhase.INVALID) }
        } else {
            viewModelScope.launch {
                val initial = container.deviceRepository.devices.first()
                _state.update { it.copy(devices = initial) }
                if (!initialized) {
                    initialized = true
                    when {
                        initial.isEmpty() -> _state.update { it.copy(phase = SharePhase.NO_DEVICES) }
                        initial.size == 1 -> send(initial.first(), auto = true)
                        else -> _state.update {
                            it.copy(
                                phase = SharePhase.CHOOSING,
                                selectedId = initial.first().id,
                            )
                        }
                    }
                }
            }
            viewModelScope.launch {
                container.deviceRepository.devices.collect { list ->
                    _state.update { current ->
                        current.copy(
                            devices = list,
                            selectedId = current.selectedId?.takeIf { id -> list.any { it.id == id } }
                                ?: list.firstOrNull()?.id,
                        )
                    }
                    if (list.isEmpty() && _state.value.phase == SharePhase.CHOOSING) {
                        _state.update { it.copy(phase = SharePhase.NO_DEVICES) }
                    }
                }
            }
        }
    }

    fun sendTo(device: PairedDevice) {
        val phase = _state.value.phase
        if (phase == SharePhase.SENDING || phase == SharePhase.SUCCESS) return
        send(device, auto = false)
    }

    fun select(id: String) {
        _state.update { it.copy(selectedId = id) }
    }

    fun retry() {
        val device = _state.value.devices.firstOrNull { it.id == _state.value.selectedId }
            ?: _state.value.devices.firstOrNull()
            ?: return
        send(device, auto = false)
    }

    private fun send(device: PairedDevice, auto: Boolean) {
        _state.update {
            it.copy(
                phase = SharePhase.SENDING,
                selectedId = device.id,
                sendingTo = device.name,
                successDevice = null,
                message = null,
                autoSent = auto,
            )
        }
        viewModelScope.launch {
            when (val outcome = container.deviceService.send(device, validText, contentType)) {
                is SendOutcome.Success -> _state.update {
                    it.copy(
                        phase = SharePhase.SUCCESS,
                        successDevice = outcome.deviceName,
                        sendingTo = null,
                        message = if (outcome.duplicate) "O receptor já tinha este conteúdo." else null,
                    )
                }
                is SendOutcome.Failure -> _state.update {
                    it.copy(
                        phase = SharePhase.ERROR,
                        sendingTo = null,
                        message = outcome.message,
                    )
                }
            }
        }
    }

    fun pairFromQr(raw: String) {
        when (val decoded = PairingPayloadCodec.decode(raw)) {
            is PairingDecodeResult.Error -> _pairingState.value = PairingUiState.Error(decoded.message)
            is PairingDecodeResult.Success -> launchPairing {
                container.pairingManager.pairFromQr(decoded.payload)
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
                    _state.update {
                        it.copy(
                            devices = it.devices + result.device,
                            selectedId = result.device.id,
                            phase = SharePhase.CHOOSING,
                        )
                    }
                    send(result.device, auto = false)
                }
                is PairingResult.Failure -> _pairingState.value = PairingUiState.Error(result.message)
            }
        }
    }

    fun dismissPairing() {
        _pairingState.value = PairingUiState.Idle
    }

    fun chooseAnother() {
        _state.update { it.copy(phase = SharePhase.CHOOSING, message = null, sendingTo = null) }
    }
}

class ShareViewModelFactory(
    private val container: AppContainer,
    private val text: String?,
    private val hasCamera: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ShareViewModel(container, text, hasCamera) as T
}
