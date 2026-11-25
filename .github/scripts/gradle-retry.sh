#!/usr/bin/env bash
# Retry only transient remote dependency rate limits; never hide test or compiler errors.
set -uo pipefail
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
for attempt in 1 2 3; do
  gradle --no-daemon --stacktrace "$@" 2>&1 | tee "$log"
  result=${PIPESTATUS[0]}
  if [[ "$result" == 0 ]]; then exit 0; fi
  if [[ "$attempt" == 3 ]] || ! grep -Eq 'Received status code (429|502|503|504) from server' "$log" || ! grep -Eq 'Could not (GET|HEAD|get resource)|Unable to load Maven meta-data' "$log"; then
    exit "$result"
  fi
  delay=$((attempt * 30))
  echo "::warning::Remote dependency repository temporarily unavailable; retrying in ${delay}s (attempt $((attempt + 1))/3)."
  sleep "$delay"
done
