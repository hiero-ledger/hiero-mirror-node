// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import org.hiero.mirror.web3.common.ContractCallContext;

@Named
final class RunningHashesSingleton implements SingletonState<RunningHashes> {

    @Override
    public int getStateId() {
        return RUNNING_HASHES_STATE_ID;
    }

    @Override
    public String getServiceName() {
        return BlockRecordService.NAME;
    }

    @Override
    public RunningHashes get() {
        final var recordFile = ContractCallContext.get().getRecordFile();
        return RunningHashes.newBuilder()
                .nMinus1RunningHash(Bytes.EMPTY)
                .runningHash(Bytes.EMPTY)
                .nMinus2RunningHash(Bytes.EMPTY)
                .nMinus3RunningHash(Bytes.fromHex(recordFile.getHash())) // Used by prevrandao
                .build();
    }
}
