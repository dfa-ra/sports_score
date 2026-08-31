#!/usr/bin/env bash
# Short release notes from commits. No PR titles, no changelog dump.
set -euo pipefail

current="${1:-}"
if [[ -z "${current}" ]]; then
  current="$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
fi

prev=""
if [[ -n "${current}" ]]; then
  prev="$(git describe --tags --abbrev=0 --match 'v[0-9]*' "${current}^" 2>/dev/null || true)"
fi

if [[ -n "${prev}" && -n "${current}" ]]; then
  range="${prev}..${current}"
elif [[ -n "${current}" ]]; then
  range="${current}"
else
  range="HEAD"
fi

mapfile -t subjects < <(git log --no-merges --pretty=format:'%s' "${range}")

bullets=()
for raw in "${subjects[@]}"; do
  line="${raw}"
  [[ "${line}" =~ ^[Mm]erge[[:space:]] ]] && continue
  [[ "${line}" =~ [Mm]erge[[:space:]]pull[[:space:]]request ]] && continue
  [[ "${line}" =~ ^chore:[[:space:]]bump[[:space:]]app[[:space:]]versions ]] && continue
  [[ "${line}" =~ [Cc]ursor/ ]] && continue
  [[ "${line}" =~ ^Phase[[:space:]] ]] && continue
  line="$(sed -E 's/^(feat|fix|chore|docs|refactor|test|style|perf):\s*//' <<<"${line}")"
  line="$(sed -E 's/[[:space:]]*\(#([0-9]+)\)[[:space:]]*$//' <<<"${line}")"
  [[ -z "${line}" ]] && continue
  bullets+=("- ${line}")
  (( ${#bullets[@]} >= 8 )) && break
done

if (( ${#bullets[@]} == 0 )); then
  echo "Обновление сборок."
  exit 0
fi

printf '%s\n' "${bullets[@]}"
