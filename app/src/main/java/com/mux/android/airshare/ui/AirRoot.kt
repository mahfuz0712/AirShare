package com.mux.android.airshare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.AppSettings
import com.mux.android.airshare.data.Nav
import com.mux.android.airshare.data.Perms

enum class Tab(val label: String, val title: String) {
    SEND("SEND", "RADAR"),
    RECV("RECV", "RECEIVE"),
    LOG("LOG", "HISTORY"),
    SYS("SYS", "SYSTEM")
}

@Composable
fun AirRoot() {
    val ctx = LocalContext.current
    HudTheme(if (AppSettings.amber) AmberPalette else CyanPalette) {
        val tablet = LocalConfiguration.current.screenWidthDp >= 600
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        // ask for everything once, on first launch
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Nav.permTick++
        }
        LaunchedEffect(Unit) {
            if (!AppSettings.askedPerms) {
                AppSettings.askedPerms = true
                val m = Perms.asked().filter { !Perms.has(ctx, it) }
                if (m.isNotEmpty()) permLauncher.launch(m.toTypedArray())
            }
        }

        // the radar only searches while the SEND tab is on screen; being found works on every tab
        val current = Tab.values()[Nav.tab]
        LaunchedEffect(Nav.tab, Nav.permTick, Nav.resumeTick) {
            Air.setDiscovery(current == Tab.SEND)
        }

        val screen: @Composable () -> Unit = {
            when (current) {
                Tab.SEND -> SendScreen(tablet)
                Tab.RECV -> ReceiveScreen(tablet)
                Tab.LOG -> LogScreen()
                Tab.SYS -> SystemScreen(tablet) { update = it }
            }
        }

        HudBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                if (tablet) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(92.dp).fillMaxHeight()) {
                            for (t in Tab.values()) {
                                HudButton(t.label, Modifier.fillMaxWidth().height(60.dp), KeyKind.OP, selected = t == current, fontSize = 14.sp) {
                                    Nav.tab = t.ordinal
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Header(current.title, Modifier.fillMaxWidth().height(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Header(current.title, Modifier.fillMaxWidth().height(34.dp))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().height(46.dp)) {
                            for (t in Tab.values()) {
                                HudButton(t.label, Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = t == current, fontSize = 12.sp) {
                                    Nav.tab = t.ordinal
                                }
                            }
                        }
                    }
                }
            }
        }

        GlobalDialogs()
        if (!AppSettings.introSeen) IntroDialog()
        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { openUrl(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }
    }
}

@Composable
private fun Header(title: String, modifier: Modifier) {
    val c = Hud.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        HudText("◈ AIR SHARE", color = c.primary, size = 16.sp, bold = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (Air.advertising) {
                HudText("● VISIBLE", color = c.good, size = 10.sp, bold = true)
                Spacer(Modifier.width(10.dp))
            }
            HudText(title, color = c.dim, size = 11.sp)
        }
    }
}
