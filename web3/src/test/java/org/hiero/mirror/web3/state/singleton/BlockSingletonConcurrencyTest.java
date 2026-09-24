// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.AbstractConcurrencyTest;
import org.hiero.mirror.web3.state.core.FunctionWritableSingletonState;
import org.junit.jupiter.api.Test;

class BlockSingletonConcurrencyTest extends AbstractConcurrencyTest {

    @Test
    void latestObservesLatestRecordFileWhileHistoricalCallInFlight() throws Exception {
        final var domainBuilder = new DomainBuilder();
        final var latest = domainBuilder
                .recordFile()
                .customize(r -> r.index(100L)
                        .consensusStart(2_000_000_000L)
                        .consensusEnd(2_000_000_100L)
                        .hash(domainBuilder.hash(96)))
                .get();
        final var historical = domainBuilder
                .recordFile()
                .customize(r -> r.index(10L)
                        .consensusStart(1_000_000_000L)
                        .consensusEnd(1_000_000_100L)
                        .hash(domainBuilder.hash(96)))
                .get();

        final var blockInfoState = new FunctionWritableSingletonState<>(
                BlockRecordService.NAME, BLOCKS_STATE_ID, new BlockInfoSingleton());
        final var runningHashesState = new FunctionWritableSingletonState<>(
                BlockRecordService.NAME, RUNNING_HASHES_STATE_ID, new RunningHashesSingleton());

        final var historicalReady = new CountDownLatch(1);
        final var latestDone = new CountDownLatch(1);
        final var latestBlockInfo = new AtomicReference<BlockInfo>();
        final var latestHashes = new AtomicReference<RunningHashes>();
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var historicalFuture = executor.submit(() -> ContractCallContext.run(ctx -> {
                ctx.setBlockSupplier(() -> historical);
                // Cache the historical values
                blockInfoState.get();
                runningHashesState.get();
                blockInfoState.put(BlockInfo.newBuilder()
                        .blockHashes(Bytes.EMPTY)
                        .consTimeOfLastHandledTxn(convertToTimestamp(historical.getConsensusEnd()))
                        .firstConsTimeOfCurrentBlock(convertToTimestamp(historical.getConsensusStart()))
                        .firstConsTimeOfLastBlock(convertToTimestamp(historical.getConsensusStart()))
                        .lastBlockNumber(999_999L)
                        .migrationRecordsStreamed(true)
                        .build());
                runningHashesState.put(RunningHashes.newBuilder()
                        .nMinus3RunningHash(Bytes.fromHex("aa".repeat(48)))
                        .build());
                historicalReady.countDown();
                await(latestDone);
                return null;
            }));
            final var latestFuture = executor.submit(() -> ContractCallContext.run(ctx -> {
                await(historicalReady);
                ctx.setBlockSupplier(() -> latest);
                latestBlockInfo.set(blockInfoState.get());
                latestHashes.set(runningHashesState.get());
                latestDone.countDown();
                return null;
            }));
            historicalFuture.get(5, TimeUnit.SECONDS);
            latestFuture.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(latestBlockInfo.get())
                .returns(latest.getIndex() - 1, BlockInfo::lastBlockNumber)
                .returns(convertToTimestamp(latest.getConsensusEnd()), BlockInfo::consTimeOfLastHandledTxn)
                .returns(convertToTimestamp(latest.getConsensusStart()), BlockInfo::firstConsTimeOfCurrentBlock);
        assertThat(latestHashes.get().nMinus3RunningHash()).isEqualTo(Bytes.fromHex(latest.getHash()));
    }
}
