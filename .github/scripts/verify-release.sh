#!/usr/bin/env bash
# 서명된 release APK 확인: 서명이 맞는지, debug 키가 아닌지 보고 chacheum-release.apk로 복사함.
# 인증서 이름(DN)은 찍지 않고 SHA-256 지문만 남김.
# EXPECTED_CERT_SHA256이 있으면 지문이 그것과 같은지도 봄(키가 바뀌면 깔린 앱 위에 덮어 깔 수 없음).
# 주의: pipefail이라 `명령 | head -1`, `명령 | grep -q`처럼 먼저 끝나는 받는 쪽을 쓰면 141로 죽음. 변수에 담아 here-string으로 봄.
set -euo pipefail

# AGP 8.8.2가 빌드에 쓰는 build-tools. 있으면 이 버전의 도구를 쓰고, 없으면 가장 높은 버전을 씀
agp_build_tools=35.0.0

fail() {
  echo "::error title=release APK::$1"
  exit 1
}

dir=app/build/outputs/apk/release
apk="$dir/app-release.apk"
[ -f "$apk" ] || fail "서명된 release APK가 없음($apk)"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$sdk" ] || fail "ANDROID_HOME이 없음"
if [ -x "$sdk/build-tools/$agp_build_tools/apksigner" ]; then
  tools="$sdk/build-tools/$agp_build_tools/"
else
  tools="$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -n 1)"
fi
[ -x "${tools}apksigner" ] || fail "apksigner를 찾지 못함($tools)"
echo "build-tools: $(basename "$tools")"

certs="$("${tools}apksigner" verify --print-certs "$apk")" || fail "서명 확인(apksigner verify)에 실패함"
if grep -q "CN=Android Debug" <<< "$certs"; then
  fail "release APK가 debug 키로 서명됨"
fi

# apksigner 버전마다 줄 머리가 다름(35·36: 'Signer #1 certificate ...', 37: 'V2 Signer: certificate ...')
digests="$(sed -n 's/^.*certificate SHA-256 digest: *//p' <<< "$certs" | sort -u)"
count="$(grep -c . <<< "$digests" || true)"
if [ "$count" -ne 1 ]; then
  echo "apksigner 출력(인증서 이름은 가림):"
  sed 's/\(certificate DN: \).*/\1(가림)/' <<< "$certs"
  fail "서명 인증서 지문을 하나로 읽지 못함(${count}개)"
fi
digest="$digests"

normalize() {
  tr -d ': \n' <<< "$1" | tr 'A-F' 'a-f'
}
expected="${EXPECTED_CERT_SHA256:-}"
if [ -n "$expected" ] && [ "$(normalize "$digest")" != "$(normalize "$expected")" ]; then
  fail "서명 인증서가 정해 둔 것과 다름(지금 $digest). 키를 바꿨다면 build.yml의 EXPECTED_CERT_SHA256을 고침"
fi

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
