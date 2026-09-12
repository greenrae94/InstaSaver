# Phase 2 device check — 2026-09-12

Checked on the owner's Samsung S25 Ultra (SM-S938B, Android 16) over wireless debugging, by the
owner using the app normally rather than following a script in one sitting. Each line below was
confirmed by them in use.

## Results

| Check | Result |
| --- | --- |
| Share a Reel from Instagram → saves with audio | Pass |
| Share the same post again → "Already saved" | Pass |
| Live states visible while downloading | Pass — owner saw the stages appear |
| Thumbnail and title appear in the list | Pass |
| List survives closing and reopening the app | Pass |
| Paste box downloads a link | Pass |
| Tap an item → video plays | Pass |
| Share from the item screen → Discord | **Pass — the app's original purpose, working end to end** |
| Playback stops when leaving the app | Pass — fixed after review found it, then confirmed |
| Content clear of the camera notch and rounded corners | Pass — fixed after the owner reported it |
| Failed download → alerting notification | Pass |
| Failed download → row in the list with the reason | Pass |
| Dismissing one failed row leaves others alone | Pass — fixed after review found the original wiped all |
| Copy a link, open the app → downloads by itself | Pass — fixed after the owner found it silently denied |

## Defects the owner's testing found that no test could

1. **Content under the camera notch and cut off by the rounded corners.** No test renders a
   screen; nothing but a human looking at a real phone would have caught it.
2. **The Share and Delete buttons rendered as unlabelled bars.** A portrait Reel expanded until it
   squeezed the button row to a sliver and clipped the labels. Only reproducible with a real 9:16
   video on a real screen.
3. **Clipboard pickup silently did nothing.** Android was refusing the read because the app asked
   a fraction too early — during `onResume` rather than once the window held focus. The code
   reviewer had approved this file, correctly: the defect was in Android's permission timing, not
   in the source. A refused read is indistinguishable from an empty clipboard, so it failed
   silently by design.

## Defects code review found that testing could not

1. **Playback continued in the background.** Tapping Share and landing in Discord left the video's
   audio running underneath. The owner had tested playback and sharing, but not what happened to
   the player afterwards.
2. **Dismissing one failed row erased the others.** The dismiss action pruned every finished work
   record, so a second failure could vanish before being read — defeating the feature's purpose.
3. **A stream leak and a permanently-pending gallery entry** on failure paths in phase 1's saving
   code, found by tracing every throw path.

## Still outstanding

- **Carousel posts are not implemented.** A post with several swipeable items would save only its
  first. Carried over from phase 1, where it was blocked on obtaining a real carousel page to work
  against, and never unblocked.
- **`tools/live-check.sh` was never written.** Planned in phase 1 as the "is Instagram still
  serving post data" diagnostic; its absence meant the first real download failure had to be
  diagnosed by assembling the check by hand.
- The app reads the clipboard on every focus gain, not only on a cold open. Intended, but worth
  watching in real use.

## Scoped storage correction

The list shows only what this app saved, not the ~138 files the owner already had in the same
folders from a previous downloader. Android limits an app to media it created unless it requests
permission to read the whole photo library, which this app deliberately does not. The phase 2
design originally assumed otherwise and has been corrected. The owner prefers the actual
behaviour.
