// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.service.RecordFileService;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.viewmodel.BlockType;

@Named
@RequiredArgsConstructor
public class BlockInfoSingleton implements SingletonState<BlockInfo> {

    @Override
    public Integer getId() {
        return BLOCKS_STATE_ID;
    }

    private final RecordFileService recordFileService;

    @Override
    public BlockInfo get() {
        var recordFile = ContractCallContext.get().getRecordFile();
        if (recordFile == null) {
            recordFile =
                    recordFileService.findByBlockType(BlockType.LATEST).orElseThrow(BlockNumberNotFoundException::new);
        }

        final var startTimestamp = Utils.convertToTimestamp(recordFile.getConsensusStart());
        final var endTimestamp = Utils.convertToTimestamp(recordFile.getConsensusEnd());

        return BlockInfo.newBuilder()
                .blockHashes(Bytes.EMPTY)
                .consTimeOfLastHandledTxn(endTimestamp)
                .firstConsTimeOfCurrentBlock(startTimestamp)
                .firstConsTimeOfLastBlock(startTimestamp)
                .lastBlockNumber(recordFile.getIndex() - 1) // Library internally increments last by one for current
                .migrationRecordsStreamed(true)
                .build();
    }
}
