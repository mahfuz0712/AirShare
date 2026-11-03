package com.mux.android.airshare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.data.Files
import com.mux.android.airshare.data.HistItem
import com.mux.android.airshare.data.HistoryStore
import com.mux.android.airshare.util.copyText
import com.mux.android.airshare.util.openFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("dd MMM  HH:mm", Locale.getDefault())

@Composable
fun LogScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    var open by remember { mutableStateOf(-1L) }
    var sure by remember { mutableStateOf(false) }
    val list = HistoryStore.items.toList()

    Column(Modifier.fillMaxSize()) {
        HudPanel(Modifier.fillMaxWidth(), "HISTORY") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText("${list.size} TRANSFERS", color = c.primary, size = 14.sp, bold = true, modifier = Modifier.weight(1f))
                HudButton(
                    if (sure) "SURE?" else "CLEAR ALL", Modifier.width(96.dp).height(38.dp), KeyKind.DANGER,
                    selected = sure, enabled = list.isNotEmpty(), fontSize = 11.sp
                ) {
                    if (sure) {
                        HistoryStore.clear()
                        sure = false
                    } else {
                        sure = true
                    }
                }
            }
            HudText("Files are not deleted when you clear the history.", color = c.dim, size = 9.sp)
        }
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) EmptyNote("NOTHING SENT OR RECEIVED YET")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(list, key = { it.time.toString() + it.peer + it.names }) { h: HistItem ->
                val isOpen = open == h.time
                val tint = when (h.result) {
                    "DONE" -> c.good
                    "REJECTED" -> c.accent
                    else -> c.danger
                }
                HudItem(Modifier.fillMaxWidth(), selected = isOpen, onClick = { open = if (isOpen) -1L else h.time }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HudText(if (h.outgoing) "↗" else "↙", color = if (h.outgoing) c.accent else c.primary, size = 22.sp, bold = true, modifier = Modifier.width(30.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(h.names, color = c.text, size = 14.sp, bold = true, maxLines = 1)
                            HudText((if (h.outgoing) "TO " else "FROM ") + h.peer + (if (h.bytes > 0) "  ·  " + Files.fmt(h.bytes) else ""), color = c.dim, size = 10.sp, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            HudText(h.result, color = tint, size = 10.sp, bold = true)
                            HudText(fmt.format(Date(h.time)), color = c.dim, size = 9.sp)
                        }
                    }
                    if (isOpen) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HudButton("OPEN", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, enabled = h.uris.isNotEmpty(), fontSize = 11.sp) {
                                openFile(ctx, h.uris[0], h.mimes.getOrElse(0) { "*/*" })
                            }
                            if (h.text.isNotEmpty()) {
                                HudButton("COPY TEXT", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp) { copyText(ctx, h.text) }
                            }
                            HudButton("DELETE", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 11.sp) { HistoryStore.remove(h) }
                        }
                        if (h.text.isNotEmpty()) HudText(h.text.take(200), color = c.accent, size = 11.sp)
                    }
                }
            }
        }
    }
}
