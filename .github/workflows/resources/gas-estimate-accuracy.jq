# SPDX-License-Identifier: Apache-2.0

# Shared filters for gas-estimate-accuracy.sh. Inputs are mirror node ContractResult objects unless stated otherwise.

def empty_to: .to == null or .to == "" or .to == "0x";

# The result's contract_id was itself created by the transaction.
def created_self:
  .contract_id as $cid
  | $cid != null and any((.created_contract_ids // [])[]; . == $cid);

def is_create: empty_to or created_self;

def creates_contracts: empty_to or (((.created_contract_ids // []) | length) > 0);

def has_function_parameters: (.function_parameters // "") as $p | $p != "" and $p != "0x";

def should_skip:
  ((.gas_used // 0) <= 0)
  or (is_create and .contract_id == null and (((.created_contract_ids // []) | length) == 0))
  or (empty_to and (created_self | not) and (has_function_parameters | not));

# /contracts/call estimate body simulated at the block before the original transaction.
def estimate_request($data):
  {
    estimate: true,
    data: $data,
    from: .from,
    gas: (.gas_limit // 15000000),
    value: (.amount // 0),
    block: (if .block_number != null then ((.block_number - 1) | tostring) else "latest" end)
  };

def call_request:
  estimate_request(.function_parameters // "0x")
  + (if empty_to then {} else {to: .to} end);

# Canonical hex for equality: optional 0x, case-insensitive, leading zeros stripped so 0x1, 0x01 and 0x001
# compare equal (empty, 0x and 0x0 too).
def normalize_hex:
  (. // "") | ascii_downcase | sub("^0x"; "") | sub("^0+"; "")
  | if . == "" then "0" else . end;

def hex_equal($a; $b): ($a | normalize_hex) == ($b | normalize_hex);

# Pre-refund gas: intrinsic gas (21000 + calldata cost) plus the top-level action's gas_used.
def pre_refund_gas($action_gas):
  ((.function_parameters // "") | sub("^0[xX]"; "")) as $h
  | 21000 + ([range(0; ($h | length); 2) | if $h[.:.+2] == "00" then 4 else 16 end] | add // 0) + $action_gas;

# Unique contract, account and token identifiers (entity IDs and EVM addresses) of a
# /contracts/results/{hash} body (including state_changes and logs) and its /actions body.
def touched_entities($actions):
  def valid_entity_id:
    type == "string"
    and test("^0\\.0\\.[0-9]+$")
    and ((split(".")[2] | tonumber) > 1000);

  def valid_evm_address:
    test("^0x[0-9a-f]{40}$")
    and (test("^0x0{37}[0-3][0-9a-f]{2}$") | not);

  (
    [
      .contract_id,
      .created_contract_ids[]?,
      .state_changes[]?.contract_id,
      .logs[]?.contract_id,
      $actions.actions[]?.caller,
      $actions.actions[]?.recipient
    ]
    | map(select(valid_entity_id))
  )
  +
  (
    [
      .from,
      .to,
      .address,
      .state_changes[]?.address,
      .logs[]?.address,
      $actions.actions[]?.from,
      $actions.actions[]?.to
    ]
    | map(select(type == "string") | ascii_downcase | select(valid_evm_address))
  )
  | unique;

def to_set: map({(.): true}) | add // {};

def slot_key: "\(.contract_id):\(.slot)";

def written_changes:
  .state_changes[]?
  | select(.value_written != null and .value_written != .value_read);

# Input: enriched transactions of one block. Walks them once in transaction_index order and returns
# {hash: verdict} for the failures: "dependent" when an earlier transaction created a contract, wrote a
# storage slot the failure accessed, or touched one of its entities; "unknown" when that cannot be decided
# because the failure or an earlier transaction could not be loaded; otherwise "independent".
def intra_block_verdicts($failures):
  ($failures | map(.hash) | to_set) as $failing
  | sort_by(.transaction_index // 0)
  | reduce .[] as $tx ({created: false, incomplete: false, written: {}, touched: {}, verdicts: {}};
      . as $acc
      | (if $failing[$tx.hash // ""] then
          .verdicts[$tx.hash] = (
            if $acc.created
              or ($tx.enriched
                and (any($tx.state_changes[]; $acc.written[slot_key]) or any($tx.touched[]; $acc.touched[.])))
            then "dependent"
            elif $acc.incomplete or ($tx.enriched | not) then "unknown"
            else "independent"
            end)
        else . end)
      | .created = (.created or ($tx | creates_contracts))
      | if $tx.enriched then
          .written += ([$tx | written_changes | slot_key] | to_set)
          | .touched += ($tx.touched | to_set)
        else
          .incomplete = true
        end)
  | .verdicts;

# Slots read holding block N-1's timestamp, provided the transaction also wrote block N's timestamp
# (e.g. TWAP/Oracle.write per-block guards where simulating at block N-1 hits last.blockTimestamp == block.timestamp).
def timestamp_candidate_slots($curr_hex; $prev_hex):
  if any(written_changes; .value_written | ascii_downcase | contains($curr_hex)) then
    [.state_changes[]? | select(.value_read // "" | ascii_downcase | contains($prev_hex)) | slot_key] | unique
  else
    []
  end;

# Input: enriched transactions of block N-1. True when one of them wrote block N-1's timestamp to one of the slots.
def wrote_timestamp($slots; $prev_hex):
  ($slots | to_set) as $wanted
  | any(.[] | written_changes | select(.value_written | ascii_downcase | contains($prev_hex)); $wanted[slot_key] // false);
