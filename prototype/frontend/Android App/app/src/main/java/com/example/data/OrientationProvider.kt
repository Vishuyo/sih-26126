package com.example.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.TelemetryPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * SensorManager wrapper providing device orientation (Yaw, Pitch, Roll) at ~20Hz.
 */
class OrientationProvider(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val _telemetry = MutableStateFlow(TelemetryPacket())
    val telemetry: StateFlow<TelemetryPacket> = _telemetry.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastEmittedTime = 0L
    private val minEmitIntervalMs = 45L // ~22Hz max emission rate

    fun start() {
        if (rotationSensor != null) {
            sensorManager?.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        if (now - lastEmittedTime < minEmitIntervalMs) return
        lastEmittedTime = now

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convert radians to degrees
            val yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

            _telemetry.value = TelemetryPacket(
                timestamp = now,
                yaw = yaw,
                pitch = pitch,
                roll = roll
            )
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            val yaw = event.values[0]
            val pitch = event.values[1]
            val roll = event.values[2]
            _telemetry.value = TelemetryPacket(
                timestamp = now,
                yaw = yaw,
                pitch = pitch,
                roll = roll
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Injects synthetic telemetry for testing when running on emulators without gyro.
     */
    fun injectSimulatedOrientation(timeSec: Float) {
        val yaw = (timeSec * 15f) % 360f - 180f
        val pitch = (sin(timeSec * 1.2f) * 8f)
        val roll = (sin(timeSec * 0.8f) * 5f)
        _telemetry.value = TelemetryPacket(
            timestamp = System.currentTimeMillis(),
            yaw = yaw,
            pitch = pitch,
            roll = roll
        )
    }
}
