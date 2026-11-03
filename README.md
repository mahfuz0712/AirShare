# Air Share

Sci-fi HUD styled **offline file sharing** for Android 5.0 (API 21) and newer - ShareIt style, not LocalSend style:
the two phones do **not** need to be on the same Wi-Fi network (or any network). Both just run Air Share.

Package `com.mux.android.airshare` - Version `1.0.0`

## How it works
- Built on Google **Nearby Connections**: phones find each other over **Bluetooth / BLE**, then Android moves the
  transfer to **Wi-Fi Direct or a temporary hotspot** for speed (Bluetooth stays as the fallback). All traffic is encrypted.
- **Receiver**: opens the *RECV* tab (visible while the app is open) -> shows up on other phones' radar under their name.
- **Sender**: picks files / text on *SEND*, sees nearby receivers on an animated radar (+ list), taps one.
- The receiver gets a full-screen **ACCEPT / REJECT** request (sender name, file list, size, pairing code).
  ACCEPT -> files are sent. REJECT -> the sender sees "RECEIVER REJECTED THE REQUEST" and nothing is sent.
- Either side can cancel a running transfer. History (*LOG*), open received files, copy received text.
- "Share > Air Share" from the gallery / file manager puts the files straight on the SEND tab.
- Received files are saved in `Downloads/AirShare`.
- SYS tab: device name, visibility, permissions, how it works, update check (GitHub releases), developer info.

## Requirements / limits
- **Google Play services** on both phones (Nearby Connections). Phones without it (e.g. some Huawei) can't use the app.
- Bluetooth + Wi-Fi switched ON (no network connection needed). Android 11 and older also need Location ON.
- Receiver must keep the app open to be found. One transfer at a time (send many files in one request).
- Speed depends on the phones: Wi-Fi Direct / hotspot upgrade is automatic, Bluetooth-only is much slower.

## Build
Open in Android Studio, or `gradle assembleDebug` (Gradle 8.9, JDK 17). CI: `.github/workflows/build.yml`.
Updates are read from GitHub releases of `mahfuz0712/AirShare` (edit `AppInfo.kt`).
