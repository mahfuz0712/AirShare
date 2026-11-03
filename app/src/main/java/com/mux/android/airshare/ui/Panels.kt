package com.mux.android.airshare.ui

import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.Files
import com.mux.android.airshare.data.Nav
import com.mux.android.airshare.data.Perms
import com.mux.android.airshare.data.TState
import com.mux.android.airshare.data.Transfer
import com.mux.android.airshare.util.openAppSettings
import com.mux.android.airshare.util.openSettingsAction

/** True when everything needed to find / be found is in place. */
fun radioReady(ctx: android.content.Context): Boolean =
    Air.gmsOk() && Perms.radioMissing(ctx).isEmpty() && Perms.bluetoothOn() && Perms.wifiOn(ctx) && !Perms.locationNeededAndOff(ctx)

/** Shows what is missing (Play services, permissions, Bluetooth, Wi-Fi, location) with one-tap fixes. */
@Composable
fun RadioCheck() {
    val ctx = LocalContext.current
    val c = Hud.colors
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Nav.permTick++ }
    val state = remember(Nav.permTick, Nav.resumeTick) {
        listOf(
            Air.gmsOk(), Perms.radioMissing(ctx), Perms.bluetoothOn(), Perms.wifiOn(ctx), Perms.locationNeededAndOff(ctx)
        )
    }
    val gms = state[0] as Boolean
    @Suppress("UNCHECKED_CAST")
    val missing = state[1] as List<String>
    val bt = state[2] as Boolean
    val wifi = state[3] as Boolean
    val locOff = state[4] as Boolean

    if (!gms) {
        HudPanel(Modifier.fillMaxWidth(), "GOOGLE PLAY SERVICES REQUIRED") {
            HudText(
                "Air Share finds phones with Google's Nearby technology (Bluetooth + Wi-Fi Direct). This phone has no working Google Play services, so sharing is not possible here.",
                color = c.danger, size = 12.sp
            )
        }
        Spacer(Modifier.height(6.dp))
    } else if (missing.isNotEmpty()) {
        HudPanel(Modifier.fillMaxWidth(), "PERMISSION REQUIRED") {
            HudText("Air Share needs these to find nearby phones and send files:", color = c.text, size = 12.sp)
            Spacer(Modifier.height(4.dp))
            for (p in missing) HudText("• " + Perms.label(p), color = c.accent, size = 11.sp)
            Spacer(Modifier.height(8.dp))
            HudButton("GRANT ACCESS", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 13.sp) {
                launcher.launch(Perms.asked().filter { !Perms.has(ctx, it) }.toTypedArray())
            }
            HudButton("APP SETTINGS", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 12.sp) { openAppSettings(ctx) }
        }
        Spacer(Modifier.height(6.dp))
    } else if (!bt || !wifi || locOff) {
        HudPanel(Modifier.fillMaxWidth(), "TURN ON") {
            HudText("Bluetooth and Wi-Fi must be ON (you do NOT need to be connected to any network).", color = c.text, size = 11.sp)
            Spacer(Modifier.height(6.dp))
            if (!bt) HudButton("BLUETOOTH IS OFF - OPEN SETTINGS", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                openSettingsAction(ctx, AndroidSettings.ACTION_BLUETOOTH_SETTINGS)
            }
            if (!wifi) HudButton("WI-FI IS OFF - OPEN SETTINGS", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                openSettingsAction(ctx, AndroidSettings.ACTION_WIFI_SETTINGS)
            }
            if (locOff) HudButton("LOCATION IS OFF - OPEN SETTINGS", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                openSettingsAction(ctx, AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    if (Air.radioNote.isNotEmpty()) {
        HudText(Air.radioNote, color = c.danger, size = 10.sp)
        Spacer(Modifier.height(4.dp))
    }
}

private fun bar(pct: Int): String {
    val n = (pct / 5).coerceIn(0, 20)
    return "█".repeat(n) + "░".repeat(20 - n)
}

/** One running / finished transfer. */
@Composable
fun TransferCard(t: Transfer) {
    val c = Hud.colors
    val pct = if (t.total > 0) (t.sent * 100 / t.total).toInt() else if (t.state == TState.DONE) 100 else 0
    val color = when (t.state) {
        TState.DONE -> c.good
        TState.REJECTED, TState.FAILED, TState.CANCELED -> c.danger
        else -> c.accent
    }
    HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), if (t.outgoing) "SENDING" else "RECEIVING") {
        HudText(t.peerName, color = c.primary, size = 20.sp, bold = true, maxLines = 1)
        HudText(t.message, color = color, size = 12.sp, bold = true)
        if (t.digits.isNotEmpty() && t.active) {
            HudText("PAIRING CODE ${t.digits} - SHOULD MATCH THE OTHER PHONE", color = c.dim, size = 10.sp)
        }
        if (t.files.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            for (f in t.files.take(4)) {
                val fp = if (f.size > 0) (if (f.done) 100 else (f.bytes * 100 / f.size).toInt()) else 0
                HudText(f.name, color = c.text, size = 12.sp, maxLines = 1)
                HudText(bar(fp) + "  " + Files.fmt(f.size), color = if (f.done) c.good else c.primary, size = 10.sp, maxLines = 1)
            }
            if (t.files.size > 4) HudText("+ ${t.files.size - 4} MORE FILES", color = c.dim, size = 10.sp)
            Spacer(Modifier.height(4.dp))
            HudText(
                "$pct%  ·  ${Files.fmt(t.sent)} / ${Files.fmt(t.total)}" + if (t.state == TState.TRANSFERRING && t.speed > 0) "  ·  ${Files.fmt(t.speed)}/s" else "",
                color = c.dim, size = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        if (t.active) {
            HudButton("CANCEL", Modifier.fillMaxWidth().height(40.dp), KeyKind.DANGER, fontSize = 12.sp) { Air.cancel(t) }
        } else {
            HudButton("OK", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 12.sp) { Air.dismiss(t) }
        }
    }
}
