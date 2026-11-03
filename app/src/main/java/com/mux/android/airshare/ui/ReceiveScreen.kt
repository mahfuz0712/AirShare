package com.mux.android.airshare.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.AppSettings
import com.mux.android.airshare.data.Files
import com.mux.android.airshare.data.HistoryStore
import com.mux.android.airshare.data.Nav
import kotlinx.coroutines.delay

@Composable
fun ReceiveScreen(tablet: Boolean) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val ready = remember(Nav.permTick, Nav.resumeTick, Air.radioNote) { radioReady(ctx) }
    val t = rememberInfiniteTransition(label = "beacon")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "p")
    val on = AppSettings.visible && ready

    // new name -> advertise again under that name
    LaunchedEffect(AppSettings.deviceName) {
        delay(900)
        Air.restartAdvertising()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RadioCheck()
        for (tr in Air.transfers.toList()) TransferCard(tr)

        HudPanel(Modifier.fillMaxWidth(), "RECEIVING MODE") {
            Box(Modifier.fillMaxWidth().height(if (tablet) 200.dp else 150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val max = minOf(size.width, size.height) / 2
                    if (on) {
                        for (i in 0..2) {
                            val p = (pulse + i / 3f) % 1f
                            drawCircle(c.primary.copy(alpha = (1f - p) * 0.55f), max * p, center, style = Stroke(2.dp.toPx()))
                        }
                    }
                    drawCircle(if (on) c.primary else c.dim, 9.dp.toPx(), center)
                }
            }
            HudText(
                when {
                    !AppSettings.visible -> "○ HIDDEN - NOBODY CAN SEND YOU FILES"
                    !ready -> "○ NOT READY - FIX THE ITEMS ABOVE"
                    Air.advertising -> "● VISIBLE AS \"${AppSettings.myName()}\""
                    else -> "STARTING..."
                },
                color = if (on && Air.advertising) c.good else c.accent, size = 14.sp, bold = true
            )
            Spacer(Modifier.height(4.dp))
            HudText(
                "Keep Air Share open. Anyone near you with Air Share sees you on their radar. When they pick you, you get a request - ACCEPT or REJECT. Nothing arrives without your OK.",
                color = c.dim, size = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText("VISIBLE TO OTHERS", color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
                HudButton(
                    if (AppSettings.visible) "ON" else "OFF", Modifier.width(100.dp).height(40.dp),
                    if (AppSettings.visible) KeyKind.GOOD else KeyKind.FN, fontSize = 12.sp
                ) {
                    AppSettings.visible = !AppSettings.visible
                    Air.refreshRadio()
                }
            }
            Spacer(Modifier.height(6.dp))
            HudText("YOUR NAME ON THEIR RADAR", color = c.dim, size = 10.sp)
            HudField(AppSettings.deviceName, AppSettings.myName(), Modifier.fillMaxWidth().height(44.dp)) { AppSettings.deviceName = it.take(40) }
        }
        Spacer(Modifier.height(8.dp))

        val received = HistoryStore.items.filter { !it.outgoing }.take(5)
        if (received.isNotEmpty()) {
            HudPanel(Modifier.fillMaxWidth(), "RECENTLY RECEIVED") {
                for (h in received) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(Modifier.weight(1f)) {
                            HudText(h.names, color = c.text, size = 12.sp, maxLines = 1)
                            HudText("FROM ${h.peer}  ·  ${Files.fmt(h.bytes)}", color = c.dim, size = 10.sp, maxLines = 1)
                        }
                        HudText(h.result, color = if (h.result == "DONE") c.good else c.danger, size = 10.sp, bold = true)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        HudText("SAVED TO:  Downloads / AirShare", color = c.dim, size = 10.sp)
        Spacer(Modifier.height(10.dp))
    }
}
