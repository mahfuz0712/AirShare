package com.mux.android.airshare.data

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.nearby.connection.Payload

/** A phone in "receive" mode that the radar found. */
data class Peer(val id: String, val name: String)

enum class TState { CONNECTING, WAITING, PROMPT, TRANSFERRING, DONE, REJECTED, FAILED, CANCELED }

class FileItem(val name: String, val size: Long, val mime: String, val uri: Uri? = null) {
    var payloadId = 0L
    var payload: Payload? = null           // outgoing: created when the transfer starts
    var pfd: ParcelFileDescriptor? = null  // outgoing: the opened file
    var bytes by mutableLongStateOf(0L)
    var done by mutableStateOf(false)
    var saved: String? = null              // incoming: content / file uri after saving
}

class Transfer(val id: String, val endpointId: String, val peerName: String, val outgoing: Boolean) {
    var state by mutableStateOf(TState.CONNECTING)
    var message by mutableStateOf("")
    var digits by mutableStateOf("")       // both phones show the same code: proof you talk to the right phone
    var files by mutableStateOf<List<FileItem>>(emptyList())
    var text by mutableStateOf("")
    var speed by mutableLongStateOf(0L)    // bytes per second
    var expectText = false
    var textPayloadId = 0L
    var textDone = false
    var finished = false
    val startedAt = System.currentTimeMillis()
    var lastSampleAt = 0L
    var lastSampleBytes = 0L

    val total: Long get() = files.sumOf { it.size }
    val sent: Long get() = files.sumOf { if (it.done) it.size else it.bytes.coerceAtMost(it.size) }
    val active: Boolean
        get() = !finished && (state == TState.CONNECTING || state == TState.WAITING || state == TState.PROMPT || state == TState.TRANSFERRING)
}

/** Message the sender sees (rejected, failed...). */
class Notice(val title: String, val message: String, val copy: String? = null)

data class HistItem(
    val time: Long,
    val peer: String,
    val outgoing: Boolean,
    val names: String,
    val count: Int,
    val bytes: Long,
    val result: String,                     // DONE, REJECTED, FAILED, CANCELED
    val uris: List<String>,
    val mimes: List<String>,
    val text: String
)
