# InstaSaver — design

**Date:** 2026-09-11
**Target device:** Samsung Galaxy S25 Ultra (Android 15+), sideloaded via Android Studio
**Status:** agreed, ready for an implementation plan

## Purpose

A personal Android app that saves Instagram Reels and post media to the phone's gallery, so
the owner can re-share memes into Discord. Not a commercial product, not going on the Play
Store, single user.

## Scope

**v1 — the main objective**

- Share a Reel or post from Instagram into the app; it downloads in the background and saves
  to the gallery without taking over the screen.
- Videos save as MP4 with their audio track.
- Images save as the full-size original.
- Carousels save every item in the post.
- A "Saved" notification when it finishes, a plain-English notification when it fails.
- A paste-a-link box as a backup route.
- No Instagram login. No Stories. No re-encoding or compression.

**Phase 2 — after v1 works on the phone**

- Songs attached to photo posts (see [Phase 2](#phase-2-songs-on-photo-posts-deferred)).

**Out of scope:** Stories, private accounts, anything bulk or scheduled, Play Store release.

## Feasibility findings (verified 2026-09-11)

Everything below was checked against two real posts: a Reel (`DdDxpiPOAzs`) and an image post
(`DdHMr9eMQQR`).

| Finding | Detail |
| --- | --- |
| Plain request **with** mobile-Chrome headers works | `GET https://www.instagram.com/{p\|reel}/{shortcode}/?l=1` returns HTML containing the post data |
| Plain request **without** those headers fails | Same URL returns a ~610 KB JavaScript shell with no post data |
| `?l=1` skips the interstitial | Without it, a Reel shows "Watch this reel in the app" |
| Where the data sits | `<script type="application/json">` blocks containing `xig_polaris_media` → `if_not_gated_logged_out` |
| Video files | `video_versions[0].url` → progressive MP4, H.264 + AAC (verified: 720×1280, 44.8 s, 13.1 MB) |
| All video versions identical | Types 101/102/103 were the same 13.1 MB file — there is no smaller copy to fall back on |
| Image files | `image_versions2.candidates[0].url` → full-size original (verified 1440×1080); later candidates are downscales and square crops |
| Media downloads | Plain request, no cookies, but **must follow redirects** (the first response is a 302) |
| Blocked routes | Logged-out GraphQL returns `401 require_login`; `?__a=1&__d=dis` returns 404 |
| Photo-post songs | Absent from logged-out data, on a post confirmed to have music in the Instagram app |
| Proven fallback | A logged-out headless browser loads the post and receives the same data, so a hidden WebView works if plain requests are ever blocked |

**Interpretation.** Instagram gates the post data on looking like a real browser, not on being
logged in. The app therefore depends on the *shape* of the data, not on any private API, and
the likely failure mode over time is a renamed field rather than a hard block.

## Architecture

```
Instagram ──Share──▶ ShareReceiverActivity ──enqueue──▶ DownloadWorker ──▶ Gallery + notification
                     (invisible, finishes                    │
                      immediately)                           ├── LinkParser
                                                             ├── MediaResolver
                                                             ├── GallerySaver
                                                             └── Notifier
```

| Component | Does | Depends on |
| --- | --- | --- |
| `ShareReceiverActivity` | Registers the app in Android's share sheet (`ACTION_SEND`, `text/plain`). Passes the shared text straight to the background job and finishes at once, so Instagram stays on screen. Invisible: no layout, translucent theme. It deliberately does no parsing, so every share-path error is reported one way — as a notification. | WorkManager |
| `MainActivity` | The only real screen. Paste-a-link box, the notification permission prompt, a short "how to use" line. Validates pasted text with `LinkParser` to show an inline error instead of a notification. | `LinkParser`, WorkManager |
| `LinkParser` | Turns shared text into a `PostRef(shortcode, kind)`. Pure function, no Android dependencies. | — |
| `MediaResolver` | `PostRef` → `List<MediaItem>`. Split into `InstagramPageFetcher` (network) and `PostDataParser` (pure parsing) so the parsing is unit-testable against saved pages. | OkHttp |
| `DownloadWorker` | The background job. Parses the link, resolves, downloads each item, saves it, posts notifications. Retries on network failure. | all of the above |
| `GallerySaver` | Writes bytes into the gallery via MediaStore. Handles naming, duplicate detection and pending state. | MediaStore |
| `Notifier` | Progress, success and failure notifications on one channel. | NotificationManager |

**Types:**

```kotlin
data class PostRef(val shortcode: String, val kind: Kind)          // Kind: POST, REEL
data class MediaItem(val url: String, val type: Type, val index: Int)  // Type: VIDEO, IMAGE
data class ResolvedPost(val username: String, val items: List<MediaItem>)
```

Keeping `LinkParser` and `PostDataParser` free of Android APIs is what makes the test suite
run on the Mac in milliseconds, with no phone or emulator.

## Behaviour

### Link handling

Accepts `instagram.com/p/<code>`, `/reel/<code>`, `/reels/<code>` and `/tv/<code>`, with or
without `www.`, and ignores everything after the code (`?igsh=…`, `?stkn=…`). Share text often
contains extra words around the URL, so the parser extracts the URL rather than assuming the
whole string is one.

Instagram sometimes produces `instagram.com/share/…` links. When one arrives, the app follows
the redirect to find the real post URL before resolving. (Unconfirmed: no `/share/` link has
been seen yet from this phone. Treat as a case to verify during the build.)

Anything else — profiles, Stories, other sites — gets a "That's not a post or Reel link"
notification and nothing else happens.

### Resolving

1. `GET https://www.instagram.com/{p|reel}/{shortcode}/?l=1` with the mobile-Chrome header set
   (user agent, `Accept`, `Accept-Language`, `sec-ch-ua`, `sec-ch-ua-mobile`,
   `sec-ch-ua-platform`, `sec-fetch-dest|mode|site|user`, `upgrade-insecure-requests`), kept in
   one constant so it can be updated in one place.
2. Extract `<script type="application/json">` blocks whose text contains `xig_polaris_media`.
3. Parse each, walk to `xig_polaris_media.if_not_gated_logged_out`.
4. Map to media items:
   - `video_versions` present → `video_versions[0].url`, type VIDEO.
   - otherwise → `image_versions2.candidates[0].url`, type IMAGE.
   - carousel → apply the same rule to each child, preserving order.
5. Anything unparseable → a resolve failure, reported as "Couldn't read this post", with
   details in the debug log.

**Carousel shape is unconfirmed.** No carousel link has been tested. The build should capture a
real carousel page as a fixture before implementing this branch, and the field name
(`carousel_media`, or whatever it turns out to be) confirmed rather than assumed.

### Saving

- Videos → `Movies/InstaSaver`, images → `Pictures/InstaSaver`, via MediaStore, so both appear
  as one album in Samsung Gallery. No storage permission needed on Android 10+.
- Filenames: `{username}_{shortcode}.mp4`, or `{username}_{shortcode}_{index}.jpg` for
  carousels.
- Written with `IS_PENDING = 1`, cleared only on a complete write, so a failed download never
  leaves a broken file in the gallery.
- Before downloading, the app checks MediaStore for that filename in that folder. Already
  there → skip, and notify "Already saved".

### Notifications

One channel, "Downloads". Progress notification while working ("Saving…"), replaced by the
result: "Saved 3 items" (tapping opens the first item), or the failure message. Android 13+
requires the `POST_NOTIFICATIONS` permission, requested once from `MainActivity`.

### Errors

| Situation | Behaviour |
| --- | --- |
| Not a post or Reel link | "That's not a post or Reel link" |
| No internet | WorkManager holds the job and retries when connectivity returns |
| Page fetched, no post data (private, deleted, Instagram changed) | "Couldn't read this post"; HTTP status and parse stage logged |
| A file fails mid-download | Up to 3 retries with backoff; pending entry deleted on give-up |
| Post already saved | "Already saved", no download |

## Technical choices

| Choice | Value | Why |
| --- | --- | --- |
| Language / UI | Kotlin + Jetpack Compose (Material 3) | Native share sheet, background work, MediaStore and notifications are all first-class; the app is mostly Android plumbing with almost no UI |
| Min Android | API 29 (Android 10) | MediaStore `RELATIVE_PATH` saving without storage permissions |
| Target Android | Newest installed platform | Only device is on Android 15+ |
| HTTP | OkHttp | Redirect following, retries, custom headers |
| JSON | kotlinx.serialization (tree API) | `org.json` ships as a non-functional stub in JVM unit tests, which would force the parser tests onto a device; kotlinx.serialization behaves identically in both places |
| Background work | WorkManager, expedited | Survives the app being killed, retries on network loss |
| Package name | `com.raegreen.instasaver` | Placeholder, easily changed |
| Not used | ffmpeg, Python/yt-dlp, any third-party API or server | Not needed: Instagram serves a ready-made MP4 with audio |

## Testing

**Unit tests (JVM, no device):**

- `LinkParser`: `/p/`, `/reel/`, `/reels/`, `/tv/`, `?igsh=`, `?stkn=`, URLs embedded in share
  text, plus rejections — profile links, Story links, non-Instagram URLs, empty text.
- `PostDataParser`: against saved page fixtures (Reel, image post, and a carousel once
  captured), trimmed to the relevant script block and committed under
  `app/src/test/resources/fixtures/`. Asserts media type, URL and count.
- `GallerySaver` naming and duplicate rules.

**On the S25 Ultra (wireless debugging):** the real flow — Instagram → Share → InstaSaver →
notification → file present in Gallery and playable with sound; Discord accepts the file.
Repeat-share shows "Already saved". Aeroplane mode shows the retry behaviour.

**Emulator:** quick checks of the paste box and the notification permission prompt.

**A manual live check:** a run configuration or instrumented test that resolves one known
public post over the real network. Run it when something breaks to tell "Instagram changed
something" apart from "the app has a bug".

## Phase 2: songs on photo posts (deferred)

Photo posts can have a song attached that plays in the Instagram app, and the joke often
depends on it. Logged-out data does not include it, so this needs a login, which is why it is
deferred until v1 works.

**Unverified assumption:** that Instagram's *website*, when logged in, includes the song for
photo posts. If it turns out only the Instagram app itself receives it, the remaining route is
impersonating the app client, which is more complex and more likely to trigger Instagram's
checks. Re-assess before building rather than assuming.

**If it is built, these rules apply:**

- Login only for photo posts; everything else stays logged out.
- One request per share the user taps, matching what the website sends. No feeds, no social
  actions, nothing scheduled, nothing in the background.
- A hard cap of roughly 20 logged-in requests an hour, never retried in a loop.
- An automatic stop: any push-back from Instagram (checkpoint, rate-limit response, or session
  expiry) disables use of the session and tells the user. It stays off until they log in again.
- Login happens on Instagram's own page in a WebView, so the app never handles the password
  and two-factor works normally. Only the session cookie is stored, in app-private storage.
- A spare Instagram account is the zero-risk option and works identically for public posts.
- Output would be the still image plus the song's snippet combined into an MP4 (Media3
  Transformer), so it plays with sound in Discord.

## Open questions

1. **Carousel data shape** — needs one real carousel link; capture as a fixture during the build.
2. **`/share/` links** — confirm what Instagram's share sheet actually sends from this phone.
3. **Phase 2 viability** — whether logged-in web data carries photo-post songs.

## Notes

The throwaway probe scripts that produced these findings live in this session's scratchpad and
are not part of the project. The page fixtures should be re-captured into the repo as part of
the first implementation task.
