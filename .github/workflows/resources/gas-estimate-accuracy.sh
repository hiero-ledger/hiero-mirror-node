#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required}"
RESULTS_COUNT="${RESULTS_COUNT:-100}"
TOLERANCE_PERCENT="${TOLERANCE_PERCENT:-20}"
SLEEP_SECONDS="${SLEEP_SECONDS:-0.3}"
MAX_PAGE_SIZE=100

processed=0
checked=0
passed=0
failed=0
skipped=0
estimate_reverts=0
api_errors=0

log() {
  printf '%s\n' "$*"
}

hex_to_dec() {
  local hex="${1#0x}"
  printf '%d' "0x${hex}"
}

within_tolerance() {
  local estimated="$1"
  local consumed="$2"
  # Estimate must be at least 5% above gas_used and at most TOLERANCE_PERCENT above it.
  awk -v e="${estimated}" -v c="${consumed}" -v t="${TOLERANCE_PERCENT}" 'BEGIN {
    if (c <= 0) exit 1
    lower = c * 1.05
    upper = c * (1.0 + t / 100.0)
    exit (e >= lower && e <= upper) ? 0 : 1
  }'
}

build_request() {
  local result_json="$1"
  jq -c '
    {
      estimate: true,
      data: .function_parameters,
      from: .from,
      gas: (.gas_limit // 15000000),
      value: (.amount // 0),
      block: (if .block_number != null then (.block_number | tostring) else "latest" end)
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
    if .result != "SUCCESS" then "non-success result"
    elif .gas_used == null then "missing gas_used"
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
  local skip_reason
  if skip_reason="$(should_skip "${result_json}")"; then
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
    # Historical SUCCESS txs often revert on estimate replay (state/simulation drift).
    # Count those separately; only unexpected API failures fail the job.
    if [[ "${http_code}" == "400" ]] && grep -q 'CONTRACT_REVERT_EXECUTED' <<<"${body}"; then
      estimate_reverts=$((estimate_reverts + 1))
      log "Estimate revert for hash=${hash}"
      log "request=${request}"
    else
      api_errors=$((api_errors + 1))
      log "API ${http_code} for hash=${hash}"
      log "request=${request}"
      log "response=${body}"
    fi
    return 0
  fi

  local result_hex
  result_hex="$(jq -r '.result // empty' <<<"${body}")"
  if [[ -z "${result_hex}" || "${result_hex}" == "null" ]]; then
    api_errors=$((api_errors + 1))
    log "Missing estimate result for hash=${hash}"
    log "request=${request}"
    log "response=${body}"
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
    log "Out of tolerance for hash=${hash}: estimated=${estimated} gas_used=${consumed} overhead=${pct}% (expected 5-${TOLERANCE_PERCENT}%)"
    log "request=${request}"
  fi
}

log "Gas estimate accuracy check"
log "  base_url=${BASE_URL}"
log "  results_count=${RESULTS_COUNT}"
log "  tolerance_percent=${TOLERANCE_PERCENT}"
log "  sleep_seconds=${SLEEP_SECONDS}"

if ((RESULTS_COUNT <= MAX_PAGE_SIZE)); then
  limit="${RESULTS_COUNT}"
else
  limit="${MAX_PAGE_SIZE}"
fi

next_path="/api/v1/contracts/results?limit=${limit}&order=desc"
page=0

while ((processed < RESULTS_COUNT)); do
  if [[ -z "${next_path}" || "${next_path}" == "null" ]]; then
    log "No further pages after processing ${processed}/${RESULTS_COUNT} results; stopping early"
    break
  fi

  page=$((page + 1))
  page_url="${BASE_URL}${next_path}"
  if ((RESULTS_COUNT > MAX_PAGE_SIZE)); then
    log "Page ${page}: GET ${page_url} (${processed}/${RESULTS_COUNT} processed)"
  else
    log "GET ${page_url}"
  fi

  page_json="$(curl -sS -f "${page_url}")"
  result_count="$(jq '.results | length' <<<"${page_json}")"
  if [[ "${result_count}" -eq 0 ]]; then
    log "Empty results page; stopping"
    break
  fi

  while IFS= read -r result_json; do
    if ((processed >= RESULTS_COUNT)); then
      break
    fi
    check_result "${result_json}"
    processed=$((processed + 1))
    sleep "${SLEEP_SECONDS}"
  done < <(jq -c '.results[]' <<<"${page_json}")

  if ((RESULTS_COUNT <= MAX_PAGE_SIZE)); then
    break
  fi

  next_path="$(jq -r '.links.next // empty' <<<"${page_json}")"
done

log ""
log "Summary"
log "  processed=${processed}"
log "  checked=${checked}"
log "  passed=${passed}"
log "  failed=${failed}"
log "  skipped=${skipped}"
log "  estimate_reverts=${estimate_reverts}"
log "  api_errors=${api_errors}"

if ((checked == 0)); then
  log "Gas estimate accuracy check failed: no comparable results"
  exit 1
fi

if ((failed > 0 || api_errors > 0)); then
  log "Gas estimate accuracy check failed"
  exit 1
fi

log "Gas estimate accuracy check passed"
exit 0
