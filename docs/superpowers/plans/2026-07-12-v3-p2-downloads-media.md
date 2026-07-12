# V3 Phase 2 — Download Engine + Media — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the system DownloadManager with our own engine (parallel segments, pause/resume/cancel/retry, auto-resume, scheduling, live speed), add in-app previews, background media playback (per-site opt-in), and Picture-in-Picture — spec items G1 G2 G3 G4 G7.

**Architecture:** A pure `DownloadPlanner` decides segmentation/resume; a coroutine-based `DownloadEngine` (HttpURLConnection + RandomAccessFile, no new runtime deps) performs transfers with progress callbacks; a foreground `DownloadService` owns running downloads and the notification; Room `downloads` table (migration v6→v7) is the single source of truth the UI observes; WorkManager handles Wi-Fi/scheduled starts and connectivity auto-resume. Media: `MediaHoldService` keeps allowed sites playing in background; `onShowCustomView` + activity PiP for video. Files land in `context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)` and open via FileProvider (decision recorded: in-app manager IS the download UX; no MediaStore write needed for v3).

**Tech Stack:** Kotlin, coroutines, Room+KSP, WorkManager (`androidx.work:work-runtime-ktx` — new dep), Compose M3, MockWebServer (test-only dep) for engine tests.

## Global Constraints

- Branch: `feature/v3-p2-downloads` off `main`; merge `--no-ff`; tag `v3-phase-2` after emulator verification.
- DB schema version becomes **7**; migrations chain 1→7; export schema JSON (pattern exists from P1).
- Incognito: downloads FROM incognito tabs are allowed but recorded like normal (files are files); no browsing data implications.
- Keep a hidden fallback: `SettingsRepository.useSystemDownloader` (default **false**, no UI) — when true, `WebViewHolder.downloadFile` uses the old DownloadManager path unchanged.
- blob:/data: URL guard in `downloadFile` stays.
- No phase merges with failing tests. Windows: `.\gradlew.bat` from `F:\Dev\Browse`; if JAVA_HOME error: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`.
- Download states (TEXT column, exact strings): `PENDING`, `RUNNING`, `PAUSED`, `SCHEDULED`, `FAILED`, `DONE`, `CANCELLED`. Legacy rows migrate to `DONE`.

## File Structure

```
app/src/main/java/com/udaytank/browse/
  browser/DownloadPlanner.kt        (create — pure)
  browser/SpeedTracker.kt           (create — pure)
  download/DownloadEngine.kt        (create)
  download/DownloadService.kt       (create — foreground service)
  download/DownloadScheduler.kt     (create — WorkManager glue)
  data/DownloadEntry.kt             (modify — new columns)
  data/DownloadDao.kt               (modify — updates by state/progress)
  data/BrowseDatabase.kt            (modify — v7 + MIGRATION_6_7)
  data/SettingsRepository.kt        (modify — useSystemDownloader, backgroundMedia, backgroundMediaSites)
  ui/WebViewHolder.kt               (modify — route downloads to engine; onShowCustomView; media detection)
  ui/DownloadsScreen.kt             (rework — live rows, actions, sparkline, preview sheet)
  ui/BrowserScreen.kt               (modify — download prompt sheet)
  BrowserViewModel.kt               (modify — download actions + prompt state; media allowlist)
  media/MediaHoldService.kt         (create)
  MainActivity.kt                   (modify — PiP enter/exit, service hooks)
AndroidManifest.xml                 (modify — services, FileProvider, PiP, permissions)
app/build.gradle.kts                (modify — work-runtime-ktx, mockwebserver test dep)
Tests: browser/DownloadPlannerTest.kt, browser/SpeedTrackerTest.kt,
       download/DownloadEngineTest.kt (MockWebServer), data updates in BrowseDatabaseTest.kt,
       BrowserViewModelTest.kt additions, FakeDownloadDao.kt update
```

---

### Task 1: Dependencies, manifest, FileProvider groundwork

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`; Create `app/src/main/res/xml/file_paths.xml`.

