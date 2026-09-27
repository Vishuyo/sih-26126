package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NavigationData
import com.example.model.TelemetryPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UGV Edge Node", appName)
    }

    @Test
    fun `test telemetry serialization`() {
        val packet = TelemetryPacket(
            timestamp = 1700000000L,
            yaw = 12.4f,
            pitch = -2.1f,
            roll = 0.3f
        )
        val json = packet.toJsonString()
        assertNotNull(json)
        assert(json.contains("\"yaw\":12.4"))
        assert(json.contains("\"pitch\":-2.1"))
    }

    @Test
    fun `test navigation data deserialization`() {
        val jsonStr = """
            {
                "boxes": [{"x": 0.2, "y": 0.3, "width": 0.1, "height": 0.15, "label": "Obstacle", "confidence": 0.92}],
                "trajectory": [[0.5, 0.9], [0.52, 0.7], [0.55, 0.5]],
                "velocity": {"v": 1.5, "omega": -0.2}
            }
        """.trimIndent()
        val nav = NavigationData.fromJson(jsonStr)
        assertEquals(1, nav.boundingBoxes.size)
        assertEquals(3, nav.trajectory.size)
        assertEquals(1.5f, nav.velocity.v, 0.01f)
        assertEquals(-0.2f, nav.velocity.omega, 0.01f)
    }
}
