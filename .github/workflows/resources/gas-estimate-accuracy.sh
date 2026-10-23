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
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULT_HEX_PATTERN='"result"[[:space:]]*:[[:space:]]*"(0x[0-9a-fA-F]+)"'

executed=0
failed=0

read -r MIN_TOLERANCE_BPS TOLERANCE_BPS < <(awk -v t="${TOLERANCE_PERCENT}" -v m="${MIN_TOLERANCE_PERCENT}" \
  'BEGIN { printf "%.0f %.0f\n", m * 100, t * 100 }')

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT
mkdir -p "${WORK_DIR}/cache"

# ---------------------------------------------------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------------------------------------------------

# Runs jq with gas-estimate-accuracy.jq loaded: jq_lib FILTER [jq options and input files...]
jq_lib() {
  local filter="$1"
  shift
  jq -L "${SCRIPT_DIR}" "include \"gas-estimate-accuracy\"; ${filter}" "$@"
}

absolute_url() {
  local path_or_url="$1"
  if [[ "${path_or_url}" =~ ^https?:// ]]; then
    printf '%s' "${path_or_url}"
  else
    printf '%s%s' "${BASE_URL}" "${path_or_url}"
  fi
}

# GET a BASE_URL path (or an absolute links.next URL), failing on HTTP errors.
http_get() {
  curl -sSf --retry "${2:-3}" --retry-all-errors "$(absolute_url "$1")"
}

# Follows links.next starting at path and prints jq_lib filter's raw output for every page.
paginate() {
  local next="$1" filter="$2" retries="${3:-3}" page
  while [[ -n "${next}" ]]; do
    page="$(http_get "${next}" "${retries}")" || return 1
    jq_lib "${filter}" -r <<<"${page}" || return 1
    next="$(jq -r '.links.next // empty' <<<"${page}")"
  done
}

# Prints the path of a cached response, fetching it at most once per run.
cached_get() {
  local file="${WORK_DIR}/cache/$1"
  if [[ ! -s "${file}" ]] && ! http_get "$2" > "${file}"; then
    rm -f "${file}"
    return 1
  fi
  printf '%s' "${file}"
}

cached_detail() {
  [[ "$1" =~ ^0x[0-9a-fA-F]+$ ]] || return 1
  cached_get "$1.detail" "/api/v1/contracts/results/$1"
}

cached_actions() {
  [[ "$1" =~ ^0x[0-9a-fA-F]+$ ]] || return 1
  cached_get "$1.actions" "/api/v1/contracts/results/$1/actions?limit=100"
}

# Sets http_code and body for a /contracts/call POST.
post_contracts_call() {
  local response nl=$'\n'
  response="$(curl -sS -w '\n%{http_code}' \
    -X POST "${BASE_URL}/api/v1/contracts/call" \
    -H 'Accept: application/json' \
    -H 'Content-Type: application/json' \
    --data "$1")" || true
  http_code="${response##*"${nl}"}"
  body="${response%"${nl}"*}"
}

# Prints the decimal gas estimate for an estimate=true request.
estimate_gas() {
  post_contracts_call "$1"
  [[ "${http_code}" == "200" && "${body}" =~ ${RESULT_HEX_PATTERN} ]] || return 1
  printf '%d' "${BASH_REMATCH[1]}"
}

# Prints the integer part of a block's start timestamp.
block_start_seconds() {
  local seconds
  seconds="$(http_get "/api/v1/blocks/$1" | jq -r '.timestamp.from // empty | split(".")[0]')" || return 1
  [[ "${seconds}" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "${seconds}"
}

# ---------------------------------------------------------------------------------------------------------------------
# Estimation
# ---------------------------------------------------------------------------------------------------------------------

# Estimate must be at least MIN_TOLERANCE_PERCENT above gas_used and at most TOLERANCE_PERCENT above it.
within_tolerance() {
  local estimated="$1" consumed="$2"
  ((consumed > 0)) || return 1
  ((estimated * 10000 >= consumed * (10000 + MIN_TOLERANCE_BPS) \
    && estimated * 10000 <= consumed * (10000 + TOLERANCE_BPS)))
}

# Accurate against gas_used, or against the pre-refund gas when the transaction got a refund.
is_accurate() {
  local estimated="$1" consumed="$2" pre_refund="$3"
  within_tolerance "${estimated}" "${consumed}" \
    || { [[ -n "${pre_refund}" ]] && ((pre_refund > consumed)) && within_tolerance "${estimated}" "${pre_refund}"; }
}

# True when a consensus timestamp (seconds.nanoseconds) is at a round hour or up to 2 seconds after.
is_near_round_hour() {
  local seconds="${1%%.*}"
  [[ "${seconds}" =~ ^[0-9]+$ ]] || return 1
  (((seconds % 3600) <= 2))
}

pre_refund_gas() {
  local result_json="$1" hash="$2" actions
  actions="$(cached_actions "${hash}")" || return 1
  jq_lib '($actions[0].actions[0].gas_used) as $gas | select($gas | type == "number") | pre_refund_gas($gas)' \
    -er --slurpfile actions "${actions}" <<<"${result_json}"
}

build_create_request() {
  local result_json="$1" contract_id params bytecode="" data

  IFS=$'\t' read -r contract_id params < <(jq -r '
    [.contract_id // .created_contract_ids[0]?, .function_parameters]
    | map(if . == null or . == "" then "-" else . end) | @tsv
  ' <<<"${result_json}")
  [[ "${contract_id}" != "-" ]] || contract_id=""
  [[ "${params}" != "-" ]] || params=""
  [[ -z "${params}" || "${params}" == 0x* ]] || params="0x${params}"

  if [[ -n "${contract_id}" ]]; then
    bytecode="$(http_get "/api/v1/contracts/${contract_id}" | jq -r '.bytecode // empty')" || bytecode=""
  fi

  if [[ -n "${bytecode}" && "${bytecode}" != "0x" ]]; then
    [[ "${bytecode}" == 0x* ]] || bytecode="0x${bytecode}"
    if [[ -n "${params}" && "${params}" != "0x" && "${params}" == "${bytecode}"* ]]; then
      data="${params}"
    else
      data="${bytecode}${params#0x}"
    fi
  elif [[ -n "${params}" && "${params}" != "0x" ]]; then
    data="${params}"
  else
    return 1
  fi

  jq_lib 'estimate_request($data)' -c --arg data "${data}" <<<"${result_json}"
}

# Prints the request for a second attempt (block N for round-hour transactions, the top-level action
# sender as from), or fails when neither changes the request.
retry_request() {
  local request="$1" hash="$2" block="$3" timestamp="$4" create="$5"
  local near_round_hour=false actions=/dev/null

  if is_near_round_hour "${timestamp}"; then
    near_round_hour=true
  fi
  if [[ "${create}" != true ]]; then
    actions="$(cached_actions "${hash}")" || actions=/dev/null
  fi

  jq_lib '
    . as $original
    | ($actions[0].actions[0].from // "") as $sender
    | (if $near_round_hour then .block = $block else . end)
    | (if $sender != "" and (hex_equal($sender; .from // "") | not) then .from = $sender else . end)
    | select(. != $original)
  ' -ce --slurpfile actions "${actions}" --argjson near_round_hour "${near_round_hour}" --arg block "${block}" \
    <<<"${request}"
}

# True when replaying the request as a plain call returns the original call_result.
is_reproducible_replay() {
  local request="$1" result_json="$2"
  post_contracts_call "$(jq -c '.estimate = false' <<<"${request}")"
  [[ "${http_code}" == "200" ]] \
    && printf '%s\n%s\n' "${result_json}" "${body}" \
    | jq_lib 'hex_equal(.[1].result // ""; .[0].call_result // "")' -es >/dev/null 2>&1
}

# Accurate estimates pass right away. Reproducible inaccurate ones are deferred to resolve_block_failures,
# which needs the complete block and block N-1 to rule out dependencies on other transactions.
check_result() {
  local result_json="$1" request="$2" hash="$3" block="$4" timestamp="$5" gas_used="$6" status="$7" create="$8"
  local estimated pre_refund="" retry

  if [[ "${create}" == true ]] && ! request="$(build_create_request "${result_json}")"; then
    return 0
  fi

  if ! estimated="$(estimate_gas "${request}")"; then
    return 0
  fi
  if [[ "${create}" != true ]] && ! within_tolerance "${estimated}" "${gas_used}"; then
    pre_refund="$(pre_refund_gas "${result_json}" "${hash}")" || pre_refund=""
  fi
  if is_accurate "${estimated}" "${gas_used}" "${pre_refund}"; then
    executed=$((executed + 1))
    return 0
  fi

  if retry="$(retry_request "${request}" "${hash}" "${block}" "${timestamp}" "${create}")"; then
    request="${retry}"
    if ! estimated="$(estimate_gas "${request}")"; then
      return 0
    fi
    if is_accurate "${estimated}" "${gas_used}" "${pre_refund}"; then
      executed=$((executed + 1))
      return 0
    fi
  fi

  # A consensus CONTRACT_REVERT_EXECUTED with a successful eth_call is not reproducible.
  if [[ "${status}" == "CONTRACT_REVERT_EXECUTED" ]] || ! is_reproducible_replay "${request}" "${result_json}"; then
    return 0
  fi

  jq -c --argjson estimated "${estimated}" --argjson request "${request}" \
    '{hash, transaction_index: (.transaction_index // 0), gas_used, estimated: $estimated, request: $request}' \
    <<<"${result_json}" >> "${WORK_DIR}/failures-${block}.jsonl"
}

record_failure() {
  local hash="$1" estimated="$2" consumed="$3" request="$4" overhead
  executed=$((executed + 1))
  failed=$((failed + 1))
  overhead="$(awk -v e="${estimated}" -v c="${consumed}" 'BEGIN { printf "%.2f", ((e - c) * 100.0 / c) }')"
  echo "Out of tolerance for hash=${hash}: estimated=${estimated} gas_used=${consumed} overhead=${overhead}% (expected ${MIN_TOLERANCE_PERCENT}-${TOLERANCE_PERCENT}%)"
  echo "request=${request}"
}

# ---------------------------------------------------------------------------------------------------------------------
# Block dependencies
# ---------------------------------------------------------------------------------------------------------------------

# A block is complete once the stream has moved past it. Otherwise (end of stream) it is listed directly.
ensure_block_complete() {
  local block="$1" file="${WORK_DIR}/block-${1}.jsonl"
  if { [[ -n "${reading_block}" ]] && ((block > reading_block)); } || [[ -e "${file}.listed" ]]; then
    return 0
  fi
  paginate "/api/v1/contracts/results?block.number=${block}&limit=${LIMIT}" '.results[]? | tojson' > "${file}.tmp" \
    || return 1
  mv "${file}.tmp" "${file}"
  touch "${file}.listed"
}

# Prints the block's transactions up to max_index (all when empty) with state_changes and, when with_actions
# is true, touched entities. enriched=false marks transactions whose detail or actions could not be loaded.
load_state_changes_from_block() {
  local block="$1" with_actions="$2" max_index="${3:-null}"
  local file="${WORK_DIR}/block-${1}.jsonl" hash summary detail actions

  ensure_block_complete "${block}" || return 1
  [[ -s "${file}" ]] || return 0

  while IFS= read -r -u 5 hash && IFS= read -r -u 5 summary; do
    actions=/dev/null
    if detail="$(cached_detail "${hash}")" \
      && { [[ "${with_actions}" != true ]] || actions="$(cached_actions "${hash}")"; } \
      && jq_lib '
        $detail[0] as $d
        | . + {
            enriched: true,
            state_changes: ($d.state_changes // []),
            touched: (if $with_actions then ($d | touched_entities($actions[0] // {actions: []})) else [] end)
          }
      ' -c --slurpfile detail "${detail}" --slurpfile actions "${actions}" --argjson with_actions "${with_actions}" \
        <<<"${summary}" 2>/dev/null; then
      continue
    fi
    jq -c '. + {enriched: false}' <<<"${summary}"
  done 5< <(jq -r --argjson max "${max_index}" \
    'select($max == null or (.transaction_index // 0) <= $max) | (.hash // ""), tojson' "${file}")
}

# Marks independent failures of a block as dependent when they read a slot that block N-1 wrote its own
# timestamp to and write block N's timestamp.
timestamp_dependant() {
  local block="$1" enriched="$2" is_timestamp_dependant="$3"
  local prev_enriched="${WORK_DIR}/enriched-prev-${1}.jsonl" curr_sec prev_sec curr_hex prev_hex candidates

  if ((block <= 0)) || ! jq -e 'any(.[]; . == "independent")' <<<"${is_timestamp_dependant}" >/dev/null; then
    printf '%s' "${is_timestamp_dependant}"
    return 0
  fi
  if ! curr_sec="$(block_start_seconds "${block}")" || ! prev_sec="$(block_start_seconds $((block - 1)))"; then
    jq -c 'map_values(if . == "independent" then "unknown" else . end)' <<<"${is_timestamp_dependant}"
    return 0
  fi
  printf -v curr_hex '%08x' "${curr_sec}"
  printf -v prev_hex '%08x' "${prev_sec}"

  candidates="$(jq_lib '
    map(select($is_timestamp_dependant[.hash // ""] == "independent")
      | {key: .hash, value: timestamp_candidate_slots($curr; $prev)}
      | select(.value != []))
    | from_entries
  ' -cs --argjson is_timestamp_dependant "${is_timestamp_dependant}" --arg curr "${curr_hex}" --arg prev "${prev_hex}" "${enriched}")" \
    || candidates='{}'
  if [[ "${candidates}" == "{}" ]]; then
    printf '%s' "${is_timestamp_dependant}"
    return 0
  fi

  load_state_changes_from_block $((block - 1)) false > "${prev_enriched}" || echo '{"enriched":false}' > "${prev_enriched}"
  jq_lib '
    . as $prev_txs
    | any(.[]; .enriched | not) as $incomplete
    | $is_timestamp_dependant + ($candidates | with_entries(
        .value as $slots
        | if ($prev_txs | wrote_timestamp($slots; $prev)) then .value = "dependent"
          elif $incomplete then .value = "unknown"
          else empty
          end))
  ' -cs --argjson is_timestamp_dependant "${is_timestamp_dependant}" --argjson candidates "${candidates}" --arg prev "${prev_hex}" \
    "${prev_enriched}"
  rm -f "${prev_enriched}"
}

# Decides the deferred failures of a block once the block and block N-1 have been read completely.
resolve_block_related_discrepancies() {
  local block="$1"
  local failures="${WORK_DIR}/failures-${1}.jsonl" enriched="${WORK_DIR}/enriched-${1}.jsonl"
  local max_index is_timestamp_dependant hash verdict estimated consumed request

  if [[ -s "${failures}" ]]; then
    max_index="$(jq -s 'map(.transaction_index) | max' "${failures}")"
    load_state_changes_from_block "${block}" true "${max_index}" > "${enriched}" || : > "${enriched}"
    is_timestamp_dependant="$(jq_lib 'intra_block_is_timestamp_dependant($failures)' -cs --slurpfile failures "${failures}" "${enriched}")" \
      || is_timestamp_dependant='{}'
    is_timestamp_dependant="$(timestamp_dependant "${block}" "${enriched}" "${is_timestamp_dependant}")"

    while IFS=$'\t' read -r -u 4 hash verdict estimated consumed && IFS= read -r -u 4 request; do
      if [[ "${verdict}" == independent ]]; then
        record_failure "${hash}" "${estimated}" "${consumed}" "${request}"
      fi
    done 4< <(jq -r --argjson is_timestamp_dependant "${is_timestamp_dependant}" \
      '"\(.hash)\t\($is_timestamp_dependant[.hash // ""] // "unknown")\t\(.estimated)\t\(.gas_used)", (.request | tojson)' \
      "${failures}")
  fi

  rm -f "${failures}" "${enriched}" "${WORK_DIR}/block-${block}.jsonl" "${WORK_DIR}/block-${block}.jsonl.listed"
}

# ---------------------------------------------------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------------------------------------------------

# Per result: a TSV line of scalars ("-" for empty, since read collapses consecutive tabs), the result JSON,
# and the call request ("-" for creates, which are built on demand).
STREAM_FILTER='
  .results[]?
  | ([.hash, .block_number, .timestamp, .gas_used, .result, is_create, should_skip]
     | map(if . == null or . == "" then "-" else tostring end) | @tsv),
    tojson,
    (if is_create then "-" else (call_request | tojson) end)
'

# Results arrive newest first, so a block's results are contiguous and block N-1 follows block N. Each result is
# estimated as it arrives and buffered per block. A block's failures are resolved once block N-1 has been read
# completely, so no block is listed twice and every transaction detail is fetched at most once.
verify_results() {
  local page_limit cutoff_timestamp first_page
  local hash block timestamp gas_used status create skip result_json request resolvable_block
  local remaining="${RESULTS_COUNT}" stop_block=""

  page_limit=$((RESULTS_COUNT < LIMIT ? RESULTS_COUNT : LIMIT))
  cutoff_timestamp="$(($(date +%s) - 2 * 86400)).000000000"
  first_page="/api/v1/contracts/results?limit=${page_limit}&order=desc&timestamp=lte:${cutoff_timestamp}"
  reading_block=""
  pending_block=""

  while IFS=$'\t' read -r -u 3 hash block timestamp gas_used status create skip \
    && IFS= read -r -u 3 result_json \
    && IFS= read -r -u 3 request; do
    [[ "${block}" != "-" ]] || continue

    if [[ "${block}" != "${reading_block}" ]]; then
      resolvable_block="${pending_block}"
      pending_block="${reading_block}"
      reading_block="${block}"
      [[ -z "${resolvable_block}" ]] || resolve_block_related_discrepancies "${resolvable_block}"
      # Once the budget is spent, read on only until block N-1 of the last counted block is complete.
      if [[ -n "${stop_block}" ]] && ((block < stop_block)); then
        break
      fi
    fi

    printf '%s\n' "${result_json}" >> "${WORK_DIR}/block-${block}.jsonl"
    [[ -z "${stop_block}" ]] || continue
    remaining=$((remaining - 1))
    ((remaining > 0)) || stop_block=$((block - 1))

    if [[ "${skip}" == true ]]; then
      continue
    fi
    check_result "${result_json}" "${request}" "${hash}" "${block}" "${timestamp}" "${gas_used}" "${status}" "${create}"
    sleep "${SLEEP_SECONDS}"
  done 3< <(paginate "${first_page}" "${STREAM_FILTER}" "${MAX_PAGE_FETCH_RETRIES}")

  for block in "${pending_block}" "${reading_block}"; do
    [[ -z "${block}" ]] || resolve_block_related_discrepancies "${block}"
  done
}

if ((RESULTS_COUNT > 0)); then
  verify_results
fi

failure_percentage="$(awk -v e="${executed}" -v f="${failed}" 'BEGIN {
  printf "%.2f", (e > 0 ? (f * 100.0 / e) : 0)
}')"

summary="${executed} of gas estimations were executed, ${failure_percentage} % (${failed}) of them has gas accuracy failures"
printf '%s\n' "${summary}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Summary:"
    printf '%s\n' "${summary}"
  } >> "${GITHUB_STEP_SUMMARY}"
fi

if ((executed == 0 || failed > 0)); then
  exit 1
fi

exit 0
