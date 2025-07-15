// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_KEY;

import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import jakarta.inject.Named;

@Named
final class BlockStreamInfoSingleton implements SingletonState<com.hedera.hapi.node.state.blockstream.BlockStreamInfo> {

    @Override
    public String getKey() {
        return BLOCK_STREAM_INFO_KEY;
    }

    @Override
    public BlockStreamInfo get() {
        return BlockStreamInfo.DEFAULT;
    }
}
