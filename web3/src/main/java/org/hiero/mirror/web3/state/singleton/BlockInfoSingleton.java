// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.ContractCallContext;

@Named
@RequiredArgsConstructor
final class BlockInfoSingleton implements SingletonState<BlockInfo> {

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
        final var blockOverride = context.getBlockOverride();
        final var overrideTimeNanos = blockOverride.timeNanos();
        final var overrideBlockNumber = blockOverride.blockNumber();
        final long startNanos = overrideTimeNanos != null ? overrideTimeNanos : recordFile.getConsensusStart();
        // An overridden block keeps the record file's duration
        final long endNanos = startNanos + recordFile.getConsensusEnd() - recordFile.getConsensusStart();
        final long blockNumber = overrideBlockNumber != null ? overrideBlockNumber : recordFile.getIndex();

        return BlockInfo.newBuilder()
                .blockHashes(Bytes.EMPTY)
                .consTimeOfLastHandledTxn(convertToTimestamp(endNanos))
                .firstConsTimeOfCurrentBlock(convertToTimestamp(startNanos))
                .lastBlockNumber(blockNumber - 1) // Library internally increments last by one for current
                .migrationRecordsStreamed(true)
                .build();
    }
}
