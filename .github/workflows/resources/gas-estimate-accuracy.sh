#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required}"
RESULTS_COUNT="${RESULTS_COUNT:-3000}"
TOLERANCE_PERCENT="${TOLERANCE_PERCENT:-20}"
MIN_TOLERANCE_PERCENT="${MIN_TOLERANCE_PERCENT:-2}"
SLEEP_SECONDS="${SLEEP_SECONDS:-0.3}"
LIMIT=100
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

# Contract create: missing to, or the result's contract_id was itself created by the tx.
is_contract_create() {
  jq -e '
    .contract_id as $cid
    | (.to == null or .to == "" or .to == "0x")
      or ($cid != null and ((.created_contract_ids // []) | index($cid) != null))
  ' <<<"$1" >/dev/null
}

fetch_contract_bytecode() {
  local contract_id="$1"
  curl -sS -f "${BASE_URL}/api/v1/contracts/${contract_id}" | jq -r '.bytecode // empty'
}

# Build create estimate data:
# - Ethereum creates: function_parameters already has initcode + args
# - Hedera creates: contracts.bytecode + function_parameters
build_create_data() {
  local result_json="$1"
  local contract_id bytecode params

  contract_id="$(jq -r '.contract_id // .created_contract_ids[0] // empty' <<<"${result_json}")"
  params="$(jq -r '.function_parameters // empty' <<<"${result_json}")"
  [[ "${params}" == 0x* || -z "${params}" ]] || params="0x${params}"

  if [[ -n "${contract_id}" ]] \
    && bytecode="$(fetch_contract_bytecode "${contract_id}")" \
    && [[ -n "${bytecode}" && "${bytecode}" != "null" && "${bytecode}" != "0x" ]]; then
    [[ "${bytecode}" == 0x* ]] || bytecode="0x${bytecode}"
    # function_parameters already contains the bytecode (ethereum-style) — use it as-is.
    if [[ -n "${params}" && "${params}" != "0x" && "${params}" == "${bytecode}"* ]]; then
      printf '%s' "${params}"
      return 0
    fi
    printf '%s%s' "${bytecode}" "${params#0x}"
    return 0
  fi

  # No usable stored bytecode — fall back to function_parameters.
  if [[ -n "${params}" && "${params}" != "0x" ]]; then
    printf '%s' "${params}"
    return 0
  fi
  return 1
}

# Build /contracts/call estimate body from a ContractResult.
build_request() {
  local result_json="$1"
  local data

  if is_contract_create "${result_json}"; then
    if ! data="$(build_create_data "${result_json}")"; then
      return 1
    fi
    jq -c --arg data "${data}" '
      {
        estimate: true,
        data: $data,
        from: .from,
        gas: (.gas_limit // 15000000),
        value: (.amount // 0),
        block: (if .block_number != null then ((.block_number - 1) | tostring) else "latest" end)
      }
    ' <<<"${result_json}"
  else
    jq -c '
      {
        estimate: true,
        data: (.function_parameters // "0x"),
        from: .from,
        gas: (.gas_limit // 15000000),
        value: (.amount // 0),
        block: (if .block_number != null then ((.block_number - 1) | tostring) else "latest" end)
      }
      + (if (.to != null and .to != "" and .to != "0x") then {to: .to} else {} end)
    ' <<<"${result_json}"
  fi
}

should_skip() {
  local result_json="$1"
  local reason
  reason="$(jq -r '
    .contract_id as $cid
    | if .gas_used == null then "missing gas_used"
      elif .gas_used <= 0 then "non-positive gas_used"
      elif (
          (.to == null or .to == "" or .to == "0x")
          or ($cid != null and ((.created_contract_ids // []) | index($cid) != null))
        )
        and ($cid == null)
        and ((.created_contract_ids // []) | length) == 0 then
        "missing contract id for contract create"
      elif (.to == null or .to == "" or .to == "0x")
        and ($cid == null or ((.created_contract_ids // []) | index($cid) == null))
        and (.function_parameters == null or .function_parameters == "" or .function_parameters == "0x") then
        "missing data for contract call"
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

  local request consumed hash
  if ! request="$(build_request "${result_json}")"; then
    api_errors=$((api_errors + 1))
    return 0
  fi
  hash="$(jq -r '.hash // "unknown"' <<<"${result_json}")"
  consumed="$(jq -r '.gas_used' <<<"${result_json}")"
  if [[ -z "${consumed}" ]] || ((consumed <= 0)); then
    skipped=$((skipped + 1))
    return 0
  fi

  local attempt estimated
  for attempt in 1 2; do
    local body http_code response
    response="$(mktemp)"
    http_code="$(curl -sS -o "${response}" -w '%{http_code}' \
      -X POST "${BASE_URL}/api/v1/contracts/call" \
      -H 'Accept: application/json' \
      -H 'Content-Type: application/json' \
      --data "${request}" || true)"
    body="$(cat "${response}")"
    rm -f "${response}"

    if [[ "${http_code}" != "200" ]]; then
      if ((attempt == 2)); then
        break
      fi
      # Historical txs often revert on estimate replay (due to state change).
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
      if ((attempt == 2)); then
        break
      fi
      api_errors=$((api_errors + 1))
      return 0
    fi

    estimated="$(hex_to_dec "${result_hex}")"
    if ((attempt == 1)); then
      checked=$((checked + 1))
    fi

    if within_tolerance "${estimated}" "${consumed}"; then
      passed=$((passed + 1))
      return 0
    fi

    local block_number
    block_number="$(jq -r '.block_number // empty' <<<"${result_json}")"
    if ((attempt == 1)) && [[ -n "${block_number}" ]]; then
      log "Out of tolerance for hash=${hash} at block ${block_number}-1; retrying with block=${block_number}"
      request="$(jq -c --arg b "${block_number}" '.block = $b' <<<"${request}")"
      continue
    fi
    break
  done

  failed=$((failed + 1))
  local pct
  pct="$(awk -v e="${estimated}" -v c="${consumed}" 'BEGIN {
    printf "%.2f", ((e - c) * 100.0 / c)
  }')"
  log "Out of tolerance for hash=${hash}: estimated=${estimated} gas_used=${consumed} overhead=${pct}% (expected ${MIN_TOLERANCE_PERCENT}-${TOLERANCE_PERCENT}%)"
  log "request=${request}"
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
  Fetched contract result count: ${processed}
  Executed validation request count: ${checked}
  Passed estimation count: ${passed}
  Out of tolerance estimation count: ${failed}
  Skipped request count: ${skipped}
  Reverted estimate request count: ${estimate_reverts}
  Errors count outside CONTRACT_REVERT_EXECUTED: ${api_errors}
EOF
)"
log "${summary}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "### Summary:" >> $GITHUB_STEP_SUMMARY
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
