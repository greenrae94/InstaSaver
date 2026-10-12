#!/usr/bin/env bash
# Diagnoses a failed download: is Instagram still serving post data, or is the app broken?
# Fetches several known-good control posts with the app's exact mobile-browser headers, plus
# the post you are asking about if you name one, then reports which case applies.
#
# Headers must match app/src/main/java/com/raegreen/instasaver/InstagramHeaders.kt exactly —
# if that file changes, update this script too, or this becomes a liar. The same goes for the
# two names from Instagram's own page markup used in classify() below.
set -u

# Control posts: long-standing public posts on @nasa — an account that has posted since 2013,
# belongs to a government agency, and keeps its back catalogue up.
#
# There are several, spread across a decade, because one control is a single point of failure.
# A post that has been deleted is served as HTTP 200 with no post data, which is exactly what
# Instagram changing its page format looks like; with one control those two cases cannot be
# told apart, and a deleted control would accuse the app of being broken. With several, one
# survivor is enough to prove Instagram is still fine.
CONTROLS=(
  "https://www.instagram.com/p/rnit2boaN5/"   # @nasa, Aug 2014
  "https://www.instagram.com/p/BJArb9bAySQ/"  # @nasa, Aug 2016
  "https://www.instagram.com/p/C9paqbwppEM/"  # @nasa, Jul 2024
)

USER_AGENT="Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FROM_FIXTURES=""

# Those controls are all image posts. If fixtures.properties is filled in, add its Reel as a
# control too: videos are what this app is mainly for, and without one, a change that gated
# videos while still serving images would pass as perfectly healthy.
if [[ -f "$ROOT/fixtures.properties" ]]; then
  FIXTURE_REEL=$(grep -E '^reel\.url[[:space:]]*=' "$ROOT/fixtures.properties" |
    head -n1 | sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]+$//')

  if [[ "$FIXTURE_REEL" == http* && "$FIXTURE_REEL" != *SHORTCODE* ]]; then
    CONTROLS+=("$FIXTURE_REEL")
    FROM_FIXTURES=", including the Reel from fixtures.properties"
  fi
fi

# Fetches a URL into a file, echoing the HTTP status (or "000" on a network failure).
fetch() {
  curl -sS -L --compressed -o "$2" -w '%{http_code}' --max-time 20 \
    -A "$USER_AGENT" \
    -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8" \
    -H "Accept-Language: en-GB,en;q=0.9" \
    -H 'sec-ch-ua: "Chromium";v="152", "Google Chrome";v="152", "Not)A;Brand";v="99"' \
    -H "sec-ch-ua-mobile: ?1" -H 'sec-ch-ua-platform: "Android"' \
    -H "sec-fetch-dest: document" -H "sec-fetch-mode: navigate" -H "sec-fetch-site: none" -H "sec-fetch-user: ?1" \
    -H "upgrade-insecure-requests: 1" \
    "$1" 2>/dev/null
}

POST_PATTERN='instagram\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)'

# Rebuilds a link the way the app fetches it: tracking parameters dropped, ?l=1 added to skip
# Instagram's "open this in the app" interstitial. Mirrors LinkParser.kt and
# InstagramPageFetcher.kt, so the script tests the request the app actually makes. Anything
# unrecognisable — a /share/ redirect link, say — is passed through for curl to follow.
app_url() {
  if [[ "$1" =~ $POST_PATTERN ]]; then
    local kind="${BASH_REMATCH[1]}" code="${BASH_REMATCH[2]}"

    [[ "$kind" == "p" ]] || kind="reel"

    echo "https://www.instagram.com/$kind/$code/?l=1"
  else
    echo "$1"
  fi
}

# Tells the three page shapes apart: real post data; Instagram's "not available to you" error
# page, which is what a deleted, private or age-restricted post returns (they are
# indistinguishable from outside); or a normal-looking page with no post data in it, which is
# what a header or page-format change looks like.
classify() {
  if grep -q xig_polaris_media "$1"; then
    echo ok
  elif grep -q PolarisErrorRoot "$1"; then
    echo gone
  else
    echo nodata
  fi
}

PAGE=$(mktemp)
trap 'rm -f "$PAGE"' EXIT

OK=0
GONE=0
NODATA=0
UNREACHABLE=0

echo "Checking ${#CONTROLS[@]} control posts (known working${FROM_FIXTURES})..."

for url in "${CONTROLS[@]}"; do
  CODE=$(fetch "$(app_url "$url")" "$PAGE")

  if [[ "$CODE" != "200" ]]; then
    UNREACHABLE=$((UNREACHABLE + 1))
    echo "  no answer for $url (HTTP ${CODE})"
    continue
  fi

  case "$(classify "$PAGE")" in
    ok)
      OK=$((OK + 1))
      ;;
    gone)
      GONE=$((GONE + 1))
      echo "  $url is no longer available — worth replacing it at the top of this script"
      ;;
    nodata)
      NODATA=$((NODATA + 1))
      echo "  $url served a page with no post data in it"
      ;;
  esac
done

# No control served data: say which of the three reasons it was, because they need different
# responses — fix the network, replace the controls, or fix the app.
if (( OK == 0 )); then
  if (( UNREACHABLE == ${#CONTROLS[@]} )); then
    echo "RESULT: every request failed (HTTP error, or no network)."
    echo "That's a connectivity problem, not something the app's parsing code can fix."
  elif (( NODATA == 0 )); then
    echo "RESULT: none of the control posts are available any more."
    echo "That says nothing about the app. Replace the controls at the top of this script with"
    echo "posts that are still up, then run it again."
  else
    echo "RESULT: Instagram returned pages with no post data at all — even for the controls."
    echo "Something general has changed (the headers, or Instagram's page format)."
    echo "The app likely needs updating. Re-capture fixtures with tools/fetch-fixtures.sh and"
    echo "check PostDataParser against them."
  fi
  exit 0
fi

if (( NODATA > 0 )); then
  echo "RESULT: mixed — some controls served post data (${OK}) and some did not (${NODATA})."
  echo "Instagram has likely changed something for part of its content rather than all of it."
  echo "Check PostDataParser against a freshly captured fixture before blaming any one post."
else
  echo "Controls are fine — Instagram is serving normal post data."
fi

# Accept a bare shortcode as well as a full URL. Either path serves either kind of post, so
# guessing /reel/ for a bare shortcode is harmless.
TARGET="${1:-}"

if [[ -z "$TARGET" ]]; then
  echo "No post given, so there's nothing else to check."
  exit 0
fi

if [[ "$TARGET" != http* ]]; then
  TARGET="https://www.instagram.com/reel/$TARGET/"
fi

echo "Checking the post you asked about..."
TARGET_CODE=$(fetch "$(app_url "$TARGET")" "$PAGE")

if [[ "$TARGET_CODE" != "200" ]]; then
  echo "RESULT: that request failed (HTTP ${TARGET_CODE}, or no network)."
  echo "That's a connectivity problem, not something the app's parsing code can fix."
  exit 0
fi

case "$(classify "$PAGE")" in
  ok)
    echo "RESULT: this post came back fine too. If a download still failed, that's a bug in the app."
    ;;
  gone)
    echo "RESULT: Instagram served its \"not available\" page for this post."
    echo "It is private, age-restricted, or deleted — none of which the app can get past while"
    echo "logged out. Not a general failure."
    ;;
  nodata)
    echo "RESULT: this post returned a normal page with no post data, while a control worked."
    echo "Very likely restricted in some way. If several unrelated posts do this, suspect the app."
    ;;
esac
