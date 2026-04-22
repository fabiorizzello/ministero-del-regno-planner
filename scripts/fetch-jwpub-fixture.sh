#!/usr/bin/env bash
# Fetch the jwpub fixture used by tests. Run manually only, result
# is committed as a fixture.
set -euo pipefail
TARGET_DIR="composeApp/src/jvmTest/resources/fixtures/jwpub"
mkdir -p "$TARGET_DIR"
API_URL="https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS"
CDN_URL=$(curl -sS "${API_URL}?output=json&pub=mwb&issue=202601&fileformat=JWPUB&alllangs=0&langwritten=I" \
    | python3 -c "import json,sys;print(json.load(sys.stdin)['files']['I']['JWPUB'][0]['file']['url'])")
curl -sS -o "$TARGET_DIR/mwb_I_202601.jwpub" "$CDN_URL"
echo "Fixture saved: $TARGET_DIR/mwb_I_202601.jwpub ($(stat -c%s "$TARGET_DIR/mwb_I_202601.jwpub") bytes)"
