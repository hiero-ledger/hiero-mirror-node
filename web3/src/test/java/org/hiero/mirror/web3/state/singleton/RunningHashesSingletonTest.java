// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.RecordFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunningHashesSingletonTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private RecordFileService recordFileService;

    @InjectMocks
    private RunningHashesSingleton runningHashesSingleton;

    @Test
    void get() {
        ContractCallContext.run(context -> {
            var recordFile = domainBuilder.recordFile().get();
            context.setRecordFile(recordFile);
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
        assertThat(runningHashesSingleton.getId()).isEqualTo(RUNNING_HASHES_STATE_ID);
    }

    @Test
    void set() {
        ContractCallContext.run(context -> {
            var recordFile = domainBuilder.recordFile().get();
            context.setRecordFile(recordFile);
            runningHashesSingleton.set(RunningHashes.DEFAULT);
            assertThat(runningHashesSingleton.get()).isNotEqualTo(RunningHashes.DEFAULT);
            return null;
        });
    }
}