**Interfaces produced:** WorkManager on classpath (`androidx.work:work-runtime-ktx:2.9.1` via catalog alias `work-runtime`), MockWebServer test dep (`com.squareup.okhttp3:mockwebserver:4.12.0`, `testImplementation`). Manifest gains: `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; activity gains `android:supportsPictureInPicture="true"` and `pictureInPicture` NOT added to configChanges (PiP recreation handled by default; smallestScreenSize|screenLayout already present — append `smallestScreenSize|screenLayout` only if missing). FileProvider:

```xml
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="com.udaytank.andromeda.files" android:exported="false"
    android:grantUriPermissions="true">
  <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
</provider>
```

`file_paths.xml`: `<paths><external-files-path name="downloads" path="Download/"/></paths>`

- [ ] Step 1: add deps + manifest entries + file_paths.xml. Step 2: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. Step 3: commit `feat(v3-p2): workmanager, fileprovider, media/pip manifest groundwork`.

---

### Task 2: Schema v7 — download columns + DAO

**Files:** Modify `data/DownloadEntry.kt`, `data/DownloadDao.kt`, `data/BrowseDatabase.kt`; Test additions in `app/src/androidTest/java/com/udaytank/browse/data/BrowseDatabaseTest.kt`; update `app/src/test/java/com/udaytank/browse/FakeDownloadDao.kt` with the new members (in-memory mirrors, same style as P1 fakes).

**Interfaces produced (verbatim for later tasks):**

```kotlin
@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long = -1,          // legacy system-DM id; -1 for engine downloads
    val fileName: String,
    val url: String,
    val createdAt: Long,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val state: String = "DONE",
    val filePath: String? = null,
    val mimeType: String? = null,
    val etag: String? = null,
    val segments: Int = 1,
    val segmentState: String? = null,   // JSON: per-segment downloaded bytes
    val error: String? = null,
)
```

DAO additions:

```kotlin
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun getById(id: Long): DownloadEntry?
    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id")
    suspend fun setState(id: Long, state: String, error: String? = null)
    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, segmentState = :segmentState WHERE id = :id")
    suspend fun setProgress(id: Long, downloaded: Long, total: Long, segmentState: String?)
    @Query("UPDATE downloads SET fileName = :fileName, filePath = :filePath, mimeType = :mimeType, etag = :etag, segments = :segments WHERE id = :id")
    suspend fun setFileInfo(id: Long, fileName: String, filePath: String?, mimeType: String?, etag: String?, segments: Int)
    @Insert suspend fun insertReturning(entry: DownloadEntry): Long   // keep old insert too
    @Query("SELECT * FROM downloads WHERE state IN ('RUNNING','PENDING')") suspend fun getActive(): List<DownloadEntry>
```

`MIGRATION_6_7`: ALTER TABLE downloads ADD COLUMN for each new column (`totalBytes INTEGER NOT NULL DEFAULT -1`, `downloadedBytes INTEGER NOT NULL DEFAULT 0`, `state TEXT NOT NULL DEFAULT 'DONE'`, `filePath TEXT`, `mimeType TEXT`, `etag TEXT`, `segments INTEGER NOT NULL DEFAULT 1`, `segmentState TEXT`, `error TEXT`). Version = 7. Register in BrowseApplication.

- [ ] Step 1: failing instrumented test `migrate6to7_preservesDownloadsWithDoneState` (insert a v6 row, migrate, assert state='DONE', downloadedBytes=0) + DAO round-trip test for setProgress/setState/getActive. Step 2: run → FAIL. Step 3: implement. Step 4: `connectedDebugAndroidTest --tests "*BrowseDatabaseTest*"` → PASS; `testDebugUnitTest` → PASS (extend FakeDownloadDao). Step 5: commit `feat(v3-p2): schema v7 - download engine columns`.

---

### Task 3: DownloadPlanner + SpeedTracker (pure, TDD)

**Files:** Create `browser/DownloadPlanner.kt`, `browser/SpeedTracker.kt` + tests.

**Interfaces produced:**

```kotlin
object DownloadPlanner {
    data class Segment(val index: Int, val start: Long, val endInclusive: Long, val downloaded: Long = 0)
    /** <2MB or unknown size or no range support → 1; <20MB → 3; else → 6 */
    fun segmentCount(totalBytes: Long, acceptRanges: Boolean): Int
    fun plan(totalBytes: Long, count: Int): List<Segment>
    /** Resume is valid only when etag matches (or both null) and total matches. */
    fun canResume(storedEtag: String?, serverEtag: String?, storedTotal: Long, serverTotal: Long): Boolean
    fun encodeState(segments: List<Segment>): String          // "0:123,1:456,2:0"
    fun decodeState(encoded: String?, planned: List<Segment>): List<Segment>
}

