// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.state.Utils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class BlockInfoSingleton implements SingletonState<BlockInfo> {

    private final RecordFileRepository recordFileRepository;

    @Override
    public String getKey() {
        return BLOCK_INFO_STATE_KEY;
    }

    @Override
    public BlockInfo get() {
        var recordFile = ContractCallContext.get().getRecordFile();
        if (recordFile == null) {
            // during mirror node startup, recordFile is not set in the context
            recordFile = recordFileRepository.findLatest().get();
        }
        var startTimestamp = Utils.convertToTimestamp(recordFile.getConsensusStart());
        var endTimestamp = Utils.convertToTimestamp(recordFile.getConsensusEnd());

        return BlockInfo.newBuilder()
                .blockHashes(Bytes.EMPTY)
                .consTimeOfLastHandledTxn(endTimestamp)
                .firstConsTimeOfCurrentBlock(endTimestamp)
                .firstConsTimeOfLastBlock(startTimestamp)
                .lastBlockNumber(recordFile.getIndex() - 1) // Library internally increments last by one for current
                .migrationRecordsStreamed(true)
                .build();
    }
}
