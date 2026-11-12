# CLPR block-stream fixtures

Real blocks captured from a local `hiero-consensus-node` subprocess network (not synthetic fixtures built via
`RecordItemBuilder`), used by `ClprBlockFileE2ETest`. Update this table whenever a file here is added, replaced, or
re-sourced from a different consensus-node version or test run.

## `0000/0000/0000/0000/001.blk.zstd`

- **Source**: `hiero-consensus-node` alpha5 (`build-00423`), single-network `hapiTestClpr` gradle task (a full rerun
  of the whole single-network CLPR HAPI suite, not a single filtered test method).
- **Block number**: 106 (of node0's run output) — the block immediately before `002.blk.zstd`.
- **Contents** (in order; non-CLPR consensus housekeeping omitted):

  1. `ClprRegisterChannel` — ownershipCommitment `29a90d6e...9f6b`, the commit half of the commit-reveal completed
     by `002.blk.zstd`'s `ClprCompleteChannel` (channelId `9632b28a...f330`).

  Transaction `SUCCESS`.

- **Covers**: the channel's register (commit) step in isolation — only a `clpr_channel_pending_commitment` row
  exists after this block, with no `clpr_channel` row yet (that's created on reveal, see `002.blk.zstd`).
- **Does not cover**: the connector's own register/complete commit-reveal — both halves of that land in
  `002.blk.zstd`.

## `0000/0000/0000/0000/002.blk.zstd`

- **Source**: same run as `001.blk.zstd` above (`hiero-consensus-node` alpha5 `build-00423`, `hapiTestClpr`) — the
  very next real block (107), parsed immediately after `001.blk.zstd` in `ClprBlockFileE2ETest` as a genuine
  sequential block stream (real hash-chain linkage, not synthetic).
- **Block number**: 107.
- **Contents** (in order; non-CLPR consensus housekeeping omitted):

  1. `ClprCompleteChannel` — channelId `9632b28a...f330`, verifierContract `11.12.1113` (the reveal half of the
     register from `001.blk.zstd`).
  2. `ContractCall` → `11.12.1113` (the verifier contract) — internal `ClprPassThroughVerifier.verifyConfig`
     dispatch triggered by completing the channel, **not** a user-level `sendMessage` call.
  3. `ClprRegisterConnector` — commitment `cd30c2cc...77f6`.
  4. `ClprCompleteConnector` — same channelId, connectorId `5c6562ad...8466b`, connectorContract `11.12.1115`,
     lockedStake `100000000`.
  5. `ContractCall` → the CLPR system contract — a user-level `sendMessage` call, landing in the same block as the
     connector commit-reveal. Produces `clpr_message` messageId 1, type `DATA`, and bumps the channel's `nextMessageId` to 2 and the
     connector's `inFlightMessageCount` to 1.

  All transactions `SUCCESS`.

- **Covers**: channel reveal (completing the `001.blk.zstd` register) + connector register/complete commit-reveal
  lifecycle, plus the first user-level `sendMessage` call on the same channel/connector.
- **Does not cover** (known gaps, not yet captured in any fixture):
  - `clpr_ledger_configuration` rows from this run (see `004.blk.zstd` below for a different run that does cover
    this).
  - `ClprSubmitBundle` — see `003.blk.zstd` below.
  - Cross-network message ack/delivery — only the multi-network run (see `004.blk.zstd`) exercises this.

## `0000/0000/0000/0000/003.blk.zstd`

- **Source**: same run as `001.blk.zstd`/`002.blk.zstd` above (`hiero-consensus-node` alpha5 `build-00423`,
  `hapiTestClpr`) — the very next real block (108), parsed immediately after `002.blk.zstd` in
  `ClprBlockFileE2ETest` as a genuine sequential block stream (real hash-chain linkage, not synthetic).
- **Block number**: 108.
- **Contents** (in order; non-CLPR consensus housekeeping omitted):

  1. `ClprSubmitBundle` — same channelId, replying to the `sendMessage` from block 107. Produces `clpr_message`
     messageId 2, type `RESPONSE`, `replyStatus=CONNECTOR_NOT_FOUND`, `replyToMessageId=1` (auto-generated because
     this is a single-network capture with no real peer connector to deliver to). Transitions the channel to
     `CLOSING`.
  2. `ContractCall` → the verifier contract — internal `ClprPassThroughVerifier.verifyBundle` dispatch, a **child**
     transaction of the `ClprSubmitBundle` above (same `parentConsensusTimestamp`).
  3. `ClprCloseChannel` — fails with `CLPR_INVALID_CHANNEL_STATUS` (expected: the channel is `CLOSING`, not
     `ACTIVE`, after the bundle above).

  All `SUCCESS` except the close-channel attempt (expected failure).

- **Covers**: `ClprSubmitBundle` reply → message persistence, channel status transition, a failed close-channel
  attempt, and (combined with `002.blk.zstd`) confirms `clpr_channel_history` picks up both channel writes across
  these two blocks (the `sendMessage` call's `nextMessageId` bump and the `ClprSubmitBundle`'s status transition
  each cut a history row today — every channel write historizes for now, see `channelHistorizes()` in
  `ClprStateChangeHandler`) — while `clpr_connector_history` only picks up the two writes from block 107 (register
  and the in-flight-count bump from the `sendMessage`), since this block never touches the connector.
- **Does not cover**: cross-network message ack/delivery (only the multi-network run exercises that).

## `0000/0000/0000/0000/004.blk.zstd`

- **Source**: `hiero-consensus-node` alpha5 (`build-00423`), two-network `hapiTestClprMultinetwork` gradle task
  (`ledgerA-test` bucket, a full rerun of the multi-network CLPR HAPI suite, not a single filtered test method).
- **Block number**: 125 of ledgerA node0's run (ledgerA is the side with the app contract that calls
  `sendMessages`).
- **Captured standalone**, not chained to any other file here: like the alpha3 capture it replaces, this block's own
  transactions don't carry a `ClprChannel`/`ClprConnector` map-update, so a mirror node that only ever sees this one
  block has no `clpr_channel`/`clpr_connector` row — only `clpr_message`, which is insert-only with no foreign key,
  so it doesn't need one.
- **Contents**: a `ClprUpdateLedgerConfiguration` call plus a `ContractCall` burst landing in the same consensus
  round, producing a `CONTROL`-type `ClprMessage` (`ClprConfigUpdate`, `chainId=eip155:298`,
  `maxMessagesPerBundle=6`) as messageId 7, followed by 6 `DATA`-type messages (ids 8-13) from the app's
  `sendMessages` burst at the new cap. `clprMessageRepository.count()` is 7 after parsing this block alone.
- **Does not cover**: the receiving side's handling of this bundle (dispatch of these `DATA` messages and any
  processing of the `CONTROL` slot on the peer network) — not captured as a fixture.
