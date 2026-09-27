package com.example.data

import android.util.Log
import com.example.model.BoundingBox
import com.example.model.NavigationData
import com.example.model.TelemetryPacket
import com.example.model.TrajectoryPoint
import com.example.model.VelocityCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING,
    ERROR
}

/**
 * WebSocket client for bidirectional streaming between Android UGV Edge Node and Laptop server.
 */
class WebSocketClient(
    private val scope: CoroutineScope
) {
    private val tag = "UGV_WebSocketClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for WebSocket
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _navigationData = MutableStateFlow(NavigationData())
    val navigationData: StateFlow<NavigationData> = _navigationData.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()

    private val _framesSent = MutableStateFlow(0)
    val framesSent: StateFlow<Int> = _framesSent.asStateFlow()

    private var simulationJob: Job? = null
    var isSimulationMode = false
        private set

    /**
     * Connect to the laptop WebSocket server (e.g. ws://192.168.1.100:8080/stream)
     */
    fun connect(url: String) {
        disconnect()

        val formattedUrl = when {
            url.startsWith("ws://") || url.startsWith("wss://") -> url
            else -> "ws://$url"
        }

        _status.value = ConnectionStatus.CONNECTING
        _errorMessage.value = null

        val request = try {
            Request.Builder()
                .url(formattedUrl)
                .build()
        } catch (e: Exception) {
            _status.value = ConnectionStatus.ERROR
            _errorMessage.value = "Invalid URL: ${e.localizedMessage}"
            return
        }

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket Connected to $formattedUrl")
                _status.value = ConnectionStatus.CONNECTED
                _errorMessage.value = null
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = NavigationData.fromJson(text)
                _navigationData.value = parsed
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                val parsed = NavigationData.fromJson(text)
                _navigationData.value = parsed
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket Closing: $code / $reason")
                _status.value = ConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket Closed: $code / $reason")
                _status.value = ConnectionStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val friendlyMessage = when (t) {
                    is SocketTimeoutException -> "Timed out connecting to $formattedUrl. Host is unreachable or offline."
                    is ConnectException -> "Connection refused at $formattedUrl. Ensure server is running on port 8080."
                    is UnknownHostException -> "Unknown host: ${t.message}. Check the IP address."
                    else -> t.localizedMessage ?: "Failed to connect to server."
                }
                Log.w(tag, "WebSocket connection failed: $friendlyMessage")
                _status.value = ConnectionStatus.ERROR
                _errorMessage.value = friendlyMessage
            }
        })
    }

    /**
     * Send binary camera frame (JPEG byte array) upstream to laptop.
     */
    fun sendFrame(jpegBytes: ByteArray): Boolean {
        if (isSimulationMode) {
            _bytesSent.value += jpegBytes.size
            _framesSent.value += 1
            if (_status.value != ConnectionStatus.STREAMING) {
                _status.value = ConnectionStatus.STREAMING
            }
            return true
        }

        val ws = webSocket ?: return false
        val byteString = jpegBytes.toByteString(0, jpegBytes.size)
        val success = ws.send(byteString)
        if (success) {
            _bytesSent.value += jpegBytes.size
            _framesSent.value += 1
            if (_status.value != ConnectionStatus.STREAMING) {
                _status.value = ConnectionStatus.STREAMING
            }
        }
        return success
    }

    /**
     * Send JSON orientation telemetry upstream to laptop.
     */
    fun sendTelemetry(telemetry: TelemetryPacket): Boolean {
        if (isSimulationMode) return true
        val ws = webSocket ?: return false
        return ws.send(telemetry.toJsonString())
    }

    /**
     * Disconnects active WebSocket.
     */
    fun disconnect() {
        stopSimulation()
        webSocket?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        if (_status.value != ConnectionStatus.ERROR) {
            _status.value = ConnectionStatus.DISCONNECTED
        }
    }

    fun clearError() {
        _errorMessage.value = null
        if (_status.value == ConnectionStatus.ERROR) {
            _status.value = ConnectionStatus.DISCONNECTED
        }
    }

    /**
     * Starts local simulated server loop.
     * Generates realistic obstacle bounding boxes, dynamic trajectory curve,
     * and velocity commands for testing without a physical laptop server.
     */
    fun startSimulation() {
        disconnect()
        isSimulationMode = true
        _status.value = ConnectionStatus.STREAMING
        _errorMessage.value = null

        simulationJob = scope.launch(Dispatchers.Default) {
            var step = 0
            while (isActive && isSimulationMode) {
                val t = step * 0.05f

                // Generate dynamic obstacle boxes
                val box1X = 0.22f + 0.04f * sin(t * 1.2f)
                val box1Y = 0.38f + 0.02f * cos(t * 0.9f)

                val box2X = 0.65f + 0.05f * cos(t * 0.7f)
                val box2Y = 0.44f + 0.03f * sin(t * 1.1f)

                val boxes = listOf(
                    BoundingBox(
                        x = box1X,
                        y = box1Y,
                        width = 0.18f,
                        height = 0.22f,
                        label = "Boulder",
                        confidence = 0.94f
                    ),
                    BoundingBox(
                        x = box2X,
                        y = box2Y,
                        width = 0.16f,
                        height = 0.26f,
                        label = "Marker Cone",
                        confidence = 0.88f
                    )
                )

                // Generate planned trajectory path (Green curve ahead of UGV)
                val steeringSway = sin(t * 0.8f) * 0.12f
                val points = mutableListOf<TrajectoryPoint>()
                val numPoints = 8
                for (i in 0 until numPoints) {
                    val progress = i / (numPoints - 1).toFloat()
                    val py = 0.92f - progress * 0.52f
                    val px = 0.50f + steeringSway * (progress * progress)
                    points.add(TrajectoryPoint(px, py))
                }

                // Commanded linear velocity (m/s) and angular rate (rad/s)
                val speed = 1.35f + 0.25f * sin(t * 0.5f)
                val omega = steeringSway * 1.5f

                _navigationData.value = NavigationData(
                    boundingBoxes = boxes,
                    trajectory = points,
                    velocity = VelocityCommand(v = speed, omega = omega),
                    timestamp = System.currentTimeMillis()
                )

                step++
                delay(50L) // 20Hz update loop
            }
        }
    }

    fun stopSimulation() {
        isSimulationMode = false
        simulationJob?.cancel()
        simulationJob = null
    }
}
