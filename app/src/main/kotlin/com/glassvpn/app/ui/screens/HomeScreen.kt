package com.glassvpn.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassvpn.app.ConnectionState
import com.glassvpn.app.UiState
import com.glassvpn.app.formatBytes
import com.glassvpn.app.formatDuration
import com.glassvpn.app.formatSpeed
import com.glassvpn.app.ui.components.ConnectButton
import com.glassvpn.app.ui.components.GlassCard
import com.glassvpn.app.ui.components.SpeedGraph
import com.glassvpn.app.ui.components.StatBadge
import com.glassvpn.app.ui.theme.BackgroundDeep
import com.glassvpn.app.ui.theme.BackgroundMid
import com.glassvpn.app.ui.theme.CyanAccent
import com.glassvpn.app.ui.theme.OutfitFontFamily
import com.glassvpn.app.ui.theme.PurpleAccent
import com.glassvpn.app.ui.theme.RedAccent
import com.glassvpn.app.ui.theme.TextMuted
import com.glassvpn.app.ui.theme.TextSecondary
import com.glassvpn.app.ui.theme.TextTertiary
import com.glassvpn.app.ui.theme.YellowAccent

@Composable
fun HomeScreen(
    uiState: UiState,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundDeep, BackgroundMid, Color(0xFF0D1F3C))
                )
            )
    ) {
        // Ambient glow blobs in background
        val isConnected = uiState.connectionState == ConnectionState.CONNECTED
        val blobColor by animateColorAsState(
            targetValue = if (isConnected) CyanAccent.copy(alpha = 0.07f)
            else RedAccent.copy(alpha = 0.04f),
            animationSpec = tween(1200),
            label = "blobColor"
        )

        // Top-center ambient blob
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopCenter)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // Bottom-left subtle blob
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .blur(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(PurpleAccent.copy(alpha = 0.05f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Top Bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF00D4FF), Color(0xFF8B5CF6)),
                                    start = Offset(0f, 0f),
                                    end = Offset(200f, 0f)
                                ),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = OutfitFontFamily,
                            )
                        ) {
                            append("GlassVPN")
                        }
                    },
                    style = TextStyle(
                        shadow = Shadow(
                            color = CyanAccent.copy(alpha = 0.4f),
                            offset = Offset(0f, 0f),
                            blurRadius = 16f
                        )
                    )
                )

                Text(
                    text = "v1.0.0",
                    color = TextMuted,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Connect Button Hero ──────────────────────────────────
            ConnectButton(
                connectionState = uiState.connectionState,
                onClick = onConnectClick,
            )

            Spacer(Modifier.height(20.dp))

            // ── Status Text ──────────────────────────────────────────
            AnimatedContent(
                targetState = uiState.connectionState,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                },
                label = "statusText"
            ) { state ->
                val (text, color) = when (state) {
                    ConnectionState.CONNECTED -> "Connected" to CyanAccent
                    ConnectionState.CONNECTING -> "Connecting..." to YellowAccent
                    ConnectionState.DISCONNECTING -> "Disconnecting..." to YellowAccent
                    ConnectionState.ERROR -> "Connection Failed" to RedAccent
                    ConnectionState.DISCONNECTED -> "Disconnected" to TextSecondary
                }
                Text(
                    text = text,
                    color = color,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    style = TextStyle(
                        shadow = if (state == ConnectionState.CONNECTED) Shadow(
                            color = CyanAccent.copy(alpha = 0.5f),
                            blurRadius = 12f
                        ) else null
                    )
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Server Info Card ─────────────────────────────────────
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanAccent.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Router,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "83.228.227.239",
                            color = Color.White,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "VLESS + REALITY • TLS 1.3",
                            color = TextTertiary,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                        )
                    }

                    // Connection quality dot
                    val dotColor by animateColorAsState(
                        targetValue = when (uiState.connectionState) {
                            ConnectionState.CONNECTED -> CyanAccent
                            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> YellowAccent
                            else -> RedAccent
                        },
                        animationSpec = tween(600),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // ── Exit IP Card (shown when connected) ──────────────────
            if (uiState.connectionState == ConnectionState.CONNECTED && uiState.exitIp.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(YellowAccent.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = YellowAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.exitIp,
                                color = Color.White,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            val location = listOfNotNull(
                                uiState.exitCity.takeIf { it.isNotEmpty() },
                                uiState.exitCountry.takeIf { it.isNotEmpty() }
                            ).joinToString(", ")
                            Text(
                                text = if (location.isNotEmpty()) location else "Unknown location",
                                color = TextTertiary,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            text = "EXIT IP",
                            color = YellowAccent.copy(alpha = 0.7f),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Stats Row: Ping | Download | Upload ──────────────────
            val pingText = if (uiState.pingMs > 0) "${uiState.pingMs}ms" else "--"
            val pingColor = when {
                uiState.pingMs < 0 -> TextMuted
                uiState.pingMs < 80 -> CyanAccent
                uiState.pingMs < 150 -> YellowAccent
                else -> RedAccent
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBadge(
                    icon = Icons.Rounded.NetworkCheck,
                    label = "PING",
                    value = pingText,
                    accentColor = pingColor,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    icon = Icons.Rounded.ArrowDownward,
                    label = "DOWN",
                    value = uiState.downloadSpeedBps.formatSpeed(),
                    accentColor = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    icon = Icons.Rounded.ArrowUpward,
                    label = "UP",
                    value = uiState.uploadSpeedBps.formatSpeed(),
                    accentColor = PurpleAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Data Usage Card ──────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DataUsage,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "SESSION DATA",
                            color = TextMuted,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = uiState.sessionDurationMs.formatDuration(),
                            color = TextTertiary,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Download total
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Downloaded",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Received",
                                    color = TextTertiary,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                text = uiState.totalDownloadBytes.formatBytes(),
                                color = CyanAccent,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = (-0.5).sp,
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(Color(0x20FFFFFF))
                                .align(Alignment.CenterVertically)
                        )

                        // Upload total
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Uploaded",
                                    tint = PurpleAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Sent",
                                    color = TextTertiary,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                text = uiState.totalUploadBytes.formatBytes(),
                                color = PurpleAccent,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = (-0.5).sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Speed Graph Card ─────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "LIVE SPEED",
                                color = TextMuted,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Legend
                            LegendDot(color = CyanAccent, label = "↓ Download")
                            LegendDot(color = PurpleAccent, label = "↑ Upload")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    SpeedGraph(
                        downloadHistory = uiState.downloadHistory,
                        uploadHistory = uiState.uploadHistory,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "last 60 seconds",
                        color = TextMuted,
                        fontFamily = OutfitFontFamily,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Footer ───────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Powered by Xray",
                    color = TextMuted,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = TextTertiary,
            fontFamily = OutfitFontFamily,
            fontSize = 10.sp,
        )
    }
}
