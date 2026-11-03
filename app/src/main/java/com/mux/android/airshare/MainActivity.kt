package com.mux.android.airshare

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.AppSettings
import com.mux.android.airshare.data.Nav
import com.mux.android.airshare.ui.AirRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        Air.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleShare(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (Nav.tab != 0) Nav.tab = 0 else finish()
            }
        })
        setContent { AirRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** "Share > Air Share" from another app: put the files on the SEND tab. */
    private fun handleShare(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { Air.addUris(listOf(it)) }
                i.getStringExtra(Intent.EXTRA_TEXT)?.let { if (Air.pendingText.isEmpty()) Air.pendingText = it.take(1000) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                if (list != null) Air.addUris(list)
            }
            else -> return
        }
        i.action = null
        Nav.tab = 0
    }

    override fun onStart() {
        super.onStart()
        Air.appVisible = true
        Air.refreshRadio()
    }

    override fun onStop() {
        Air.appVisible = false
        Air.refreshRadio()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Nav.resumeTick++
    }
}
