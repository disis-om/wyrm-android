#!/usr/bin/env bash
#
# Publishes a Wyrm build.
#
# One command, because a release is four things that must agree with each other
# — the APK, its checksum, the manifest that describes it, and the signature
# over that manifest — and doing them by hand is how a release goes out that
# every installed copy refuses to install.
#
#   tools/publish-release.sh [--beta] 6.2.1 621 "What changed" ["second note"...]
#
# Stable writes update/latest.json, which every install reads. --beta writes
# update/beta.json and marks the release as a pre-release, so only players who
# turned on "Beta updates" are offered it. Beta = 6.2.x patch steps; stable
# moves the major/minor. Ask OM before any beta release.
#
# The private signing key never leaves ./release-signing, which is not in the
# repository and must be backed up somewhere that is not this machine.
set -euo pipefail

CHANNEL=latest
PRERELEASE=()
if [ "${1:-}" = "--beta" ]; then
    CHANNEL=beta
    PRERELEASE=(--prerelease)
    shift
fi

VERSION="${1:?usage: publish-release.sh <version> <versionCode> [notes...]}"
VERSION_CODE="${2:?usage: publish-release.sh <version> <versionCode> [notes...]}"
shift 2

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="disis-om/wyrm-android"
KEY="$ROOT/release-signing/wyrm-update-manifest.key"
APK="$ROOT/android/app/build/outputs/apk/release/app-release.apk"
ASSET="Wyrm-v$VERSION.apk"
STAGE="$ROOT/release-signing/stage"

[ -f "$KEY" ] || { echo "No manifest signing key at $KEY" >&2; exit 1; }

echo "==> Building $VERSION ($VERSION_CODE)"
(cd "$ROOT/android" && JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}" \
    ./gradlew.bat assembleRelease "-PWYRM_VERSION_CODE=$VERSION_CODE" "-PWYRM_VERSION_NAME=$VERSION")

[ -f "$APK" ] || { echo "No APK at $APK" >&2; exit 1; }

rm -rf "$STAGE" && mkdir -p "$STAGE/update"
cp "$APK" "$STAGE/$ASSET"

SIZE=$(stat -c %s "$STAGE/$ASSET")
SHA=$(openssl dgst -sha256 -hex "$STAGE/$ASSET" | awk '{print $NF}')
PUBLISHED=$(date -u +%Y-%m-%dT%H:%M:%SZ)

notes_json="[]"
if [ "$#" -gt 0 ]; then
    notes_json=$(printf '%s\n' "$@" | python -c '
import json,sys
print(json.dumps([line.rstrip("\n") for line in sys.stdin if line.strip()]))')
fi

cat > "$STAGE/update/$CHANNEL.json" <<JSON
{
  "schemaVersion": 1,
  "source": "github",
  "productName": "Wyrm",
  "packageName": "com.wyrm.omrajput",
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION",
  "minimumVersionCode": 0,
  "mandatory": false,
  "apkUrl": "https://github.com/$REPO/releases/download/v$VERSION/$ASSET",
  "apkSize": $SIZE,
  "sha256": "$SHA",
  "publishedAt": "$PUBLISHED",
  "headline": "A new version of Wyrm",
  "author": "OM Rajput",
  "releaseNotes": $notes_json
}
JSON

# The app verifies this signature before it believes a single field above.
openssl dgst -sha256 -sign "$KEY" -out "$STAGE/update/$CHANNEL.sig.bin" "$STAGE/update/$CHANNEL.json"
openssl base64 -A -in "$STAGE/update/$CHANNEL.sig.bin" -out "$STAGE/update/$CHANNEL.json.sig"
rm "$STAGE/update/$CHANNEL.sig.bin"

echo "==> Publishing v$VERSION"
TITLE="Wyrm $VERSION"
[ "$CHANNEL" = beta ] && TITLE="Wyrm $VERSION (beta)"
gh release create "v$VERSION" "$STAGE/$ASSET" --repo "$REPO" "${PRERELEASE[@]}" \
    --title "$TITLE" --notes "${*:-A new version of Wyrm}" >/dev/null

# The manifest goes up last: until it does, nobody is offered the build, so a
# half-finished release is invisible rather than broken.
for file in "update/$CHANNEL.json" "update/$CHANNEL.json.sig"; do
    gh api --method PUT "repos/$REPO/contents/$file" \
        -f message="Publish $VERSION" \
        -f content="$(openssl base64 -A -in "$STAGE/$file")" \
        $(gh api "repos/$REPO/contents/$file" --jq '.sha' 2>/dev/null | sed 's/^/-f sha=/') \
        >/dev/null
done

echo "==> v$VERSION is live ($(numfmt --to=iec "$SIZE" 2>/dev/null || echo "$SIZE bytes"))"
