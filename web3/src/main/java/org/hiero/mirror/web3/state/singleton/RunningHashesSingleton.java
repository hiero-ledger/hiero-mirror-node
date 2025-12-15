// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import lombok.AllArgsConstructor;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.service.RecordFileService;
import org.hiero.mirror.web3.viewmodel.BlockType;

@Named
@AllArgsConstructor
public class RunningHashesSingleton implements SingletonState<RunningHashes> {

    private RecordFileService recordFileService;

    @Override
    public Integer getId() {
        return RUNNING_HASHES_STATE_ID;
    }

    @Override
    public RunningHashes get() {
        final var recordFile = ContractCallContext.get().resolveRecordFile(() -> recordFileService
                .findByBlockType(BlockType.LATEST)
                .orElseThrow(BlockNumberNotFoundException::new));
        return RunningHashes.newBuilder()
                .runningHash(Bytes.EMPTY)
                .nMinus1RunningHash(Bytes.EMPTY)
                .nMinus2RunningHash(Bytes.EMPTY)
                .nMinus3RunningHash(Bytes.fromHex(recordFile.getHash())) // Used by prevrandao
                .build();
    }
}
