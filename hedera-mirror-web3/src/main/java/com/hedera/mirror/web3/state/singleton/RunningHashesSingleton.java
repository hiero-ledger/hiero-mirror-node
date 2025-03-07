// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class RunningHashesSingleton implements SingletonState<RunningHashes> {

    private final RecordFileRepository recordFileRepository;

    @Override
    public String getKey() {
        return RUNNING_HASHES_STATE_KEY;
    }

    @Override
    public RunningHashes get() {
        var recordFile = ContractCallContext.get().getRecordFile();
        if (recordFile == null) {
            // during mirror node startup, recordFile is not set in the context
            recordFile = recordFileRepository.findLatest().get();
        }
        return RunningHashes.newBuilder()
                .runningHash(Bytes.EMPTY)
                .nMinus1RunningHash(Bytes.EMPTY)
                .nMinus2RunningHash(Bytes.EMPTY)
                .nMinus3RunningHash(Bytes.fromHex(recordFile.getHash())) // Used by prevrandao
                .build();
    }
}
