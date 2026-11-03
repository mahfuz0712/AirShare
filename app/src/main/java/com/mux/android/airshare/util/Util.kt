package com.mux.android.airshare.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast

fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("text", text))
    toast(ctx, "COPIED")
}

fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

fun openSettingsAction(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast(ctx, "OPEN ANDROID SETTINGS MANUALLY")
    }
}

/** Opens a received / sent file with whatever app can show it. */
fun openFile(ctx: Context, uri: String, mime: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        toast(ctx, "NO APP TO OPEN THIS FILE")
    }
}
