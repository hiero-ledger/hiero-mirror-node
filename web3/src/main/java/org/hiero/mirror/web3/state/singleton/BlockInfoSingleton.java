// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.ContractCallContext;

@Named
@RequiredArgsConstructor
final class BlockInfoSingleton implements SingletonState<BlockInfo> {

    /**
     * Synthetic block period used to derive the previous block's start, and the end of an overridden block.
     */
    private static final long BLOCK_PERIOD_NANOS = Duration.ofSeconds(2).toNanos();

    @Override
    public int getStateId() {
        return BLOCKS_STATE_ID;
    }

    @Override
    public String getServiceName() {
        return BlockRecordService.NAME;
    }

    @Override
    public BlockInfo get() {
        final var context = ContractCallContext.get();
        final var recordFile = context.getRecordFile();
        final var overrideTimeNanos = context.getEvmBlockOverride().timeNanos();
        final var startNanos = overrideTimeNanos != null ? overrideTimeNanos : recordFile.getConsensusStart();
        final var endNanos =
                overrideTimeNanos != null ? overrideTimeNanos + BLOCK_PERIOD_NANOS : recordFile.getConsensusEnd();

        return BlockInfo.newBuilder()
                .blockHashes(Bytes.EMPTY)
                .consTimeOfLastHandledTxn(convertToTimestamp(endNanos))
                .firstConsTimeOfCurrentBlock(convertToTimestamp(startNanos))
                .firstConsTimeOfLastBlock(convertToTimestamp(startNanos - BLOCK_PERIOD_NANOS))
                .lastBlockNumber(context.evmBlockNumber(recordFile.getIndex())
                        - 1) // Library internally increments last by one for current
                .migrationRecordsStreamed(true)
                .build();
    }
}
