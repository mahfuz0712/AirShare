package com.mux.android.airshare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.AppInfo
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.AppSettings
import com.mux.android.airshare.data.Nav
import com.mux.android.airshare.data.Perms
import com.mux.android.airshare.util.openAppSettings
import kotlinx.coroutines.launch

/** "SYS" tab: how it works, settings, permissions, updates, developer info. */
@Composable
fun SystemScreen(tablet: Boolean, onUpdateFound: (UpdateInfo) -> Unit) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { link -> runCatching { uri.openUri(link) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (tablet) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    AppPanel(); Gap(); HowPanel(); Gap(); UpdatePanel(onUpdateFound)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    DevicePanel(); Gap(); PermissionsPanel(); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
                }
            }
        } else {
            AppPanel(); Gap(); DevicePanel(); Gap(); HowPanel(); Gap(); PermissionsPanel(); Gap()
            UpdatePanel(onUpdateFound); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Gap() = Spacer(Modifier.height(8.dp))

@Composable
private fun SettingRow(label: String, hint: String?, value: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HudText(label, color = Hud.colors.text, size = 12.sp)
            if (hint != null) HudText(hint, color = Hud.colors.dim, size = 9.sp)
        }
        HudButton(value, Modifier.width(118.dp).height(38.dp), kind, fontSize = 11.sp, onClick = onClick)
    }
}

@Composable
private fun AppPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "SYSTEM") {
        HudText("◈ ${AppInfo.NAME.uppercase()}", color = c.primary, size = 28.sp, bold = true)
        HudText("VERSION ${appVersion(LocalContext.current)}", color = c.accent, size = 12.sp)
        Spacer(Modifier.height(8.dp))
        HudText("Share files with phones around you. No internet, no router and no shared Wi-Fi network needed.", color = c.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        SettingRow("COLOR THEME", null, if (AppSettings.amber) "AMBER" else "CYAN", KeyKind.ACCENT) { AppSettings.amber = !AppSettings.amber }
    }
}

@Composable
private fun DevicePanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "THIS DEVICE") {
        HudText("NAME SHOWN ON OTHER RADARS", color = c.dim, size = 10.sp)
        HudField(AppSettings.deviceName, AppSettings.myName(), Modifier.fillMaxWidth().height(44.dp)) { AppSettings.deviceName = it.take(40) }
        Spacer(Modifier.height(6.dp))
        SettingRow("VISIBLE TO OTHERS", "WHILE AIR SHARE IS OPEN", if (AppSettings.visible) "ON" else "OFF", if (AppSettings.visible) KeyKind.GOOD else KeyKind.FN) {
            AppSettings.visible = !AppSettings.visible
            Air.refreshRadio()
        }
        HudText("RECEIVED FILES ARE SAVED IN  Downloads / AirShare", color = c.dim, size = 10.sp)
    }
}

@Composable
private fun HowPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "HOW IT WORKS") {
        HudText("1. THE RECEIVER'S PHONE ANNOUNCES ITSELF OVER BLUETOOTH / BLE (NO NETWORK NEEDED).", color = c.text, size = 11.sp)
        HudText("2. THE SENDER'S RADAR SHOWS IT. TAP IT TO SEND A REQUEST.", color = c.text, size = 11.sp)
        HudText("3. THE RECEIVER TAPS ACCEPT OR REJECT. REJECTED = THE SENDER GETS A MESSAGE, NOTHING IS SENT.", color = c.text, size = 11.sp)
        HudText("4. ON ACCEPT, ANDROID SWITCHES TO WI-FI DIRECT / A TEMPORARY HOTSPOT FOR SPEED, THEN BACK. BLUETOOTH IS THE FALLBACK.", color = c.text, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        HudText(
            "Needs Google Play services on both phones (uses Google's Nearby Connections). Transfers are encrypted. Phones must be within a few metres. Both must keep the app open.",
            color = c.dim, size = 9.sp
        )
    }
}

@Composable
private fun PermissionsPanel() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Nav.permTick++ }
    val perms = remember { Perms.asked() }
    val status = remember(Nav.permTick, Nav.resumeTick) { perms.map { it to Perms.has(ctx, it) } }
    HudPanel(Modifier.fillMaxWidth(), "PERMISSIONS") {
        for ((p, ok) in status) {
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                HudText(Perms.label(p), color = c.text, size = 10.sp, modifier = Modifier.weight(1f))
                HudText(if (ok) "OK" else "MISSING", color = if (ok) c.good else c.danger, size = 11.sp, bold = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("GRANT ALL", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, enabled = status.any { !it.second }, fontSize = 12.sp) {
                val m = perms.filter { !Perms.has(ctx, it) }
                if (m.isNotEmpty()) launcher.launch(m.toTypedArray())
            }
            HudButton("APP SETTINGS", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 12.sp) { openAppSettings(ctx) }
        }
    }
}

@Composable
private fun UpdatePanel(onUpdateFound: (UpdateInfo) -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    HudPanel(Modifier.fillMaxWidth(), "UPDATES") {
        HudButton("CHECK FOR UPDATES", Modifier.fillMaxWidth().height(46.dp), KeyKind.OP, fontSize = 13.sp) {
            scope.launch {
                status = "CHECKING..."
                if (!isOnline(ctx)) {
                    status = "NO INTERNET CONNECTION"
                    return@launch
                }
                val (ok, info) = checkUpdate(ctx)
                status = when {
                    !ok -> "CHECK FAILED"
                    info == null -> "UP TO DATE"
                    else -> "NEW VERSION AVAILABLE: v${info.version}"
                }
                if (info != null) onUpdateFound(info)
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HudText(status, color = c.dim, size = 12.sp)
        }
    }
}

@Composable
private fun AuthorPanel(open: (String) -> Unit) {
    HudPanel(Modifier.fillMaxWidth(), "AUTHOR") {
        LinkRow("NAME", AppInfo.AUTHOR, null, open)
        LinkRow("EMAIL", AppInfo.EMAIL, "mailto:${AppInfo.EMAIL}", open)
        LinkRow("GITHUB", "github.com/mahfuz0712", AppInfo.GITHUB, open)
        LinkRow("PORTFOLIO", "portfolio-mahfuz0712.vercel.app", AppInfo.PORTFOLIO, open)
    }
}

@Composable
private fun MoreAppsPanel(open: (String) -> Unit) {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "MORE APPS") {
        HudText("Discover more apps by the developer on zApps Store.", color = c.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        HudButton("OPEN zApps STORE ▸", Modifier.fillMaxWidth().height(48.dp), KeyKind.ACCENT, fontSize = 14.sp) { open(AppInfo.MORE_APPS) }
    }
}

@Composable
private fun LinkRow(label: String, value: String, link: String?, open: (String) -> Unit) {
    val c = Hud.colors
    Column(
        Modifier.fillMaxWidth().then(if (link != null) Modifier.clickable { open(link) } else Modifier).padding(vertical = 6.dp)
    ) {
        HudText(label, color = c.dim, size = 10.sp)
        HudText(value, color = if (link != null) c.primary else c.text, size = 16.sp)
    }
}
