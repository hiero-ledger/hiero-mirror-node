#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# handle input argument defaults
network=${1:-demo}
account_limit=${2:-20}
transfer_window_ns=${3:-604800000000000}

currency=$(cat <<EOF
{
  "symbol": "HBAR",
  "decimals": 8,
  "metadata": {
    "issuer": "Hedera"
  }
}
EOF
)

genesis_timestamp_query=$(cat <<EOF
select consensus_timestamp
from account_balance
order by consensus_timestamp asc
limit 1
EOF
)

if [[ "$account_limit" == "0" ]]; then
  echo "Account limit removed, all accounts with non-zero balance account in initial balance file will be considered"
  applicable_accounts_query=$(cat <<EOF
    with genesis_balance as (
      select account_id, balance
      from account_balance
      where balance <> 0 and consensus_timestamp = :genesis_timestamp
    )
EOF
  )
else
  applicable_accounts_query=$(cat <<EOF
    with recent_crypto_accounts as (
      select distinct(entity_id)
      from crypto_transfer where consensus_timestamp > :genesis_timestamp and consensus_timestamp <= :genesis_timestamp + :transfer_window_ns
      order by entity_id asc
      limit :account_limit
    ),
    genesis_balance as (
      select account_id, balance
      from account_balance ab
      join recent_crypto_accounts ct
        on ct.entity_id = ab.account_id
      where balance <> 0 and ab.consensus_timestamp = :genesis_timestamp
      group by account_id,balance
    )
EOF
  )
fi

genesis_hbar_balance_query=$(cat <<EOF
\set ON_ERROR_STOP on
${applicable_accounts_query}
select json_agg(json_build_object('id', account_id::text, 'value', balance::text))
from genesis_balance
EOF
)

genesis_token_balance_query=$(cat <<EOF
\set ON_ERROR_STOP on
select json_agg(json_build_object(
  'id', account_id::text,
  'token', tb.token_id::text,
  'decimals', t.decimals,
  'value', balance::text,
  'type', t.type
))
from token_balance tb
join token t on t.token_id = tb.token_id
where tb.consensus_timestamp = :genesis_timestamp and
  tb.account_id in (:account_ids) and
  balance <> 0
EOF
)

parent_path="$(cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P)"
psql_cmd="psql -h localhost -U mirror_rosetta mirror_node -t -P format=unaligned"

echo "localhost:5432:mirror_node:mirror_rosetta:mirror_rosetta_pass" > ~/.pgpass && chmod 0600 ~/.pgpass

SECONDS=0
while [[ $SECONDS -lt 120 ]];
do
  genesis_timestamp=$(echo "$genesis_timestamp_query" | $psql_cmd)
  echo "Retrieved genesis balance file timestamp ${genesis_timestamp}"
  if [[ -z "$genesis_timestamp" ]]; then
    echo "Failed to get genesis timestamp"
    exit 1
  fi

  if [[ -n "$genesis_timestamp" ]]; then
    # get genesis hbar balances from genesis account balance file
    account_balances=$(echo "$genesis_hbar_balance_query" | $psql_cmd -v genesis_timestamp="$genesis_timestamp" -v transfer_window_ns="$transfer_window_ns" -v account_limit="$account_limit")
    echo "account balances - $(echo "$account_balances" | jq . )"
    break
  fi

  echo "${SECONDS}s elapsed, retry getting genesis timestamp in 5 seconds"
  sleep 5
done

hbar_json=$(echo "$account_balances" | \
  jq --argjson currency "$currency" \
  '[.[] | .account_identifier.address=("0.0." + .id) | del(.id) ] | .[].currency=$currency')

echo "$hbar_json" > "$parent_path/$network/data_genesis_balances.json"

echo "Data genesis balances written after ${SECONDS}s"
