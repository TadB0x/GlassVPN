package com.glassvpn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glassvpn.app.ui.theme.GlassBorder
import com.glassvpn.app.ui.theme.GlassFill
import com.glassvpn.app.ui.theme.GlassShine

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x22FFFFFF),  // slightly brighter at top
                        Color(0x12FFFFFF),  // dim at bottom
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassShine,           // bright top border
                        GlassBorder,          // mid
                        Color(0x0AFFFFFF),    // nearly invisible bottom
                    )
                ),
                shape = shape
            )
            // Subtle inner top shine line
            .drawWithContent {
                drawContent()
                // Top edge shimmer
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x40FFFFFF),
                            Color(0x60FFFFFF),
                            Color(0x40FFFFFF),
                            Color.Transparent,
                        )
                    ),
                    start = Offset(cornerRadius.toPx() * 2, 1.5f),
                    end = Offset(size.width - cornerRadius.toPx() * 2, 1.5f),
                    strokeWidth = 1.5f
                )
            },
        content = content
    )
}
