# InstaSaver Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make downloads visible and files checkable inside the app: a live list of saved items, in-app playback with share and delete, and automatic pickup of a freshly copied Instagram link.

**Architecture:** The screen becomes a list fed by two existing sources — MediaStore for saved files and WorkManager for in-flight downloads — so no database is introduced. Tapping an item opens a second screen holding a Media3 player or a decoded image, with share and delete acting directly on the MediaStore uri. On resume, the activity compares the clipboard against the last link it handled and enqueues a download when it is new.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Media3 (ExoPlayer + PlayerView), WorkManager, MediaStore, SharedPreferences, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-12-instasaver-phase-2-design.md` (phase 1: `docs/superpowers/specs/2026-09-11-instasaver-design.md`)

## Global Constraints

- **Never commit, never push.** This folder is a deliberately local-only git repository: no remotes, a `pre-push` hook that refuses every push, identity `Rae <rae@localhost>`. Do not run `git commit`, `git push`, `git remote add`, or any history-altering command, and never ask a subagent to. Tasks end with a verification step instead of a commit.
- **Package / namespace:** `com.raegreen.instasaver`. App label: `InstaSaver`.
- **minSdk 30** (raised from 29 in Task 1), compileSdk 36, targetSdk 36. SDK at `/Users/raegreen/Library/Android/sdk`.
- **Java 17 bytecode target.**
- **All dependency versions live in `gradle/libs.versions.toml`,** resolved from live Maven metadata. Never invent a version number.
- **Gallery destinations stay:** videos in `Movies/InstaSaver`, images in `Pictures/InstaSaver`. Filenames stay `{username}_{shortcode}.mp4` / `{username}_{shortcode}_{index}.jpg`.
- **Phase 1 behaviour is untouched:** the share-from-Instagram flow, notifications, retry routing, naming and duplicate detection all keep working. The only phase 1 file that changes behaviour is `DownloadWorker`/`DownloadCoordinator`, and only to publish a label.
- **The owner's phone is the test device** (`192.168.1.75:39897`, wireless debugging), not an emulator. It is their personal phone: instrumented tests must delete anything they create, and must never delete anything they did not create.
- **`./gradlew :app:connectedDebugAndroidTest` exits 1 on this project even when tests pass** — a known AGP 9.4.0 defect. Read `app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` for the real result (`tests=`, `failures=`, `errors=`).
- **`Log.d` for debug output, never `println`.**
- Every task ends with its tests passing. The phase 1 unit suite (23 tests) must stay green throughout.

---

### Task 1: Dependencies and minimum version

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing.
- Produces: Media3 available as `libs.media3.exoplayer` and `libs.media3.ui`; `minSdk = 30`.

- [ ] **Step 1: Resolve the Media3 version**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app
curl -s https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml \
  | grep -oE '<version>[^<]+</version>' | sed -E 's#</?version>##g' \
  | grep -viE 'alpha|beta|rc' | tail -1
```

Use that exact version for both Media3 artifacts. If it fails to resolve at build time because it requires a compileSdk above 36, step back to the newest version whose `aar-metadata.properties` reports `minCompileSdk` of 36 or lower — the same constraint Task 1 of phase 1 hit with Compose.

- [ ] **Step 2: Add the versions and libraries**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
media3 = "<from step 1>"
```

and to `[libraries]`:

```toml
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
```

- [ ] **Step 3: Wire them in and raise minSdk**

In `app/build.gradle.kts`, change `minSdk = 29` to `minSdk = 30`, and add to `dependencies`:

```kotlin
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
```

- [ ] **Step 4: Verify the build and the existing suite**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew assembleDebug :app:testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, 23/23 unit tests passing. Media3 is not used yet; this task only proves it resolves and that raising minSdk breaks nothing.

---

### Task 2: SavedItem and GalleryRepository

Reads the two gallery folders into the list, and re-reads whenever MediaStore changes.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/SavedItem.kt`
- Create: `app/src/main/java/com/raegreen/instasaver/GalleryRepository.kt`
- Test: `app/src/androidTest/java/com/raegreen/instasaver/GalleryRepositoryTest.kt`

