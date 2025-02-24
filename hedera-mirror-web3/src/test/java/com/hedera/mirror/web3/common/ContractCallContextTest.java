// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.utils.BareDatabaseAccessor;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class ContractCallContextTest {

    private final StackedStateFrames stackedStateFrames = new StackedStateFrames(
            List.of(new BareDatabaseAccessor<Object, Character>() {}, new BareDatabaseAccessor<Object, String>() {}));

    @Test
    void testGet() {
        var context = ContractCallContext.get();
        assertThat(ContractCallContext.get()).isEqualTo(context);
    }

    @Test
    void testReset() {
        var context = ContractCallContext.get();
        context.setRecordFile(RecordFile.builder().consensusEnd(123L).build());
        context.initializeStackFrames(stackedStateFrames);
        stackedStateFrames.push();
        context.setStack(stackedStateFrames.top());

        context.reset();
        assertThat(context.getStack()).isEqualTo(context.getStackBase());
    }

    @Test
    void testGetTimestampNonHistorical() {
        var context = ContractCallContext.get();
        context.setTimestamp(Optional.of(123L));
        context.setCallServiceParameters(
                ContractExecutionParameters.builder().block(BlockType.LATEST).build());

        assertThat(context.getTimestamp()).isEmpty();
    }

    @Test
    void testGetTimestampHistorical() {
        var context = ContractCallContext.get();
        var timestamp = 123L;
        context.setTimestamp(Optional.of(timestamp));
        context.setCallServiceParameters(
                ContractExecutionParameters.builder().block(BlockType.EARLIEST).build());

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(timestamp));
    }
}
