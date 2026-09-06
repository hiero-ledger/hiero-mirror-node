// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.app.blocks.BlockStreamService;
import jakarta.inject.Named;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.Utils;

@Named
final class BlockStreamInfoSingleton implements SingletonState<BlockStreamInfo> {

    /**
     * Fallback used before a record file is bound (executor warmup). A non-zero {@code lastHandleTime} keeps exchange
     * rates from falling back to genesis config.
     */
    private static final BlockStreamInfo FALLBACK = BlockStreamInfo.newBuilder()
            .lastHandleTime(Timestamp.newBuilder().seconds(1).build())
            .build();

    @Override
    public int getStateId() {
        return BLOCK_STREAM_INFO_STATE_ID;
    }

    @Override
    public String getServiceName() {
        return BlockStreamService.NAME;
    }

    @Override
    public BlockStreamInfo get() {
        final var context = ContractCallContext.get();
        final var recordFile = context.getRecordFile();
        if (recordFile == null) {
            return FALLBACK;
        }
        final var blockTime = Utils.convertToTimestamp(recordFile.getConsensusStart());
        final var blockEndTime = Utils.convertToTimestamp(recordFile.getConsensusEnd());
        return BlockStreamInfo.newBuilder()
                .blockNumber(recordFile.getIndex())
                .blockTime(blockTime)
                .lastHandleTime(blockEndTime)
                .blockEndTime(blockEndTime)
                .build();
    }
}
