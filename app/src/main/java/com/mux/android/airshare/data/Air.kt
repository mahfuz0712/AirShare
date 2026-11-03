package com.mux.android.airshare.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.mux.android.airshare.AirService
import com.mux.android.airshare.AppInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The heart of Air Share. Uses Google's Nearby Connections: phones find each other over Bluetooth / BLE
 * (no router, no shared Wi-Fi network), then Android switches the link to Wi-Fi Direct / a temporary
 * hotspot for the fast file transfer - the same idea ShareIt uses.
 *
 * Our own small protocol on top (JSON messages):
 *   sender -> {"t":"offer", files:[...]}      receiver shows ACCEPT / REJECT
 *   receiver -> {"t":"accept"} | {"t":"reject"}
 *   sender -> file payloads (+ {"t":"text"})  only after ACCEPT
 *   either -> {"t":"cancel"}
 */
object Air {
    private const val ASK_TIMEOUT_MS = 90_000L

    private lateinit var app: Context
    private var client: ConnectionsClient? = null
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val incoming = HashMap<Long, Payload>()

    /** Phones in receive mode that the radar sees. */
    val peers = mutableStateListOf<Peer>()
    val transfers = mutableStateListOf<Transfer>()

    /** What the user picked to send. */
    val selection = mutableStateListOf<FileItem>()
    var pendingText by mutableStateOf("")

    var advertising by mutableStateOf(false)
        private set
    var discovering by mutableStateOf(false)
        private set
    var radioNote by mutableStateOf("")
    var notice by mutableStateOf<Notice?>(null)

    /** Set by MainActivity (onStart / onStop) and the SEND tab. */
    var appVisible = false
    var wantDiscovery = false

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    private fun nearby(): ConnectionsClient = client ?: Nearby.getConnectionsClient(app).also { client = it }

