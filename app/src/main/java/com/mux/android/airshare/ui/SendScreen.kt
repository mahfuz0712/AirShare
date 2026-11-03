package com.mux.android.airshare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.AppSettings
import com.mux.android.airshare.data.Files
import com.mux.android.airshare.data.Nav
import com.mux.android.airshare.data.Peer
import com.mux.android.airshare.util.toast

@Composable
fun SendScreen(tablet: Boolean) {
    val ctx = LocalContext.current
    val c = Hud.colors
    var textDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        Air.addUris(uris)
    }
    val ready = remember(Nav.permTick, Nav.resumeTick, Air.radioNote) { radioReady(ctx) }

    fun pick(p: Peer) {
        val err = Air.send(p)
        if (err != null) toast(ctx, err)
    }

    if (textDialog) TextDialog(onClose = { textDialog = false })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RadioCheck()
        for (t in Air.transfers.toList()) TransferCard(t)

        // ---- what to send
        HudPanel(Modifier.fillMaxWidth(), "1 · CHOOSE WHAT TO SEND") {
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HudButton("+ FILES", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, fontSize = 12.sp) { picker.launch(arrayOf("*/*")) }
                HudButton("+ TEXT", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 12.sp) { textDialog = true }
                HudButton(
                    "CLEAR", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN,
                    enabled = Air.selection.isNotEmpty() || Air.pendingText.isNotEmpty(), fontSize = 12.sp
                ) { Air.clearSelection() }
            }
            if (Air.selection.isEmpty() && Air.pendingText.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                HudText("Nothing chosen yet. Add photos, videos, apps, documents - anything. You can also use Share > Air Share from other apps.", color = c.dim, size = 11.sp)
            }
            for (f in Air.selection.toList()) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HudText(f.name, color = c.text, size = 13.sp, bold = true, maxLines = 1)
                        HudText(Files.fmt(f.size), color = c.dim, size = 10.sp)
                    }
                    HudButton("✕", Modifier.width(40.dp).height(34.dp), KeyKind.DANGER, fontSize = 13.sp) { Air.removeSelected(f) }
                }
            }
            if (Air.pendingText.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HudText("TEXT: " + Air.pendingText.take(80), color = c.accent, size = 11.sp, maxLines = 2)
            }
            if (Air.selection.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                HudText("${Air.selection.size} FILES · ${Files.fmt(Air.selection.sumOf { it.size })}", color = c.primary, size = 11.sp, bold = true)
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- radar
        HudPanel(Modifier.fillMaxWidth(), "2 · TAP A RECEIVER") {
            HudText(
                when {
                    !ready -> "FIX THE ITEMS ABOVE TO START THE RADAR"
                    Air.peers.isEmpty() -> "SEARCHING FOR NEARBY PHONES..."
                    else -> "${Air.peers.size} RECEIVER${if (Air.peers.size > 1) "S" else ""} FOUND"
                },
                color = if (Air.peers.isEmpty()) c.dim else c.good, size = 12.sp, bold = true
            )
            RadarView(
                peers = Air.peers.toList(), myName = AppSettings.myName(), scanning = ready && Air.discovering,
                modifier = Modifier.fillMaxWidth().height(if (tablet) 340.dp else 270.dp)
            ) { pick(it) }
            if (Air.peers.isEmpty() && ready) {
                HudText(
                    "On the other phone: open Air Share and keep it on the RECEIVE tab (VISIBLE). No Wi-Fi network or internet is needed on either phone.",
                    color = c.dim, size = 10.sp
                )
            }
            for (p in Air.peers.toList()) {
                HudItem(Modifier.fillMaxWidth(), onClick = { pick(p) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, Modifier.size(38.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(p.name, color = c.text, size = 15.sp, bold = true, maxLines = 1)
                            HudText("RECEIVER · READY", color = c.good, size = 10.sp)
                        }
                        HudText("SEND ▸", color = c.accent, size = 13.sp, bold = true)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
