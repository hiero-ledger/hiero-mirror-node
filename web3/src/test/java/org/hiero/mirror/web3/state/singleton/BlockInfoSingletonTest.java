// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class BlockInfoSingletonTest {

    private final BlockInfoSingleton blockInfoSingleton = new BlockInfoSingleton();
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final RecordFile recordFile = domainBuilder.recordFile().get();

    @Test
    void get() {
        ContractCallContext.get().setBlockSupplier(() -> recordFile);
        assertThat(blockInfoSingleton.get())
                .isEqualTo(BlockInfo.newBuilder()
                        .blockHashes(Bytes.EMPTY)
                        .consTimeOfLastHandledTxn(convertToTimestamp(recordFile.getConsensusEnd()))
                        .firstConsTimeOfCurrentBlock(convertToTimestamp(recordFile.getConsensusStart()))
                        .firstConsTimeOfLastBlock(convertToTimestamp(recordFile.getConsensusStart()))
                        .lastBlockNumber(recordFile.getIndex() - 1)
                        .migrationRecordsStreamed(true)
                        .build());
    }

    @Test
    void key() {
        assertThat(blockInfoSingleton.getId()).isEqualTo(BLOCKS_STATE_ID);
    }

    @Test
    void set() {
        ContractCallContext.get().setBlockSupplier(() -> recordFile);
        blockInfoSingleton.set(BlockInfo.DEFAULT);
        assertThat(blockInfoSingleton.get()).isNotEqualTo(BlockInfo.DEFAULT);
    }
}
