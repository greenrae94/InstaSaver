# InstaSaver phase 2 — visible downloads, in-app viewing, clipboard pickup

**Date:** 2026-09-12
**Builds on:** `docs/superpowers/specs/2026-09-11-instasaver-design.md` (phase 1, built and working)
**Status:** agreed, ready for an implementation plan

## Why

Phase 1 saves quietly and tells you nothing until a notification arrives. In use, two gaps
showed up:

1. **No visible state.** Opening the app shows only a paste box. There is no sign that a
   download started, is running, or finished.
2. **No way to check a file.** Confirming a video downloaded properly means leaving the app and
   hunting through the gallery.

A third item came from the owner's experience of Play Store downloaders: opening the app with a
freshly copied Instagram link starts the download by itself, and doing so twice does not
re-download.

## Scope

- The app's screen becomes a list of saved items, newest first, with live rows for downloads in
  progress.
- Tapping an item opens it in the app: video plays, image displays, with Share and Delete.
- On opening the app, a newly copied Instagram link starts downloading automatically.

**Unchanged:** the share-from-Instagram flow, notifications, file naming, gallery destinations,
the no-login rule, and everything phase 1 built. This is additive.

**Still out of scope:** Stories, login, songs attached to photo posts (phase 1's phase 2 note),
re-encoding, the Play Store.

## What the owner sees

| Before | After |
| --- | --- |
| A paste box | A list of every saved item, newest first, paste box compacted to the top |
| Nothing while downloading | A live row: "Waiting…" → "Downloading…" → the finished file |
| Check a file via the gallery app | Tap it in the list; it plays or displays in place |
| Share/delete via the gallery app | Share and Delete on the item |
| Paste or share a link | Also: open the app with a copied link and it just starts |

## Architecture

```
MediaStore (Movies/InstaSaver, Pictures/InstaSaver) ──▶ GalleryRepository ──┐
                                                                            ├──▶ DownloadsScreen ──▶ ItemScreen
WorkManager (tagged download work) ──▶ ActiveDownloads ─────────────────────┘
Clipboard (on resume) ──▶ ClipboardPickup ──▶ DownloadWorker.enqueue
```

| Component | Does | Depends on |
| --- | --- | --- |
| `SavedItem` | One row of the list: `uri`, `displayName`, `type`, `dateAdded`, `sizeBytes` | — |
| `GalleryRepository` | Reads both MediaStore collections into `List<SavedItem>`, newest first; re-queries when MediaStore changes so saves and deletions appear by themselves | ContentResolver |
| `ActiveDownloads` | Turns WorkManager's state for tagged download work into live rows (`label`, `stage`) | WorkManager |
| `Thumbnails` | Loads a thumbnail per item off the main thread, with a small in-memory cache | ContentResolver |
| `ClipboardPickup` | On app resume, decides whether the clipboard holds a post link not handled before; remembers the last one handled | `LinkParser`, SharedPreferences |
| `DownloadsScreen` | The list: compact paste row, live rows, saved items | all of the above |
| `ItemScreen` | One item full-screen: Media3 player for video, decoded bitmap for image, Share, Delete | Media3, ContentResolver |
| `MainActivity` | Hosts both screens; holds which item is open | — |

`DownloadWorker` changes in exactly one way: it publishes a label for the post it is working on,
so a live row can say what is downloading. Its saving, retry and notification behaviour is
untouched.

## Behaviour

### The list

- Sources: every file in `Movies/InstaSaver` and `Pictures/InstaSaver`, merged and sorted newest
  first. This deliberately includes the ~138 files already there from the owner's previous
  downloader — the gallery is the record, and the app stores no list of its own.
- Each row: thumbnail, display name, relative time ("2 minutes ago"), and a video/image marker.
- Live rows sit above saved items. A download shows "Waiting…" while queued and "Downloading…"
  while running; when it finishes, the live row disappears and the file appears in the list
  below, because MediaStore has changed. Failed and cancelled work does not appear as a row —
  failures already notify.
- A live row's label starts as a generic "Instagram post", because the share receiver
  deliberately does no parsing and the job has not read the link yet. Once the worker has parsed
  the link it publishes the post's shortcode, and once it has fetched the page it publishes the
  account name, so the row becomes specific as the app learns more. A row never shows a blank or
  placeholder label.
- The list updates without user action: a MediaStore observer covers saves and deletions, and
  WorkManager's own state stream covers live rows.
- Empty state: a short line explaining how to save something (share from Instagram, or paste a
  link).

### Opening an item

- **Video:** Media3 `PlayerView`, playing the file from its MediaStore uri, with standard
  controls. Playback stops and releases when the screen closes or the app goes to the background.
- **Image:** decoded from the uri and displayed to fit.
- **Share:** `ACTION_SEND` with the item's uri and mime type, read permission granted to the
  receiving app, through the system chooser — Discord is one tap from there.
- **Delete:** try a direct delete first, which succeeds silently for files this app saved. For
  files it does not own (the ~138 older ones), Android throws a recoverable security exception;
  the app then asks the system to show its own confirmation dialog and deletes on approval.
  Declining leaves the file alone.
- Closing the item returns to the list, which reflects any deletion immediately.

### Clipboard pickup

- On resume, read the clipboard's text. Android only permits this while the app is focused, so
  this is the only moment it can happen.
- If the text parses as a post link (`LinkParser`) **and** differs from the last link the app
  acted on, enqueue a download and store the new link as last-handled.
- Otherwise do nothing at all — no message, no prompt.
- The remembered link is the app's only owned persistent state: one string in SharedPreferences.
- **Known side effect:** Android 12+ shows a system toast ("InstaSaver pasted from your
  clipboard") whenever an app reads clipboard content it did not put there. This cannot be
  suppressed and is expected.
- **Accepted trade-off:** any Instagram post link copied for any reason will be downloaded on
  next open. The owner chose automatic over a one-tap confirmation banner.

## Technical choices

| Choice | Value | Why |
| --- | --- | --- |
| Video playback | Media3 (ExoPlayer + `PlayerView`) | Correct scaling, real controls, reliable local playback; ~1.5 MB |
| Thumbnails | `ContentResolver.loadThumbnail` | Built into Android, handles both video and image; no image library |
| Image display | `ImageDecoder` from the item uri | Built in; no second dependency |
| List state | MediaStore + WorkManager, no database | The gallery already is the record; a private list would only drift from it |
| Clipboard memory | SharedPreferences, one string | A few dozen bytes; a database or DataStore dependency would be disproportionate |
| minSdk | **raised from 29 to 30** | The only device is on Android 16. It removes a legacy deletion path that could never run here, and lifts the D8 restriction that forced instrumented test names to use underscores |
| Navigation | Plain Compose state in one activity | Two screens; a navigation library would be more machinery than the thing it navigates |
| Not used | Coil or any image library, Room, DataStore, a navigation library | Revisit Coil only if the list scrolls poorly with ~140 thumbnails |

## Errors

| Situation | Behaviour |
| --- | --- |
| Thumbnail cannot be loaded | Row shows a generic video/image icon; no crash, no blank row |
| Video will not play | Player shows a short "Couldn't play this file" line; the file stays listed |
| Delete declined at the system prompt | Nothing is deleted; the list is unchanged |
| Delete fails for another reason | Item stays, with a brief message |
| Clipboard holds nothing useful | Silent no-op |
| Gallery folders are empty | Empty-state line, not a blank screen |

## Testing

**Unit tests (JVM, no device):**
- The stage mapping: each WorkManager state to "Waiting", "Downloading", or no row at all.
- The clipboard decision: a pure function over (clipboard text, last-handled link) returning the
  link to download or nothing. Cases: a new post link, the same link twice, a profile link, empty
  clipboard, null clipboard, a link with different tracking parameters than the stored one.
- Relative-time formatting, if it turns out to need its own logic.

**Instrumented tests (the owner's phone):**
- `GalleryRepository` returns items from both collections, newest first, after writing and then
  removing a temporary file — cleaning up after itself as phase 1's gallery test does.
- Deleting a file the app owns succeeds without a prompt.

**By hand on the phone:**
- Share a Reel and watch the live row appear, change, and resolve into a listed file.
- Tap it, watch it play with sound, share it to Discord from the item screen.
- Delete one of the app's own files (no prompt) and one older file (prompt).
- Copy a link in Instagram, open the app, watch it download by itself; reopen and confirm
  nothing happens; copy a different post and confirm it fires again.

## Open questions

1. Whether ~140 thumbnails scroll acceptably with the built-in loader. If not, revisit Coil.
2. Whether the paste box still earns its place once sharing and clipboard pickup both exist. Kept
   for now; drop it later if unused.
