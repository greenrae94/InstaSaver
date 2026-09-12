#!/usr/bin/env bash
# Diagnoses a failed download: is Instagram still serving post data, or is the app broken?
# Fetches a known-good control post with the app's exact mobile-browser headers, and
# optionally a post the owner is asking about, then reports which of three cases applies.
#
# Headers must match app/src/main/java/com/raegreen/instasaver/InstagramHeaders.kt exactly —
# if that file changes, update this script too, or this becomes a liar.
set -u
CONTROL="https://www.instagram.com/reel/DaIc_V9Mw83/?l=1"
USER_AGENT="Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"

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

has_post_data() { grep -q xig_polaris_media "$1"; }

CONTROL_PAGE=$(mktemp)
TARGET_PAGE=$(mktemp)
trap 'rm -f "$CONTROL_PAGE" "$TARGET_PAGE"' EXIT

# Accept a bare shortcode as well as a full URL.
TARGET="${1:-}"
if [[ -n "$TARGET" && "$TARGET" != http* ]]; then
  TARGET="https://www.instagram.com/reel/$TARGET/"
fi

echo "Checking the control post (known working)..."
CONTROL_CODE=$(fetch "$CONTROL" "$CONTROL_PAGE")

if [[ "$CONTROL_CODE" != "200" ]]; then
  echo "RESULT: The request itself failed (HTTP ${CONTROL_CODE}, or no network)."
  echo "That's a connectivity problem, not something the app's parsing code can fix."
  exit 0
fi

if ! has_post_data "$CONTROL_PAGE"; then
  echo "RESULT: Instagram returned a page with no post data at all — even for the control post."
  echo "Something general has changed (the headers, or Instagram's page format)."
  echo "The app likely needs updating. Re-capture a fixture and check PostDataParser."
  exit 0
fi
echo "Control post is fine — Instagram is serving normal post data."

if [[ -z "$TARGET" ]]; then
  echo "No post given, so there's nothing else to check."
  exit 0
fi

echo "Checking the post you asked about..."
TARGET_CODE=$(fetch "$TARGET" "$TARGET_PAGE")

if [[ "$TARGET_CODE" != "200" ]]; then
  echo "RESULT: That request failed (HTTP ${TARGET_CODE}, or no network)."
  echo "That's a connectivity problem, not something the app's parsing code can fix."
elif has_post_data "$TARGET_PAGE"; then
  echo "RESULT: Both posts came back fine. If a download still failed, that's a bug in the app."
else
  echo "RESULT: The control works but this post does not."
  echo "This post is very likely restricted, private, or removed — not a general failure."
fi
