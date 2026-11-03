package com.mux.android.airshare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.AppSettings
import com.mux.android.airshare.data.Files
import com.mux.android.airshare.data.Notice
import com.mux.android.airshare.data.TState
import com.mux.android.airshare.data.Transfer
import com.mux.android.airshare.util.copyText

/** The ACCEPT / REJECT request the receiver sees. */
@Composable
fun OfferDialog(t: Transfer) {
    val c = Hud.colors
    HudDialog("INCOMING REQUEST", onDismiss = { Air.reject(t) }) {
        HudText(t.peerName, color = c.primary, size = 24.sp, bold = true, maxLines = 2)
        HudText("WANTS TO SEND YOU:", color = c.dim, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        for (f in t.files.take(6)) {
            HudText("• ${f.name}", color = c.text, size = 13.sp, maxLines = 1)
            HudText("   ${Files.fmt(f.size)}", color = c.dim, size = 10.sp)
        }
        if (t.files.size > 6) HudText("+ ${t.files.size - 6} MORE FILES", color = c.dim, size = 11.sp)
        if (t.expectText) HudText("• A TEXT MESSAGE", color = c.accent, size = 13.sp)
        if (t.files.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            HudText("TOTAL ${Files.fmt(t.total)}", color = c.primary, size = 12.sp, bold = true)
        }
        if (t.digits.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            HudText("PAIRING CODE  ${t.digits}", color = c.good, size = 13.sp, bold = true)
            HudText("The sender's screen shows the same code.", color = c.dim, size = 9.sp)
        }
        DialogButtons(
            Triple("REJECT", KeyKind.DANGER) { Air.reject(t) },
            Triple("ACCEPT", KeyKind.GOOD) { Air.accept(t) }
        )
    }
}

@Composable
fun NoticeDialog(n: Notice) {
    val ctx = LocalContext.current
    val c = Hud.colors
    HudDialog(n.title, onDismiss = { Air.notice = null }) {
        HudText(n.message, color = c.text, size = 14.sp)
        if (n.copy != null) {
            DialogButtons(
                Triple("COPY", KeyKind.OP) { copyText(ctx, n.copy) },
                Triple("OK", KeyKind.GOOD) { Air.notice = null }
            )
        } else {
            DialogButtons(Triple("OK", KeyKind.GOOD) { Air.notice = null })
        }
    }
}

/** Short text / link to send together with (or instead of) files. */
@Composable
fun TextDialog(onClose: () -> Unit) {
    var text by remember { mutableStateOf(Air.pendingText) }
    HudDialog("SEND TEXT / LINK", onDismiss = onClose) {
        HudField(text, "TYPE OR PASTE", Modifier.fillMaxWidth().height(46.dp)) { text = it.take(1000) }
        DialogButtons(
            Triple("CANCEL", KeyKind.FN) { onClose() },
            Triple("ADD", KeyKind.GOOD) {
                Air.pendingText = text.trim()
                onClose()
            }
        )
    }
}

@Composable
fun IntroDialog() {
    val c = Hud.colors
    HudDialog("WELCOME", onDismiss = { AppSettings.introSeen = true }) {
        HudText("◈ AIR SHARE", color = c.primary, size = 22.sp, bold = true)
        Spacer(Modifier.height(8.dp))
        HudText("Send files to a phone next to you - no internet, no hotspot to set up, no shared Wi-Fi network.", color = c.text, size = 12.sp)
        Spacer(Modifier.height(6.dp))
        HudText("• Receiver: open the RECEIVE tab and leave it open.", color = c.text, size = 11.sp)
        HudText("• Sender: choose files, then tap the receiver on the radar.", color = c.text, size = 11.sp)
        HudText("• The receiver sees your request and can ACCEPT or REJECT. If rejected, you are told.", color = c.good, size = 11.sp)
        HudText("• Both phones need Air Share, Google Play services, Bluetooth and Wi-Fi turned ON.", color = c.accent, size = 11.sp)
        DialogButtons(Triple("LET'S GO", KeyKind.GOOD) { AppSettings.introSeen = true })
    }
}

/** Shows the offer / notice popups on top of any screen. */
@Composable
fun GlobalDialogs() {
    val offer = Air.transfers.firstOrNull { it.state == TState.PROMPT && !it.finished }
    if (offer != null) OfferDialog(offer)
    Air.notice?.let { NoticeDialog(it) }
}
