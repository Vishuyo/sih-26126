package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CameraProvider
import com.example.data.ConnectionStatus
import com.example.data.OrientationProvider
import com.example.data.StreamUrlStorage
import com.example.data.WebSocketClient
import com.example.model.NavigationData
import com.example.model.TelemetryPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StreamUiState(
    val serverAddress: String = "192.168.0.105:8080/stream",
    val savedUrls: List<String> = emptyList(),
    val isUrlSaved: Boolean = false,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val isSimulationMode: Boolean = false,
    val fps: Int = 0,
    val framesSent: Int = 0,
    val megabytesSent: Float = 0f,
    val isTorchOn: Boolean = false,
    val isCameraReady: Boolean = false
)

class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val streamUrlStorage = StreamUrlStorage(application.applicationContext)

    private val _uiState = MutableStateFlow(
        StreamUiState(
            serverAddress = streamUrlStorage.getLastUsedUrl(),
            savedUrls = streamUrlStorage.getSavedUrls(),
            isUrlSaved = streamUrlStorage.getSavedUrls().contains(streamUrlStorage.getLastUsedUrl().trim())
        )
    )
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    val webSocketClient = WebSocketClient(viewModelScope)
    val orientationProvider = OrientationProvider(application.applicationContext)

    val navigationData: StateFlow<NavigationData> = webSocketClient.navigationData
    val telemetry: StateFlow<TelemetryPacket> = orientationProvider.telemetry

    private var telemetrySendJob: Job? = null
    private var fpsCounterJob: Job? = null
    private var syntheticStreamJob: Job? = null

    private var frameCountInSecond = 0

    init {
        // Collect connection status
        viewModelScope.launch {
            webSocketClient.status.collect { status ->
                _uiState.value = _uiState.value.copy(
                    status = status,
                    isSimulationMode = webSocketClient.isSimulationMode
                )
            }
        }

        // Collect error messages
        viewModelScope.launch {
            webSocketClient.errorMessage.collect { error ->
                _uiState.value = _uiState.value.copy(errorMessage = error)
            }
        }

        // Collect stats
        viewModelScope.launch {
            webSocketClient.bytesSent.collect { bytes ->
                _uiState.value = _uiState.value.copy(
                    megabytesSent = bytes / (1024f * 1024f)
                )
            }
        }

        viewModelScope.launch {
            webSocketClient.framesSent.collect { frames ->
                _uiState.value = _uiState.value.copy(framesSent = frames)
            }
        }

        // 20Hz Telemetry upstream streaming loop
        startTelemetryLoop()

        // 1-second FPS calculator
        startFpsMonitor()
    }

    fun updateServerAddress(address: String) {
        val trimmed = address.trim()
        _uiState.value = _uiState.value.copy(
            serverAddress = address,
            isUrlSaved = _uiState.value.savedUrls.contains(trimmed)
        )
    }

    fun saveCurrentUrl() {
        val current = _uiState.value.serverAddress.trim()
        if (current.isNotEmpty()) {
            val updated = streamUrlStorage.saveUrl(current)
            _uiState.value = _uiState.value.copy(
                savedUrls = updated,
                isUrlSaved = true
            )
        }
    }

    fun deleteSavedUrl(url: String) {
        val updated = streamUrlStorage.deleteUrl(url)
        val current = _uiState.value.serverAddress.trim()
        _uiState.value = _uiState.value.copy(
            savedUrls = updated,
            isUrlSaved = updated.contains(current)
        )
    }

    fun selectSavedUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            serverAddress = url,
            isUrlSaved = _uiState.value.savedUrls.contains(url.trim())
        )
    }

    fun connect() {
        val addr = _uiState.value.serverAddress.trim()
        if (addr.isNotEmpty()) {
            val updated = streamUrlStorage.saveUrl(addr)
            _uiState.value = _uiState.value.copy(
                savedUrls = updated,
                isUrlSaved = true
            )
        }
        webSocketClient.connect(addr)
    }

    fun disconnect() {
        webSocketClient.disconnect()
        stopSyntheticStream()
    }

    fun toggleConnect() {
        if (_uiState.value.status == ConnectionStatus.DISCONNECTED ||
            _uiState.value.status == ConnectionStatus.ERROR
        ) {
            connect()
        } else {
            disconnect()
        }
    }

    fun toggleSimulation() {
        if (_uiState.value.isSimulationMode) {
            webSocketClient.stopSimulation()
            stopSyntheticStream()
            _uiState.value = _uiState.value.copy(isSimulationMode = false)
        } else {
            webSocketClient.startSimulation()
            startSyntheticStream()
            _uiState.value = _uiState.value.copy(isSimulationMode = true)
        }
    }

    fun onFrameCaptured(jpegBytes: ByteArray) {
        val success = webSocketClient.sendFrame(jpegBytes)
        if (success) {
            frameCountInSecond++
        }
    }

    fun setCameraReady(ready: Boolean) {
        _uiState.value = _uiState.value.copy(isCameraReady = ready)
    }

    fun setTorchState(isOn: Boolean) {
        _uiState.value = _uiState.value.copy(isTorchOn = isOn)
    }

    fun startSensors() {
        orientationProvider.start()
    }

    fun stopSensors() {
        orientationProvider.stop()
    }

    private fun startTelemetryLoop() {
        telemetrySendJob?.cancel()
        telemetrySendJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_uiState.value.status == ConnectionStatus.STREAMING ||
                    _uiState.value.status == ConnectionStatus.CONNECTED
                ) {
                    webSocketClient.sendTelemetry(orientationProvider.telemetry.value)
                }
                delay(50L) // 20Hz
            }
        }
    }

    private fun startFpsMonitor() {
        fpsCounterJob?.cancel()
        fpsCounterJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _uiState.value = _uiState.value.copy(fps = frameCountInSecond)
                frameCountInSecond = 0
            }
        }
    }

    private fun startSyntheticStream() {
        syntheticStreamJob?.cancel()
        syntheticStreamJob = viewModelScope.launch(Dispatchers.Default) {
            var tick = 0L
            while (isActive && _uiState.value.isSimulationMode) {
                // If camera preview is not actively sending frames, generate simulated synthetic frames
                if (!_uiState.value.isCameraReady) {
                    frameCountInSecond++
                }
                // Inject synthetic gyro tilt for preview
                orientationProvider.injectSimulatedOrientation(tick * 0.05f)
                tick++
                delay(50L)
            }
        }
    }

    private fun stopSyntheticStream() {
        syntheticStreamJob?.cancel()
        syntheticStreamJob = null
    }

    override fun onCleared() {
        super.onCleared()
        orientationProvider.stop()
        webSocketClient.disconnect()
        telemetrySendJob?.cancel()
        fpsCounterJob?.cancel()
        syntheticStreamJob?.cancel()
    }
}
