package com.mux.android.airshare.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.data.Peer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Animated radar. Every nearby receiver is a tappable blip. */
@Composable
fun RadarView(peers: List<Peer>, myName: String, scanning: Boolean, modifier: Modifier = Modifier, onPick: (Peer) -> Unit) {
    val c = Hud.colors
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse")

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val radius = minOf(w, h) / 2 - 34.dp

        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val r = minOf(size.width, size.height) / 2 - 34.dp.toPx()
            for (f in listOf(0.33f, 0.66f, 1f)) {
                drawCircle(c.primary.copy(alpha = 0.28f), r * f, center, style = Stroke(1.dp.toPx()))
            }
            drawLine(c.primary.copy(alpha = 0.18f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.dp.toPx())
            drawLine(c.primary.copy(alpha = 0.18f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())
            // expanding "ping" ring while searching
            if (scanning) {
                drawCircle(c.accent.copy(alpha = (1f - pulse) * 0.5f), r * pulse, center, style = Stroke(2.dp.toPx()))
            }
            rotate(sweep, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(c.primary.copy(alpha = 0f), c.primary.copy(alpha = 0.35f)),
                        center
                    ),
                    startAngle = -70f,
                    sweepAngle = 70f,
                    useCenter = true,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                )
            }
        }

        // me, in the middle
        Column(
            Modifier.align(Alignment.Center).offset(y = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(22.dp).background(c.primary.copy(alpha = 0.25f), CircleShape).border(2.dp, c.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) { HudText("◈", color = c.primary, size = 11.sp) }
        }
        HudText(
            myName.take(14), color = c.dim, size = 9.sp, align = TextAlign.Center, maxLines = 1,
            modifier = Modifier.align(Alignment.Center).offset(y = 22.dp)
        )

        // receivers
        peers.forEach { p ->
            val hash = p.id.hashCode()
            val angle = ((hash and 0xFFFF) % 360) * PI / 180.0
            val ring = 0.42f + (((hash ushr 16) and 3) * 0.19f)
            val x = w / 2 + radius * ring * cos(angle).toFloat()
            val y = h / 2 + radius * ring * sin(angle).toFloat()
            Column(
                Modifier
                    .offset(x - 36.dp, y - 24.dp)
                    .width(72.dp)
                    .clickable { onPick(p) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(38.dp).background(c.accent.copy(alpha = 0.22f), CircleShape).border(2.dp, c.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) { HudText(p.name.trim().firstOrNull()?.uppercase() ?: "#", color = c.accent, size = 16.sp, bold = true) }
                HudText(p.name.take(12), color = c.text, size = 10.sp, bold = true, align = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