    fun gmsOk(): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS
    } catch (e: Throwable) {
        false
    }

    fun busy(): Boolean = transfers.any { it.active }

    // ---------------------------------------------------------------------------------------------
    // radio: be found (advertise) + find others (discover)
    // ---------------------------------------------------------------------------------------------

    fun setDiscovery(want: Boolean) {
        wantDiscovery = want
        refreshRadio()
    }

    /** Starts / stops advertising and discovery so they match what the screen needs right now. */
    fun refreshRadio() {
        if (!gmsOk()) return
        val permsOk = Perms.radioMissing(app).isEmpty()
        val idle = !busy()
        val wantAdv = appVisible && AppSettings.visible && idle && permsOk
        val wantDisc = appVisible && wantDiscovery && idle && permsOk
        if (wantAdv) startAdvertising() else stopAdvertising()
        if (wantDisc) startDiscovery() else stopDiscovery()
    }

    /** After the device name changes. */
    fun restartAdvertising() {
        stopAdvertising()
        refreshRadio()
    }

    private fun startAdvertising() {
        if (advertising) return
        advertising = true
        try {
            nearby().startAdvertising(
                AppSettings.myName(), AppInfo.SERVICE_ID, lifecycle,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
            ).addOnSuccessListener { radioNote = "" }
                .addOnFailureListener { e -> advertising = radioError(e, true) }
        } catch (e: Exception) {
            advertising = false
            radioNote = "RADIO ERROR: ${e.message}"
        }
    }

    private fun stopAdvertising() {
        if (!advertising) return
        advertising = false
        try {
            nearby().stopAdvertising()
        } catch (e: Exception) {
        }
    }

    private fun startDiscovery() {
        if (discovering) return
        discovering = true
        peers.clear()
        try {
            nearby().startDiscovery(
                AppInfo.SERVICE_ID, discoveryCb,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
            ).addOnSuccessListener { radioNote = "" }
                .addOnFailureListener { e -> discovering = radioError(e, false) }
        } catch (e: Exception) {
            discovering = false
            radioNote = "RADIO ERROR: ${e.message}"
        }
    }

    private fun stopDiscovery() {
        if (!discovering) return
        discovering = false
        try {
            nearby().stopDiscovery()
        } catch (e: Exception) {
        }
        peers.clear()
    }

    /** @return true when the radio is in fact already running (so the flag stays on). */
    private fun radioError(e: Exception, adv: Boolean): Boolean {
        val code = (e as? ApiException)?.statusCode
        if (adv && code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) return true
        if (!adv && code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) return true
        radioNote = "RADIO ERROR ${code ?: ""}: ${e.message ?: ""} - CHECK BLUETOOTH / WI-FI / PERMISSIONS"
        return false
    }

    private val discoveryCb = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (peers.none { it.id == endpointId }) peers.add(Peer(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) {
            peers.removeAll { it.id == endpointId }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // sender
    // ---------------------------------------------------------------------------------------------

    /** Asks [peer] to accept the current selection. @return an error text, or null when started. */
    fun send(peer: Peer): String? {
        if (!gmsOk()) return "GOOGLE PLAY SERVICES IS REQUIRED"
        if (selection.isEmpty() && pendingText.isBlank()) return "CHOOSE FILES OR TYPE A TEXT FIRST"
        if (busy()) return "ANOTHER TRANSFER IS RUNNING"

        val opened = ArrayList<FileItem>()
        for (s in selection) {
            val uri = s.uri ?: continue
            val pfd = try {
                app.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                null
            }
            if (pfd == null) {
                opened.forEach { try { it.pfd?.close() } catch (e: Exception) {} }
                return "CAN'T READ \"${s.name}\" - ADD IT AGAIN"
            }
            val f = FileItem(s.name, s.size, s.mime, uri)
            f.pfd = pfd
            val p = Payload.fromFile(pfd)
            f.payload = p
            f.payloadId = p.id
            opened.add(f)
        }

        val t = Transfer(UUID.randomUUID().toString(), peer.id, peer.name, true)
        t.files = opened
        t.text = pendingText.trim()
        t.message = "CONNECTING TO ${peer.name}..."
        transfers.add(0, t)
        AirService.start(app)
        stopAdvertising()
        stopDiscovery()
        try {
            nearby().requestConnection(AppSettings.myName(), peer.id, lifecycle)
                .addOnFailureListener { e -> main.post { finish(t, TState.FAILED, "COULD NOT CONNECT: ${e.message ?: ""}") } }
        } catch (e: Exception) {
            finish(t, TState.FAILED, "COULD NOT CONNECT: ${e.message ?: ""}")
        }
        main.postDelayed({
            if (!t.finished && t.state == TState.CONNECTING) cancel(t, "NO ANSWER FROM ${peer.name}")
        }, 30_000)
        return null
    }

    private fun sendOffer(t: Transfer) {
        val arr = JSONArray()
        t.files.forEach { arr.put(JSONObject().put("n", it.name).put("s", it.size).put("m", it.mime).put("p", it.payloadId)) }
        val msg = JSONObject().put("t", "offer").put("from", AppSettings.myName())
            .put("files", arr).put("text", t.text.isNotEmpty())
        sendMsg(t, msg)
        t.message = "WAITING FOR ${t.peerName} TO ACCEPT..."
        main.postDelayed({
            if (!t.finished && t.state == TState.WAITING) cancel(t, "NO RESPONSE FROM ${t.peerName}")
        }, ASK_TIMEOUT_MS)
    }

    private fun startSending(t: Transfer) {
        t.state = TState.TRANSFERRING
        t.message = "SENDING..."
        for (f in t.files) f.payload?.let { nearby().sendPayload(t.endpointId, it) }
        if (t.text.isNotEmpty()) {
            val p = Payload.fromBytes(JSONObject().put("t", "text").put("v", t.text).toString().toByteArray(Charsets.UTF_8))
            t.textPayloadId = p.id
            nearby().sendPayload(t.endpointId, p)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // receiver: ACCEPT / REJECT
    // ---------------------------------------------------------------------------------------------

    fun accept(t: Transfer) {
        if (t.finished || t.state != TState.PROMPT) return
        t.state = TState.TRANSFERRING
        t.message = "RECEIVING..."
        sendMsg(t, JSONObject().put("t", "accept"))
    }

    fun reject(t: Transfer) {
        if (t.finished || t.state != TState.PROMPT) return
        sendMsg(t, JSONObject().put("t", "reject"))
        finish(t, TState.REJECTED, "YOU REJECTED THE REQUEST")
    }

    /** Either side can stop a running transfer. */
    fun cancel(t: Transfer, reason: String = "CANCELED") {
        if (t.finished) return
        try {
            sendMsg(t, JSONObject().put("t", "cancel"))
        } catch (e: Exception) {
        }
        finish(t, TState.CANCELED, reason, cancelPayloads = true)
    }

    // ---------------------------------------------------------------------------------------------
    // connection + payload callbacks
    // ---------------------------------------------------------------------------------------------

    private fun find(endpointId: String): Transfer? = transfers.firstOrNull { it.endpointId == endpointId && !it.finished }

    private fun digitsOf(info: ConnectionInfo): String {
        // name differs between Play services versions
        for (m in listOf("getAuthenticationDigits", "getAuthenticationToken")) {
            try {
                val v = info.javaClass.getMethod(m).invoke(info)
                if (v is String && v.isNotBlank()) return v
            } catch (e: Exception) {
            }
        }
        return ""
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            var t = find(endpointId)
            if (t == null) {
                // somebody wants to send to us
                if (!AppSettings.visible || !info.isIncomingConnection || busy()) {
                    nearby().rejectConnection(endpointId)
                    return
                }
                t = Transfer(UUID.randomUUID().toString(), endpointId, info.endpointName, false)
                t.message = "CONNECTING TO ${info.endpointName}..."
                transfers.add(0, t)
                AirService.start(app)
                stopAdvertising()
                stopDiscovery()
            }
            t.digits = digitsOf(info)
            nearby().acceptConnection(endpointId, payloadCb)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val t = find(endpointId) ?: return
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                t.state = TState.WAITING
                if (t.outgoing) {
                    sendOffer(t)
                } else {
                    t.message = "WAITING FOR ${t.peerName}'S REQUEST..."
                }
            } else {
                finish(t, TState.FAILED, "COULD NOT CONNECT (CODE ${result.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            val t = find(endpointId)
            if (t != null) finish(t, TState.FAILED, "CONNECTION LOST")
            else refreshRadio()
        }
    }

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { onMessage(endpointId, String(it, Charsets.UTF_8)) }
                Payload.Type.FILE -> incoming[payload.id] = payload
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, u: PayloadTransferUpdate) {
            val t = find(endpointId) ?: return
            val f = t.files.firstOrNull { it.payloadId == u.payloadId }
            if (f != null) {
                when (u.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        f.bytes = u.bytesTransferred
                        sampleSpeed(t)
                    }
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        f.bytes = f.size
                        if (t.outgoing) {
                            f.done = true
                            checkComplete(t)
                        } else {
                            saveIncoming(t, f)
                        }
                    }
                    PayloadTransferUpdate.Status.FAILURE -> finish(t, TState.FAILED, "TRANSFER FAILED", cancelPayloads = true)
                    PayloadTransferUpdate.Status.CANCELED -> finish(t, TState.CANCELED, "TRANSFER CANCELED", cancelPayloads = true)
                }
            } else if (t.outgoing && u.payloadId == t.textPayloadId && u.status == PayloadTransferUpdate.Status.SUCCESS) {
                t.textDone = true
                checkComplete(t)
            }
        }
    }

    private fun sampleSpeed(t: Transfer) {
        val now = System.currentTimeMillis()
        if (t.lastSampleAt == 0L) {
            t.lastSampleAt = now
            t.lastSampleBytes = t.sent
        } else if (now - t.lastSampleAt >= 700) {
            val cur = t.sent
            t.speed = ((cur - t.lastSampleBytes) * 1000 / (now - t.lastSampleAt)).coerceAtLeast(0)
            t.lastSampleAt = now
            t.lastSampleBytes = cur
        }
    }

    private fun onMessage(endpointId: String, json: String) {
        val t = find(endpointId) ?: return
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            return
        }
        when (o.optString("t")) {
            "offer" -> if (!t.outgoing && t.state == TState.WAITING) {
                val list = ArrayList<FileItem>()
                val arr = o.optJSONArray("files")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val j = arr.getJSONObject(i)
                        val f = FileItem(Files.safeName(j.optString("n", "file")), j.optLong("s"), j.optString("m", "application/octet-stream"))
                        f.payloadId = j.optLong("p")
                        list.add(f)
                    }
                }
                t.files = list
                t.expectText = o.optBoolean("text")
                t.state = TState.PROMPT
                t.message = "${t.peerName} WANTS TO SEND YOU SOMETHING"
                main.postDelayed({
                    if (!t.finished && t.state == TState.PROMPT) reject(t)
                }, ASK_TIMEOUT_MS)
            }
            "accept" -> if (t.outgoing && t.state == TState.WAITING) startSending(t)
            "reject" -> if (t.outgoing) finish(t, TState.REJECTED, "${t.peerName} REJECTED THE REQUEST")
            "text" -> if (!t.outgoing) {
                t.text = o.optString("v")
                checkComplete(t)
            }
            "cancel" -> finish(t, TState.CANCELED, "CANCELED BY ${t.peerName}", cancelPayloads = true)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // finishing
    // ---------------------------------------------------------------------------------------------

    private fun saveIncoming(t: Transfer, f: FileItem) {
        val payload = incoming[f.payloadId]
        io.execute {
            var saved: String? = null
            try {
                val file = payload?.asFile()
                if (file != null) {
                    // Android 10+: Nearby already wrote the file into Downloads - just move + rename it
                    val uri = try {
                        file.javaClass.getMethod("asUri").invoke(file) as? Uri
                    } catch (e: Exception) {
                        null
                    }
                    if (uri != null && Files.moveIntoAirShare(app, uri, f.name, f.mime)) {
                        saved = uri.toString()
                    } else {
                        val pfd: ParcelFileDescriptor? = file.asParcelFileDescriptor()
                        if (pfd != null) {
                            saved = Files.saveReceived(app, f.name, f.mime, ParcelFileDescriptor.AutoCloseInputStream(pfd))
                            try {
                                file.asJavaFile()?.delete()
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
            main.post {
                incoming.remove(f.payloadId)
                if (saved == null) {
                    finish(t, TState.FAILED, "COULD NOT SAVE ${f.name}")
                } else {
                    f.saved = saved
                    f.done = true
                    checkComplete(t)
                }
            }
        }
    }

    private fun checkComplete(t: Transfer) {
        if (t.finished) return
        val filesDone = t.files.all { it.done }
        val textOk = if (t.outgoing) (t.text.isEmpty() || t.textDone) else (!t.expectText || t.text.isNotEmpty())
        if (filesDone && textOk) finish(t, TState.DONE, "DONE")
    }

    private fun sendMsg(t: Transfer, o: JSONObject) {
        nearby().sendPayload(t.endpointId, Payload.fromBytes(o.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun finish(t: Transfer, s: TState, msg: String, cancelPayloads: Boolean = false) {
        if (t.finished) return
        t.finished = true
        t.state = s
        t.message = msg
        if (cancelPayloads) {
            for (f in t.files) {
                try {
                    nearby().cancelPayload(f.payloadId)
                } catch (e: Exception) {
                }
            }
        }
        for (f in t.files) {
            try {
                f.pfd?.close()
            } catch (e: Exception) {
            }
            incoming.remove(f.payloadId)
        }
        addHistory(t)

        if (t.outgoing) {
            when {
                s == TState.REJECTED -> notice = Notice("REQUEST REJECTED", "${t.peerName} REJECTED THE REQUEST.\nNOTHING WAS SENT.")
                s == TState.FAILED -> notice = Notice("TRANSFER FAILED", msg)
                s == TState.CANCELED && msg.startsWith("CANCELED BY") -> notice = Notice("TRANSFER CANCELED", msg)
                s == TState.CANCELED && msg.startsWith("NO ") -> notice = Notice("NO RESPONSE", msg)
            }
        } else {
            if (s == TState.DONE && t.text.isNotEmpty()) notice = Notice("TEXT FROM ${t.peerName}", t.text, copy = t.text)
            else if (s == TState.CANCELED && msg.startsWith("CANCELED BY")) notice = Notice("TRANSFER CANCELED", msg)
        }

        // give the last messages a moment to arrive, then free the radio
        main.postDelayed({
            try {
                nearby().disconnectFromEndpoint(t.endpointId)
            } catch (e: Exception) {
            }
            refreshRadio()
        }, 1200)
    }

    private fun addHistory(t: Transfer) {
        if (t.files.isEmpty() && t.text.isEmpty()) return
        val names = t.files.take(3).joinToString(", ") { it.name } +
            (if (t.files.size > 3) " +${t.files.size - 3}" else "") +
            (if (t.files.isEmpty()) "TEXT MESSAGE" else "")
        val uris = ArrayList<String>()
        val mimes = ArrayList<String>()
        for (f in t.files) {
            val u = if (t.outgoing) f.uri?.toString() else f.saved
            if (u != null) {
                uris.add(u)
                mimes.add(f.mime)
            }
        }
        val result = when (t.state) {
            TState.DONE -> "DONE"
            TState.REJECTED -> "REJECTED"
            TState.CANCELED -> "CANCELED"
            else -> "FAILED"
        }
        HistoryStore.add(HistItem(System.currentTimeMillis(), t.peerName, t.outgoing, names, t.files.size, t.total, result, uris, mimes, t.text))
    }

    // ---------------------------------------------------------------------------------------------
    // picking files
    // ---------------------------------------------------------------------------------------------

    fun addUris(uris: List<Uri>) {
        for (u in uris) {
            if (selection.any { it.uri == u }) continue
            selection.add(Files.describe(app, u))
        }
    }

    /** Removes a finished / failed transfer card from the screen. */
    fun dismiss(t: Transfer) {
        if (t.finished) transfers.remove(t)
    }

    fun removeSelected(f: FileItem) {
        selection.remove(f)
    }

    fun clearSelection() {
        selection.clear()
        pendingText = ""
    }

    fun summary(): String {
        val t = transfers.firstOrNull { it.active } ?: return "Air Share"
        val pct = if (t.total > 0) (t.sent * 100 / t.total).toInt() else 0
        return (if (t.outgoing) "Sending to " else "Receiving from ") + t.peerName + (if (t.state == TState.TRANSFERRING) " · $pct%" else "")
    }
}
