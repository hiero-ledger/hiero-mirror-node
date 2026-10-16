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

executed=0
failed=0

if ! awk -v t="${TOLERANCE_PERCENT}" -v m="${MIN_TOLERANCE_PERCENT}" 'BEGIN { exit (t > m) ? 0 : 1 }'; then
  exit 1
fi

hex_to_dec() {
  local hex="${1#0x}"
  printf '%d' "0x${hex}"
}

# Canonical hex for equality: optional 0x, case-insensitive, odd nibble padded,
# leading zeros stripped so 0x1, 0x01, and 0x001 compare equal (empty/0x/0x0 too).
normalize_hex() {
  local hex="${1:-}"
  if [[ "${hex}" == [0][xX]* ]]; then
    hex="${hex:2}"
  fi
  hex="$(tr '[:upper:]' '[:lower:]' <<<"${hex}")"
  if ((${#hex} % 2 == 1)); then
    hex="0${hex}"
  fi
  hex="${hex#"${hex%%[!0]*}"}"
  printf '%s' "${hex:-0}"
}

hex_equal() {
  [[ "$(normalize_hex "$1")" == "$(normalize_hex "$2")" ]]
}

# True when eth_call replay matches the original contract result.
# A consensus CONTRACT_REVERT_EXECUTED with a successful eth_call is not reproducible.
is_reproducible_replay() {
  local result_json="$1"
  local http_code="$2"
  local replay_body="$3"
  local expected_result replay_result original_status

  expected_result="$(jq -r '.call_result // empty' <<<"${result_json}")"
  replay_result="$(jq -r '.result // empty' <<<"${replay_body}")"
  original_status="$(jq -r '.result // empty' <<<"${result_json}")"

  [[ "${http_code}" == "200" ]] \
    && hex_equal "${expected_result}" "${replay_result}" \
    && [[ "${original_status}" != "CONTRACT_REVERT_EXECUTED" ]]
}

# Sets http_code and body for a /contracts/call POST.
post_contracts_call() {
  local request_body="$1"
  local tmp
  tmp="$(mktemp)"
  http_code="$(curl -sS -o "${tmp}" -w '%{http_code}' \
    -X POST "${BASE_URL}/api/v1/contracts/call" \
    -H 'Accept: application/json' \
    -H 'Content-Type: application/json' \
    --data "${request_body}" || true)"
  body="$(cat "${tmp}")"
  rm -f "${tmp}"
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

# Check if a consensus_timestamp (format: seconds.nanoseconds) is at a round hour or up to 2 seconds after.
is_near_round_hour() {
  local timestamp="$1"
  local seconds="${timestamp%%.*}"
  if [[ -z "${seconds}" || ! "${seconds}" =~ ^[0-9]+$ ]]; then
    return 1
  fi
  local seconds_in_hour=$((seconds % 3600))
  # Return true (0) if at round hour (0) or up to 2 seconds after (1, 2)
  ((seconds_in_hour <= 2))
}

# Contract create: missing to, or the result's contract_id was itself created by the tx.
is_contract_create() {
  jq -e '
    .contract_id as $cid
    | (.to == null or .to == "" or .to == "0x")
      or ($cid != null and ((.created_contract_ids // []) | index($cid) != null))
  ' <<<"$1" >/dev/null
}

# Fetch top-level caller from actions if available
resolve_action_sender() {
  local hash="$1"
  curl -sS --retry 3 --retry-all-errors "${BASE_URL}/api/v1/contracts/results/${hash}/actions?limit=1" \
    | jq -r '.actions[0].from // empty' || true
}

# Reconstruct pre-refund gas (intrinsic gas + top-level action gas_used) for contract calls.
get_pre_refund_gas() {
  local result_json="$1"
  is_contract_create "${result_json}" && return 1

  local hash action_gas
  hash="$(jq -r '.hash // empty' <<<"${result_json}")"
  [[ -n "${hash}" ]] || return 1

  action_gas="$(curl -sS --retry 3 --retry-all-errors "${BASE_URL}/api/v1/contracts/results/${hash}/actions?limit=1" \
    | jq -r '.actions[0].gas_used // empty' || true)"
  [[ -n "${action_gas}" && "${action_gas}" =~ ^[0-9]+$ ]] || return 1

  jq -r --argjson ag "${action_gas}" '
    ((.function_parameters // "") | sub("^0[xX]"; "")) as $h
    | 21000 + ([range(0; ($h | length); 2) | if $h[.:.+2] == "00" then 4 else 16 end] | add // 0) + $ag
  ' <<<"${result_json}"
}

# True when an earlier transaction in the same block created a contract.
has_intra_block_dependency() {
  local result_json="$1"
  local tx_index block_number ts
  tx_index="$(jq -r '.transaction_index // 0' <<<"${result_json}")"
  if ((tx_index <= 0)); then
    return 1
  fi
  block_number="$(jq -r '.block_number // empty' <<<"${result_json}")"
  ts="$(jq -r '.timestamp // empty' <<<"${result_json}")"
  [[ -n "${block_number}" && -n "${ts}" ]] || return 1

  curl -sS --retry 3 --retry-all-errors \
    "${BASE_URL}/api/v1/contracts/results?block.number=${block_number}&limit=100" \
    | jq -e --arg ts "${ts}" '
        any(.results[]?; .timestamp < $ts and (
          (.to == null or .to == "" or .to == "0x") or ((.created_contract_ids // []) | length > 0)
        ))
      ' >/dev/null 2>&1
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
  jq -e '
    .contract_id as $cid
    | (.gas_used == null)
      or (.gas_used <= 0)
      or (
        (
          (.to == null or .to == "" or .to == "0x")
          or ($cid != null and ((.created_contract_ids // []) | index($cid) != null))
        )
        and ($cid == null)
        and (((.created_contract_ids // []) | length) == 0)
      )
      or (
        (.to == null or .to == "" or .to == "0x")
        and ($cid == null or ((.created_contract_ids // []) | index($cid) == null))
        and (.function_parameters == null or .function_parameters == "" or .function_parameters == "0x")
      )
  ' <<<"$1" >/dev/null
}

check_result() {
  local result_json="$1"
  if should_skip "${result_json}"; then
    return 0
  fi

  local request consumed hash
  if ! request="$(build_request "${result_json}")"; then
    return 0
  fi
  hash="$(jq -r '.hash // "unknown"' <<<"${result_json}")"
  consumed="$(jq -r '.gas_used' <<<"${result_json}")"

  local attempt estimated body http_code pre_refund_gas=""
  for attempt in 1 2; do
    post_contracts_call "${request}"

    if [[ "${http_code}" != "200" ]]; then
      if ((attempt == 2)); then
        break
      fi
      return 0
    fi

    local result_hex
    result_hex="$(jq -r '.result // empty' <<<"${body}")"
    if [[ -z "${result_hex}" || "${result_hex}" == "null" ]]; then
      if ((attempt == 2)); then
        break
      fi
      return 0
    fi

    estimated="$(hex_to_dec "${result_hex}")"

    if within_tolerance "${estimated}" "${consumed}" || {
      [[ -n "${pre_refund_gas}" ]] || pre_refund_gas="$(get_pre_refund_gas "${result_json}" || true)"
      [[ -n "${pre_refund_gas}" ]] \
        && ((pre_refund_gas > consumed)) \
        && within_tolerance "${estimated}" "${pre_refund_gas}"
    }; then
      executed=$((executed + 1))
      return 0
    fi

    if ((attempt == 1)); then
      local block_number consensus_timestamp action_from retried=false
      block_number="$(jq -r '.block_number // empty' <<<"${result_json}")"
      consensus_timestamp="$(jq -r '.consensus_timestamp // empty' <<<"${result_json}")"
      if [[ -n "${block_number}" ]] && is_near_round_hour "${consensus_timestamp}"; then
        request="$(jq -c --arg b "${block_number}" '.block = $b' <<<"${request}")"
        retried=true
      fi
      if ! is_contract_create "${result_json}"; then
        action_from="$(resolve_action_sender "${hash}")"
        if [[ -n "${action_from}" ]] && ! hex_equal "${action_from}" "$(jq -r '.from // empty' <<<"${request}")"; then
          request="$(jq -c --arg f "${action_from}" '.from = $f' <<<"${request}")"
          retried=true
        fi
      fi
      if [[ "${retried}" == true ]]; then
        continue
      fi
    fi
    break
  done

  local replay_request
  replay_request="$(jq -c '.estimate = false' <<<"${request}")"
  post_contracts_call "${replay_request}"
  if ! is_reproducible_replay "${result_json}" "${http_code}" "${body}" \
    || has_intra_block_dependency "${result_json}"; then
    return 0
  fi

  executed=$((executed + 1))
  failed=$((failed + 1))
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

while ((RESULTS_COUNT > 0)); do
  if [[ -z "${next_path}" || "${next_path}" == "null" ]]; then
    break
  fi

  page_url="$(page_url_from_next "${next_path}")"
  if ! page_json="$(curl -sS --retry "${MAX_PAGE_FETCH_RETRIES}" --retry-all-errors -f "${page_url}")"; then
    break
  fi

  if ! result_count="$(jq '.results | length' <<<"${page_json}")" \
    || [[ -z "${result_count}" || "${result_count}" -eq 0 ]]; then
    break
  fi

  while IFS= read -r result_json; do
    if ((RESULTS_COUNT <= 0)); then
      break
    fi
    check_result "${result_json}"
    RESULTS_COUNT=$((RESULTS_COUNT - 1))
    sleep "${SLEEP_SECONDS}"
  done < <(jq -c '.results[]' <<<"${page_json}")

  next_path="$(jq -r '.links.next // empty' <<<"${page_json}")"
done

failure_percentage="$(awk -v e="${executed}" -v f="${failed}" 'BEGIN {
  printf "%.2f", (e > 0 ? (f * 100.0 / e) : 0)
}')"

summary="${executed} number of gas estimations were executed, ${failure_percentage} % (${failed} number) of them has gas accuracy failures"
printf '%s\n' "${summary}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "### Summary:" >> "${GITHUB_STEP_SUMMARY}"
  printf '%s\n' "${summary}" >> "${GITHUB_STEP_SUMMARY}"
fi

if ((executed == 0 || failed > 0)); then
  exit 1
fi

exit 0