**Interfaces:**
- Consumes: `MediaType` (phase 1, `PostDataParser.kt`).
- Produces:
  - `data class SavedItem(val uri: Uri, val displayName: String, val type: MediaType, val dateAddedSeconds: Long, val sizeBytes: Long)`
  - `class GalleryRepository(private val context: Context)` with `fun load(): List<SavedItem>` (blocking, newest first) and `fun items(): Flow<List<SavedItem>>` (emits immediately, then on every MediaStore change).

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/raegreen/instasaver/GalleryRepositoryTest.kt`:

```kotlin
package com.raegreen.instasaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val saver = GallerySaver(context)
    private val repository = GalleryRepository(context)

    @Test
    fun `lists a saved image and puts the newest first`() {
        val name = "instasaver_repotest_${System.currentTimeMillis()}.jpg"
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val uri = saver.save(name, MediaType.IMAGE, bytes.inputStream())

        try {
            val items = repository.load()
            val mine = items.first { it.displayName == name }

            assertEquals(MediaType.IMAGE, mine.type)
            assertEquals(bytes.size.toLong(), mine.sizeBytes)
            assertEquals(name, items.first().displayName)
            assertTrue(items.zipWithNext().all { (a, b) -> a.dateAddedSeconds >= b.dateAddedSeconds })
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun `reads both video and image folders`() {
        val stamp = System.currentTimeMillis()
        val imageName = "instasaver_repotest_${stamp}.jpg"
        val videoName = "instasaver_repotest_${stamp}.mp4"
        val bytes = byteArrayOf(1, 2, 3, 4)
        val imageUri = saver.save(imageName, MediaType.IMAGE, bytes.inputStream())
        val videoUri = saver.save(videoName, MediaType.VIDEO, bytes.inputStream())

        try {
            val names = repository.load().map { it.displayName }

            assertTrue(names.contains(imageName))
            assertTrue(names.contains(videoName))
        } finally {
            context.contentResolver.delete(imageUri, null, null)
            context.contentResolver.delete(videoUri, null, null)
        }
    }
}
```

Both tests delete only files they created, in a `finally` so a failed assertion still cleans up.

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:connectedDebugAndroidTest
```

Expected: compilation failure — `GalleryRepository` and `SavedItem` do not exist. (Ignore the task's exit code; read the XML named in Global Constraints.)

- [ ] **Step 3: Write SavedItem**

`app/src/main/java/com/raegreen/instasaver/SavedItem.kt`:

```kotlin
package com.raegreen.instasaver

import android.net.Uri

data class SavedItem(
    val uri: Uri,
    val displayName: String,
    val type: MediaType,
    val dateAddedSeconds: Long,
    val sizeBytes: Long,
)
```

- [ ] **Step 4: Write GalleryRepository**

`app/src/main/java/com/raegreen/instasaver/GalleryRepository.kt`:

```kotlin
package com.raegreen.instasaver

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class GalleryRepository(private val context: Context) {

    // Newest first, both folders merged — the gallery is the app's only record of what it saved
    fun load(): List<SavedItem> =
        (query(videoCollection, MediaType.VIDEO) + query(imageCollection, MediaType.IMAGE))
            .sortedByDescending { it.dateAddedSeconds }

    // Emits once immediately, then again every time MediaStore changes, so saves and deletions
    // made anywhere on the phone show up without the user refreshing
    fun items(): Flow<List<SavedItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { send(load()) }
            }
        }

        context.contentResolver.registerContentObserver(videoCollection, true, observer)
        context.contentResolver.registerContentObserver(imageCollection, true, observer)

        send(load())

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun query(collection: Uri, type: MediaType): List<SavedItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${relativePath(type)}%")
        val items = mutableListOf<SavedItem>()

        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                items += SavedItem(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                    displayName = cursor.getString(nameColumn),
                    type = type,
                    dateAddedSeconds = cursor.getLong(dateColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                )
            }
        }

        return items
    }

    private val videoCollection: Uri
        get() = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private val imageCollection: Uri
        get() = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun relativePath(type: MediaType): String =
        if (type == MediaType.VIDEO) "Movies/InstaSaver" else "Pictures/InstaSaver"
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:connectedDebugAndroidTest
```

Expected: the results XML reports 4 tests (2 new, 2 from phase 1's `GallerySaverTest`), 0 failures, 0 errors.

---

### Task 3: Live download rows

Teaches the download job to say what it is working on, and maps queue states to what a row shows.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/ActiveDownload.kt`
- Create: `app/src/test/java/com/raegreen/instasaver/DownloadStagesTest.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/DownloadCoordinator.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/DownloadWorker.kt`
- Modify: `app/src/test/java/com/raegreen/instasaver/DownloadCoordinatorTest.kt`

**Interfaces:**
- Consumes: `Outcome`, `PostSource`, `MediaSink` (phase 1).
- Produces:
  - `enum class DownloadStage { WAITING, DOWNLOADING }`
  - `data class ActiveDownload(val id: java.util.UUID, val label: String, val stage: DownloadStage)`
  - `object DownloadStages { fun stageOf(state: WorkInfo.State): DownloadStage?; fun activeDownloads(infos: List<WorkInfo>): List<ActiveDownload> }`
  - `DownloadWorker.TAG` (a work tag) and `DownloadWorker.KEY_LABEL` (the progress key holding the row label).
  - `DownloadCoordinator.run` becomes `suspend fun run(sharedText: String?, onLabel: suspend (String) -> Unit = {}): Outcome`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/raegreen/instasaver/DownloadStagesTest.kt`:

```kotlin
package com.raegreen.instasaver

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStagesTest {

    @Test
    fun `queued work is waiting`() {
        assertEquals(DownloadStage.WAITING, DownloadStages.stageOf(WorkInfo.State.ENQUEUED))
        assertEquals(DownloadStage.WAITING, DownloadStages.stageOf(WorkInfo.State.BLOCKED))
    }

    @Test
    fun `running work is downloading`() {
        assertEquals(DownloadStage.DOWNLOADING, DownloadStages.stageOf(WorkInfo.State.RUNNING))
    }

    @Test
    fun `finished work gets no row`() {
        assertNull(DownloadStages.stageOf(WorkInfo.State.SUCCEEDED))
        assertNull(DownloadStages.stageOf(WorkInfo.State.FAILED))
        assertNull(DownloadStages.stageOf(WorkInfo.State.CANCELLED))
    }
}
```

Add to `DownloadCoordinatorTest`, and convert its six existing tests to `runBlocking { }` since `run` is now suspending:

```kotlin
    @Test
    fun `reports what it is working on as it learns it`() = runBlocking {
        val labels = mutableListOf<String>()

        DownloadCoordinator(FakeSource(onePost), FakeSink()).run(reelLink) { labels += it }

        assertEquals(listOf("DdDxpiPOAzs", "@connorirwin_98"), labels)
    }
```

Add the import `import kotlinx.coroutines.runBlocking` to that test file.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*DownloadStagesTest*' --tests '*DownloadCoordinatorTest*'
```

Expected: compilation failure — `DownloadStages`, `DownloadStage` do not exist and `run` takes no label callback.

- [ ] **Step 3: Write the stage mapping**

`app/src/main/java/com/raegreen/instasaver/ActiveDownload.kt`:

```kotlin
package com.raegreen.instasaver

import androidx.work.WorkInfo
import java.util.UUID

enum class DownloadStage { WAITING, DOWNLOADING }

data class ActiveDownload(val id: UUID, val label: String, val stage: DownloadStage)

object DownloadStages {

    // Only unfinished work earns a row: a finished download becomes a file in the list below,
    // and a failure has already notified
    fun stageOf(state: WorkInfo.State): DownloadStage? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStage.WAITING
        WorkInfo.State.RUNNING -> DownloadStage.DOWNLOADING
        else -> null
    }

    fun activeDownloads(infos: List<WorkInfo>): List<ActiveDownload> = infos.mapNotNull { info ->
        val stage = stageOf(info.state) ?: return@mapNotNull null
        val label = info.progress.getString(DownloadWorker.KEY_LABEL) ?: DEFAULT_LABEL

        ActiveDownload(info.id, label, stage)
    }

    const val DEFAULT_LABEL = "Instagram post"
}
```

- [ ] **Step 4: Make the coordinator report what it is working on**

In `DownloadCoordinator.kt`, change the signature and add two callback calls. The rest of the method body is unchanged:

```kotlin
    // onLabel is called as the coordinator learns what this download is: the shortcode as soon
    // as the link parses, then the account once the page is fetched
    suspend fun run(sharedText: String?, onLabel: suspend (String) -> Unit = {}): Outcome {
        val target = LinkParser.parse(sharedText) ?: return Outcome.NotAPostLink

        (target as? LinkTarget.Post)?.ref?.shortcode?.let { onLabel(it) }

        val post = try {
            source.resolve(target)
        } catch (error: PostDataParseException) {
            return Outcome.CouldNotRead(error.message ?: "unreadable post")
        } catch (error: IOException) {
            return Outcome.NetworkFailed(error.message ?: "network error")
        }

        onLabel("@${post.username}")
```

Everything from `var saved = 0` onwards stays exactly as it is.

- [ ] **Step 5: Make the worker publish the label**

In `DownloadWorker.kt`: add the tag and key to the companion object, tag the request, and pass a callback that publishes progress.

```kotlin
        const val KEY_LABEL = "label"
        const val TAG = "instasaver-download"
```

In `enqueue`, add `.addTag(TAG)` to the `OneTimeWorkRequestBuilder` chain.

In `doWork`, change the coordinator call to:

```kotlin
        val outcome = DownloadCoordinator(source, sink).run(sharedText) { label ->
            setProgress(androidx.work.workDataOf(KEY_LABEL to label))
        }
```

Leave the surrounding try/catch, the cancellation rethrow, the retry gate and the notification calls exactly as they are.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, 27 tests passing (23 existing, 3 stage tests, 1 label test).

---

### Task 4: The list screen

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/Thumbnails.kt`
- Create: `app/src/main/java/com/raegreen/instasaver/DownloadsScreen.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/MainActivity.kt`

**Interfaces:**
- Consumes: `SavedItem`, `GalleryRepository` (Task 2); `ActiveDownload`, `DownloadStage`, `DownloadStages`, `DownloadWorker` (Task 3); `LinkParser` (phase 1).
- Produces:
  - `object Thumbnails { suspend fun load(context: Context, item: SavedItem): ImageBitmap? }`
  - `@Composable fun DownloadsScreen(onOpenItem: (SavedItem) -> Unit)`

- [ ] **Step 1: Write the thumbnail loader**

`app/src/main/java/com/raegreen/instasaver/Thumbnails.kt`:

```kotlin
package com.raegreen.instasaver

import android.content.Context
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Thumbnails {

    private const val PIXELS = 256
    private val cache = LruCache<String, ImageBitmap>(64)

    // MediaStore generates thumbnails for both video and image entries, so no image library is
    // needed. A failure returns null and the row falls back to an icon.
    suspend fun load(context: Context, item: SavedItem): ImageBitmap? {
        cache.get(item.uri.toString())?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(item.uri, Size(PIXELS, PIXELS), null).asImageBitmap()
            }.getOrNull()
        }

        bitmap?.let { cache.put(item.uri.toString(), it) }

        return bitmap
    }
}
```

- [ ] **Step 2: Write the list screen**

`app/src/main/java/com/raegreen/instasaver/DownloadsScreen.kt`:

```kotlin
package com.raegreen.instasaver

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import kotlinx.coroutines.flow.map

@Composable
fun DownloadsScreen(onOpenItem: (SavedItem) -> Unit) {
    val context = LocalContext.current
    val repository = remember { GalleryRepository(context) }

    // Saved files come from the gallery; live rows come from the work queue
    val items by remember { repository.items() }.collectAsStateWithLifecycle(emptyList())
    val active by remember {
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(DownloadWorker.TAG)
            .map { DownloadStages.activeDownloads(it) }
    }.collectAsStateWithLifecycle(emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PasteRow()

        if (items.isEmpty() && active.isEmpty()) {
            Text(
                "Nothing saved yet. In Instagram, tap Share and pick InstaSaver — or paste a link above.",
                modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(active, key = { it.id.toString() }) { download ->
                ActiveRow(download)
            }
            items(items, key = { it.uri.toString() }) { item ->
                SavedRow(item, onClick = { onOpenItem(item) })
            }
        }
    }
}

@Composable
private fun PasteRow() {
    val context = LocalContext.current

    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = link,
            onValueChange = {
                link = it
                error = null
            },
            label = { Text("Paste a link") },
            isError = error != null,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                if (LinkParser.parse(link) == null) {
                    error = "Not a post link"
                } else {
                    DownloadWorker.enqueue(context, link)
                    link = ""
                }
            },
            enabled = link.isNotBlank(),
        ) {
            Text("Save")
        }
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ActiveRow(download: ActiveDownload) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Column {
            Text(download.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (download.stage == DownloadStage.WAITING) "Waiting…" else "Downloading…",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SavedRow(item: SavedItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, item.uri) {
        value = Thumbnails.load(context, item)
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            thumbnail?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
            } ?: Icon(Icons.Filled.PlayArrow, contentDescription = null)
        }
        Column {
            Text(item.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${DateUtils.getRelativeTimeSpanString(item.dateAddedSeconds * 1000)} · ${Formatter.formatShortFileSize(context, item.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

- [ ] **Step 3: Add the lifecycle-aware collection dependency**

`collectAsStateWithLifecycle` lives in `androidx.lifecycle:lifecycle-runtime-compose`. Resolve and add it the same way as Task 1:

```bash
curl -s https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose/maven-metadata.xml \
  | grep -oE '<version>[^<]+</version>' | sed -E 's#</?version>##g' | grep -viE 'alpha|beta|rc' | tail -1
```

Add `lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycleCompose" }` to the catalog with a matching `[versions]` entry, and `implementation(libs.lifecycle.runtime.compose)` to `app/build.gradle.kts`. Material icons come from `androidx.compose.material:material-icons-core`, which the Compose BOM already provides through `material3`; if `Icons.Filled.PlayArrow` does not resolve, add `implementation("androidx.compose.material:material-icons-core")` under the existing BOM platform rather than pinning a version.

- [ ] **Step 4: Show the list in MainActivity**

Replace the body of `setContent` in `MainActivity.kt` so the screen state is held there, keeping the existing notification-permission request exactly as it is:

```kotlin
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var openItem by remember { mutableStateOf<SavedItem?>(null) }

                    NotificationPermissionRequest()

                    val item = openItem

                    if (item == null) {
                        DownloadsScreen(onOpenItem = { openItem = it })
                    } else {
                        Text("Item screen arrives in Task 5: ${item.displayName}")
                    }
                }
            }
        }
```

Move the existing `rememberLauncherForActivityResult` + `LaunchedEffect` permission code into a small private `@Composable fun NotificationPermissionRequest()` in the same file, unchanged in behaviour.

- [ ] **Step 5: Verify the build and suite**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, 27/27 unit tests passing.

- [ ] **Step 6: Check it on the phone**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew installDebug
/Users/raegreen/Library/Android/sdk/platform-tools/adb -s 192.168.1.75:39897 shell am start -n com.raegreen.instasaver/.MainActivity
```

Then ask the owner to confirm: the list shows their existing files newest first, thumbnails appear, and scrolling ~140 items is smooth. Record the answer — the spec's first open question is whether the built-in thumbnail loader is good enough or whether Coil is needed.

---

### Task 5: The item screen

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/ItemScreen.kt`
- Create: `app/src/main/java/com/raegreen/instasaver/ItemActions.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/MainActivity.kt`
- Test: `app/src/androidTest/java/com/raegreen/instasaver/ItemActionsTest.kt`

**Interfaces:**
- Consumes: `SavedItem`, `MediaType`.
- Produces:
  - `object ItemActions` with `fun shareIntent(item: SavedItem): Intent` and `fun deleteOrRequest(context: Context, uri: Uri): IntentSender?` (null when the file was deleted outright).
  - `@Composable fun ItemScreen(item: SavedItem, onBack: () -> Unit)`

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/raegreen/instasaver/ItemActionsTest.kt`:

```kotlin
package com.raegreen.instasaver

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemActionsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `share intent carries the file and its type`() {
        val saver = GallerySaver(context)
        val name = "instasaver_sharetest_${System.currentTimeMillis()}.mp4"
        val uri = saver.save(name, MediaType.VIDEO, byteArrayOf(1, 2, 3).inputStream())

        try {
            val item = SavedItem(uri, name, MediaType.VIDEO, 0, 3)
            val intent = ItemActions.shareIntent(item)

            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("video/mp4", intent.type)
            assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun `deleting a file this app saved needs no confirmation`() {
        val saver = GallerySaver(context)
        val name = "instasaver_deletetest_${System.currentTimeMillis()}.jpg"
        val uri = saver.save(name, MediaType.IMAGE, byteArrayOf(9, 9, 9).inputStream())

        val request = ItemActions.deleteOrRequest(context, uri)

        assertNull(request)
        assertEquals(0, GalleryRepository(context).load().count { it.displayName == name })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:connectedDebugAndroidTest
```

Expected: compilation failure — `ItemActions` does not exist.

- [ ] **Step 3: Write the actions**

`app/src/main/java/com/raegreen/instasaver/ItemActions.kt`:

```kotlin
package com.raegreen.instasaver

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore

object ItemActions {

    fun shareIntent(item: SavedItem): Intent = Intent(Intent.ACTION_SEND).apply {
        type = if (item.type == MediaType.VIDEO) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Files this app saved delete outright. Files it does not own (the owner's older downloads)
    // throw, and Android supplies a confirmation the user must approve — returned for the caller
    // to launch.
    fun deleteOrRequest(context: Context, uri: Uri): IntentSender? = try {
        context.contentResolver.delete(uri, null, null)

        null
    } catch (error: SecurityException) {
        (error as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
            ?: MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
    }
}
```

- [ ] **Step 4: Write the item screen**

`app/src/main/java/com/raegreen/instasaver/ItemScreen.kt`:

```kotlin
package com.raegreen.instasaver

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ItemScreen(item: SavedItem, onBack: () -> Unit) {
    val context = LocalContext.current

    // Android asks the user to confirm deleting files this app did not create
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) onBack()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }

        Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2)

        if (item.type == MediaType.VIDEO) {
            VideoPlayer(item)
        } else {
            ImageView(item)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { context.startActivity(Intent.createChooser(ItemActions.shareIntent(item), null)) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Share")
            }
            Button(
                onClick = {
                    val request = ItemActions.deleteOrRequest(context, item.uri)

                    if (request == null) {
                        onBack()
                    } else {
                        deleteLauncher.launch(IntentSenderRequest.Builder(request).build())
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun VideoPlayer(item: SavedItem) {
    val context = LocalContext.current

    var failed by remember(item.uri) { mutableStateOf(false) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    // A file that will not play must say so rather than showing a silent black rectangle
    DisposableEffect(item.uri) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    if (failed) {
        Text("Couldn't play this file")
    } else {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ImageView(item: SavedItem) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }

    bitmap?.let {
        Image(bitmap = it, contentDescription = item.displayName, modifier = Modifier.fillMaxWidth())
    } ?: Text("Couldn't open this image")
}
```

- [ ] **Step 5: Show it from MainActivity**

Replace the Task 4 placeholder branch with `ItemScreen(item = item, onBack = { openItem = null })`.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:connectedDebugAndroidTest
```

Expected: the results XML reports 6 tests, 0 failures, 0 errors.

- [ ] **Step 7: Check it on the phone**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew installDebug
```

Ask the owner to open a saved video, confirm it plays with sound and correct proportions, share it to Discord from the item screen, then delete one of the app's own saves (expect no prompt) and one older file (expect the system prompt). Record what they report.

---

### Task 6: Clipboard pickup

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/ClipboardPickup.kt`
- Create: `app/src/test/java/com/raegreen/instasaver/ClipboardPickupTest.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/MainActivity.kt`

**Interfaces:**
- Consumes: `LinkParser` (phase 1), `DownloadWorker.enqueue`.
- Produces:
  - `object ClipboardPickup { fun linkToDownload(clipboardText: String?, lastHandled: String?): String? }`
  - `class LastHandledLink(context: Context)` with `fun get(): String?` and `fun set(link: String)`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/raegreen/instasaver/ClipboardPickupTest.kt`:

```kotlin
package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardPickupTest {

    private val link = "https://www.instagram.com/reel/DdDxpiPOAzs/?stkn=abc"

    @Test
    fun `a newly copied post link is picked up`() {
        assertEquals(link, ClipboardPickup.linkToDownload(link, lastHandled = null))
    }

    @Test
    fun `the same link is not picked up twice`() {
        assertNull(ClipboardPickup.linkToDownload(link, lastHandled = link))
    }

    @Test
    fun `a different post is picked up after an earlier one`() {
        val newer = "https://www.instagram.com/p/DdHMr9eMQQR/"

        assertEquals(newer, ClipboardPickup.linkToDownload(newer, lastHandled = link))
    }

    @Test
    fun `surrounding whitespace does not make a link look new`() {
        assertNull(ClipboardPickup.linkToDownload("  $link  ", lastHandled = link))
    }

    @Test
    fun `non post text is ignored`() {
        assertNull(ClipboardPickup.linkToDownload("https://www.instagram.com/fleexyz/", lastHandled = null))
        assertNull(ClipboardPickup.linkToDownload("just some text", lastHandled = null))
        assertNull(ClipboardPickup.linkToDownload("", lastHandled = null))
        assertNull(ClipboardPickup.linkToDownload(null, lastHandled = null))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*ClipboardPickupTest*'
```

Expected: compilation failure — `ClipboardPickup` does not exist.

- [ ] **Step 3: Write the decision and the store**

`app/src/main/java/com/raegreen/instasaver/ClipboardPickup.kt`:

```kotlin
package com.raegreen.instasaver

import android.content.Context

object ClipboardPickup {

    // Returns the text to download, or null when there is nothing new to act on. Trimmed before
    // comparing so re-copying the same link with stray whitespace does not look like a new one.
    fun linkToDownload(clipboardText: String?, lastHandled: String?): String? {
        val text = clipboardText?.trim().orEmpty()

        if (text.isEmpty() || text == lastHandled) return null

        return if (LinkParser.parse(text) != null) text else null
    }
}

// The app's only owned persistent state: the last link it acted on
class LastHandledLink(context: Context) {

    private val preferences = context.getSharedPreferences("instasaver", Context.MODE_PRIVATE)

    fun get(): String? = preferences.getString(KEY, null)

    fun set(link: String) {
        preferences.edit().putString(KEY, link).apply()
    }

    private companion object {
        const val KEY = "last_handled_link"
    }
}
```

- [ ] **Step 4: Read the clipboard when the app comes to the front**

In `MainActivity.kt`, add the import `import android.content.ClipboardManager` and override `onResume`:

```kotlin
    // Android only allows clipboard reads while the app is in front, which is exactly when a
    // freshly copied link should be picked up
    override fun onResume() {
        super.onResume()

        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        val store = LastHandledLink(this)
        val link = ClipboardPickup.linkToDownload(text, store.get()) ?: return

        Log.d("InstaSaver", "clipboard pickup: ${link.take(60)}")

        store.set(link)

        DownloadWorker.enqueue(this, link)
    }
```

Add `import android.util.Log` if it is not already imported.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew assembleDebug :app:testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, 32 tests passing (27 from Task 3 plus 5 clipboard tests).

---

### Task 7: Failure visibility

Added after owner testing. A download failed silently: the app detected it correctly and posted
"Couldn't read this post", but the notification channel is `IMPORTANCE_LOW`, so it arrived with no
banner and no sound and the owner never saw it. The post turned out to be age-restricted —
Instagram serves its page without the post data to anyone not signed in.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/OutcomeMessages.kt`
- Create: `app/src/test/java/com/raegreen/instasaver/OutcomeMessagesTest.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/Notifier.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/DownloadWorker.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/ActiveDownload.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/DownloadsScreen.kt`
- Modify: `app/src/test/java/com/raegreen/instasaver/DownloadStagesTest.kt`

**Interfaces:**
- Consumes: `Outcome` (phase 1), `DownloadStage`, `ActiveDownload`, `DownloadStages`, `DownloadWorker`.
- Produces:
  - `object OutcomeMessages { fun isFailure(outcome: Outcome): Boolean; fun title(outcome: Outcome): String; fun text(outcome: Outcome): String }`
  - `DownloadStage` gains `FAILED`; `ActiveDownload` gains `val reason: String? = null`
  - `DownloadWorker.KEY_ERROR`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/raegreen/instasaver/OutcomeMessagesTest.kt`:

```kotlin
package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeMessagesTest {

    @Test
    fun `saving and skipping are not failures`() {
        assertFalse(OutcomeMessages.isFailure(Outcome.Saved(1)))
        assertFalse(OutcomeMessages.isFailure(Outcome.AlreadySaved))
    }

    @Test
    fun `every unsuccessful outcome is a failure`() {
        assertTrue(OutcomeMessages.isFailure(Outcome.NotAPostLink))
        assertTrue(OutcomeMessages.isFailure(Outcome.CouldNotRead("no post data in page")))
        assertTrue(OutcomeMessages.isFailure(Outcome.NetworkFailed("timeout")))
    }

    @Test
    fun `saved text counts the items`() {
        assertEquals("1 item saved to your gallery", OutcomeMessages.text(Outcome.Saved(1)))
        assertEquals("3 items saved to your gallery", OutcomeMessages.text(Outcome.Saved(3)))
    }

    @Test
    fun `an unreadable post explains the likely causes without inventing one`() {
        val text = OutcomeMessages.text(Outcome.CouldNotRead("no post data in page"))

        assertTrue(text.contains("age-restricted"))
        assertTrue(text.contains("private"))
        assertTrue(text.contains("removed"))
    }

    @Test
    fun `a network failure carries its reason`() {
        assertTrue(OutcomeMessages.text(Outcome.NetworkFailed("timeout")).contains("timeout"))
    }
}
```

Append to `DownloadStagesTest`:

```kotlin
    @Test
    fun `failed work gets a row so the user is told`() {
        assertEquals(DownloadStage.FAILED, DownloadStages.stageOf(WorkInfo.State.FAILED))
    }
```

and change the existing `finished work gets no row` test to drop `FAILED` from its assertions, keeping `SUCCEEDED` and `CANCELLED`.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*OutcomeMessagesTest*' --tests '*DownloadStagesTest*'
```

Expected: compilation failure — `OutcomeMessages` and `DownloadStage.FAILED` do not exist.

- [ ] **Step 3: Write the message source of truth**

`app/src/main/java/com/raegreen/instasaver/OutcomeMessages.kt`:

```kotlin
package com.raegreen.instasaver

// One place for what the user is told, used by both the notification and the failed row in the
// list, so the two can never drift apart.
object OutcomeMessages {

    fun isFailure(outcome: Outcome): Boolean = when (outcome) {
        is Outcome.Saved, Outcome.AlreadySaved -> false
        else -> true
    }

    fun title(outcome: Outcome): String = when (outcome) {
        is Outcome.Saved -> "Saved"
        Outcome.AlreadySaved -> "Already saved"
        Outcome.NotAPostLink -> "Not a post link"
        is Outcome.CouldNotRead -> "Couldn't read this post"
        is Outcome.NetworkFailed -> "Download failed"
    }

    fun text(outcome: Outcome): String = when (outcome) {
        is Outcome.Saved ->
            if (outcome.count == 1) "1 item saved to your gallery" else "${outcome.count} items saved to your gallery"

        Outcome.AlreadySaved -> "This post is already in your gallery"

        Outcome.NotAPostLink -> "That's not a post or Reel link"

        // Instagram does not say which restriction applies, so name the likely causes rather
        // than guessing one
        is Outcome.CouldNotRead ->
            "Instagram didn't send this post's data. It may be age-restricted, private, or removed. Open it in Instagram to check."

        is Outcome.NetworkFailed -> "Couldn't reach Instagram: ${outcome.reason}"
    }
}
```

- [ ] **Step 4: Give failures their own notification channel**

In `Notifier.kt`: create a second channel alongside the existing one, and pick per outcome.

```kotlin
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )

        // Failures need to actually reach the user; successes stay silent
        manager.createNotificationChannel(
            NotificationChannel(PROBLEM_CHANNEL_ID, "Problems", NotificationManager.IMPORTANCE_DEFAULT)
        )
```

Replace the `when` block inside `result` with calls to `OutcomeMessages`, and route the notification to `PROBLEM_CHANNEL_ID` when `OutcomeMessages.isFailure(outcome)` is true. `progress()` and the success path keep using `CHANNEL_ID`. Add `private const val PROBLEM_CHANNEL_ID = "problems"` to the companion object. `failure(...)` keeps its existing signature and uses the problem channel.

- [ ] **Step 5: Record failures as failed work carrying their reason**

In `DownloadWorker.doWork`, after `notifier.result(outcome)`, replace the final `Result` decision so any unsuccessful outcome is recorded as failed work with its message attached:

```kotlin
        notifier.result(outcome)

        // Recording a failure as failed work — with its reason — is what lets the list show it
        if (OutcomeMessages.isFailure(outcome)) {
            return@withContext Result.failure(Data.Builder().putString(KEY_ERROR, OutcomeMessages.text(outcome)).build())
        }

        Result.success()
```

Add `const val KEY_ERROR = "error"` to the companion object. Leave the retry gate, the cancellation rethrow and the unexpected-exception catch exactly as they are, except that the unexpected-exception path should also attach `KEY_ERROR` with its existing message so it appears in the list too.

- [ ] **Step 6: Turn failed work into a row**

In `ActiveDownload.kt`: add `FAILED` to `DownloadStage`, add `val reason: String? = null` to `ActiveDownload`, map `WorkInfo.State.FAILED` to `DownloadStage.FAILED` in `stageOf`, and in `activeDownloads` read the reason for failed work:

```kotlin
    fun activeDownloads(infos: List<WorkInfo>): List<ActiveDownload> = infos.mapNotNull { info ->
        val stage = stageOf(info.state) ?: return@mapNotNull null
        val label = info.progress.getString(DownloadWorker.KEY_LABEL) ?: DEFAULT_LABEL
        val reason = if (stage == DownloadStage.FAILED) info.outputData.getString(DownloadWorker.KEY_ERROR) else null

        ActiveDownload(info.id, label, stage, reason)
    }
```

- [ ] **Step 7: Show the failed row**

In `DownloadsScreen.kt`, extend `ActiveRow` so a `FAILED` download renders differently: no spinner, the reason in the error colour, and tapping it clears the row. Keep the waiting and downloading cases exactly as they are.

```kotlin
@Composable
private fun ActiveRow(download: ActiveDownload) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (download.stage == DownloadStage.FAILED) it.clickable { WorkManager.getInstance(context).pruneWork() } else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (download.stage == DownloadStage.FAILED) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        Column {
            Text(download.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (download.stage) {
                    DownloadStage.WAITING -> "Waiting…"
                    DownloadStage.DOWNLOADING -> "Downloading…"
                    DownloadStage.FAILED -> download.reason ?: "Download failed"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (download.stage == DownloadStage.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (download.stage == DownloadStage.FAILED) {
                Text("Tap to dismiss", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
```

Add the imports this needs: `androidx.compose.material.icons.filled.Warning` and `androidx.compose.foundation.clickable` if not already present.

- [ ] **Step 8: Run the tests to verify they pass**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew assembleDebug :app:testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, 38 tests passing (32 existing plus 5 message tests plus 1 stage test).

- [ ] **Step 9: Put it on the phone**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew installDebug
```

Then ask the owner to share the age-restricted post `https://www.instagram.com/reel/DdJ9yvzjvNh/?stkn=YXRzN3RnOHU0a2px` and confirm: a notification that actually alerts, and a failed row in the list naming the likely causes, which clears when tapped.

---

### Task 8: Whole-app check on the phone

**Files:**
- Create: `docs/phase-2-device-check-2026-09-12.md`

**Interfaces:**
- Consumes: everything built above.
- Produces: a record of what the owner observed, and answers to the spec's two open questions.

- [ ] **Step 1: Install the finished app**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew installDebug
/Users/raegreen/Library/Android/sdk/platform-tools/adb -s 192.168.1.75:39897 logcat -c
```

- [ ] **Step 2: Walk the owner through the checks and record each answer**

1. Share a Reel from Instagram, then open the app: a live row appears and resolves into a listed file.
2. The list shows their existing ~140 files, newest first, with thumbnails, and scrolls smoothly.
3. Tap a video: it plays with sound, correctly proportioned; controls work.
4. Share from the item screen to Discord: the file arrives and plays there.
5. Delete one of the app's own saves: no prompt, the row disappears.
6. Delete one older file: the system prompt appears; approving removes it, declining leaves it.
7. Copy a post link in Instagram, open the app: the download starts by itself and the clipboard toast appears.
8. Reopen the app without copying anything: nothing happens.
9. Copy a different post, open the app: it downloads.
10. Tap a saved image: it displays.

- [ ] **Step 3: Check the log for anything unexpected**

```bash
/Users/raegreen/Library/Android/sdk/platform-tools/adb -s 192.168.1.75:39897 logcat -d -s InstaSaver | tail -30
```

- [ ] **Step 4: Write up the results**

Create `docs/phase-2-device-check-2026-09-12.md` recording each check's outcome, the answers to the spec's two open questions (are thumbnails good enough without Coil; does the paste box still earn its place), and anything that behaved differently from the spec.

---

## Done means

- The app opens on a list of saved items, newest first, with thumbnails.
- A download in progress is visible as a live row that resolves into a file.
- Tapping an item plays the video or shows the image, with working Share and Delete.
- Opening the app with a freshly copied post link starts that download by itself, and does nothing on a second open.
- `./gradlew :app:testDebugUnitTest` passes with 32 tests; the instrumented suite reports 6 tests, 0 failures.
- Nothing has been committed or pushed.
