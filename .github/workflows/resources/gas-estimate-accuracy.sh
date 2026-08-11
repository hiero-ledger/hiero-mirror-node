#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required}"
RESULTS_COUNT="${RESULTS_COUNT:-6000}"
TOLERANCE_PERCENT="${TOLERANCE_PERCENT:-20}"
SLEEP_SECONDS="${SLEEP_SECONDS:-0.3}"
LIMIT=100
# Minimum estimate overhead above gas_used; TOLERANCE_PERCENT is the max and must exceed this.
MIN_TOLERANCE_PERCENT=5
MAX_PAGE_FETCH_RETRIES=30

processed=0
checked=0
passed=0
failed=0
skipped=0
estimate_reverts=0
api_errors=0
page_fetch_failures=0

log() {
  printf '%s\n' "$*"
}

if ! awk -v t="${TOLERANCE_PERCENT}" -v m="${MIN_TOLERANCE_PERCENT}" 'BEGIN { exit (t > m) ? 0 : 1 }'; then
  log "TOLERANCE_PERCENT must be greater than ${MIN_TOLERANCE_PERCENT} (got ${TOLERANCE_PERCENT})"
  exit 1
fi

hex_to_dec() {
  local hex="${1#0x}"
  printf '%d' "0x${hex}"
}

within_tolerance() {
  local estimated="$1"
  local consumed="$2"
  # Estimate must be at least MIN_TOLERANCE_PERCENT above gas_used and at most TOLERANCE_PERCENT above it.
  awk -v e="${estimated}" -v c="${consumed}" -v t="${TOLERANCE_PERCENT}" -v m="${MIN_TOLERANCE_PERCENT}" 'BEGIN {
    if (c <= 0) exit 1
    lower = c * (1.0 + m / 100.0)
    upper = c * (1.0 + t / 100.0)
    exit (e >= lower && e <= upper) ? 0 : 1
  }'
}