class SpeedTracker(private val windowMs: Long = 3_000) {
    fun sample(nowMs: Long, totalDownloadedBytes: Long)
    fun bytesPerSecond(nowMs: Long): Long                      // over the window
    fun history(): List<Long>                                  // last 30 speeds for the sparkline
}
```

Test cases (write first, exact):

```kotlin
class DownloadPlannerTest {
    @Test fun `small unknown or unsupported gets one segment`() {
        assertEquals(1, DownloadPlanner.segmentCount(1_000_000, true))
        assertEquals(1, DownloadPlanner.segmentCount(-1, true))
        assertEquals(1, DownloadPlanner.segmentCount(50_000_000, false))
    }
    @Test fun `medium gets three large gets six`() {
        assertEquals(3, DownloadPlanner.segmentCount(10_000_000, true))
        assertEquals(6, DownloadPlanner.segmentCount(100_000_000, true))
    }
    @Test fun `plan covers every byte exactly once`() {
        val segs = DownloadPlanner.plan(10_000_001, 3)
        assertEquals(0, segs.first().start)
        assertEquals(10_000_000, segs.last().endInclusive)
        for (i in 1 until segs.size) assertEquals(segs[i - 1].endInclusive + 1, segs[i].start)
    }
    @Test fun `resume validation`() {
        assertTrue(DownloadPlanner.canResume("abc", "abc", 100, 100))
        assertTrue(DownloadPlanner.canResume(null, null, 100, 100))
        assertFalse(DownloadPlanner.canResume("abc", "def", 100, 100))
        assertFalse(DownloadPlanner.canResume("abc", "abc", 100, 200))
    }
    @Test fun `state round-trips`() {
        val planned = DownloadPlanner.plan(300, 3)
        val withProgress = planned.map { it.copy(downloaded = it.index * 10L) }
        val decoded = DownloadPlanner.decodeState(DownloadPlanner.encodeState(withProgress), planned)
        assertEquals(listOf(0L, 10L, 20L), decoded.map { it.downloaded })
    }
    @Test fun `corrupt state falls back to planned zeros`() {
        val planned = DownloadPlanner.plan(300, 3)
        assertEquals(listOf(0L, 0L, 0L), DownloadPlanner.decodeState("garbage", planned).map { it.downloaded })
        assertEquals(listOf(0L, 0L, 0L), DownloadPlanner.decodeState(null, planned).map { it.downloaded })
    }
}
class SpeedTrackerTest {
    @Test fun `computes speed over window and keeps history`() {
        val t = SpeedTracker(windowMs = 1_000)
        t.sample(0, 0); t.sample(500, 500_000); t.sample(1_000, 1_000_000)
        assertEquals(1_000_000, t.bytesPerSecond(1_000))
        assertTrue(t.history().isNotEmpty())
    }
    @Test fun `stale samples fall out of window`() {
        val t = SpeedTracker(windowMs = 1_000)
        t.sample(0, 0); t.sample(100, 1_000_000); t.sample(5_000, 1_000_000)
        assertEquals(0, t.bytesPerSecond(5_000))
    }
}
```

- [ ] TDD steps: failing tests → implement → `test --tests "*DownloadPlannerTest*" --tests "*SpeedTrackerTest*"` PASS → commit `feat(v3-p2): download planner + speed tracker`.

---

### Task 4: DownloadEngine (MockWebServer-tested)

**Files:** Create `download/DownloadEngine.kt`; Test `app/src/test/java/com/udaytank/browse/download/DownloadEngineTest.kt`.

**Interface produced:**

```kotlin
class DownloadEngine(private val scope: CoroutineScope, private val io: CoroutineDispatcher = Dispatchers.IO) {
    interface Listener {
        fun onProgress(id: Long, downloaded: Long, total: Long, segmentState: String)
        fun onStateChanged(id: Long, state: String, error: String? = null)  // RUNNING/PAUSED/DONE/FAILED/CANCELLED
        fun onFileInfo(id: Long, fileName: String, total: Long, etag: String?, segments: Int)
    }
    /** Starts or resumes download [id]. HEAD (or GET probe) → plan → parallel segment loop.
     *  destFile is created/append-positioned by the engine. Safe to call again after pause. */
    fun start(id: Long, url: String, destFile: File, userAgent: String?, priorEtag: String?,
              priorTotal: Long, priorSegmentState: String?, listener: Listener)
    fun pause(id: Long)    // cooperative cancel of jobs; state -> PAUSED via listener
    fun cancel(id: Long)   // cancel + delete destFile; state -> CANCELLED
    fun isActive(id: Long): Boolean
}
```

Implementation notes (follow exactly): probe with `HttpURLConnection` GET + `Range: bytes=0-0` → 206 means ranges supported (read `Content-Range: bytes 0-0/TOTAL` for size, `ETag` header); 200 means no range support (Content-Length for size). Plan via `DownloadPlanner`. Pre-size the file with `RandomAccessFile(dest, "rw").setLength(total)` when total known. Each segment coroutine: GET with `Range: bytes=(start+downloaded)-endInclusive`, stream 64KB buffer, `RandomAccessFile.seek(start + downloaded)` then write, update `AtomicLongArray` per segment; a 500ms ticker aggregates → `onProgress` with encoded segmentState. On all segments complete → `onStateChanged(DONE)`. IOException in any segment → cancel siblings → `FAILED` with message (file kept for resume). Pause → cancel jobs, `PAUSED`. Single-stream path (1 segment) identical minus planning. Keep per-id `Job` map; guard double-start.

Test (MockWebServer, ~1MB payload of deterministic bytes, Dispatcher answering Range requests with 206 slices):

```kotlin
@Test fun `segmented download reassembles identical bytes`()      // 3 segments, compare SHA-256
@Test fun `pause then start resumes without redownloading all`()  // count served range-request start offsets > 0 on resume
@Test fun `server without ranges falls back to single stream and completes`()
@Test fun `cancel deletes the file`()
```

Use `runTest` + real IO dispatcher for the engine's transfers (engine takes dispatcher param; tests pass `Dispatchers.IO` and await listener states via `CompletableDeferred`).

- [ ] TDD steps → `test --tests "*DownloadEngineTest*"` PASS → full unit suite → commit `feat(v3-p2): segmented download engine with pause/resume`.

---

### Task 5: DownloadService + VM download API

**Files:** Create `download/DownloadService.kt`; Modify `BrowserViewModel.kt`, `data/SettingsRepository.kt` (+fake): add `useSystemDownloader: Flow<Boolean>` default false + setter (no UI).

**Produces:**
- `DownloadService` — foreground service (type dataSync). Intent actions: `START(id)`, `PAUSE(id)`, `RESUME(id)`, `CANCEL(id)`. Holds one `DownloadEngine`; Listener writes through `downloadDao.setProgress/setState/setFileInfo` (goAsync via its own scope); notification (channel "downloads"): filename, progress bar, speed text, Pause/Cancel action buttons; stopSelf when no active downloads. `POST_NOTIFICATIONS` runtime request happens in MainActivity on first download (Task 6).
- VM: `fun onStartDownload(url: String, suggestedName: String, mimeType: String?, userAgent: String?, constraint: DownloadWhen)` where `enum class DownloadWhen { NOW, WIFI, LATER_1H }` — inserts `DownloadEntry(state = if NOW "PENDING" else "SCHEDULED", fileName, url, createdAt = now)`, then NOW → `context.startForegroundService(START intent)`; WIFI/LATER_1H → `DownloadScheduler.enqueue(...)` (Task 6). Also `onPauseDownload(id)`, `onResumeDownload(id)`, `onCancelDownload(id)`, `onRetryDownload(id)` (FAILED→PENDING→service), `downloads: StateFlow<List<DownloadEntry>>` from `observeAll()`.
- VM unit tests (fakes): start inserts PENDING row; retry flips FAILED→PENDING; cancel sets CANCELLED. (Service itself is emulator-verified, not unit-tested.)

- [ ] Steps: failing VM tests → implement VM + service → full unit suite PASS → commit `feat(v3-p2): download service + viewmodel download actions`.

---

### Task 6: Wire WebView → engine, prompt sheet, scheduling

**Files:** Modify `ui/WebViewHolder.kt`, `ui/BrowserScreen.kt`, `BrowserViewModel.kt`; Create `download/DownloadScheduler.kt`.

- `WebViewHolder.downloadFile(...)`: keep blob/data guard; if `useSystemDownloader` (@Volatile pushed like forceDark) → old path; else → `listener.onDownloadRequested(url, fileName, mimeType, userAgent)` (new Listener method; fileName via `URLUtil.guessFileName(url, contentDisposition, mimetype)`).
- VM: `uiState.downloadPrompt: DownloadPrompt?` (`data class DownloadPrompt(url, fileName, mimeType, userAgent)`), `onDownloadRequested(...)` sets it; `onDownloadPromptDismissed()`. MainActivity listener forwards.
- `BrowserScreen`: ModalBottomSheet when downloadPrompt != null — filename, three buttons: **Download now** (`NOW`), **On Wi-Fi** (`WIFI`), **In 1 hour** (`LATER_1H`) → `viewModel.onStartDownload(...)` + dismiss. First "now" download triggers POST_NOTIFICATIONS request (Activity ResultLauncher; proceed regardless of grant).
- `DownloadScheduler`: `enqueue(context, downloadEntryId, when)` → OneTimeWorkRequest for `StartDownloadWorker` (CoroutineWorker: flips row PENDING + starts service) with `NetworkType.UNMETERED` constraint for WIFI or `setInitialDelay(1h)` for LATER_1H; plus `registerAutoResume(context)` — a periodic-free approach: on `FAILED` with network error the service enqueues a one-time `NetworkType.CONNECTED` retry work for that id (auto-resume on connectivity return).
- [ ] Steps: implement (no new unit tests — pure wiring; VM prompt state gets 1 test: onDownloadRequested sets prompt) → `assembleDebug` PASS → full unit suite → commit `feat(v3-p2): download prompt, engine wiring, wifi/delayed scheduling`.

---

### Task 7: DownloadsScreen rework + previews (G2)

**Files:** Rework `ui/DownloadsScreen.kt`.

Build order (compile between steps): (1) rows show state chip + LinearProgressIndicator(progress = downloaded/total when total>0 else indeterminate) + human sizes ("12.3 MB / 100 MB · 4.2 MB/s") — speed derived VM-side? No: derive in-screen from successive `downloadedBytes` emissions with `remember` timestamps (simple delta/Δt, no engine coupling). (2) Row action buttons by state: RUNNING→Pause,Cancel · PAUSED→Resume,Cancel · FAILED→Retry,Delete · SCHEDULED→Cancel · DONE→(tap=preview, long-press=Delete/Share). (3) **Sparkline**: `Canvas` 80x24dp drawing polyline of the last 30 speed samples (kept in a `remember` mutableStateList per running row). (4) **Preview sheet** for DONE: image (`BitmapFactory.decodeFile`, fit width), text (first 4KB monospace), APK → "Install" (ACTION_VIEW + FileProvider URI + FLAG_GRANT_READ_URI_PERMISSION), else → "Open with…" chooser + "Share". Delete removes row AND file (filePath).
- [ ] `assembleDebug` PASS → commit `feat(v3-p2): downloads screen - live progress, sparkline, previews`.

---

### Task 8: Background media playback (G3)

**Files:** Create `media/MediaHoldService.kt`; Modify `SettingsRepository` (+fake): `backgroundMedia: Flow<Boolean>` default false + setter, `backgroundMediaSites: Flow<Set<String>>` + `setBackgroundMediaSites`; Modify `WebViewHolder` (track `isMediaPlaying` per tab via `onShowCustomView`/JS heuristic: inject on page-finish a listener posting play/pause events through existing JS bridge? SIMPLER, do this: expose `fun hasPlayingMedia(tabId): Boolean` using WebView's `MediaPlaybackRequiresUserGesture` not viable — instead: MainActivity.onStop checks `audioManager.isMusicActive`), `MainActivity`, `SettingsScreen` (Media section switch "Background playback" + hint text), menu item "Play in background on this site" toggle (BrowserScreen menu, shown when setting on).

Behavior: in `MainActivity.onStop`, IF backgroundMedia setting on AND active tab's host ∈ allowlist AND `audioManager.isMusicActive` → start `MediaHoldService` (foreground, type mediaPlayback, MediaSession notification with Play/Pause mapped to `evaluateJavascript("document.querySelectorAll('video,audio').forEach(m=>m.paused?m.play():m.pause())")` on the active WebView via holder reference) and SKIP the holder's usual `webView.onPause()` for that tab (add `holder.setKeepAlive(tabId, true)`); onStart → stop service, clear keepAlive. Honest limits documented in code comment: OEM battery managers may still kill; labeled experimental in Settings hint.
- [ ] `assembleDebug` + unit suite PASS → commit `feat(v3-p2): background media playback (per-site opt-in, experimental)`.

---

### Task 9: Picture-in-Picture (G4)

**Files:** Modify `ui/WebViewHolder.kt` (`onShowCustomView`/`onHideCustomView` in the WebChromeClient: store custom view + callback, notify listener `onFullscreenVideo(view: View?)`), `MainActivity.kt` (render the custom view over the NavHost in a full-black Box when non-null; `onUserLeaveHint` → if custom view active → `enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).build())`; in `onPictureInPictureModeChanged` exit → if custom view was closed, call the stored callback).
- [ ] `assembleDebug` PASS → commit `feat(v3-p2): fullscreen video + picture-in-picture`.

---

### Task 10: Verification, merge, tag

- [ ] Full suites: `testDebugUnitTest` + `connectedDebugAndroidTest` green.
- [ ] Emulator walkthrough: (1) download a ~100MB real file (e.g. a Linux ISO fragment or testfile.org URL) via a page link → prompt sheet → Now → notification with progress; Downloads screen shows speed + sparkline; Pause → Resume continues (downloadedBytes not reset); `adb shell svc wifi disable && svc data disable` mid-download → FAILED/auto-resume when re-enabled; (2) completed small image download → preview sheet renders; (3) Settings > enable background playback + allow site on a video page (youtube.com or big buck bunny html5 demo) → home button → audio keeps playing + media notification; (4) fullscreen a video → home → PiP window appears; (5) `adb logcat -d | grep FATAL` empty; (6) old downloads from v2 still listed (migration).
- [ ] Merge `--no-ff` → `git tag v3-phase-2` → push.

## Self-review notes
- Spec coverage: G1 (T2-T6), G2 (T7), G3 (T8), G4 (T9), G7 (T6). Hidden system-downloader fallback per spec §6. Speed graph = sparkline (T7). Auto-resume = connectivity-constrained retry work (T6).
- Type consistency: `DownloadEntry` fields ↔ DAO setters ↔ engine listener writes ↔ screen reads all use downloaded/total/state strings from Global Constraints.
- Known simplifications recorded: speed derived from DB deltas in UI (not engine push) keeps layers decoupled; media-playing detection via `audioManager.isMusicActive` (system-wide) is an approximation — acceptable behind per-site opt-in.
