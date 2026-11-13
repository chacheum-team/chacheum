#!/usr/bin/env bash
# 서명된 release APK 확인: 서명이 맞는지, debug 키가 아닌지 보고 chacheum-release.apk로 복사함.
# 인증서 이름(DN)은 찍지 않고 SHA-256 지문만 남김.
# 주의: pipefail이라 `명령 | head -1`, `명령 | grep -q`처럼 먼저 끝나는 받는 쪽을 쓰면 141로 죽음. 변수에 담아 here-string으로 봄.
set -euo pipefail

fail() {
  echo "::error title=release APK::$1"
  exit 1
}

dir=app/build/outputs/apk/release
apk="$dir/app-release.apk"
[ -f "$apk" ] || fail "서명된 release APK가 없음($apk)"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$sdk" ] || fail "ANDROID_HOME이 없음"
tools="$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -n 1)"
[ -x "${tools}apksigner" ] || fail "apksigner를 찾지 못함($tools)"

certs="$("${tools}apksigner" verify --print-certs "$apk")" || fail "서명 확인(apksigner verify)에 실패함"
if grep -q "CN=Android Debug" <<< "$certs"; then
  fail "release APK가 debug 키로 서명됨"
fi
digest="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$certs")"
[ -n "$digest" ] || fail "서명 인증서를 읽지 못함"

badging="$("${tools}aapt2" dump badging "$apk")" || fail "APK 정보를 읽지 못함(aapt2)"
version="$(sed -n "1s/.*versionName='\([^']*\)'.*/\1/p" <<< "$badging")"
code="$(sed -n "1s/.*versionCode='\([^']*\)'.*/\1/p" <<< "$badging")"
size="$(stat -c %s "$apk")"

cp "$apk" "$dir/chacheum-release.apk"

{
  echo "### release APK"
  echo ""
  echo "| 항목 | 값 |"
  echo "|---|---|"
  echo "| 버전 | $version ($code) |"
  echo "| 크기 | $size 바이트 |"
  echo "| 서명 인증서 SHA-256 | \`$digest\` |"
  echo "| 커밋 | \`${GITHUB_SHA:-로컬}\` |"
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
