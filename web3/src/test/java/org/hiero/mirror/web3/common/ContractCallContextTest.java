// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.utils.HexUtils;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class ContractCallContextTest {

    @Test
    void testGet() {
        var context = ContractCallContext.get();
        assertThat(ContractCallContext.get()).isEqualTo(context);
    }

    @Test
    void testReset() {
        var context = ContractCallContext.get();
        context.setBlockSupplier(() -> RecordFile.builder().consensusEnd(123L).build());
        context.reset();
    }

    @Test
    void testGetTimestampNonHistorical() {
        var context = ContractCallContext.get();
        context.setTimestamp(Optional.of(123L));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEmpty();
        assertThat(context.getTimestampForSystemFiles()).isEmpty();
    }

    @Test
    void testGetTimestampOpcodeReplay() {
        var context = ContractCallContext.get();
        var previousBlockTimestamp = 122L;
        var consensusTimestamp = 123L;
        context.setTimestamp(Optional.of(previousBlockTimestamp));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEmpty();
        assertThat(context.getTimestampForSystemFiles()).isEmpty();

        var opcodeContext = new OpcodeContext(
                new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false),
                0,
                new OpcodesProperties());
        context.setOpcodeContext(opcodeContext);

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(previousBlockTimestamp));
        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(consensusTimestamp));
    }

    @Test
    void testGetTimestampHistorical() {
        var context = ContractCallContext.get();
        var timestamp = 123L;
        context.setTimestamp(Optional.of(timestamp));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.EARLIEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(timestamp));
        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(timestamp + 1));
    }

    @Test
    void testGetTimestampForSystemFilesFallsBackToRecordFileWhenNotOpcodeReplay() {
        var context = ContractCallContext.get();
        var consensusEnd = 1_786_518_658_483_854_104L;
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.of("39156482"))
                .callData(new byte[0])
                .gasPrice(0L)
                .build());
        context.setBlockSupplier(() ->
                RecordFile.builder().consensusEnd(consensusEnd).index(39156482L).build());

        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(consensusEnd + 1));
    }

    @Test
    void remainingMillisUsesDeadlineWhenSet() {
        var context = ContractCallContext.get();
        context.setDeadlineMillis(context.getStartTime() + 1_000);

        assertThat(context.remainingMillis(4_000)).isBetween(0L, 1_000L);
        assertThat(context.isDeadlineExceeded()).isFalse();
    }

    @Test
    void remainingMillisFallsBackWhenDeadlineUnset() {
        var context = ContractCallContext.get();

        assertThat(context.remainingMillis(4_000)).isBetween(0L, 4_000L);
        assertThat(context.isDeadlineExceeded()).isFalse();
    }

    @Test
    void remainingMillisNegativeWhenDeadlinePassed() {
        var context = ContractCallContext.get();
        context.setDeadlineMillis(context.getStartTime() - 1);

        assertThat(context.remainingMillis(4_000)).isNegative();
        assertThat(context.isDeadlineExceeded()).isTrue();
    }

    @Test
    void applyBlockOverrideNumberIsUsedInContext() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setNumber("0x100");

        context.applyBlockOverride(override);

        assertThat(context.getBlockOverrideNumber()).isEqualTo(256L);
        assertThat(context.getBlockOverrideTimeNanos()).isNull();
        assertThat(context.evmBlockNumber(1L)).isEqualTo(256L);
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(9L);
    }

    @Test
    void applyBlockOverrideTimeIsUsedInContext() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setTime("0x65f9e0c0");

        context.applyBlockOverride(override);

        final var expectedNanos = DomainUtils.convertToNanosMax(HexUtils.parseValue("0x65f9e0c0"), 0);
        assertThat(context.getBlockOverrideTimeNanos()).isEqualTo(expectedNanos);
        assertThat(context.getBlockOverrideNumber()).isNull();
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(expectedNanos);
        assertThat(context.evmBlockNumber(1L)).isEqualTo(1L);
    }

    @Test
    void applyBlockOverrideRejectsInvalidQuantity() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setNumber("0xzz");

        assertThatThrownBy(() -> context.applyBlockOverride(override))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessageContaining("Invalid block_override");
    }

    @Test
    void evmBlockHelpersUseFallbackWhenOverrideUnset() {
        final var context = ContractCallContext.get();

        assertThat(context.evmBlockNumber(42L)).isEqualTo(42L);
        assertThat(context.evmBlockTimeNanos(99L)).isEqualTo(99L);
    }
}
