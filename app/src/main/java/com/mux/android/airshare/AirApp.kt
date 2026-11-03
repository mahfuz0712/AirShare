package com.mux.android.airshare

import android.app.Application
import com.mux.android.airshare.data.AppSettings
import com.mux.android.airshare.data.Air
import com.mux.android.airshare.data.HistoryStore

class AirApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        HistoryStore.init(this)
        Air.init(this)
    }
}
