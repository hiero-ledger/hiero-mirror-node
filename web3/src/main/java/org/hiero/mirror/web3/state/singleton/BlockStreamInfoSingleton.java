// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;

import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.app.blocks.BlockStreamService;
import jakarta.inject.Named;

@Named
final class BlockStreamInfoSingleton implements SingletonState<BlockStreamInfo> {

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
        return BlockStreamInfo.DEFAULT;
    }
}
