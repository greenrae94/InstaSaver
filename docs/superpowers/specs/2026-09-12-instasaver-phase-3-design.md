# InstaSaver phase 3 — signing in to Instagram

**Date:** 2026-09-12
**Builds on:** phase 1 (`2026-09-11-instasaver-design.md`) and phase 2 (`2026-09-12-instasaver-phase-2-design.md`), both built and working
**Status:** draft for the owner's review

## Why

Two things a logged-out visitor cannot get:

1. **Age-restricted posts.** The owner shared a Reel and got nothing. Instagram serves that post's page without any post data unless you are signed in as an adult. Confirmed by fetching it from a Mac with the app's exact headers: zero post-data blocks, while another Reel fetched seconds later returned three.
2. **Songs attached to photo posts.** Raised by the owner in phase 1 and deferred: "a good part of most of the jokes with the image are also because of the audio that comes with it." A logged-out fetch of a photo post the owner confirmed has music showed no music data at all.

Both need the app to ask Instagram as a signed-in user.

## Scope

- Sign in to Instagram inside the app, on Instagram's own login page.
- Use the session **only when a logged-out fetch fails**, so most downloads never touch the account.
- Save age-restricted posts that the account can view.
- For a photo post with a song, save a video of the image with that song's snippet, so the joke survives into Discord.
- Sign out, and see at a glance whether the app is signed in.

**Out of scope, deliberately:** Stories (ruled out in phase 1 and not revisited), private accounts the owner follows (works incidentally with their main account, but nothing is built for it), posting, liking, following, or any action that writes to Instagram. This app only reads.

## The two assumptions this rests on

Neither is verified, and the plan must verify both **before** building anything else:

1. **That a signed-in web fetch returns age-restricted post data.** Very likely, but unproven.
2. **That signed-in web data includes music for photo posts.** Less certain. Instagram's app definitely has it; whether the *web* client receives it is unknown. If it does not, the only remaining route is impersonating Instagram's own app client, which is more complex, more fragile, and more likely to trip their checks — at which point the honest move is to drop songs rather than build that.

The plan's first task is a spike answering both against the owner's real account. If assumption 2 fails, songs are dropped and the phase continues with age-restricted posts alone.

## How signing in works

- The login screen is a WebView showing `instagram.com/accounts/login/` — **Instagram's own page**. The app never sees the password, and two-factor authentication works exactly as normal.
- Success is detected by the session cookie appearing, not by scraping the page.
- Cookies live in the WebView's own store, which is private to the app. The app reads them only to attach to its own fetches.
- **Which account is the owner's choice at the login screen.** A spare account carries no risk to their main one and works identically for public-but-restricted posts; their main account additionally sees private accounts they follow. The app neither knows nor cares which they use.

## Keeping the account safe

These are requirements, not nice-to-haves. Instagram's terms do not permit using an account outside their own apps, so the design's job is to look like ordinary browsing and to stop the moment it does not.

| Rule | Why |
| --- | --- |
| The session is used **only** after a logged-out fetch returns no post data | Most downloads never touch the account at all |
| One request per post the owner asked for — never a feed, a crawl, or a schedule | Volume is what gets accounts flagged, not a handful of reads |
| A hard cap of 20 signed-in requests per hour, enforced in the app | A bug cannot flood Instagram under the owner's name |
| No retry loop on a signed-in request | A failing request stays failed until the owner tries again |
| An automatic stop: any checkpoint, challenge, rate-limit or expiry response disables the session and tells the owner | The app stops touching the account the moment Instagram objects |
| Sign out clears the cookies completely | The owner can end it at any time |
| The same mobile-browser headers the app already uses | The request looks like the browser it claims to be |

## Architecture

```
DownloadCoordinator
   └─ MediaResolver ── logged-out fetch ── no post data? ──▶ SignedInFetch ──▶ post data
                                                              │  guarded by
                                                              ├─ InstagramSession (cookies)
                                                              ├─ RequestBudget (20/hour)
                                                              └─ SessionGuard (stop on push-back)
PhotoPost + music ──▶ SongDownloader ──▶ StillVideoBuilder ──▶ MP4 into the gallery
```

| Component | Does |
| --- | --- |
| `InstagramSession` | Reads the WebView cookie store; reports whether a session exists; clears it on sign-out |
| `LoginScreen` | Hosts Instagram's login page in a WebView; finishes when the session cookie appears |
| `SignedInFetch` | Fetches a post page with cookies attached, through the budget and the guard |
| `RequestBudget` | Counts signed-in requests in a rolling hour and refuses past the cap. Pure logic, unit-tested |
| `SessionGuard` | Recognises Instagram's push-back responses and disables the session. Pure logic, unit-tested |
| `MusicInfo` | Parses a photo post's song — its file, and which part of it the post uses. Pure, tested against a saved page |
| `StillVideoBuilder` | Combines an image and an audio snippet into an MP4 (Media3 Transformer) |

`MediaResolver` gains one branch: if the logged-out page yields no post data **and** a session exists, try again signed in. Everything else in phase 1 and 2 is untouched.

## What the owner sees

- A **"Sign in to Instagram"** row in the app. Once signed in it shows the account name and a **Sign out** button.
- A failed download that might have succeeded signed in says so: *"This post needs you to be signed in. Instagram didn't send its data."* — rather than silently doing nothing.
- If the automatic stop trips: *"Instagram asked for confirmation. Open Instagram, then sign in again here."* The app stops using the session until they do.
- A photo post with a song saves as a video, so it plays with sound in Discord. One without a song saves as an image, exactly as now.

## Errors

| Situation | Behaviour |
| --- | --- |
| Not signed in, post needs it | Failure row and notification saying signing in would help |
| Signed in, still no data | "Couldn't read this post" — the account cannot see it either |
| Hourly cap reached | "Too many signed-in requests this hour. Try again later." Nothing is sent |
| Instagram pushes back | Session disabled, owner told, no further signed-in requests until they sign in again |
| Session expired | Treated as push-back: disabled, with a clear message |
| Song found but cannot be combined | The image is saved anyway, with a note that the song could not be added |

## Testing

**Unit (JVM):** the budget's rolling-hour arithmetic including the boundary; the guard's recognition of each push-back shape; the music parser against a saved signed-in page; the resolver's decision to escalate only when logged-out data is missing and a session exists.

**On the owner's phone:** the login flow including two-factor; the age-restricted Reel that started this, which must now save; a photo post with a song; sign-out actually clearing the session.

**Not tested automatically:** anything that would need the app to log in repeatedly. Login is exercised by hand.

## Open questions

1. **Does signed-in web data carry photo-post songs?** The spike answers this. If not, songs are dropped from this phase.
2. **Image-plus-song output:** the video replaces the image rather than saving both. Worth confirming with the owner once they see one.
3. **Whether the hourly cap of 20 is right.** It is a guess — generous for a few memes, tight enough to stop a runaway loop.
