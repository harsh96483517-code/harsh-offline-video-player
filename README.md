# HARSH Offline Video Player

**PLAY • WATCH • ENJOY** — a premium, lightweight, offline-first video player for Android TV, built to run on low-end TVs with **1 GB RAM**.

> Brand: **HARSH** · Signature: **ALIVE HARSH**

This is a complete, buildable native Android application (Kotlin + AndroidX Media3 / ExoPlayer). It discovers and plays the videos already on the device — no accounts, no cloud, no internet.

---

## 1. What's inside

| Area | Implementation |
|---|---|
| Language / UI | Kotlin, Android Views + XML layouts, ViewBinding |
| Architecture | Lightweight MVVM (ViewModel + StateFlow) with a single in-memory repository |
| Playback | AndroidX Media3 **ExoPlayer** (hardware decoding left to the platform) |
| Discovery | MediaStore (internal + USB volumes) + Storage Access Framework folders |
| Persistence | DataStore Preferences (settings) + SharedPreferences (history / favorites / playlists) |
| Thumbnails | MediaStore-provided thumbnails, small in-memory LRU cache, no per-video poster generation |
| Concurrency | Kotlin Coroutines |
| Async images | Coil, configured with tight caches |

Minimum supported version: **Android 5.1 (API 21)** — the minimum required by Media3 — with `targetSdk 34`.

---

## 2. Project structure

```
HarshOfflineVideoPlayer/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/…        # Gradle 8.7 wrapper (jar included)
└── app/
    ├── build.gradle.kts                          # AGP 8.5.2, Kotlin 1.9.24
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml               # TV launcher intent, storage perms, NO INTERNET
        │   ├── java/com/harsh/player/
        │   │   ├── HarshApp.kt                   # Application + Coil config
        │   │   ├── data/                         # MediaStoreScanner, VideoRepository, Prefs, HistoryStore
        │   │   ├── data/model/VideoItem.kt
        │   │   ├── ui/                           # Splash, home, library, player, settings, about
        │   │   └── util/                         # DeviceCapabilities, Formatters, ThumbnailLoader
        │   └── res/                              # layouts, themes, drawables, mipmaps, tv banner
        └── test/java/com/harsh/player/FormattersTest.kt
```

---

## 3. Building

### Prerequisites
- **Android Studio** (Hedgehog / Iguana or newer), or a command-line Android SDK.
- **JDK 17** (bundled with recent Android Studio).
- Android SDK with **API 34 platform** and **build-tools** installed.
- Internet access **for the build only** (to download Gradle + dependencies). The app itself needs no internet.

### From Android Studio
1. `File ▸ Open…` and select the `HarshOfflineVideoPlayer` folder.
2. Let Gradle sync (it will download AGP 8.5.2, Kotlin 1.9.24, Media3 1.4.1, etc.).
3. `Run ▸ Run 'app'` on an emulator or connected device.

### From the command line
```bash
# Point the build at your SDK (either export ANDROID_HOME or create local.properties)
export ANDROID_HOME=/path/to/Android/Sdk
# or: echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # -> app/build/outputs/apk/release/app-release.apk (needs signing)
./gradlew test               # runs the JVM unit tests
```

### Release signing (optional)
Create a keystore and add a `signingConfigs` block, or build an unsigned release and sign it with `apksigner`. The debug APK installs and runs on a TV without signing setup.

---

## 4. Installing on an Android TV

1. Build `app-debug.apk` (or a signed release APK).
2. On the TV, enable **Developer options** and **USB debugging** (Settings ▸ About ▸ tap *Build* 7×).
3. Get the APK onto the TV — any one of:
   - **adb over network:** `adb connect <TV_IP>:5555` then `adb install -r app-debug.apk`.
   - **adb over USB:** connect a laptop and `adb install -r app-debug.apk`.
   - **USB drive:** copy the APK to a USB stick, use a file-manager / "install unknown apps" flow on the TV to install it.
4. The app appears in the TV launcher under its **TV banner** (`tv_banner`) as **HARSH**.

> USB storage exposure differs between TV firmwares. If a drive does not appear in MediaStore, use the in-app **folder picker** (SAF) to grant access to it.

---

## 5. Design notes — Liquid Glass on 1 GB RAM

The visual language (black background, frosted panels, blue/violet accents, thin borders, rounded corners) is produced **without** expensive real-time blur:

- Translucent solid-colour panels and **static** gradients (`bg_topbar`, `bg_controls`, `bg_sidebar`, `bg_splash`).
- Vector icons only; no large decorative bitmaps in memory.
- Focus handled by **drawable state selectors**, not animated effects.
- Player overlay fades with a short, cheap alpha animation and auto-hides.

### Memory strategy (target ≈ 80–120 MB while browsing)
- Thumbnail LRU cache capped at **≤ 8 MB**; Coil memory cache **4 MB** / disk **24 MB** in low-RAM mode.
- `RecyclerView` view recycling, `setHasFixedSize(true)`, animations disabled on lists.
- Library loaded once and filtered/sorted **in memory** — no repeated full-storage scans.
- A **single** ExoPlayer instance; buffers kept modest (15 s min / 30 s max).
- Player resources released in `onStop`/`onDestroy`; playback position saved on a 1-second tick, on pause and on stop (never every frame).
- **LOW-RAM MODE** toggle, auto-suggested when the device reports low RAM (`ActivityManager.isLowRamDevice` or ≤ 1.5 GB).

---

## 6. Feature checklist

- **Home:** top bar (logo, search, folder picker, settings, storage), left sidebar (10 entries), shelves for Continue Watching / Recently Played / Recently Added / Favorites / USB / Movies / Series, empty state.
- **Library:** All Videos, Movies, Series, Folders, Recently Played, Favorites, Playlists; search; sort by name / duration / size / date.
- **Player:** play, pause, ±10 s seek, previous / next, seek bar, elapsed / remaining / total, aspect ratio, playback speed, audio-track and subtitle selection, subtitle size, resume, favourite, graceful errors with Retry, auto-hiding overlay, D-pad + media-key handling.
- **Settings:** resume, auto-hide, subtitle size, LOW-RAM MODE, clear history, clear cache, permissions, About.
- **Privacy:** no registration, no analytics, **no `INTERNET` permission**, all data stays on device.

---

## 7. Compatibility & honest limitations

- Codec support depends on the **device's decoders**. MP4 (H.264 + AAC) is the safest bet; MKV/WebM/AVI play only when the TV's decoder supports their contained codecs.
- 1080p (and any 4K) playback is **not** guaranteed on a 1 GB TV — it depends on the SoC, decoder, bitrate and firmware.
- The app **never transcode**s and never forces software decoding or 4K output.
- USB behaviour varies by firmware; folder access falls back to SAF.

### Testing status
This project was authored and assembled in an environment **without an Android SDK / JDK**, so the APK could **not** be compiled or run here. The source is complete and structured to build with the commands above, but the following remain **unverified until you build it**: compilation, runtime behaviour, and all 18 test scenarios listed in the brief (first launch offline, MP4/MKV playback, seeking, subtitles, USB disconnect, memory pressure, etc.).

Recommended: build the debug APK, run the unit tests, then profile memory with Android Studio Profiler on a real 1 GB TV and tune the budget from measurements rather than assumptions.

---

## 8. License / attribution

Icons use standard Material Design path data. All other code and assets are original to this project.

**ALIVE HARSH**
