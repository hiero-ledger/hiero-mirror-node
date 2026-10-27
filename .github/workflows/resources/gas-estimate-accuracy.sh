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

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

processed=0
passed=0
has_failure=false

if ! awk -v t="${TOLERANCE_PERCENT}" -v m="${MIN_TOLERANCE_PERCENT}" 'BEGIN { exit (t > m) ? 0 : 1 }'; then
  exit 1
fi

hex_to_dec() {
  local hex="${1#0x}"
  printf '%d' "0x${hex}"
}

# Ignore the optional prefix and letter case, preserving every return-data byte.
normalize_hex() {
  local hex="${1:-}"
  if [[ "${hex}" == [0][xX]* ]]; then
    hex="${hex:2}"
  fi
  hex="$(tr '[:upper:]' '[:lower:]' <<<"${hex}")"
  printf '%s' "${hex}"
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
    && [[ -n "${expected_result}" && -n "${replay_result}" ]] \
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

# Loads a response into cache_file. Collections follow links.next and are cached as one array.
# Unavailable or partial responses leave cache_file empty; jq errors in processing remain fatal.
load_cached_response() {
  local key="$1" next="$2" collection="${3:-}" page code
  local file="${WORK_DIR}/${key}.json"
  cache_file=""
  [[ ! -e "${file}.missing" ]] || return 0
  if [[ -s "${file}" ]]; then
    cache_file="${file}"
    return 0
  fi

  : > "${file}.jsonl"
  while [[ -n "${next}" ]]; do
    page="${file}.page"
    if ! code="$(curl -sS -f --retry 3 --retry-all-errors -o "${page}" -w '%{http_code}' \
      "$(page_url_from_next "${next}")")" \
      || [[ "${code}" != 200 ]] \
      || ! jq -e --arg collection "${collection}" '
        type == "object" and (if $collection == "" then true else (.[$collection] | type == "array") end)
      ' "${page}" >/dev/null; then
      touch "${file}.missing"
      return 0
    fi
    if [[ -z "${collection}" ]]; then
      mv "${page}" "${file}"
      cache_file="${file}"
      return 0
    fi
    jq -c --arg collection "${collection}" '.[$collection][]' "${page}" >> "${file}.jsonl"
    next="$(jq -r '.links.next // empty' "${page}")"
  done
  jq -cs --arg collection "${collection}" '{($collection): .}' "${file}.jsonl" > "${file}"
  cache_file="${file}"
}

load_actions() {
  cache_file=""
  [[ "$1" =~ ^0x[0-9a-fA-F]+$ ]] || return 0
  load_cached_response "$1-actions" "/api/v1/contracts/results/$1/actions?limit=${LIMIT}&order=asc" actions
}

# Build prefix verdicts once, up to the first disputed transaction in a descending block stream.
# Later disputes in the same block are earlier transactions and reuse these verdicts and responses.
load_block_verdicts() {
  local block="$1" index="$2" listing summary hash detail actions earlier_create
  local verdicts="${WORK_DIR}/block-${block}-verdicts.json" enriched="${WORK_DIR}/block-${block}-enriched.jsonl"
  verdict=unknown
  if [[ -s "${verdicts}" ]]; then
    verdict="$(jq -r --arg index "${index}" '.[$index] // "unknown"' "${verdicts}")"
    return 0
  fi

  load_cached_response "block-${block}-results" "/api/v1/contracts/results?block.number=${block}&limit=${LIMIT}&order=asc" results
  [[ -n "${cache_file}" ]] || return 0
  listing="${cache_file}"
  if ! jq -e --argjson index "${index}" '
    all(.results[]; .transaction_index | type == "number")
    and any(.results[]; .transaction_index == $index)
  ' "${listing}" >/dev/null; then
    return 0
  fi
  earlier_create="$(jq --argjson index "${index}" '
    any(.results[]; .transaction_index < $index
      and ((.to == null or .to == "" or .to == "0x") or ((.created_contract_ids // []) | length > 0)))
  ' "${listing}")"
  if [[ "${earlier_create}" == true ]]; then
    verdict=dependent
    return 0
  fi

  jq -c --argjson index "${index}" '.results | sort_by(.transaction_index)[] | select(.transaction_index <= $index)' \
    "${listing}" > "${enriched}.selected"
  : > "${enriched}"
  while IFS= read -r summary; do
    hash="$(jq -r '.hash // empty' <<<"${summary}")"
    detail=""
    actions=""
    if [[ "${hash}" =~ ^0x[0-9a-fA-F]+$ ]]; then
      load_cached_response "${hash}-detail" "/api/v1/contracts/results/${hash}"
      detail="${cache_file}"
      load_actions "${hash}"
      actions="${cache_file}"
    fi
    if [[ -n "${detail}" && -n "${actions}" ]] \
      && jq -e '.state_changes | type == "array"' "${detail}" >/dev/null; then
      jq -c --slurpfile detail "${detail}" --slurpfile actions "${actions}" '
        . + $detail[0] + {transaction_index: .transaction_index, actions: $actions[0].actions, enriched: true}
      ' <<<"${summary}" >> "${enriched}"
    else
      jq -c '. + {enriched: false}' <<<"${summary}" >> "${enriched}"
    fi
  done < "${enriched}.selected"

  jq -cs '
    def creates: (.to == null or .to == "" or .to == "0x") or ((.created_contract_ids // []) | length > 0);
    def entity: type == "string" and test("^([0-9]+\\.){2}[0-9]+$|^0x[0-9a-f]{40}$");
    def pairs:
      [.contract_id, .address], [.contract_id, .to],
      (.logs[]? | [.contract_id, .address]), (.state_changes[]? | [.contract_id, .address]),
      (.actions[]? | [.caller, .from], [.recipient, .to]);
    def entities:
      [.contract_id, .from, .to, .address, .created_contract_ids[]?,
       (.logs[]? | .contract_id, .address), (.state_changes[]? | .contract_id, .address),
       (.actions[]? | .caller, .recipient, .from, .to)]
      | map(select(type == "string") | ascii_downcase | select(entity)) | unique;
    # Explicit ID/address associations let aliases and long-zero addresses share an identity.
    (reduce (.[] | select(.enriched) | pairs | map(if type == "string" then ascii_downcase else . end)
      | select((.[0] | entity) and (.[1] | entity))) as $pair ({}; .[$pair[1]] = $pair[0])) as $aliases
    | reduce .[] as $tx ({created: false, incomplete: false, touched: {}, verdicts: {}};
        . as $previous
        | ($tx | entities | map($aliases[.] // .)) as $entities
        | .verdicts[($tx.transaction_index | tostring)] = (
            if $previous.created or ($tx.enriched and any($entities[]; $previous.touched[.])) then "dependent"
            elif $previous.incomplete or ($tx.enriched | not) then "unknown"
            else "independent" end)
        | .created = (.created or ($tx | creates))
        | if $tx.enriched then
            .touched += ($entities | map({(.): true}) | add // {})
          else .incomplete = true end)
    | .verdicts
  ' "${enriched}" > "${verdicts}"
  verdict="$(jq -r --arg index "${index}" '.[$index] // "unknown"' "${verdicts}")"
}

# Recognize a parent-to-current timestamp update at the same byte position within one contract.
# Oracle ring buffers may read the latest observation and write the next one in a different slot.
# This is a conservative heuristic for per-block oracle/TWAP guards, not proof of control flow.
check_dependencies() {
  local result_json="$1" block index hash detail has_writes current previous current_hex previous_hex
  verdict=unknown
  block="$(jq -r '.block_number // empty' <<<"${result_json}")"
  index="$(jq -r '.transaction_index // empty' <<<"${result_json}")"
  [[ "${block}" =~ ^[0-9]+$ && "${index}" =~ ^[0-9]+$ ]] || return 0
  load_block_verdicts "${block}" "${index}"
  [[ "${verdict}" == independent ]] || return 0

  hash="$(jq -r '.hash // empty' <<<"${result_json}")"
  detail="${WORK_DIR}/${hash}-detail.json"
  # Calls without storage writes cannot match this timestamp-update pattern.
  has_writes="$(jq 'any(.state_changes[]; .value_written != null)' "${detail}")"
  if ((block == 0)) || [[ "${has_writes}" == false ]]; then
    return 0
  fi
  verdict=unknown
  load_cached_response "block-${block}" "/api/v1/blocks/${block}"
  [[ -n "${cache_file}" ]] || return 0
  current="$(jq -r '.timestamp.from // "" | split(".")[0]' "${cache_file}")"
  load_cached_response "block-$((block - 1))" "/api/v1/blocks/$((block - 1))"
  [[ -n "${cache_file}" ]] || return 0
  previous="$(jq -r '.timestamp.from // "" | split(".")[0]' "${cache_file}")"
  [[ "${current}" =~ ^[0-9]+$ && "${previous}" =~ ^[0-9]+$ ]] || return 0
  printf -v current_hex '%08x' "${current}"
  printf -v previous_hex '%08x' "${previous}"
  verdict="$(jq -r --arg current "${current_hex}" --arg previous "${previous_hex}" '
    def word: ascii_downcase | sub("^0x"; "") | ("0" * 64 + .)[-64:];
    def timestamp_keys($timestamp; $value):
      (if (.contract_id // "") != "" then .contract_id else (.address // "" | ascii_downcase) end) as $contract
      | select($contract != "")
      | ($value | word) as $word
      | range(0; 65 - ($timestamp | length); 2) as $offset
      | select($word[$offset:$offset + ($timestamp | length)] == $timestamp)
      | [$contract, $offset] | tojson;
    if $current == $previous then "independent"
    else
      # Index reads once; each write probes by contract and byte offset, regardless of slot.
      (reduce (.state_changes[] | timestamp_keys($previous; .value_read)) as $key ({}; .[$key] = true)) as $reads
      | if any(.state_changes[]
          | select(.value_written != null and ((.value_read | word) != (.value_written | word)))
          | timestamp_keys($current; .value_written); $reads[.] // false)
        then "dependent" else "independent" end
    end
  ' "${detail}")"
}

is_accurate() {
  within_tolerance "$1" "$2" \
    || { [[ -n "$3" ]] && (($3 > $2)) && within_tolerance "$1" "$3"; }
}

check_result() {
  local result_json="$1"
  if should_skip "${result_json}" >/dev/null; then
    return 0
  fi

  local request consumed hash create=false block_number consensus_timestamp
  if ! request="$(build_request "${result_json}")"; then
    return 0
  fi
  hash="$(jq -r '.hash // "unknown"' <<<"${result_json}")"
  consumed="$(jq -r '.gas_used' <<<"${result_json}")"
  block_number="$(jq -r '.block_number // empty' <<<"${result_json}")"
  consensus_timestamp="$(jq -r '.timestamp // .consensus_timestamp // empty' <<<"${result_json}")"
  if is_contract_create "${result_json}"; then
    create=true
  fi

  local attempt estimated body http_code result_hex actions sender pre_refund="" round_hour_retried=false
  for attempt in 1 2 3; do
    post_contracts_call "${request}"
    if [[ "${http_code}" != "200" ]]; then
      return 0
    fi

    result_hex="$(jq -r '.result // empty' <<<"${body}")"
    if [[ ! "${result_hex}" =~ ^0x[0-9a-fA-F]+$ ]]; then
      return 0
    fi
    estimated="$(hex_to_dec "${result_hex}")"

    if ((attempt == 1)) && [[ "${create}" == false ]] && ! within_tolerance "${estimated}" "${consumed}"; then
      load_actions "${hash}"
      actions="${cache_file}"
      if [[ -z "${actions}" ]] || ! jq -e '
        .actions[0] | .index == 0 and (.gas_used | type == "number" and . >= 0)
      ' "${actions}" >/dev/null; then
        return 0
      fi
      pre_refund="$(jq -r --slurpfile actions "${actions}" '
        ((.function_parameters // "") | sub("^0[xX]"; "")) as $data
        | 21000 + $actions[0].actions[0].gas_used
          + ([range(0; ($data | length); 2) | if $data[.:.+2] == "00" then 4 else 16 end] | add // 0)
      ' <<<"${result_json}")"
    fi
    if is_accurate "${estimated}" "${consumed}" "${pre_refund}"; then
      processed=$((processed + 1))
      passed=$((passed + 1))
      return 0
    fi

    if ((attempt == 1)) && [[ "${create}" == false ]]; then
      sender="$(jq -r '.actions[0].from // empty' "${actions}")"
      if [[ -n "${sender}" ]] && ! hex_equal "${sender}" "$(jq -r '.from // empty' <<<"${result_json}")"; then
        request="$(jq -c --arg sender "${sender}" '.from = $sender' <<<"${request}")"
        continue
      fi
    fi
    if [[ "${round_hour_retried}" == false && -n "${block_number}" ]] && is_near_round_hour "${consensus_timestamp}"; then
      request="$(jq -c --arg b "${block_number}" '.block = $b' <<<"${request}")"
      round_hour_retried=true
      continue
    fi
    break
  done

  check_dependencies "${result_json}"
  if [[ "${verdict}" != independent ]]; then
    return 0
  fi

  local replay_request
  replay_request="$(jq -c '.estimate = false' <<<"${request}")"
  post_contracts_call "${replay_request}"
  if ! is_reproducible_replay "${result_json}" "${http_code}" "${body}"; then
    return 0
  fi

  processed=$((processed + 1))
  has_failure=true
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

remaining="${RESULTS_COUNT}"
while ((remaining > 0)); do
  if [[ -z "${next_path}" || "${next_path}" == "null" ]]; then
    break
  fi

  page_url="$(page_url_from_next "${next_path}")"
  for ((page_attempt = 0; page_attempt < MAX_PAGE_FETCH_RETRIES; page_attempt++)); do
    if page_json="$(curl -sS -f "${page_url}")" \
      && result_count="$(jq -er '.results | if type == "array" then length else error("missing results array") end' \
        <<<"${page_json}")"; then
      break
    fi
    sleep "${SLEEP_SECONDS}"
  done
  if ((page_attempt == MAX_PAGE_FETCH_RETRIES)); then
    has_failure=true
    break
  fi
  if ((result_count == 0)); then
    break
  fi

  while IFS= read -r result_json; do
    if ((remaining <= 0)); then
      break
    fi
    remaining=$((remaining - 1))
    check_result "${result_json}"
    sleep "${SLEEP_SECONDS}"
  done < <(jq -c '.results[]' <<<"${page_json}")

  ((remaining > 0)) || break
  # Request only the remaining sample budget on the final page.
  next_path="$(jq -r --argjson limit "$((remaining < LIMIT ? remaining : LIMIT))" '
    .links.next // empty
    | if test("[?&]limit=") then
        sub("(?<prefix>[?&]limit=)[0-9]+"; "\(.prefix)\($limit)")
      else . + (if contains("?") then "&" else "?" end) + "limit=\($limit)"
      end
  ' <<<"${page_json}")"
done

summary="processed=${processed} passed=${passed}"
printf '%s\n' "${summary}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '%s\n' "${summary}" >> "${GITHUB_STEP_SUMMARY}"
fi

if [[ "${has_failure}" == true ]] || ((passed == 0)); then
  exit 1
fi

exit 0
