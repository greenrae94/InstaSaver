# InstaSaver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that saves an Instagram Reel or post to the phone's gallery when the post is shared into it, without taking over the screen.

**Architecture:** An invisible share-target activity hands the shared text to a WorkManager job. The job parses the link, fetches the post page with a mobile-Chrome header set, reads the media URLs out of the JSON embedded in that page, downloads each file with OkHttp and writes it into the gallery through MediaStore, then posts a notification. All Instagram-specific knowledge lives in two pure Kotlin classes (`LinkParser`, `PostDataParser`) that are unit-tested on the Mac against saved copies of real pages.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), OkHttp, kotlinx.serialization, WorkManager, MediaStore, JUnit 4 + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-09-11-instasaver-design.md`

## Global Constraints

- **Never commit, never push.** This folder is a deliberately local-only git repository: no remotes, a `pre-push` hook that refuses every push, and a local identity (`Rae <rae@localhost>`). Do not run `git commit`, `git push`, `git remote add`, or any history-altering command, and never ask a subagent to — the owner commits when they choose. Tasks end with a verification step instead of a commit.
- **Package / namespace:** `com.raegreen.instasaver`. App label: `InstaSaver`.
- **minSdk 29, compileSdk 36, targetSdk 36.** SDK at `/Users/raegreen/Library/Android/sdk`.
- **Java 17 bytecode target** (`compileOptions` and `jvmTarget`). System JDK is 17.0.20; Android Studio's bundled JDK 21 is at `/Applications/Android Studio.app/Contents/jbr/Contents/Home` if a newer AGP demands it.
- **All dependency versions live in `gradle/libs.versions.toml`.** Resolve them from the live Maven metadata (Task 1); never invent a version number.
- **Gallery destinations:** videos in `Movies/InstaSaver`, images in `Pictures/InstaSaver`.
- **Filenames:** `{username}_{shortcode}.mp4`, or `{username}_{shortcode}_{index}.jpg` for multi-item posts (index starts at 1).
- **The header set sent to Instagram is one constant** (`InstagramHeaders`) — never inline these headers at a call site.
- **No login, no Stories, no re-encoding, no third-party API, no server.** Songs on photo posts are explicitly phase 2 and out of scope here.
- **`console.debug` has no equivalent here:** use `Log.d(TAG, …)` for debug output, never `println`.
- Every task ends with tests passing. Do not start the next task with a failing test suite.

---

### Task 1: Project skeleton that builds

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/raegreen/instasaver/MainActivity.kt`
- Create: `.gitignore` (so the folder stays tidy even without git)
- Create: Gradle wrapper (`gradlew`, `gradle/wrapper/*`)

**Interfaces:**
- Consumes: nothing.
- Produces: a buildable Android project; `./gradlew assembleDebug` and `./gradlew :app:testDebugUnitTest` both work from the project root.

- [ ] **Step 1: Resolve the current versions**

Never guess these. Run:

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app
latest() { curl -s "$1" | grep -oE '<version>[^<]+</version>' | sed -E 's#</?version>##g' \
  | grep -viE 'alpha|beta|rc|dev|eap|-M[0-9]' | tail -1; }
