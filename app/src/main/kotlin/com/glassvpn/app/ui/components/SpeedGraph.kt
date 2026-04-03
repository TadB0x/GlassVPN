package com.glassvpn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.glassvpn.app.ui.theme.CyanAccent
import com.glassvpn.app.ui.theme.PurpleAccent

/**
 * Custom Canvas-based speed graph — dual animated line chart showing
 * download (cyan) and upload (purple) over the last 60 seconds.
 * No external chart library needed, zero API compatibility risk.
 */
@Composable
fun SpeedGraph(
    downloadHistory: List<Float>,
    uploadHistory: List<Float>,
    modifier: Modifier = Modifier
) {
    val safeDownload = if (downloadHistory.size == 60) downloadHistory else List(60) { 0f }
    val safeUpload = if (uploadHistory.size == 60) uploadHistory else List(60) { 0f }

    // Animate draw progress on data change
    val animProgress = remember { Animatable(1f) }
    LaunchedEffect(downloadHistory.lastOrNull(), uploadHistory.lastOrNull()) {
        animProgress.snapTo(0.98f)
        animProgress.animateTo(1f, animationSpec = tween(300))
    }

    val progress = animProgress.value

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val w = size.width
        val h = size.height
        val padding = 4.dp.toPx()

        // Combined max for consistent scale
        val maxVal = maxOf(
            safeDownload.maxOrNull() ?: 0f,
            safeUpload.maxOrNull() ?: 0f,
            1f
        )

        drawSpeedLine(
            values = safeDownload,
            maxVal = maxVal,
            width = w,
            height = h,
            padding = padding,
            lineColor = CyanAccent,
            fillStartColor = CyanAccent.copy(alpha = 0.35f),
            progress = progress
        )

        drawSpeedLine(
            values = safeUpload,
            maxVal = maxVal,
            width = w,
            height = h,
            padding = padding,
            lineColor = PurpleAccent,
            fillStartColor = PurpleAccent.copy(alpha = 0.25f),
            progress = progress
        )
    }
}

private fun DrawScope.drawSpeedLine(
    values: List<Float>,
    maxVal: Float,
    width: Float,
    height: Float,
    padding: Float,
    lineColor: Color,
    fillStartColor: Color,
    progress: Float
) {
    if (values.isEmpty()) return

    val plotWidth = width - padding * 2
    val plotHeight = height - padding * 2
    val stepX = plotWidth / (values.size - 1).coerceAtLeast(1)

    fun yFor(v: Float) = padding + plotHeight - (v / maxVal).coerceIn(0f, 1f) * plotHeight
    fun xFor(i: Int) = padding + i * stepX

    // Build line path (smooth via cubic bezier control points)
    val linePath = Path()
    linePath.moveTo(xFor(0), yFor(values[0]))
    for (i in 1 until values.size) {
        val prevX = xFor(i - 1)
        val prevY = yFor(values[i - 1])
        val currX = xFor(i)
        val currY = yFor(values[i])
        val cpX = (prevX + currX) / 2f
        linePath.cubicTo(cpX, prevY, cpX, currY, currX, currY)
    }

    // Fill path (close to bottom)
    val fillPath = Path()
    fillPath.addPath(linePath)
    fillPath.lineTo(xFor(values.size - 1), height)
    fillPath.lineTo(xFor(0), height)
    fillPath.close()

    // Draw fill gradient
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(fillStartColor, Color.Transparent),
            startY = padding,
            endY = height
        )
    )

    // Draw line
    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Glowing dot at the latest value
    val lastIdx = values.size - 1
    val dotX = xFor(lastIdx)
    val dotY = yFor(values[lastIdx])
    drawCircle(
        color = lineColor.copy(alpha = 0.3f),
        radius = 6.dp.toPx(),
        center = Offset(dotX, dotY)
    )
    drawCircle(
        color = lineColor,
        radius = 3.dp.toPx(),
        center = Offset(dotX, dotY)
    )
}
