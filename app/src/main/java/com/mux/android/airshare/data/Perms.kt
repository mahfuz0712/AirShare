package com.mux.android.airshare.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    /** Runtime permissions Nearby Connections needs on this Android version. */
    fun required(): List<String> = when {
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES
        )
        Build.VERSION.SDK_INT >= 31 -> listOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION
        )
        Build.VERSION.SDK_INT >= 29 -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    /** Required + notification permission (so the transfer notification can show). */
    fun asked(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) required() + Manifest.permission.POST_NOTIFICATIONS else required()

    fun has(ctx: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun radioMissing(ctx: Context): List<String> = required().filter { !has(ctx, it) }

    fun label(p: String): String = when (p) {
        Manifest.permission.BLUETOOTH_SCAN -> "BLUETOOTH SCAN (FIND PHONES)"
        Manifest.permission.BLUETOOTH_ADVERTISE -> "BLUETOOTH ADVERTISE (BE FOUND)"
        Manifest.permission.BLUETOOTH_CONNECT -> "BLUETOOTH CONNECT"
        Manifest.permission.NEARBY_WIFI_DEVICES -> "NEARBY WI-FI DEVICES"
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> "LOCATION (REQUIRED BY ANDROID FOR NEARBY SCAN)"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "STORAGE (SAVE RECEIVED FILES)"
        Manifest.permission.POST_NOTIFICATIONS -> "NOTIFICATIONS (TRANSFER PROGRESS)"
        else -> p.substringAfterLast('.')
    }

    // ---- radios ---------------------------------------------------------------------------------

    fun bluetoothOn(): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
    } catch (e: Exception) {
        true
    }

    fun wifiOn(ctx: Context): Boolean = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) {
        true
    }

    /** Android 11 and older only hand out Bluetooth scan results when Location is on. */
    fun locationNeededAndOff(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return false
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            !(if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        } catch (e: Exception) {
            false
        }
    }
}
