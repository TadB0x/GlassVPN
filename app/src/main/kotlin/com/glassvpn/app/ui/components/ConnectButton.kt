package com.glassvpn.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glassvpn.app.ConnectionState
import com.glassvpn.app.ui.theme.CyanAccent
import com.glassvpn.app.ui.theme.CyanAccentGlow
import com.glassvpn.app.ui.theme.RedAccent
import com.glassvpn.app.ui.theme.RedAccentGlow
import com.glassvpn.app.ui.theme.YellowAccent
import com.glassvpn.app.ui.theme.YellowAccentDim

@Composable
fun ConnectButton(
    connectionState: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.DISCONNECTING

    // Colors
    val accentColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> CyanAccent
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> YellowAccent
            else -> RedAccent
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "accentColor"
    )

    val glowColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> CyanAccentGlow
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> YellowAccentDim
            else -> RedAccentGlow
        },
        animationSpec = tween(600),
        label = "glowColor"
    )

    // Press scale
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "pressScale"
    )

    // Pulse rings when connected or connecting
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1Alpha"
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    val pulse2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2Alpha"
    )

    val showPulse = isConnected || isConnecting

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(160.dp)
    ) {
        // Outer pulse ring 1
        if (showPulse) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulse1)
                    .alpha(pulse1Alpha)
                    .clip(CircleShape)
                    .border(2.dp, accentColor.copy(alpha = 0.4f), CircleShape)
            )
            // Outer pulse ring 2
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulse2)
                    .alpha(pulse2Alpha)
                    .clip(CircleShape)
                    .border(2.dp, accentColor.copy(alpha = 0.25f), CircleShape)
            )
        }

        // Glow shadow layer
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pressScale)
                .shadow(
                    elevation = if (isConnected) 32.dp else 16.dp,
                    shape = CircleShape,
                    ambientColor = glowColor,
                    spotColor = accentColor.copy(alpha = 0.5f)
                )
        )

        // Button body
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isConnected) 0.28f else 0.15f),
                            accentColor.copy(alpha = if (isConnected) 0.10f else 0.06f),
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.8f),
                            accentColor.copy(alpha = 0.2f),
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (!isConnecting) onClick()
                    }
                )
        ) {
            // Spinning arc when connecting
            if (isConnecting) {
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                    label = "rotation"
                )
            }

            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = if (isConnected) "Disconnect" else "Connect",
                tint = accentColor,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
