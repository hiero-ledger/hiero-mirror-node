// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.app.blocks.BlockStreamService;
import jakarta.inject.Named;

@Named
final class BlockStreamInfoSingleton implements SingletonState<BlockStreamInfo> {

    private final BlockStreamInfo cachedBlockStreamInfo;

    public BlockStreamInfoSingleton() {
        // We just need a non-zero value for seconds so that the exchange rates will be loaded from the DB
        // and not from the default configuration file as this can cause differences in the gas estimations.
        this.cachedBlockStreamInfo = BlockStreamInfo.newBuilder()
                .lastHandleTime(Timestamp.newBuilder().seconds(1).build())
                .build();
    }

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
        return cachedBlockStreamInfo;
    }
}
