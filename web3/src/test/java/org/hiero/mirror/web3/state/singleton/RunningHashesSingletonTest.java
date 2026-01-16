// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.junit.jupiter.api.Test;

class RunningHashesSingletonTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final RunningHashesSingleton runningHashesSingleton = new RunningHashesSingleton();

    @Test
    void get() {
        ContractCallContext.run(context -> {
            var recordFile = domainBuilder.recordFile().get();
            context.setBlockSupplier(() -> recordFile);
            assertThat(runningHashesSingleton.get())
                    .returns(Bytes.EMPTY, RunningHashes::runningHash)
                    .returns(Bytes.EMPTY, RunningHashes::nMinus1RunningHash)
                    .returns(Bytes.EMPTY, RunningHashes::nMinus2RunningHash)
                    .returns(Bytes.fromHex(recordFile.getHash()), RunningHashes::nMinus3RunningHash);
            return null;
        });
    }

    @Test
    void key() {
        assertThat(runningHashesSingleton.getStateId()).isEqualTo(RUNNING_HASHES_STATE_ID);
    }

    @Test
    void set() {
        ContractCallContext.run(context -> {
            var recordFile = domainBuilder.recordFile().get();
            context.setBlockSupplier(() -> recordFile);
            runningHashesSingleton.set(RunningHashes.DEFAULT);
            assertThat(runningHashesSingleton.get()).isNotEqualTo(RunningHashes.DEFAULT);
            return null;
        });
    }
}
