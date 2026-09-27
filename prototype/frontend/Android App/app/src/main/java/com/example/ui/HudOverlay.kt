package com.example.ui

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import com.example.model.BoundingBox
import com.example.model.NavigationData
import com.example.model.TelemetryPacket
import com.example.model.TrajectoryPoint
import com.example.ui.theme.HudObstacleRed
import com.example.ui.theme.HudTrajectoryGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Real-time HUD Canvas Overlay for UGV Edge Node.
 * Renders:
 * - Red bounding boxes around detected obstacles
 * - Green planned trajectory navigation path
 * - Central vehicle reticle & horizon pitch/roll ladder
 * - Waynest minimal black/white velocity & steering gauges
 */
@Composable
fun HudOverlay(
    navigationData: NavigationData,
    telemetry: TelemetryPacket,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Draw Horizon & Attitude Ladder (Pitch / Roll)
        drawAttitudeIndicator(
            width = canvasWidth,
            height = canvasHeight,
            pitch = telemetry.pitch,
            roll = telemetry.roll
        )

        // 2. Draw Center Reticle / Crosshair Anchor
        drawCenterReticle(
            centerX = canvasWidth / 2f,
            centerY = canvasHeight / 2f
        )

        // 3. Draw Green Trajectory Path
        if (navigationData.trajectory.isNotEmpty()) {
            drawTrajectoryPath(
                points = navigationData.trajectory,
                width = canvasWidth,
                height = canvasHeight,
                pulseAlpha = pulseAlpha
            )
        }

        // 4. Draw Red Bounding Boxes with Labels
        navigationData.boundingBoxes.forEach { box ->
            drawObstacleBox(
                box = box,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                pulseAlpha = pulseAlpha
            )
        }

        // 5. Draw Minimal Black/White Waynest Velocity & Steering Gauges
        drawVelocityGauges(
            v = navigationData.velocity.v,
            omega = navigationData.velocity.omega,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            isLandscape = isLandscape
        )
    }
}

/**
 * Draws Red Bounding Box around detected obstacle with corner brackets,
 * semi-transparent alert fill, and label badge.
 */
private fun DrawScope.drawObstacleBox(
    box: BoundingBox,
    canvasWidth: Float,
    canvasHeight: Float,
    pulseAlpha: Float
) {
    val left = box.x * canvasWidth
    val top = box.y * canvasHeight
    val width = box.width * canvasWidth
    val height = box.height * canvasHeight

    val rectColor = HudObstacleRed
    val strokeWidth = 3.5f

    // Soft alert tinted fill
    drawRect(
        color = rectColor.copy(alpha = 0.12f * pulseAlpha),
        topLeft = Offset(left, top),
        size = Size(width, height)
    )

    // Dashed / solid border
    drawRect(
        color = rectColor.copy(alpha = 0.85f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    )

    // High-visibility corner brackets
    val cornerLen = (width.coerceAtMost(height) * 0.25f).coerceAtLeast(16f)
    // Top-Left
    drawLine(rectColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
    drawLine(rectColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)
    // Top-Right
    drawLine(rectColor, Offset(left + width, top), Offset(left + width - cornerLen, top), strokeWidth)
    drawLine(rectColor, Offset(left + width, top), Offset(left + width, top + cornerLen), strokeWidth)
    // Bottom-Left
    drawLine(rectColor, Offset(left, top + height), Offset(left + cornerLen, top + height), strokeWidth)
    drawLine(rectColor, Offset(left, top + height), Offset(left, top + height - cornerLen), strokeWidth)
    // Bottom-Right
    drawLine(rectColor, Offset(left + width, top + height), Offset(left + width - cornerLen, top + height), strokeWidth)
    drawLine(rectColor, Offset(left + width, top + height), Offset(left + width, top + height - cornerLen), strokeWidth)

    // Obstacle label badge
    val badgeHeight = 22f
    val badgeWidth = (width * 0.85f).coerceIn(80f, 150f)
    val badgeY = (top - badgeHeight - 4f).coerceAtLeast(10f)

    drawRoundRect(
        color = Color(0xEE111418),
        topLeft = Offset(left, badgeY),
        size = Size(badgeWidth, badgeHeight),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = rectColor,
        topLeft = Offset(left, badgeY),
        size = Size(badgeWidth, badgeHeight),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = 1.2f)
    )

    // Label text using nativeCanvas
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val labelStr = "${box.label.uppercase()} ${(box.confidence * 100).toInt()}%"
        drawText(labelStr, left + 8f, badgeY + 16f, paint)
    }
}

/**
 * Draws the Green Trajectory Curve showing the planned path ahead of the UGV.
 * Enhanced with glowing gradient stroke, waypoint dots, and steering flow arrows.
 */