echo "gradle:      $(curl -s https://services.gradle.org/versions/current | grep -oE '"version" *: *"[^"]+"' | head -1 | sed -E 's/.*: *"(.*)"/\1/')"
echo "agp:         $(latest https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)"
echo "kotlin:      $(latest https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml)"
echo "serialization: $(latest https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml)"
echo "okhttp:      $(curl -s https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml | grep -oE '<version>4\.[^<]+</version>' | sed -E 's#</?version>##g' | grep -viE 'alpha|beta|rc' | tail -1)"
echo "composeBom:  $(latest https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml)"
echo "activityCompose: $(latest https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml)"
echo "coreKtx:     $(latest https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/maven-metadata.xml)"
echo "work:        $(latest https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/maven-metadata.xml)"
echo "junit:       4.13.2"
echo "androidxTestRunner: $(latest https://dl.google.com/dl/android/maven2/androidx/test/runner/maven-metadata.xml)"
echo "androidxJunit: $(latest https://dl.google.com/dl/android/maven2/androidx/test/ext/junit/maven-metadata.xml)"
```

Record the printed values; every `<from step 1>` below means the matching value.

OkHttp is deliberately pinned to the newest **4.x**, not 5.x: the tests in Tasks 4 and 5 use
`okhttp3.mockwebserver`, whose package and API moved in 5.x. Everything else takes the newest
stable release.

- [ ] **Step 2: Create the Gradle wrapper**

There is no `gradle` on this machine, so download one distribution into the scratchpad and use it once to generate the wrapper:

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app
GV=<gradle from step 1>
TMP=$(mktemp -d)
curl -sL "https://services.gradle.org/distributions/gradle-$GV-bin.zip" -o "$TMP/g.zip"
unzip -q "$TMP/g.zip" -d "$TMP"
"$TMP/gradle-$GV/bin/gradle" wrapper --gradle-version "$GV"
ls gradlew gradle/wrapper/gradle-wrapper.jar
```

Expected: both paths exist.

- [ ] **Step 3: Write the version catalog**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "<agp from step 1>"
kotlin = "<kotlin from step 1>"
serialization = "<serialization from step 1>"
okhttp = "<okhttp from step 1>"
composeBom = "<composeBom from step 1>"
activityCompose = "<activityCompose from step 1>"
coreKtx = "<coreKtx from step 1>"
work = "<work from step 1>"
junit = "4.13.2"
androidxTestRunner = "<androidxTestRunner from step 1>"
androidxJunit = "<androidxJunit from step 1>"

[libraries]
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 4: Write the Gradle files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "InstaSaver"

include(":app")
```

`build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`local.properties`:

```properties
sdk.dir=/Users/raegreen/Library/Android/sdk
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.raegreen.instasaver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.raegreen.instasaver"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Kotlin 2.x sets the JVM target in its own top-level block, not inside android { }
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

`.gitignore`:

```gitignore
local.properties
.gradle/
build/
app/build/
.idea/
*.iml
.DS_Store
.superpowers/
```

- [ ] **Step 5: Write the manifest and a placeholder screen**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="false"
        android:label="InstaSaver"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/java/com/raegreen/instasaver/MainActivity.kt`:

```kotlin
package com.raegreen.instasaver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    Text("InstaSaver")
                }
            }
        }
    }
}
```

- [ ] **Step 6: Verify the build**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

Two failures are expected possibilities, both self-describing:
- "Minimum supported Gradle version is X" → re-run `./gradlew wrapper --gradle-version X` and build again.
- "Android Gradle plugin requires Java N" → add `org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home` to `gradle.properties` and build again.

---

### Task 2: LinkParser

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/LinkParser.kt`
- Test: `app/src/test/java/com/raegreen/instasaver/LinkParserTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `enum class PostKind { POST, REEL }`
  - `data class PostRef(val shortcode: String, val kind: PostKind)`
  - `sealed interface LinkTarget` with `data class Post(val ref: PostRef) : LinkTarget` and `data class Redirect(val url: String) : LinkTarget`
  - `object LinkParser { fun parse(sharedText: String?): LinkTarget? }` — `null` means "not an Instagram post link".

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/raegreen/instasaver/LinkParserTest.kt`:

```kotlin
package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkParserTest {

    private fun post(text: String) = LinkParser.parse(text) as LinkTarget.Post

    @Test
    fun `parses a reel link with tracking parameter`() {
        val target = post("https://www.instagram.com/reel/DdDxpiPOAzs/?stkn=OTQwa3YzaWNzcWZo")

        assertEquals(PostRef("DdDxpiPOAzs", PostKind.REEL), target.ref)
    }

    @Test
    fun `parses a post link with tracking parameter`() {
        val target = post("https://www.instagram.com/p/DdHMr9eMQQR/?stkn=bmRucHNmY2Y2aDd5")

        assertEquals(PostRef("DdHMr9eMQQR", PostKind.POST), target.ref)
    }

    @Test
    fun `parses reels and tv paths as reels`() {
        assertEquals(PostKind.REEL, post("https://www.instagram.com/reels/AbC123_-x/").ref.kind)
        assertEquals(PostKind.REEL, post("https://instagram.com/tv/AbC123_-x/").ref.kind)
    }

    @Test
    fun `finds the link inside surrounding share text`() {
        val target = post("Check this out https://www.instagram.com/reel/DdDxpiPOAzs/?igsh=abc \n- sent from Instagram")

        assertEquals("DdDxpiPOAzs", target.ref.shortcode)
    }

    @Test
    fun `treats a share link as needing a redirect`() {
        val target = LinkParser.parse("https://www.instagram.com/share/reel/AbCdEf123/") as LinkTarget.Redirect

        assertEquals("https://www.instagram.com/share/reel/AbCdEf123/", target.url)
    }

    @Test
    fun `rejects links that are not posts`() {
        assertNull(LinkParser.parse("https://www.instagram.com/fleexyz/"))
        assertNull(LinkParser.parse("https://www.instagram.com/stories/fleexyz/3982244854065794284/"))
        assertNull(LinkParser.parse("https://youtube.com/watch?v=abc"))
        assertNull(LinkParser.parse(""))
        assertNull(LinkParser.parse(null))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*LinkParserTest*'
```

Expected: compilation failure — `LinkParser`, `LinkTarget`, `PostRef` and `PostKind` do not exist.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/raegreen/instasaver/LinkParser.kt`:

```kotlin
package com.raegreen.instasaver

enum class PostKind { POST, REEL }

data class PostRef(val shortcode: String, val kind: PostKind)

sealed interface LinkTarget {
    data class Post(val ref: PostRef) : LinkTarget
    data class Redirect(val url: String) : LinkTarget
}

object LinkParser {

    // Instagram share sheets wrap the link in other text, so both patterns search rather than match whole
    private val POST_PATTERN = Regex("""instagram\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)""")
    private val SHARE_PATTERN = Regex("""https?://\S*instagram\.com/share/\S+""")

    fun parse(sharedText: String?): LinkTarget? {
        val text = sharedText?.trim().orEmpty()

        if (text.isEmpty()) return null

        // Short share links hide the real post id behind a redirect, so they are resolved later
        SHARE_PATTERN.find(text)?.let { return LinkTarget.Redirect(it.value.trimEnd('.', ',')) }

        val match = POST_PATTERN.find(text) ?: return null
        val kind = if (match.groupValues[1] == "p") PostKind.POST else PostKind.REEL

        return LinkTarget.Post(PostRef(match.groupValues[2], kind))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*LinkParserTest*'
```

Expected: `BUILD SUCCESSFUL`, 6 tests pass.

---

### Task 3: PostDataParser for single-media posts

Reads the media out of a saved Instagram page. Carousels come later (Task 9), once a real carousel page has been captured.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/PostDataParser.kt`
- Create: `app/src/test/resources/fixtures/reel.html`, `app/src/test/resources/fixtures/image-post.html`
- Test: `app/src/test/java/com/raegreen/instasaver/PostDataParserTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `enum class MediaType { VIDEO, IMAGE }`
  - `data class MediaItem(val url: String, val type: MediaType, val index: Int)` — `index` starts at 1
  - `data class ResolvedPost(val username: String, val shortcode: String, val items: List<MediaItem>)` — the shortcode is read from the page's own `code` field, so a `/share/` link that hid the id still produces correctly named files
  - `class PostDataParseException(message: String) : Exception(message)`
  - `object PostDataParser { fun parse(html: String): ResolvedPost }`

- [ ] **Step 1: Capture the fixtures**

The session that produced this plan saved real pages in its scratchpad. Copy them if they are still there, otherwise re-fetch:

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app
mkdir -p app/src/test/resources/fixtures
PROBE=/private/tmp/claude-501/-Users-raegreen-Documents-Personal-Apps-insta-vid-download-app/ca05610c-bfc7-4f2d-ba0b-cb1b8d5cbfa1/scratchpad/probe

if [ -f "$PROBE/curl-reel.html" ]; then
  cp "$PROBE/curl-reel.html" app/src/test/resources/fixtures/reel.html
  cp "$PROBE/curl-image.html" app/src/test/resources/fixtures/image-post.html
else
  fetch() { curl -sS -L --compressed -o "$2" \
    -A "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36" \
    -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8" \
    -H "Accept-Language: en-GB,en;q=0.9" \
    -H 'sec-ch-ua: "Chromium";v="152", "Google Chrome";v="152", "Not)A;Brand";v="99"' \
    -H "sec-ch-ua-mobile: ?1" -H 'sec-ch-ua-platform: "Android"' \
    -H "sec-fetch-dest: document" -H "sec-fetch-mode: navigate" -H "sec-fetch-site: none" -H "sec-fetch-user: ?1" \
    -H "upgrade-insecure-requests: 1" "$1"; }
  fetch "https://www.instagram.com/reel/DdDxpiPOAzs/?l=1" app/src/test/resources/fixtures/reel.html
  fetch "https://www.instagram.com/p/DdHMr9eMQQR/?l=1" app/src/test/resources/fixtures/image-post.html
fi

grep -c xig_polaris_media app/src/test/resources/fixtures/*.html
```

Expected: both files report at least 1 match. If a re-fetch reports 0, the posts may have been deleted — ask the owner for two fresh links (one Reel, one single image) before continuing, because every later task depends on these fixtures.

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/raegreen/instasaver/PostDataParserTest.kt`:

```kotlin
package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDataParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) { "missing fixture $name" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `reads a single video from a reel page`() {
        val post = PostDataParser.parse(fixture("reel.html"))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.VIDEO, post.items[0].type)
        assertEquals(1, post.items[0].index)
        assertTrue(post.items[0].url.startsWith("https://"))
        assertTrue(post.items[0].url.contains("fbcdn"))
        assertEquals("connorirwin_98", post.username)
        assertEquals("DdDxpiPOAzs", post.shortcode)
    }

    @Test
    fun `reads a single image from a photo post page`() {
        val post = PostDataParser.parse(fixture("image-post.html"))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.IMAGE, post.items[0].type)
        assertTrue(post.items[0].url.contains("fbcdn"))
        assertEquals("fleexyz", post.username)
        assertEquals("DdHMr9eMQQR", post.shortcode)
    }

    @Test
    fun `rejects a page with no post data`() {
        val error = assertThrows(PostDataParseException::class.java) {
            PostDataParser.parse("<html><body>nothing here</body></html>")
        }

        assertTrue(error.message!!.contains("no post data"))
    }
}
```

The two usernames are the real authors of the fixture posts. If Step 1 had to use fresh links, update these two assertions to the new authors.

- [ ] **Step 3: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*PostDataParserTest*'
```

Expected: compilation failure — `PostDataParser` does not exist.

- [ ] **Step 4: Write the implementation**

`app/src/main/java/com/raegreen/instasaver/PostDataParser.kt`:

```kotlin
package com.raegreen.instasaver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class MediaType { VIDEO, IMAGE }

data class MediaItem(val url: String, val type: MediaType, val index: Int)

data class ResolvedPost(val username: String, val shortcode: String, val items: List<MediaItem>)

class PostDataParseException(message: String) : Exception(message)

object PostDataParser {

    private val SCRIPT_PATTERN = Regex(
        """<script type="application/json"[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String): ResolvedPost {
        val node = findMediaNode(html) ?: throw PostDataParseException("no post data in page")
        val username = node["user"]?.jsonObjectOrNull()?.get("username")?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no username in post data")

        // The page states its own shortcode, which is what a share link's redirect would otherwise hide
        val shortcode = node["code"]?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no shortcode in post data")

        return ResolvedPost(username, shortcode, listOf(mediaItem(node, index = 1)))
    }

    // The page embeds its data in JSON script blocks; only some of them carry the post
    private fun findMediaNode(html: String): JsonObject? =
        SCRIPT_PATTERN.findAll(html)
            .map { it.groupValues[1] }
            .filter { it.contains("xig_polaris_media") }
            .mapNotNull { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            .mapNotNull { findUngatedMedia(it) }
            .firstOrNull()

    // The post sits at an unpredictable depth, so walk the tree looking for the wrapper
    private fun findUngatedMedia(element: JsonElement): JsonObject? {
        when (element) {
            is JsonObject -> {
                val media = element["xig_polaris_media"]?.jsonObjectOrNull()
                val ungated = media?.get("if_not_gated_logged_out")?.jsonObjectOrNull()

                if (ungated != null) return ungated

                element.values.forEach { child -> findUngatedMedia(child)?.let { return it } }
            }

            is JsonArray -> element.forEach { child -> findUngatedMedia(child)?.let { return it } }

            else -> return null
        }

        return null
    }

    // Videos list ready-made files; images list candidates with the full-size original first
    private fun mediaItem(node: JsonObject, index: Int): MediaItem {
        val video = (node["video_versions"] as? JsonArray)?.firstOrNull()
            ?.jsonObjectOrNull()?.get("url")?.jsonPrimitive?.contentOrNull

        if (video != null) return MediaItem(video, MediaType.VIDEO, index)

        val image = node["image_versions2"]?.jsonObjectOrNull()
            ?.get("candidates")?.let { it as? JsonArray }?.firstOrNull()
            ?.jsonObjectOrNull()?.get("url")?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no video or image in post data")

        return MediaItem(image, MediaType.IMAGE, index)
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*PostDataParserTest*'
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass.

---

### Task 4: InstagramPageFetcher

Fetches a post page the way Chrome on Android does. Without the full header set Instagram returns a page with no post data, so the headers are the substance of this task.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/InstagramHeaders.kt`
- Create: `app/src/main/java/com/raegreen/instasaver/InstagramPageFetcher.kt`
- Test: `app/src/test/java/com/raegreen/instasaver/InstagramPageFetcherTest.kt`

**Interfaces:**
- Consumes: `PostRef`, `PostKind` (Task 2).
- Produces:
  - `object InstagramHeaders { val values: Map<String, String> }`
  - `class InstagramPageFetcher(private val client: OkHttpClient, private val baseUrl: String = "https://www.instagram.com")` with `fun fetch(ref: PostRef): String` and `fun resolveRedirect(url: String): String` (returns the final URL after redirects).
  - Throws `java.io.IOException` on a non-2xx response.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/raegreen/instasaver/InstagramPageFetcherTest.kt`:

```kotlin
package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class InstagramPageFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: InstagramPageFetcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = InstagramPageFetcher(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `requests the reel path with the interstitial skipped`() {
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        val body = fetcher.fetch(PostRef("DdDxpiPOAzs", PostKind.REEL))
        val request = server.takeRequest()

        assertEquals("/reel/DdDxpiPOAzs/?l=1", request.path)
        assertEquals("<html>page</html>", body)
    }

    @Test
    fun `requests the post path for photo posts`() {
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        fetcher.fetch(PostRef("DdHMr9eMQQR", PostKind.POST))

        assertEquals("/p/DdHMr9eMQQR/?l=1", server.takeRequest().path)
    }

    @Test
    fun `sends the browser headers instagram requires`() {
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        fetcher.fetch(PostRef("DdDxpiPOAzs", PostKind.REEL))
        val request = server.takeRequest()

        assertTrue(request.getHeader("User-Agent")!!.contains("Android"))
        assertEquals("?1", request.getHeader("sec-ch-ua-mobile"))
        assertEquals("\"Android\"", request.getHeader("sec-ch-ua-platform"))
        assertEquals("document", request.getHeader("sec-fetch-dest"))
        assertEquals("navigate", request.getHeader("sec-fetch-mode"))
        assertEquals("none", request.getHeader("sec-fetch-site"))
        assertEquals("1", request.getHeader("upgrade-insecure-requests"))
        assertTrue(request.getHeader("Accept")!!.contains("text/html"))
        assertTrue(request.getHeader("Accept-Language")!!.contains("en"))
        assertTrue(request.getHeader("sec-ch-ua")!!.isNotEmpty())
    }

    @Test
    fun `reports a failed response`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("gone"))

        assertThrows(IOException::class.java) {
            fetcher.fetch(PostRef("DdDxpiPOAzs", PostKind.REEL))
        }
    }

    @Test
    fun `follows a share link to its final url`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/reel/DdDxpiPOAzs/"))
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        val finalUrl = fetcher.resolveRedirect(server.url("/share/reel/xyz/").toString())

        assertTrue(finalUrl.endsWith("/reel/DdDxpiPOAzs/"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*InstagramPageFetcherTest*'
```

Expected: compilation failure — `InstagramPageFetcher` does not exist.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/raegreen/instasaver/InstagramHeaders.kt`:

```kotlin
package com.raegreen.instasaver

// Instagram only embeds post data for requests that look like a real mobile browser.
// Verified 2026-09-11: dropping these returns a page with no post data at all.
object InstagramHeaders {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"

    val values: Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-GB,en;q=0.9",
        "sec-ch-ua" to "\"Chromium\";v=\"152\", \"Google Chrome\";v=\"152\", \"Not)A;Brand\";v=\"99\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "sec-fetch-dest" to "document",
        "sec-fetch-mode" to "navigate",
        "sec-fetch-site" to "none",
        "sec-fetch-user" to "?1",
        "upgrade-insecure-requests" to "1",
    )
}
```

`app/src/main/java/com/raegreen/instasaver/InstagramPageFetcher.kt`:

```kotlin
package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class InstagramPageFetcher(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://www.instagram.com",
) {

    fun fetch(ref: PostRef): String {
        val segment = if (ref.kind == PostKind.POST) "p" else "reel"

        // ?l=1 skips the "watch this reel in the app" interstitial
        return get("$baseUrl/$segment/${ref.shortcode}/?l=1")
    }

    fun resolveRedirect(url: String): String {
        val request = Request.Builder().url(url).headers(headers()).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")

            return response.request.url.toString()
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).headers(headers()).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")

            return response.body?.string() ?: throw IOException("empty body for $url")
        }
    }

    private fun headers() = okhttp3.Headers.Builder().apply {
        InstagramHeaders.values.forEach { (name, value) -> add(name, value) }
    }.build()
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*InstagramPageFetcherTest*'
```

Expected: `BUILD SUCCESSFUL`, 5 tests pass.

---

### Task 5: MediaResolver

Joins the parser and the fetcher, and resolves `/share/` links before fetching.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/MediaResolver.kt`
- Test: `app/src/test/java/com/raegreen/instasaver/MediaResolverTest.kt`

**Interfaces:**
- Consumes: `LinkTarget`, `LinkParser`, `PostRef` (Task 2); `ResolvedPost`, `PostDataParser`, `PostDataParseException` (Task 3); `InstagramPageFetcher` (Task 4).
- Produces: `class MediaResolver(private val fetcher: InstagramPageFetcher)` with `fun resolve(target: LinkTarget): ResolvedPost`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/raegreen/instasaver/MediaResolverTest.kt`:

```kotlin
package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MediaResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: MediaResolver

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")).bufferedReader().use { it.readText() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = MediaResolver(InstagramPageFetcher(OkHttpClient(), server.url("/").toString().trimEnd('/')))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resolves a reel to one video item`() {
        server.enqueue(MockResponse().setBody(fixture("reel.html")))

        val post = resolver.resolve(LinkTarget.Post(PostRef("DdDxpiPOAzs", PostKind.REEL)))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.VIDEO, post.items[0].type)
    }

    @Test
    fun `follows a share link before resolving`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/reel/DdDxpiPOAzs/"))
        server.enqueue(MockResponse().setBody(fixture("reel.html")))
        server.enqueue(MockResponse().setBody(fixture("reel.html")))

        val post = resolver.resolve(LinkTarget.Redirect(server.url("/share/reel/xyz/").toString()))

        assertEquals(MediaType.VIDEO, post.items[0].type)
    }

    @Test
    fun `reports a share link that does not lead to a post`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/accounts/login/"))
        server.enqueue(MockResponse().setBody("<html>login</html>"))

        assertThrows(PostDataParseException::class.java) {
            resolver.resolve(LinkTarget.Redirect(server.url("/share/reel/xyz/").toString()))
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*MediaResolverTest*'
```

Expected: compilation failure — `MediaResolver` does not exist.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/raegreen/instasaver/MediaResolver.kt`:

```kotlin
package com.raegreen.instasaver

class MediaResolver(private val fetcher: InstagramPageFetcher) {

    fun resolve(target: LinkTarget): ResolvedPost {
        val ref = when (target) {
            is LinkTarget.Post -> target.ref
            is LinkTarget.Redirect -> refFromRedirect(target.url)
        }

        return PostDataParser.parse(fetcher.fetch(ref))
    }

    // Short share links hide the post id, so follow the redirect and read the real URL
    private fun refFromRedirect(url: String): PostRef {
        val finalUrl = fetcher.resolveRedirect(url)
        val target = LinkParser.parse(finalUrl)

        return (target as? LinkTarget.Post)?.ref
            ?: throw PostDataParseException("share link did not lead to a post: $finalUrl")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*MediaResolverTest*'
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass.

---

### Task 6: MediaDownloader and GallerySaver

Writes files into the phone's gallery. MediaStore only exists on a device, so this task is verified with an instrumented test on the emulator.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/MediaDownloader.kt`
- Create: `app/src/main/java/com/raegreen/instasaver/GallerySaver.kt`
- Test: `app/src/androidTest/java/com/raegreen/instasaver/GallerySaverTest.kt`

**Interfaces:**
- Consumes: `MediaItem`, `MediaType`, `ResolvedPost` (Task 3).
- Produces:
  - `class MediaDownloader(private val client: OkHttpClient)` with `fun download(url: String): InputStream` (follows redirects; throws `IOException` on failure).
  - `object FileNaming { fun fileName(username: String, shortcode: String, item: MediaItem, totalItems: Int): String }`
  - `class GallerySaver(private val context: Context)` with `fun alreadySaved(fileName: String, type: MediaType): Boolean` and `fun save(fileName: String, type: MediaType, source: InputStream): Uri`.

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/raegreen/instasaver/GallerySaverTest.kt`:

```kotlin
package com.raegreen.instasaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GallerySaverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val saver = GallerySaver(context)

    @Test
    fun `names single and multi item files`() {
        val video = MediaItem("https://x/v.mp4", MediaType.VIDEO, 1)
        val image = MediaItem("https://x/i.jpg", MediaType.IMAGE, 2)

        assertEquals("fleexyz_DdHMr9eMQQR.mp4", FileNaming.fileName("fleexyz", "DdHMr9eMQQR", video, totalItems = 1))
        assertEquals("fleexyz_DdHMr9eMQQR_2.jpg", FileNaming.fileName("fleexyz", "DdHMr9eMQQR", image, totalItems = 3))
    }

    @Test
    fun `saves an image into the gallery and finds it again`() {
        val name = "instasaver_test_${System.currentTimeMillis()}.jpg"
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        assertFalse(saver.alreadySaved(name, MediaType.IMAGE))

        val uri = saver.save(name, MediaType.IMAGE, bytes.inputStream())

        assertTrue(saver.alreadySaved(name, MediaType.IMAGE))
        assertEquals(bytes.size, context.contentResolver.openInputStream(uri)!!.use { it.readBytes().size })

        context.contentResolver.delete(uri, null, null)
    }
}
```

- [ ] **Step 2: Start an emulator and run the test to verify it fails**

```bash
SDK=/Users/raegreen/Library/Android/sdk
nohup $SDK/emulator/emulator -avd Pixel_6_Pro_API_35 -no-snapshot-load > /tmp/emulator.log 2>&1 &
$SDK/platform-tools/adb wait-for-device
$SDK/platform-tools/adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:connectedDebugAndroidTest
```

Expected: compilation failure — `GallerySaver` and `FileNaming` do not exist.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/raegreen/instasaver/MediaDownloader.kt`:

```kotlin
package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

class MediaDownloader(private val client: OkHttpClient) {

    // Instagram's CDN answers the first request with a 302, so redirects must be followed
    fun download(url: String): InputStream {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", InstagramHeaders.USER_AGENT)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()

            throw IOException("HTTP ${response.code} downloading $url")
        }

        return response.body?.byteStream() ?: throw IOException("empty body for $url")
    }
}
```

`app/src/main/java/com/raegreen/instasaver/GallerySaver.kt`:

```kotlin
package com.raegreen.instasaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.io.InputStream

object FileNaming {

    fun fileName(username: String, shortcode: String, item: MediaItem, totalItems: Int): String {
        val extension = if (item.type == MediaType.VIDEO) "mp4" else "jpg"
        val suffix = if (totalItems > 1) "_${item.index}" else ""

        return "${username}_${shortcode}$suffix.$extension"
    }
}

class GallerySaver(private val context: Context) {

    fun alreadySaved(fileName: String, type: MediaType): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(fileName, "${relativePath(type)}%")

        context.contentResolver.query(collection(type), projection, selection, args, null)?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }

    // IS_PENDING hides the file from the gallery until it is written in full
    fun save(fileName: String, type: MediaType, source: InputStream): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(type))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(collection(type), values)
            ?: throw IOException("could not create gallery entry for $fileName")

        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.use { it.copyTo(output) }
            } ?: throw IOException("could not open $fileName for writing")
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)

            throw error
        }

        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)

        return uri
    }

    private fun collection(type: MediaType): Uri =
        if (type == MediaType.VIDEO) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

    private fun relativePath(type: MediaType): String =
        if (type == MediaType.VIDEO) "Movies/InstaSaver" else "Pictures/InstaSaver"
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`, 2 tests pass.

---

### Task 7: DownloadCoordinator

All the decision-making in one testable place: what to do with shared text, and what outcome to report. Kept free of Android APIs so it runs in JVM tests with fakes.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/DownloadCoordinator.kt`
- Test: `app/src/test/java/com/raegreen/instasaver/DownloadCoordinatorTest.kt`

**Interfaces:**
- Consumes: `LinkParser`, `LinkTarget` (Task 2); `ResolvedPost`, `MediaItem`, `MediaType`, `PostDataParseException` (Task 3); `MediaResolver` (Task 5); `MediaDownloader`, `GallerySaver`, `FileNaming` (Task 6).
- Produces:
  - `interface MediaSink { fun alreadySaved(fileName: String, type: MediaType): Boolean; fun save(fileName: String, type: MediaType, source: InputStream) }`
  - `interface PostSource { fun resolve(target: LinkTarget): ResolvedPost; fun download(url: String): InputStream }`
  - `sealed interface Outcome` with `data class Saved(val count: Int)`, `object AlreadySaved`, `object NotAPostLink`, `data class CouldNotRead(val reason: String)`, `data class NetworkFailed(val reason: String)`
  - `class DownloadCoordinator(private val source: PostSource, private val sink: MediaSink)` with `fun run(sharedText: String?): Outcome`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/raegreen/instasaver/DownloadCoordinatorTest.kt`:

```kotlin
package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

private class FakeSource(
    private val post: ResolvedPost? = null,
    private val error: Exception? = null,
) : PostSource {
    var downloaded = mutableListOf<String>()

    override fun resolve(target: LinkTarget): ResolvedPost = post ?: throw error!!

    override fun download(url: String): InputStream {
        downloaded += url

        return ByteArrayInputStream(byteArrayOf(1, 2, 3))
    }
}

private class FakeSink(private val existing: Set<String> = emptySet()) : MediaSink {
    val saved = mutableListOf<String>()

    override fun alreadySaved(fileName: String, type: MediaType) = fileName in existing

    override fun save(fileName: String, type: MediaType, source: InputStream) {
        source.readBytes()
        saved += fileName
    }
}

class DownloadCoordinatorTest {

    private val reelLink = "https://www.instagram.com/reel/DdDxpiPOAzs/?stkn=abc"
    private val onePost = ResolvedPost("connorirwin_98", "DdDxpiPOAzs", listOf(MediaItem("https://cdn/v.mp4", MediaType.VIDEO, 1)))
    private val carousel = ResolvedPost(
        "fleexyz",
        "DdHMr9eMQQR",
        listOf(
            MediaItem("https://cdn/1.jpg", MediaType.IMAGE, 1),
            MediaItem("https://cdn/2.mp4", MediaType.VIDEO, 2),
        ),
    )

    @Test
    fun `saves a single video and names it after the account and post`() {
        val sink = FakeSink()

        val outcome = DownloadCoordinator(FakeSource(onePost), sink).run(reelLink)

        assertEquals(Outcome.Saved(1), outcome)
        assertEquals(listOf("connorirwin_98_DdDxpiPOAzs.mp4"), sink.saved)
    }

    @Test
    fun `saves every item of a multi item post in order`() {
        val sink = FakeSink()

        val outcome = DownloadCoordinator(FakeSource(carousel), sink).run(reelLink)

        assertEquals(Outcome.Saved(2), outcome)
        assertEquals(listOf("fleexyz_DdHMr9eMQQR_1.jpg", "fleexyz_DdHMr9eMQQR_2.mp4"), sink.saved)
    }

    @Test
    fun `skips a post that is already saved`() {
        val sink = FakeSink(existing = setOf("connorirwin_98_DdDxpiPOAzs.mp4"))

        val outcome = DownloadCoordinator(FakeSource(onePost), sink).run(reelLink)

        assertEquals(Outcome.AlreadySaved, outcome)
        assertTrue(sink.saved.isEmpty())
    }

    @Test
    fun `rejects text that is not a post link`() {
        val outcome = DownloadCoordinator(FakeSource(onePost), FakeSink()).run("https://www.instagram.com/fleexyz/")

        assertEquals(Outcome.NotAPostLink, outcome)
    }

    @Test
    fun `reports a page it cannot read`() {
        val source = FakeSource(error = PostDataParseException("no post data in page"))

        val outcome = DownloadCoordinator(source, FakeSink()).run(reelLink)

        assertTrue(outcome is Outcome.CouldNotRead)
    }

    @Test
    fun `reports a network failure separately so it can be retried`() {
        val source = FakeSource(error = IOException("timeout"))

        val outcome = DownloadCoordinator(source, FakeSink()).run(reelLink)

        assertTrue(outcome is Outcome.NetworkFailed)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*DownloadCoordinatorTest*'
```

Expected: compilation failure — `DownloadCoordinator`, `PostSource`, `MediaSink` and `Outcome` do not exist.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/raegreen/instasaver/DownloadCoordinator.kt`:

```kotlin
package com.raegreen.instasaver

import java.io.IOException
import java.io.InputStream

interface PostSource {
    fun resolve(target: LinkTarget): ResolvedPost
    fun download(url: String): InputStream
}

interface MediaSink {
    fun alreadySaved(fileName: String, type: MediaType): Boolean
    fun save(fileName: String, type: MediaType, source: InputStream)
}

sealed interface Outcome {
    data class Saved(val count: Int) : Outcome
    object AlreadySaved : Outcome
    object NotAPostLink : Outcome
    data class CouldNotRead(val reason: String) : Outcome
    data class NetworkFailed(val reason: String) : Outcome
}

class DownloadCoordinator(private val source: PostSource, private val sink: MediaSink) {

    fun run(sharedText: String?): Outcome {
        val target = LinkParser.parse(sharedText) ?: return Outcome.NotAPostLink

        val post = try {
            source.resolve(target)
        } catch (error: PostDataParseException) {
            return Outcome.CouldNotRead(error.message ?: "unreadable post")
        } catch (error: IOException) {
            return Outcome.NetworkFailed(error.message ?: "network error")
        }

        var saved = 0

        for (item in post.items) {
            val fileName = FileNaming.fileName(post.username, post.shortcode, item, post.items.size)

            if (sink.alreadySaved(fileName, item.type)) continue

            try {
                sink.save(fileName, item.type, source.download(item.url))
            } catch (error: IOException) {
                return Outcome.NetworkFailed(error.message ?: "download failed")
            }

            saved++
        }

        return if (saved == 0) Outcome.AlreadySaved else Outcome.Saved(saved)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*DownloadCoordinatorTest*'
```

Expected: `BUILD SUCCESSFUL`, 6 tests pass.

Note: filenames come from the page's own `code` field rather than the shared link, so a
`/share/` link whose redirect hid the id still produces a correctly named file.

---

### Task 8: The app itself — worker, notifications, share target, screen

Wires the tested pieces into the running app. Verified by hand on the emulator at the end of this task, and properly on the phone in Task 9.

**Files:**
- Create: `app/src/main/java/com/raegreen/instasaver/Notifier.kt`
- Create: `app/src/main/java/com/raegreen/instasaver/DownloadWorker.kt`
- Create: `app/src/main/java/com/raegreen/instasaver/ShareReceiverActivity.kt`
- Modify: `app/src/main/java/com/raegreen/instasaver/MainActivity.kt` (replace placeholder screen)
- Modify: `app/src/main/AndroidManifest.xml` (add the share target)

**Interfaces:**
- Consumes: everything from Tasks 2–7.
- Produces:
  - `class Notifier(private val context: Context)` with `fun progress()`, `fun result(outcome: Outcome)`
  - `class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker` with `companion object { const val KEY_TEXT = "shared_text"; fun enqueue(context: Context, sharedText: String) }`

- [ ] **Step 1: Write the notifier**

`app/src/main/java/com/raegreen/instasaver/Notifier.kt`:

```kotlin
package com.raegreen.instasaver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class Notifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun progress() = notify("Saving…", null)

    fun result(outcome: Outcome) {
        val (title, text) = when (outcome) {
            is Outcome.Saved -> "Saved" to if (outcome.count == 1) "1 item saved to your gallery" else "${outcome.count} items saved to your gallery"
            Outcome.AlreadySaved -> "Already saved" to "This post is already in your gallery"
            Outcome.NotAPostLink -> "Not a post link" to "That's not a post or Reel link"
            is Outcome.CouldNotRead -> "Couldn't read this post" to "Instagram didn't return the post data"
            is Outcome.NetworkFailed -> "Download failed" to outcome.reason
        }

        notify(title, text)
    }

    private fun notify(title: String, text: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1
    }
}
```

- [ ] **Step 2: Write the worker**

`app/src/main/java/com/raegreen/instasaver/DownloadWorker.kt`:

```kotlin
package com.raegreen.instasaver

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStream

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sharedText = inputData.getString(KEY_TEXT)
        val notifier = Notifier(applicationContext)

        notifier.progress()

        val client = OkHttpClient()
        val resolver = MediaResolver(InstagramPageFetcher(client))
        val downloader = MediaDownloader(client)
        val saver = GallerySaver(applicationContext)

        val source = object : PostSource {
            override fun resolve(target: LinkTarget) = resolver.resolve(target)
            override fun download(url: String) = downloader.download(url)
        }

        val sink = object : MediaSink {
            override fun alreadySaved(fileName: String, type: MediaType) = saver.alreadySaved(fileName, type)
            override fun save(fileName: String, type: MediaType, source: InputStream) {
                saver.save(fileName, type, source)
            }
        }

        val outcome = DownloadCoordinator(source, sink).run(sharedText)

        Log.d(TAG, "outcome for ${sharedText?.take(80)}: $outcome")

        // Only network problems are worth another attempt; a bad link or unreadable page will fail again
        if (outcome is Outcome.NetworkFailed && runAttemptCount < 3) return@withContext Result.retry()

        notifier.result(outcome)

        Result.success()
    }

    companion object {
        const val KEY_TEXT = "shared_text"
        private const val TAG = "InstaSaver"

        fun enqueue(context: Context, sharedText: String) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putString(KEY_TEXT, sharedText).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
```

- [ ] **Step 3: Write the share target**

`app/src/main/java/com/raegreen/instasaver/ShareReceiverActivity.kt`:

```kotlin
package com.raegreen.instasaver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

// Invisible on purpose: it hands the text to the worker and closes, so Instagram stays on screen
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)

        if (!sharedText.isNullOrBlank()) {
            DownloadWorker.enqueue(applicationContext, sharedText)
        }

        finish()
    }
}
```

Add to `AndroidManifest.xml`, inside `<application>`:

```xml
        <activity
            android:name=".ShareReceiverActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:taskAffinity=""
            android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
```

- [ ] **Step 4: Write the main screen**

Replace `app/src/main/java/com/raegreen/instasaver/MainActivity.kt`:

```kotlin
package com.raegreen.instasaver

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PasteScreen()
                }
            }
        }
    }
}

@Composable
private fun PasteScreen() {
    val context = LocalContext.current

    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Android 13+ will not show notifications until the user allows them
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("InstaSaver", style = MaterialTheme.typography.headlineMedium)
        Text("In Instagram, tap Share and pick InstaSaver. Or paste a link here.")
        OutlinedTextField(
            value = link,
            onValueChange = {
                link = it
                error = null
            },
            label = { Text("Instagram link") },
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (LinkParser.parse(link) == null) {
                    error = "That's not a post or Reel link"
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
}
```

- [ ] **Step 5: Build and check the whole suite still passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all unit tests pass.

- [ ] **Step 6: Smoke test on the emulator**

With the emulator from Task 6 running:

```bash
SDK=/Users/raegreen/Library/Android/sdk
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew installDebug
$SDK/platform-tools/adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://www.instagram.com/reel/DdDxpiPOAzs/" \
  -n com.raegreen.instasaver/.ShareReceiverActivity
sleep 20
$SDK/platform-tools/adb shell 'content query --uri content://media/external/video/media --projection _display_name,relative_path' | grep -i instasaver
$SDK/platform-tools/adb logcat -d -s InstaSaver | tail -5
```

Expected: the query lists `connorirwin_98_DdDxpiPOAzs.mp4` under `Movies/InstaSaver/`, and the log line reports `Saved(count=1)`.

If the emulator has no network access to Instagram, note it and move to Task 9 — the phone is the real test.

---

### Task 9: End-to-end on the S25 Ultra, and the two open questions

Answers the open questions from the spec: what Instagram's share sheet actually sends, and what a carousel's data looks like.

**Files:**
- Create: `app/src/test/resources/fixtures/carousel.html` (captured here, used in Task 10)
- Create: `tools/live-check.sh` (diagnoses future breakage)
- Create: `docs/device-check-2026-09-11.md` (what was observed, for future reference)

**Interfaces:**
- Consumes: the installed app from Task 8.
- Produces: a carousel fixture and confirmed real-world behaviour.

- [ ] **Step 1: Connect the phone**

Ask the owner to enable **Settings → Developer options → Wireless debugging** on the S25 Ultra, tap **Pair device with pairing code**, and read out the pairing address, port and code. Then:

```bash
SDK=/Users/raegreen/Library/Android/sdk
$SDK/platform-tools/adb pair <pairing-ip:port> <code>
$SDK/platform-tools/adb connect <device-ip:port>
$SDK/platform-tools/adb devices -l
```

Expected: the S25 Ultra appears as `device`.

- [ ] **Step 2: Install and share a real Reel**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew installDebug
/Users/raegreen/Library/Android/sdk/platform-tools/adb logcat -c
```

Ask the owner to open Instagram, find a Reel, tap **Share**, pick **InstaSaver**, and report what happens. Then:

```bash
/Users/raegreen/Library/Android/sdk/platform-tools/adb logcat -d -s InstaSaver | tail -20
```

Expected: Instagram stays on screen, a "Saved" notification appears, and the file plays with sound in Samsung Gallery. Record in `docs/device-check-2026-09-11.md`: the exact text Instagram shared (visible in the log line), whether it was a direct link or a `/share/` link, and whether the file landed in the right album.

- [ ] **Step 3: Check the awkward cases on the phone**

Ask the owner to try, and record each result:
- The same Reel again → expect "Already saved".
- A photo post → expect the image in `Pictures/InstaSaver`.
- Aeroplane mode on, share something, aeroplane mode off → expect it to complete by itself once the connection returns.
- A profile link (share someone's profile) → expect "Not a post link".
- Sending a saved file in Discord → expect it to upload and play with sound.

- [ ] **Step 4: Capture a carousel fixture**

Ask the owner for a link to a carousel (a post with multiple swipeable items), then:

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app
curl -sS -L --compressed -o app/src/test/resources/fixtures/carousel.html \
  -A "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36" \
  -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8" \
  -H "Accept-Language: en-GB,en;q=0.9" \
  -H 'sec-ch-ua: "Chromium";v="152", "Google Chrome";v="152", "Not)A;Brand";v="99"' \
  -H "sec-ch-ua-mobile: ?1" -H 'sec-ch-ua-platform: "Android"' \
  -H "sec-fetch-dest: document" -H "sec-fetch-mode: navigate" -H "sec-fetch-site: none" -H "sec-fetch-user: ?1" \
  -H "upgrade-insecure-requests: 1" \
  "https://www.instagram.com/p/<carousel-shortcode>/?l=1"
grep -c xig_polaris_media app/src/test/resources/fixtures/carousel.html
```

- [ ] **Step 5: Find the field that holds the carousel's items**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app
node -e '
const fs = require("fs")
const html = fs.readFileSync("app/src/test/resources/fixtures/carousel.html", "utf8")
const blocks = [...html.matchAll(/<script type="application\/json"[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1]).filter(s => s.includes("xig_polaris_media"))
let node
const walk = o => { if (!o || typeof o !== "object" || node) return; if (o.xig_polaris_media?.if_not_gated_logged_out) node = o.xig_polaris_media.if_not_gated_logged_out; Object.values(o).forEach(walk) }
blocks.forEach(b => walk(JSON.parse(b)))
console.log("typename:", node.__typename)
console.log("keys:", Object.keys(node).join(", "))
const childKey = Object.keys(node).find(k => Array.isArray(node[k]) && node[k][0] && (node[k][0].image_versions2 || node[k][0].video_versions))
console.log("child array field:", childKey, "| count:", childKey ? node[childKey].length : 0)
if (childKey) console.log("child keys:", Object.keys(node[childKey][0]).join(", "))
console.log("username:", node.user?.username)
'
```

Record the printed field name, item count, and which items are video versus image. Task 10 implements against exactly this.

- [ ] **Step 6: Add the live check script**

When a download fails months from now, this says whether Instagram changed something or the app
has a bug. Create `tools/live-check.sh` (and `chmod +x` it):

```bash
#!/usr/bin/env bash
# Fetches one known public post the way the app does and reports whether the data is still there.
set -u
URL="${1:-https://www.instagram.com/reel/DdDxpiPOAzs/?l=1}"
PAGE=$(curl -sS -L --compressed \
  -A "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36" \
  -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8" \
  -H "Accept-Language: en-GB,en;q=0.9" \
  -H 'sec-ch-ua: "Chromium";v="152", "Google Chrome";v="152", "Not)A;Brand";v="99"' \
  -H "sec-ch-ua-mobile: ?1" -H 'sec-ch-ua-platform: "Android"' \
  -H "sec-fetch-dest: document" -H "sec-fetch-mode: navigate" -H "sec-fetch-site: none" -H "sec-fetch-user: ?1" \
  -H "upgrade-insecure-requests: 1" "$URL")

printf 'page size:        %s bytes\n' "${#PAGE}"
printf 'post data block:  %s\n' "$(grep -c xig_polaris_media <<<"$PAGE")"
printf 'media fields:     %s\n' "$(grep -oE 'video_versions|image_versions2' <<<"$PAGE" | sort -u | tr '\n' ' ')"

if grep -q xig_polaris_media <<<"$PAGE"; then
  echo "RESULT: Instagram still serves post data — a failure is a bug in the app."
else
  echo "RESULT: no post data — Instagram changed something. Re-capture fixtures and check PostDataParser."
fi
```

Run it once to confirm it prints the healthy result:

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./tools/live-check.sh
```

Expected: a non-zero post data block count and the "still serves post data" line.

- [ ] **Step 7: Write up what was found**

Create `docs/device-check-2026-09-11.md` with: the share text format Instagram produced, each awkward-case result from Step 3, and the carousel field name, item count and username from Step 5. Note anything that behaved differently from the spec.

---

### Task 10: Carousel support

Only start this once Task 9 Step 5 has printed the real field name. Replace `<child field>` and `<expected count>` below with those values.

**Files:**
- Modify: `app/src/main/java/com/raegreen/instasaver/PostDataParser.kt`
- Modify: `app/src/test/java/com/raegreen/instasaver/PostDataParserTest.kt`

**Interfaces:**
- Consumes: `ResolvedPost`, `MediaItem`, `MediaType` (Task 3).
- Produces: no new types. `PostDataParser.parse` now returns every item of a multi-item post, in the order Instagram lists them, with `index` running 1…n.

- [ ] **Step 1: Add the failing test**

Append to `PostDataParserTest`:

```kotlin
    @Test
    fun `reads every item of a carousel in order`() {
        val post = PostDataParser.parse(fixture("carousel.html"))

        assertEquals(<expected count>, post.items.size)
        assertEquals(listOf(1, 2), post.items.take(2).map { it.index })
        assertTrue(post.items.all { it.url.contains("fbcdn") })
        assertEquals(<the username printed in Task 9 Step 5>, post.username)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest --tests '*PostDataParserTest*'
```

Expected: FAIL — one item found instead of `<expected count>`.

- [ ] **Step 3: Implement the carousel branch**

In `PostDataParser`, replace the body of `parse` with:

```kotlin
    fun parse(html: String): ResolvedPost {
        val node = findMediaNode(html) ?: throw PostDataParseException("no post data in page")
        val username = node["user"]?.jsonObjectOrNull()?.get("username")?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no username in post data")
        val shortcode = node["code"]?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no shortcode in post data")

        // A multi-item post carries its children in an array; a single post is its own item
        val children = node["<child field>"] as? JsonArray

        val items = children
            ?.mapIndexedNotNull { position, child -> child.jsonObjectOrNull()?.let { mediaItem(it, position + 1) } }
            ?: listOf(mediaItem(node, index = 1))

        if (items.isEmpty()) throw PostDataParseException("no media in post data")

        return ResolvedPost(username, shortcode, items)
    }
```

- [ ] **Step 4: Run the whole unit suite to verify it passes**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. The carousel test passes and the single-video and single-image tests still pass.

- [ ] **Step 5: Check a real carousel on the phone**

```bash
cd /Users/raegreen/Documents/Personal/Apps/insta-vid-download-app && ./gradlew installDebug
```

Ask the owner to share a carousel from Instagram. Expected: a "Saved N items" notification, and all items present in the gallery with `_1`, `_2`… in their names. Add the result to `docs/device-check-2026-09-11.md`.

---

## Done means

- Sharing a Reel from Instagram saves an MP4 with sound to `Movies/InstaSaver` without leaving Instagram, and it uploads to Discord.
- Sharing a photo post saves the full-size image to `Pictures/InstaSaver`.
- Sharing a carousel saves every item.
- Sharing the same post twice reports "Already saved".
- Sharing anything else reports "Not a post link".
- `./gradlew :app:testDebugUnitTest` passes, covering the link parser, the page parser against three real saved pages, the header set, the resolver and every coordinator outcome.
- Nothing has been committed or pushed; the working tree is left for the owner to commit when they choose.
