#!/usr/bin/env bash
# release 서명 키 준비: Secrets의 base64 글자를 키 저장소 파일로 풀고, 비밀번호와 별칭이 맞는지 미리 봄.
# 값은 어디에도 찍지 않음. 결과는 GITHUB_OUTPUT의 signed(true/false)와 keystore(파일 경로).
set -euo pipefail

out="${GITHUB_OUTPUT:-/dev/null}"
names=(RELEASE_KEYSTORE_BASE64 RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD)
missing=()
for name in "${names[@]}"; do
  [ -n "${!name:-}" ] || missing+=("$name")
done

if [ "${#missing[@]}" -eq "${#names[@]}" ]; then
  echo "::notice title=서명 없음::서명 키 Secrets가 없어서 release는 서명 없이 빌드만 확인함"
  echo "signed=false" >> "$out"
  exit 0
fi
if [ "${#missing[@]}" -gt 0 ]; then
  echo "::error title=서명 키 Secrets::비어 있는 Secrets: ${missing[*]}"
  exit 1
fi

keystore="${RUNNER_TEMP:?RUNNER_TEMP이 없음}/release.jks"
# PowerShell로 만든 한 줄, certutil -encode로 만든 여러 줄(머리·꼬리 줄, CRLF) 둘 다 받음
( umask 077; : > "$keystore" )
if ! printf '%s' "$RELEASE_KEYSTORE_BASE64" | tr -d '\r' | grep -v '^-----' | base64 --decode --ignore-garbage > "$keystore" 2> /dev/null; then
  rm -f "$keystore"
  echo "::error title=서명 키 Secrets::RELEASE_KEYSTORE_BASE64가 base64 글자가 아님"
  exit 1
fi

if ! keytool -list -keystore "$keystore" -storepass:env RELEASE_KEYSTORE_PASSWORD > /dev/null 2>&1; then
  rm -f "$keystore"
  echo "::error title=서명 키 Secrets::키 저장소를 열지 못함. RELEASE_KEYSTORE_BASE64(파일 내용)와 RELEASE_KEYSTORE_PASSWORD를 확인"
  exit 1
fi
if ! keytool -list -keystore "$keystore" -storepass:env RELEASE_KEYSTORE_PASSWORD -alias "$RELEASE_KEY_ALIAS" > /dev/null 2>&1; then
  rm -f "$keystore"
  echo "::error title=서명 키 Secrets::키 저장소에 RELEASE_KEY_ALIAS와 같은 별칭이 없음"
  exit 1
fi

echo "keystore=$keystore" >> "$out"
echo "signed=true" >> "$out"
echo "서명 키 준비 끝"
