# InstaSaver

An Android app that saves Instagram Reels, posts and carousels into your phone's
gallery. Share a post to it — or just copy the link and open the app — and the
file is in your gallery seconds later, with its audio intact.

Built because every downloader on the Play Store is full of ads.

## What it does

- **Share from Instagram** and it downloads in the background. You never leave Instagram.
- **Or copy a link** — opening the app picks up a freshly copied post and starts it.
- **Carousels** save every item, not just the first.
- **Videos keep their audio**; images save at full resolution.
- **A list of what you've saved**, with live rows while something is downloading.
- **Tap to play**, then Share straight into Discord, or Delete.
- **Failures say why**, with a notification you'll actually notice. Silence means it worked.
- Files land in `Movies/InstaSaver` and `Pictures/InstaSaver`, named `account_postid`.
  Sharing the same post twice doesn't download it twice.

No account, no login, no ads, no analytics.

## Requirements

Android 11 or newer.

## Installing

Grab the APK from a release build and open it on the phone — Android will ask
once for permission to install from that app.

```
./gradlew assembleRelease     # signed, shrunk, ~4MB
./gradlew installDebug        # straight onto a connected device
```

Release signing reads its keystore and password from `~/.gradle/gradle.properties`
(`INSTASAVER_STORE_FILE`, `INSTASAVER_STORE_PASSWORD`, `INSTASAVER_KEY_ALIAS`,
`INSTASAVER_KEY_PASSWORD`), so nothing secret lives in this repository. Without
those properties a release build is simply unsigned; debug builds are unaffected.

## Tests

```
./gradlew testDebugUnitTest          # 46 tests, no device needed
./gradlew connectedDebugAndroidTest  # 10 tests, needs a phone or emulator
```

The connected-test task exits non-zero even when every test passes — a defect in
the Android Gradle plugin. The results XML under
`app/build/outputs/androidTest-results/` is the authority.

## How it works, and the one fragile part

Instagram embeds a post's data in the page it serves, but **only** for requests
carrying a full mobile-browser header set. Without those headers the same URL
returns a page with no post data at all. That header set is therefore the single
thing this app depends on, and it lives in one file: `InstagramHeaders.kt`.

When a download fails, this answers why in one command:

```
./tools/live-check.sh                        # is Instagram still serving data?
./tools/live-check.sh <a post url>           # ...or is that one post restricted?
```

It checks a known-good post alongside yours, which separates "Instagram changed
and the app needs updating" from "that post is age-restricted, private or gone".

## Known limits

- **Age-restricted posts fail.** Instagram withholds their data from anyone not
  signed in, and this app deliberately doesn't sign in.
- **Songs attached to photo posts aren't saved** — same reason.
- **Stories aren't supported.**
- The list shows only what this app saved, not other files in those folders.
  Android limits an app to the media it created unless it asks for access to your
  whole photo library, which this one doesn't.

## Documentation

`docs/` holds the design specs and implementation plans the app was built from,
including what was verified on a real device and which assumptions turned out to
be wrong.

## Status

A personal project, built for one phone. It is not on the Play Store and isn't
intended to be.
