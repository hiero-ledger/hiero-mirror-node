// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.callback.SimpleContext;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.RecordFileParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

// Parses record files through the real importer and checks the stored EVM transaction indexes. Where the fix migration
// discovers the block, it then runs the migration over the importer's output and checks it changes nothing.
@DisablePartitionMaintenance
@RequiredArgsConstructor
@Tag("migration")
final class EvmTransactionIndexImporterIntegrationTest
        extends AbstractAsyncJavaMigrationTest<FixScheduledAndHookEvmTransactionIndexMigration> {

    private static final ContractID PRECOMPILE_ID =
            ContractID.newBuilder().setContractNum(359).build();

    @Getter
    private final FixScheduledAndHookEvmTransactionIndexMigration migration;

    private final BackfillContractChildMigration childMigration;
    private final RecordFileParser recordFileParser;
    private final RecordItemBuilder recordItemBuilder;

    private final List<RecordItem> items = new ArrayList<>();
    private int parsedCount;
    private ContractID hookContractId;
    private RecordFile previousFile;
    private long timestamp;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
        hookContractId = recordItemBuilder.contractId(RecordItem.HOOK_CONTRACT_NUM);
        items.clear();
        parsedCount = 0;
        previousFile = null;
        timestamp = DomainUtils.convertToNanosMax(Instant.now());
    }

    @Test
    void rootsClaimSequentialIndexes() {
        // given
        final var call = add(recordItemBuilder.contractCall(), result(100), 0, null, false);
        final var ethereum = add(recordItemBuilder.ethereumTransaction(), result(200), 0, null, false);
        final var secondCall = add(recordItemBuilder.contractCall(), result(300), 0, null, false);

        // when
        parse();

        // then
        assertIndex(call, 0);
        assertIndex(ethereum, 1);
        assertIndex(secondCall, 2);
        assertChild(call, false);
        assertChild(ethereum, false);
        assertChild(secondCall, false);
    }

    @Test
    void rootWithoutGasUsedGetsNoIndexAndDoesNotShiftLaterRoots() {
        // given
        final var failedCall = add(recordItemBuilder.contractCall(), result(0), 0, null, false);
        final var call = add(recordItemBuilder.contractCall(), result(100), 0, null, false);

        // when
        parse();

        // then
        assertIndex(failedCall, null);
        assertIndex(call, 0);
    }

    @Test
    void precompileEffectsAndLogsShareTheirCallIndex() {
        // given
        final var otherCall = add(recordItemBuilder.contractCall(), result(100), 0, null, false);
        final var call = add(recordItemBuilder.contractCall(), resultWithLog(500), 0, null, false);
        final var mint = add(recordItemBuilder.tokenMint(), precompileResult(14121), 1, call, false);
        final var transfer = add(recordItemBuilder.cryptoTransfer(), precompileResult(0), 2, call, false);

        // when
        parse();

        // then
        assertIndex(otherCall, 0);
        assertIndex(call, 1);
        assertIndex(mint, 1);
        assertIndex(transfer, 1);
        assertLogIndexes(call, 1);
        assertChild(otherCall, false);
        assertChild(call, false);
        assertChild(mint, true);
        assertChild(transfer, true);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void atomicBatchInnerCallsClaimTheirOwnIndexes() {
        // given
        final var batch = add(recordItemBuilder.atomicBatch(), null, 0, null, false);
        final var firstInner = add(recordItemBuilder.contractCall(), result(100), 0, batch, false);
        final var effect = add(recordItemBuilder.cryptoTransfer(), precompileResult(13148), 1, firstInner, false);
        final var secondInner = add(recordItemBuilder.contractCall(), result(200), 0, batch, false);

        // when
        parse();

        // then
        assertIndex(firstInner, 0);
        assertIndex(effect, 0);
        assertIndex(secondInner, 1);
        assertChild(firstInner, false);
        assertChild(effect, true);
        assertChild(secondInner, false);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void hookCallsClaimIndexesAndSiblingsInheritUntilTheChainBreaks() {
        // given
        final var transfer = add(recordItemBuilder.cryptoTransfer(), null, 0, null, false);
        final var hook = add(recordItemBuilder.contractCall(hookContractId), hookResult(50), 1, transfer, false);
        final var sibling = add(recordItemBuilder.contractCall(), result(30), 2, transfer, false);
        add(recordItemBuilder.cryptoTransfer(), null, 3, transfer, false);
        final var afterBreak = add(recordItemBuilder.contractCall(), result(30), 4, transfer, false);

        // when
        parse();

        // then
        assertIndex(hook, 0);
        assertIndex(sibling, 0);
        assertIndex(afterBreak, 1);
        assertMigrationKeepsIndexes();
        assertChild(hook, false);
        assertChild(sibling, true);
        assertChild(afterBreak, false);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void effectsOfFailedCallWithoutGasUsedGetNoIndex() {
        // given
        final var failedCall = add(
                recordItemBuilder.contractCall().status(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED),
                result(0),
                0,
                null,
                false);
        final var mint = add(recordItemBuilder.tokenMint(), precompileResult(15284), 1, failedCall, false);
        final var transfer = add(recordItemBuilder.cryptoTransfer(), precompileResult(13148), 2, failedCall, false);
        final var call = add(recordItemBuilder.contractCall(), result(100), 0, null, false);

        // when
        parse();

        // then
        assertIndex(failedCall, null);
        assertIndex(mint, null);
        assertIndex(transfer, null);
        assertIndex(call, 0);
        assertMigrationKeepsIndexes();
        assertChild(failedCall, false);
        assertChild(mint, true);
        assertChild(transfer, true);
        assertChild(call, false);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void effectsOfFailedCallWithoutContractResultGetNoIndex() {
        // given
        final var failedCall =
                add(recordItemBuilder.contractCall().status(ResponseCodeEnum.INVALID_ALIAS_KEY), null, 0, null, false);
        final var transfer = add(recordItemBuilder.cryptoTransfer(), precompileResult(13292), 1, failedCall, false);
        final var precompileCall = add(recordItemBuilder.contractCall(), precompileResult(100), 2, failedCall, false);
        final var call = add(recordItemBuilder.contractCall(), result(100), 0, null, false);

        // when
        parse();

        // then
        assertIndex(failedCall, null);
        assertIndex(transfer, null);
        assertIndex(precompileCall, null);
        assertIndex(call, 0);
        assertMigrationKeepsIndexes();
        assertChild(failedCall, false);
        assertChild(transfer, true);
        assertChild(precompileCall, true);
        assertChild(call, false);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void scheduledCallEffectsShareTheScheduledCallIndex() {
        // given
        final var scheduleId = scheduleCreate();
        final var root = add(recordItemBuilder.contractCall(), result(100), 0, null, false);
        final var sign = add(scheduleSign(scheduleId), null, 0, null, false);
        final var scheduledCall = add(scheduled(scheduleId), result(227547), 0, null, true);
        final var transfer = add(recordItemBuilder.cryptoTransfer(), precompileResult(15284), 1, sign, false);
        final var mint = add(recordItemBuilder.tokenMint(), precompileResult(15284), 2, sign, false);
        final var laterRoot = add(recordItemBuilder.contractCall(), result(100), 0, null, false);

        // when
        parse();

        // then
        assertIndex(root, 0);
        assertIndex(scheduledCall, 1);
        assertIndex(transfer, 1);
        assertIndex(mint, 1);
        assertIndex(laterRoot, 2);
        assertMigrationKeepsIndexes();
        assertChild(root, false);
        assertChild(scheduledCall, false);
        assertChild(transfer, true);
        assertChild(mint, true);
        assertChild(laterRoot, false);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void scheduledCallWithPresetNonceClaimsItsOwnIndex() {
        // given - executed at expiry, so its effects are parented to the scheduled call itself
        final var scheduleId = scheduleCreate();
        final var scheduledCall = add(scheduled(scheduleId), result(8000000), 53, null, true);
        final var effect = add(recordItemBuilder.cryptoTransfer(), precompileResult(15284), 54, scheduledCall, false);
        final var call = add(recordItemBuilder.contractCall(), result(100), 0, null, false);

        // when
        parse();

        // then
        assertIndex(scheduledCall, 0);
        assertIndex(effect, 0);
        assertIndex(call, 1);
        assertMigrationKeepsIndexes();
        assertChild(scheduledCall, false);
        assertChild(effect, true);
        assertChild(call, false);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void effectsOfScheduledCallWithoutGasUsedGetNoIndex() {
        // given
        final var scheduleId = scheduleCreate();
        final var sign = add(scheduleSign(scheduleId), null, 0, null, false);
        final var scheduledCall = add(scheduled(scheduleId), result(0), 0, null, true);
        final var effect = add(recordItemBuilder.cryptoTransfer(), precompileResult(15284), 1, sign, false);
        final var call = add(recordItemBuilder.contractCall(), result(100), 0, null, false);

        // when
        parse();

        // then
        assertIndex(scheduledCall, null);
        assertIndex(effect, null);
        assertIndex(call, 0);
        assertMigrationKeepsIndexes();
        assertChild(scheduledCall, false);
        assertChild(effect, true);
        assertChild(call, false);
        assertChildBackfillMatchesImporter();
    }

    @Test
    void indexRestartsInEveryRecordFile() {
        // given
        final var first = add(recordItemBuilder.contractCall(), result(100), 0, null, false);
        final var second = add(recordItemBuilder.contractCall(), result(100), 0, null, false);
        parse();
        final var nextFileCall = add(recordItemBuilder.contractCall(), result(100), 0, null, false);

        // when
        parse();

        // then
        assertIndex(first, 0);
        assertIndex(second, 1);
        assertIndex(nextFileCall, 0);
    }

    private RecordItem add(
            RecordItemBuilder.Builder<?> builder,
            ContractFunctionResult contractResult,
            int nonce,
            RecordItem parent,
            boolean scheduled) {
        final var consensusTimestamp = ++timestamp;
        final var previous = items.isEmpty() ? null : items.getLast();
        final var payer = AccountID.newBuilder().setAccountNum(1001).build();
        final var transactionId = TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(timestamp(consensusTimestamp - 1000))
                .setNonce(nonce)
                .setScheduled(scheduled)
                .build();
        final var recordItem = builder.transactionBodyWrapper(body -> body.setTransactionID(transactionId))
                .record(record -> {
                    record.setConsensusTimestamp(timestamp(consensusTimestamp))
                            .setTransactionID(transactionId)
                            .clearContractCallResult()
                            .clearContractCreateResult()
                            .clearParentConsensusTimestamp();
                    if (contractResult != null) {
                        record.setContractCallResult(contractResult);
                    }
                    if (parent != null) {
                        record.setParentConsensusTimestamp(timestamp(parent.getConsensusTimestamp()));
                    }
                })
                .recordItem(r -> r.previous(previous))
                .build();
        items.add(recordItem);
        return recordItem;
    }

    private ScheduleID scheduleCreate() {
        final var scheduleId = recordItemBuilder.scheduleId();
        add(recordItemBuilder.scheduleCreate().receipt(r -> r.setScheduleID(scheduleId)), null, 0, null, false);
        return scheduleId;
    }

    private RecordItemBuilder.Builder<?> scheduleSign(ScheduleID scheduleId) {
        return recordItemBuilder
                .scheduleSign()
                .transactionBody(b -> b.setScheduleID(scheduleId))
                .receipt(r -> r.setScheduleID(scheduleId));
    }

    private RecordItemBuilder.Builder<?> scheduled(ScheduleID scheduleId) {
        return recordItemBuilder.contractCall().record(r -> r.setScheduleRef(scheduleId));
    }

    private void parse() {
        final var fileItems = new ArrayList<>(items.subList(parsedCount, items.size()));
        final var recordFile = domainBuilder
                .recordFile()
                .customize(r -> {
                    r.consensusStart(fileItems.getFirst().getConsensusTimestamp())
                            .consensusEnd(fileItems.getLast().getConsensusTimestamp())
                            .count((long) fileItems.size())
                            .items(fileItems);
                    if (previousFile != null) {
                        r.index(previousFile.getIndex() + 1).previousHash(previousFile.getHash());
                    }
                })
                .get();
        recordFileParser.parse(recordFile);
        previousFile = recordFile;
        parsedCount = items.size();
        timestamp += 1_000_000;
    }

    private void assertChild(RecordItem recordItem, boolean expected) {
        final var consensusTimestamp = recordItem.getConsensusTimestamp();
        assertThat(jdbcOperations.queryForList(
                        "select child from contract_result where consensus_timestamp = ?",
                        Boolean.class,
                        consensusTimestamp))
                .as("contract result child at %d", consensusTimestamp)
                .containsExactly(expected);
        assertThat(jdbcOperations.queryForList(
                        "select child from contract_log where consensus_timestamp = ?",
                        Boolean.class,
                        consensusTimestamp))
                .as("contract log child at %d", consensusTimestamp)
                .doesNotContain(!expected);
    }

    // The child backfill must reproduce exactly what the importer stored
    @SneakyThrows
    private void assertChildBackfillMatchesImporter() {
        final var before = children();
        jdbcOperations.update("update contract_result set child = false");
        jdbcOperations.update("update contract_log set child = false");
        jdbcOperations.update(
                "update flyway_schema_history set checksum = -1 where description = ?",
                childMigration.getDescription());

        childMigration.doMigrate();
        childMigration.handle(Event.AFTER_MIGRATE_OPERATION_FINISH, new SimpleContext(new FluentConfiguration()));
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> Integer.valueOf(childMigration.getSuccessChecksum())
                        .equals(jdbcOperations.queryForObject(
                                "select checksum from flyway_schema_history where description = ? "
                                        + "order by installed_rank desc limit 1",
                                Integer.class,
                                childMigration.getDescription())));
        assertThat(children()).isEqualTo(before);
    }

    private Map<String, Boolean> children() {
        final var result = new HashMap<String, Boolean>();
        jdbcOperations.query("select consensus_timestamp, child from contract_result", rs -> {
            result.put("result " + rs.getLong(1), rs.getBoolean(2));
        });
        jdbcOperations.query("select consensus_timestamp, index, child from contract_log", rs -> {
            result.put("log " + rs.getLong(1) + "/" + rs.getInt(2), rs.getBoolean(3));
        });
        return result;
    }

    private void assertIndex(RecordItem recordItem, Integer expected) {
        assertThat(jdbcOperations.queryForList(
                        "select transaction_index from contract_result where consensus_timestamp = ?",
                        Integer.class,
                        recordItem.getConsensusTimestamp()))
                .as("contract result index at %d", recordItem.getConsensusTimestamp())
                .containsExactly(expected);
    }

    private void assertLogIndexes(RecordItem recordItem, Integer expected) {
        assertThat(jdbcOperations.queryForList(
                        "select transaction_index from contract_log where consensus_timestamp = ?",
                        Integer.class,
                        recordItem.getConsensusTimestamp()))
                .isNotEmpty()
                .containsOnly(expected);
    }

    // The migration must reproduce exactly what the importer stored
    private void assertMigrationKeepsIndexes() {
        final var before = indexes();
        runMigration();
        waitForCompletion();
        assertThat(indexes()).isEqualTo(before);
    }

    private Map<String, Integer> indexes() {
        final var result = new HashMap<String, Integer>();
        jdbcOperations.query("select consensus_timestamp, transaction_index from contract_result", rs -> {
            result.put("result " + rs.getLong(1), (Integer) rs.getObject(2));
        });
        jdbcOperations.query("select consensus_timestamp, index, transaction_index from contract_log", rs -> {
            result.put("log " + rs.getLong(1) + "/" + rs.getInt(2), (Integer) rs.getObject(3));
        });
        return result;
    }

    private static ContractFunctionResult result(long gasUsed) {
        return ContractFunctionResult.newBuilder()
                .setContractID(ContractID.newBuilder().setContractNum(5000))
                .setGasUsed(gasUsed)
                .build();
    }

    private ContractFunctionResult hookResult(long gasUsed) {
        return result(gasUsed).toBuilder().setContractID(hookContractId).build();
    }

    private static ContractFunctionResult resultWithLog(long gasUsed) {
        return result(gasUsed).toBuilder()
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(5000))
                        .setData(ByteString.copyFromUtf8("log")))
                .build();
    }

    private static ContractFunctionResult precompileResult(long gasUsed) {
        return ContractFunctionResult.newBuilder()
                .setContractID(PRECOMPILE_ID)
                .setGasUsed(gasUsed)
                .build();
    }

    private static Timestamp timestamp(long nanos) {
        return Timestamp.newBuilder()
                .setSeconds(nanos / 1_000_000_000L)
                .setNanos((int) (nanos % 1_000_000_000L))
                .build();
    }
}