# Build /contracts/call estimate body from a ContractResult.
build_request() {
  local result_json="$1"
  jq -c '
    {
      estimate: true,
      data: (.function_parameters // "0x"),
      from: .from,
      gas: (.gas_limit // 15000000),
      value: (.amount // 0),
      block: (if .block_number != null then ((.block_number - 1) | tostring) else "latest" end)
    }
    + (if (.created_contract_ids | length) > 0 then {}
       elif (.to != null and .to != "" and .to != "0x") then {to: .to}
       else {} end)
  ' <<<"${result_json}"
}

should_skip() {
  local result_json="$1"
  local reason
  reason="$(jq -r '
    if .gas_used == null then "missing gas_used"
    elif (.to == null or .to == "" or .to == "0x") and
         (.function_parameters == null or .function_parameters == "" or .function_parameters == "0x") then
      "missing data for contract deploy"
    else empty end
  ' <<<"${result_json}")"
  if [[ -n "${reason}" ]]; then
    printf '%s' "${reason}"
    return 0
  fi
  return 1
}

check_result() {
  local result_json="$1"
  if should_skip "${result_json}" >/dev/null; then
    skipped=$((skipped + 1))
    return 0
  fi

  local request body http_code response estimated consumed hash
  request="$(build_request "${result_json}")"
  hash="$(jq -r '.hash // "unknown"' <<<"${result_json}")"
  consumed="$(jq -r '.gas_used' <<<"${result_json}")"

  response="$(mktemp)"
  http_code="$(curl -sS -o "${response}" -w '%{http_code}' \
    -X POST "${BASE_URL}/api/v1/contracts/call" \
    -H 'Accept: application/json' \
    -H 'Content-Type: application/json' \
    --data "${request}" || true)"
  body="$(cat "${response}")"
  rm -f "${response}"

  if [[ "${http_code}" != "200" ]]; then
    # Historical txs often revert on estimate replay (state/simulation drift).
    # Count those separately; only out-of-tolerance estimates fail the job.
    if [[ "${http_code}" == "400" ]] && grep -q 'CONTRACT_REVERT_EXECUTED' <<<"${body}"; then
      estimate_reverts=$((estimate_reverts + 1))
    else
      api_errors=$((api_errors + 1))
    fi
    return 0
  fi

  local result_hex
  result_hex="$(jq -r '.result // empty' <<<"${body}")"
  if [[ -z "${result_hex}" || "${result_hex}" == "null" ]]; then
    api_errors=$((api_errors + 1))
    return 0
  fi

  estimated="$(hex_to_dec "${result_hex}")"
  checked=$((checked + 1))

  if within_tolerance "${estimated}" "${consumed}"; then
    passed=$((passed + 1))
  else
    failed=$((failed + 1))
    local pct
    pct="$(awk -v e="${estimated}" -v c="${consumed}" 'BEGIN {
      printf "%.2f", ((e - c) * 100.0 / c)
    }')"
    log "Out of tolerance for hash=${hash}: estimated=${estimated} gas_used=${consumed} overhead=${pct}% (expected ${MIN_TOLERANCE_PERCENT}-${TOLERANCE_PERCENT}%)"
    log "request=${request}"
  fi
}

page_url_from_next() {
  local path_or_url="$1"
  if [[ "${path_or_url}" =~ ^https?:// ]]; then
    printf '%s' "${path_or_url}"
  else
    printf '%s%s' "${BASE_URL}" "${path_or_url}"
  fi
}

if ((RESULTS_COUNT < LIMIT)); then
  page_limit="${RESULTS_COUNT}"
else
  page_limit="${LIMIT}"
fi

next_path="/api/v1/contracts/results?limit=${page_limit}&order=desc"

while ((processed < RESULTS_COUNT)); do
  if [[ -z "${next_path}" || "${next_path}" == "null" ]]; then
    break
  fi

  page_url="$(page_url_from_next "${next_path}")"
  if ! page_json="$(curl -sS -f "${page_url}")"; then
    page_fetch_failures=$((page_fetch_failures + 1))
    log "Failed to fetch results page (${page_fetch_failures}/${MAX_PAGE_FETCH_RETRIES}): ${page_url}"
    if ((page_fetch_failures >= MAX_PAGE_FETCH_RETRIES)); then
      log "Giving up after ${MAX_PAGE_FETCH_RETRIES} consecutive results page fetch failures"
      break
    fi
    sleep "${SLEEP_SECONDS}"
    continue
  fi

  if ! result_count="$(jq '.results | length' <<<"${page_json}")" || [[ -z "${result_count}" ]]; then
    page_fetch_failures=$((page_fetch_failures + 1))
    log "Invalid results page response (${page_fetch_failures}/${MAX_PAGE_FETCH_RETRIES}): ${page_url}"
    if ((page_fetch_failures >= MAX_PAGE_FETCH_RETRIES)); then
      log "Giving up after ${MAX_PAGE_FETCH_RETRIES} consecutive results page fetch failures"
      break
    fi
    sleep "${SLEEP_SECONDS}"
    continue
  fi

  if [[ "${result_count}" -eq 0 ]]; then
    break
  fi

  page_fetch_failures=0

  while IFS= read -r result_json; do
    if ((processed >= RESULTS_COUNT)); then
      break
    fi
    check_result "${result_json}"
    processed=$((processed + 1))
    sleep "${SLEEP_SECONDS}"
  done < <(jq -c '.results[]' <<<"${page_json}")

  next_path="$(jq -r '.links.next // empty' <<<"${page_json}")"
done

summary="$(cat <<EOF
Summary
  processed=${processed}
  checked=${checked}
  passed=${passed}
  failed=${failed}
  skipped=${skipped}
  estimate_reverts=${estimate_reverts}
  api_errors=${api_errors}
EOF
)"
log "${summary}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '%s\n' "${summary}" >> "${GITHUB_STEP_SUMMARY}"
fi

if ((page_fetch_failures >= MAX_PAGE_FETCH_RETRIES)); then
  log "Gas estimate accuracy check failed: results page fetch retries exhausted"
  exit 1
fi

if ((checked == 0)); then
  log "Gas estimate accuracy check failed: no estimates were successfully checked (api_errors=${api_errors}, estimate_reverts=${estimate_reverts}, skipped=${skipped})"
  exit 1
fi

if ((failed > 0)); then
  log "Gas estimate accuracy check failed: ${failed} out-of-tolerance estimate(s)"
  exit 1
fi

log "Gas estimate accuracy check passed"
exit 0
