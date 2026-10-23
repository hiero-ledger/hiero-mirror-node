# CLPR block-stream fixtures

Real blocks captured from a local `hiero-consensus-node` subprocess network (not synthetic fixtures built via
`RecordItemBuilder`), used by `ClprBlockFileE2ETest`. Update this table whenever a file here is added, replaced, or
re-sourced from a different consensus-node version or test run.

## `0000/0000/0000/0000/001.blk.zstd`

- **Source**: `hiero-consensus-node` tag `v0.80.0-alpha.3`, single-network `hapiTestClpr` gradle task running
  `com.hedera.services.bdd.suites.clpr.ClprSubmitBundleSuite.closedPeerTriggersClosingOnActiveChannel`.
- **Block number**: 13 (of node0's run output).
- **Contents** (in order; non-CLPR consensus housekeeping omitted):

  1. `ClprRegisterChannel` — commitment `16f4a1e0...`
  2. `ClprCompleteChannel` — channelId `163cfdaf...6ffb9`, verifierContract `11.12.1004`
  3. `ContractCall` → `11.12.1004` (the verifier contract) — internal `ClprPassThroughVerifier.verifyConfig` dispatch
     triggered by completing the channel, **not** a user-level `sendMessage` call
  4. `ClprRegisterConnector` — commitment `2f634379...`
  5. `ClprCompleteConnector` — same channelId, connectorId `b67137cc...`, connectorContract `11.12.1006`,
     lockedStake `100000000`

  All transactions `SUCCESS`.

- **Covers**: channel register/complete, connector register/complete commit-reveal lifecycle.
- **Does not cover** (known gaps, not yet captured in any fixture):
  - `clpr_ledger_configuration` rows (the suite's `clprUpdateLedgerConfiguration` call landed in a different block
    from this run).
  - `clpr_message` rows / a user-level `sendMessage` call to the CLPR system contract — see `002.blk.zstd` below.
  - `ClprSubmitBundle` — see `002.blk.zstd` below.
  - Cross-network message ack/delivery — only the multi-network `ClprMessagesSuite` round-trip exercises this;
    deliberately not attempted yet (heavier, two-network run).

## `0000/0000/0000/0000/002.blk.zstd`

- **Source**: same run as `001.blk.zstd` above (`v0.80.0-alpha.3`, `hapiTestClpr` /
  `ClprSubmitBundleSuite.closedPeerTriggersClosingOnActiveChannel`) — this is the very next real block (block 14),
  parsed immediately after `001.blk.zstd` in `ClprBlockFileE2ETest` as a genuine sequential two-block stream (real
  hash-chain linkage, not synthetic).
- **Block number**: 14.
- **Contents** (in order; non-CLPR consensus housekeeping omitted):

  1. `ContractCall` → `11.12.1008` (the CLPR system contract), payer account `1009` ("caller"), gas `2000000` — the
     real user-level `sendMessage` call. Produces `clpr_message` messageId 1, type `DATA`.
  2. `ClprSubmitBundle` — same channelId, bundle payload 143 bytes. Produces `clpr_message` messageId 2, type
     `RESPONSE`, `replyStatus=CONNECTOR_NOT_FOUND`, `replyToMessageId=1` (auto-generated because this is a
     single-network capture with no real peer connector to deliver to). Transitions the channel to `CLOSING`.
  3. `ContractCall` → `11.12.1004` (the verifier contract) — internal `ClprPassThroughVerifier.verifyBundle`
     dispatch, a **child** transaction of the `ClprSubmitBundle` above (same `parentConsensusTimestamp`).
  4. `ClprCloseChannel` — fails with `CLPR_INVALID_CHANNEL_STATUS` (expected: the channel is `CLOSING`, not
     `ACTIVE`, after the bundle above).

  All `SUCCESS` except the close-channel attempt (expected failure).

- **Covers**: `sendMessage` → `ClprSubmitBundle` → message persistence, channel status transition, a failed
  close-channel attempt, and (combined with `001.blk.zstd`) confirms `clpr_channel_history` picks up **both**
  channel writes in this block — the `sendMessage` call's `nextMessageId` bump and the `ClprSubmitBundle`'s status
  transition each cut a history row today (every channel write historizes for now, see `channelHistorizes()` in
  `ClprStateChangeHandler`) — while `clpr_connector_history` stays empty, since this block never touches the
  connector.
- **Bug found via this fixture**: step 3's child `ContractCall` shares its parent `ClprSubmitBundle`'s
  `StateChangeContext` by reference (`BlockTransaction.createStateChangeContext()`), so before the fix in
  `AbstractBlockTransactionTransformer.transformClprSendMessageSideEffects` (skip when `blockTransaction.getParent()
!= null`), messageId 2 was transformed onto _both_ the parent's and the child's `RecordItem` and inserted twice
  (`ClprMessage` is insert-only with no dedup). Caught by asserting `clpr_message` row count against this real
  block instead of trusting `isPositive()`.
- **Does not cover**: cross-network message ack/delivery (only `ClprMessagesSuite`'s multi-network round-trip
  exercises that).

## `0000/0000/0000/0000/003.blk.zstd`

- **Source**: `hiero-consensus-node` tag `v0.80.0-alpha.3`, two-network `testSubprocess` run (filtered to a single
  test) of `com.hedera.services.bdd.suites.clpr.ClprHieroToHieroSuite.configUpdateMidStreamBumpsBundleCap` —
  `ledgerA`/`ledgerB`, real TSS signatures (`tss.forceMockSignatures=false`), using the pre-existing warm
  `tss-startup-assets/ledgerA-genesis-network.json.gz` / `ledgerB-genesis-network.json.gz` cache to skip the ~8-14 min
  cold WRAPS bootstrap. This is the only scenario in the whole consensus-node test suite that produces a
  `CONTROL`-type `ClprMessage` (a `ClprConfigUpdate` slot, prepended to a sender's next outbound bundle when its local
  config has a newer `lastConfigTimestamp` than the channel's recorded one) — every other CLPR scenario is
  single-network and never emits one.
- **Block number**: 22 of ledgerA's run (ledgerA is the side with the app contract that calls `sendMessages`).
- **Captured standalone**, not chained to any other file here: unlike `001`/`002.blk.zstd`, this block's own
  transactions don't carry a `ClprChannel`/`ClprConnector` map-update (nothing about either changed in this specific
  bundle), so a mirror node that only ever sees this one block has no `clpr_channel`/`clpr_connector` row — only
  `clpr_message`, which is insert-only with no foreign key, so it doesn't need one.
- **Contents**: a single `ContractCall` burst (`SOURCE_APP.sendMessages(6)` after the test bumped
  `maxMessagesPerBundle` from 2 to 6) plus a `ClprSubmitBundle` landed in the same consensus round. Together they
  produce 7 new `clpr_message` rows, assigned ids 7-13 on this channel:
  - id 7, type `CONTROL`: `ClprConfigUpdate` — `ClprControlConfiguration` with `chainId=hiero:mainnet`,
    `maxMessagesPerBundle=6` (the value the suite just set), plus the rest of the ledger config
    (`maxGasPerMessage`, `maxQueueDepth`, `maxSyncBytes`, etc.) and a trust-anchor pair.
  - ids 8-13, type `DATA`: the 6 `sendMessages` calls from this burst (the first burst, 6 messages at cap=2, landed
    earlier in ledgerA's own block 16 as ids 1-6 — not captured here).
- **Does not cover**: the receiving side's handling of this bundle (on ledgerB, dispatch of these DATA messages
  and any processing of the CONTROL slot) — that's on ledgerB's blocks, not captured as a fixture (see catalog
  below).

### Other CLPR content captured in the same run, not turned into fixtures

Captured but intentionally left out for now — scope/time, not because it's uninteresting. Re-running
`configUpdateMidStreamBumpsBundleCap` is cheap (~2.5 min) once the TSS warm cache exists, so these can be mined later
without repeating the original ~multi-minute capture process. The warm-cache genesis fixtures
(`tss-startup-assets/{ledgerA,ledgerB}-genesis-network.json.gz`) are what make reruns cheap; the raw block files
themselves live only in each node's ephemeral `build/` output dir and are **not preserved anywhere durable** — a
future `./gradlew clean` (or just rerunning the test, which produces fresh per-run consensus timestamps) will lose
them, so anything below worth having must be re-extracted before that happens.

- **ledgerA, block 7**: `CLPRUPDATELEDGERCONFIGURATION` — the real ledger-config update the suite performs at setup.
  We have never captured a `clpr_ledger_configuration` row from a real block; this would be the first.
- **ledgerA, blocks 13-14**: `ClprRegisterChannel`/`ClprRegisterConnector` then `ClprCompleteChannel`/
  `ClprCompleteConnector`/`ClprRegisterConnector` — register/complete split across two blocks here (vs. one block in
  the single-network `001.blk.zstd` capture).
- **ledgerA, block 16**: burst 1 — 6 `DATA` messages (ids 1-6) at `maxMessagesPerBundle=2`, fragmented across
  multiple underlying bundles but landing in one block.
- **ledgerA, blocks 28-39** (trickle): individual `RESPONSE` messages arriving one at a time as ledgerB's replies to
  the burst-2 DATA messages come back.
- **ledgerB, block 6**: the same `CLPRUPDATELEDGERCONFIGURATION` as ledgerA's block 7, mirrored on the peer.
- **ledgerB, block 13**: register/complete for both channel and connector in a single block (unlike ledgerA's split).
- **ledgerB, block 15, 18-20, 24-27, 29-38 (repeated)**: `CLPRSUBMITBUNDLE` failing with
  `CLPR_NO_PROGRESS` (`ResponseCodeEnum` 571) — a real, repeatable **failure case** we don't have in any existing
  fixture. Happens whenever a bundle is resubmitted with nothing new to report; worth a dedicated negative-path test.
  Block 21 is the counterexample: a `SUCCESS` pure-ack `ClprSubmitBundle` (no new message, just an ack) in the same
  run.
  - Note: `DispatchProcessor` also logs a `WARN ... status=CLPR_NO_PROGRESS` line per retry here — harmless retry
    noise, not a crash signal (we over-matched on it with a broad `grep "Exception"` monitor filter while chasing
    this capture and had to retighten it; see the session's monitor history).
- **ledgerB, block 17**: 6 `RESPONSE` messages (ids 1-6, `APPLICATION_ERROR`) generated in a single block — B's
  replies to burst 1's DATA messages. The dispatch failure is itself real and reproducible: B's `EvmClprVerifier`
  dispatches to `target=EVM_ADDRESS(0x0…0)` and gets `INVALID_CONTRACT_ID`/`NullPointerException`, because this
  suite's `SOURCE_APP` is only deployed on ledgerA — there's no matching target app on B for the dispatch to reach.
  Not a bug in our importer; a property of this test's setup.
- **ledgerB, blocks 23-24**: 6 more `RESPONSE` messages (ids 7-12) — replies to burst 2's DATA messages (same
  `INVALID_CONTRACT_ID` cause as above). Note the CONTROL slot (A's id 7) gets no corresponding reply — only DATA
  slots get app-dispatch replies, consistent with spec.
- **ledgerB, blocks 34-38 (trickle)**: more `DATA`/`RESPONSE` messages (ids 15-20), but a different kind than the
  app burst — the message content is a literal ASCII `"active-probe"` payload the HapiTest harness sends to keep the
  channel's liveness check satisfied while a `networkHapiTest` step awaits a condition. Not application traffic, and
  not currently decoded/asserted anywhere.
- **Open question, not yet investigated**: whether `clpr_message.id` (channel_id, message_id) could collide between
  a connector's own locally-assigned outbound counter and a peer's inbound-assigned counter if a channel has
  multiple connectors active concurrently — the schema's primary key doesn't include `connector_id`. Not reproduced;
  flagged from reading the numbering behavior in this capture, not from a confirmed bug.