private fun DrawScope.drawTrajectoryPath(
    points: List<TrajectoryPoint>,
    width: Float,
    height: Float,
    pulseAlpha: Float
) {
    if (points.size < 2) return

    val path = Path()
    val pixelPoints = points.map { Offset(it.x * width, it.y * height) }

    path.moveTo(pixelPoints.first().x, pixelPoints.first().y)
    for (i in 1 until pixelPoints.size) {
        val prev = pixelPoints[i - 1]
        val curr = pixelPoints[i]
        val midX = (prev.x + curr.x) / 2f
        val midY = (prev.y + curr.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
    }
    path.lineTo(pixelPoints.last().x, pixelPoints.last().y)

    // Glowing wider halo line
    drawPath(
        path = path,
        color = HudTrajectoryGreen.copy(alpha = 0.25f * pulseAlpha),
        style = Stroke(
            width = 16f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Main sharp green path
    drawPath(
        path = path,
        color = HudTrajectoryGreen,
        style = Stroke(
            width = 5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Draw Waypoint nodes & target pin at final waypoint
    pixelPoints.forEachIndexed { index, point ->
        if (index == pixelPoints.size - 1) {
            // Target point (final destination / waypoint pin)
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = point
            )
            drawCircle(
                color = HudTrajectoryGreen,
                radius = 7f,
                center = point
            )
            drawCircle(
                color = HudTrajectoryGreen.copy(alpha = 0.4f * pulseAlpha),
                radius = 18f,
                center = point,
                style = Stroke(width = 2.5f)
            )
        } else {
            // Waypoint node
            drawCircle(
                color = Color.Black.copy(alpha = 0.7f),
                radius = 4.5f,
                center = point
            )
            drawCircle(
                color = HudTrajectoryGreen,
                radius = 3.5f,
                center = point
            )
        }
    }
}

/**
 * Draws minimal center crosshair reticle.
 */
private fun DrawScope.drawCenterReticle(centerX: Float, centerY: Float) {
    val reticleRadius = 26f
    val gap = 8f
    val armLen = 18f
    val strokeColor = Color.White.copy(alpha = 0.75f)

    // Outer circle
    drawCircle(
        color = strokeColor.copy(alpha = 0.35f),
        radius = reticleRadius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1.2f)
    )

    // Center dot
    drawCircle(
        color = Color.White,
        radius = 2.5f,
        center = Offset(centerX, centerY)
    )

    // Crosshair ticks
    drawLine(strokeColor, Offset(centerX - reticleRadius - armLen, centerY), Offset(centerX - reticleRadius + gap, centerY), 1.8f)
    drawLine(strokeColor, Offset(centerX + reticleRadius - gap, centerY), Offset(centerX + reticleRadius + armLen, centerY), 1.8f)
    drawLine(strokeColor, Offset(centerX, centerY - reticleRadius - armLen), Offset(centerX, centerY - reticleRadius + gap), 1.8f)
    drawLine(strokeColor, Offset(centerX, centerY + reticleRadius - gap), Offset(centerX, centerY + reticleRadius + armLen), 1.8f)
}

/**
 * Draws an artificial attitude horizon ladder showing vehicle pitch & roll.
 */
private fun DrawScope.drawAttitudeIndicator(
    width: Float,
    height: Float,
    pitch: Float,
    roll: Float
) {
    val centerX = width / 2f
    val centerY = height / 2f
    val pitchPixelOffset = (pitch * 3.5f).coerceIn(-120f, 120f)
    val rollRad = Math.toRadians(-roll.toDouble()).toFloat()

    val ladderY = centerY + pitchPixelOffset
    val halfWidth = 60f

    val cosR = cos(rollRad)
    val sinR = sin(rollRad)

    val p1 = Offset(centerX - halfWidth * cosR, ladderY - halfWidth * sinR)
    val p2 = Offset(centerX + halfWidth * cosR, ladderY + halfWidth * sinR)

    // Horizon line
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = p1,
        end = p2,
        strokeWidth = 1.5f
    )
}

/**
 * Minimal Black/White Waynest style velocity and steering gauges.
 */
private fun DrawScope.drawVelocityGauges(
    v: Float,
    omega: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    isLandscape: Boolean
) {
    // Positioned in top HUD strip below status header
    // In landscape, vertical space is reduced so we place it higher up (~56dp).
    val startY = if (isLandscape) 56.dp.toPx() else 110.dp.toPx()
    val cardWidth = 140.dp.toPx()
    val cardHeight = 64.dp.toPx()
    val margin = 16.dp.toPx()
    val cornerRad = 14.dp.toPx()

    // 1. Linear Velocity Gauge Card (Left)
    val leftX = margin
    drawRoundRect(
        color = Color(0xDD111418), // Waynest dark floating card
        topLeft = Offset(leftX, startY),
        size = Size(cardWidth, cardHeight),
        cornerRadius = CornerRadius(cornerRad, cornerRad)
    )
    drawRoundRect(
        color = Color(0x33FFFFFF),
        topLeft = Offset(leftX, startY),
        size = Size(cardWidth, cardHeight),
        cornerRadius = CornerRadius(cornerRad, cornerRad),
        style = Stroke(width = 1.dp.toPx())
    )

    // 2. Angular Velocity / Steering Gauge Card (Right)
    val rightX = canvasWidth - cardWidth - margin
    drawRoundRect(
        color = Color(0xDD111418),
        topLeft = Offset(rightX, startY),
        size = Size(cardWidth, cardHeight),
        cornerRadius = CornerRadius(cornerRad, cornerRad)
    )
    drawRoundRect(
        color = Color(0x33FFFFFF),
        topLeft = Offset(rightX, startY),
        size = Size(cardWidth, cardHeight),
        cornerRadius = CornerRadius(cornerRad, cornerRad),
        style = Stroke(width = 1.dp.toPx())
    )

    // Text metrics
    drawContext.canvas.nativeCanvas.apply {
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 10.dp.toPx()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val valPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 14.dp.toPx()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val textPaddingX = 16.dp.toPx()
        val titlePaddingY = 24.dp.toPx()
        val valPaddingY = 52.dp.toPx()

        // Draw Velocity
        drawText("SPEED (v)", leftX + textPaddingX, startY + titlePaddingY, titlePaint)
        val vStr = String.format(Locale.US, "%.2f m/s", v)
        drawText(vStr, leftX + textPaddingX, startY + valPaddingY, valPaint)

        // Draw Omega / Steering
        drawText("STEER (ω)", rightX + textPaddingX, startY + titlePaddingY, titlePaint)
        val sign = if (omega >= 0f) "+" else ""
        val omegaStr = String.format(Locale.US, "%s%.2f rad/s", sign, omega)
        drawText(omegaStr, rightX + textPaddingX, startY + valPaddingY, valPaint)
    }
}
