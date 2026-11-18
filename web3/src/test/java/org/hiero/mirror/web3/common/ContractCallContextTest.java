// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
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
    void applyBlockOverrideClearsPreviousWhenUnset() {
        final var context = ContractCallContext.get();
        final var number = new BlockOverride();
        number.setBlockNumber(256L);
        final var time = new BlockOverride();
        time.setTime(1710874816L);
        final var expectedNanos = DomainUtils.convertToNanosMax(1710874816L, 0);

        context.applyBlockOverride(number);
        assertThat(context.getEvmBlockOverride().blockNumber()).isEqualTo(256L);

        context.applyBlockOverride(null);
        assertThat(context.getEvmBlockOverride().blockNumber()).isNull();
        assertThat(context.getEvmBlockOverride().timeNanos()).isNull();
        assertThat(context.evmBlockNumber(1L)).isEqualTo(1L);
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(9L);

        context.applyBlockOverride(number);
        context.applyBlockOverride(time);
        assertThat(context.getEvmBlockOverride().blockNumber()).isNull();
        assertThat(context.getEvmBlockOverride().timeNanos()).isEqualTo(expectedNanos);
        assertThat(context.evmBlockNumber(1L)).isEqualTo(1L);
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(expectedNanos);
    }

    @Test
    void getEvmBlockOverrideSafeReturnsCurrentOverride() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setFeeRecipient("0x0000000000000000000000000000000000000062");
        override.setGasLimit(1L);

        assertThat(ContractCallContext.getEvmBlockOverrideSafe()).isEqualTo(EvmBlockOverride.EMPTY);

        context.applyBlockOverride(override);
        assertThat(ContractCallContext.getEvmBlockOverrideSafe())
                .isSameAs(context.getEvmBlockOverride())
                .isNotEqualTo(EvmBlockOverride.EMPTY);
    }

    @Test
    void getEvmBlockOverrideSafeReturnsEmptyWhenUnbound() {
        // The scoped context is not bound on another thread.
        final var result = CompletableFuture.supplyAsync(ContractCallContext::getEvmBlockOverrideSafe)
                .join();

        assertThat(result).isEqualTo(EvmBlockOverride.EMPTY);
    }

    @Test
    void applyBlockOverrideSetsEveryEvmField() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setBaseFeePerGas(10L);
        override.setBlobBaseFee(2L);
        override.setBlockNumber(80L);
        override.setFeeRecipient("0x0000000000000000000000000000000000000062");
        override.setGasLimit(15000000L);
        override.setPrevRandao(123456789L);
        override.setTime(1793642747L);

        context.applyBlockOverride(override);

        final var evm = context.getEvmBlockOverride();
        assertThat(evm.baseFee()).isEqualTo(Wei.of(10L));
        assertThat(evm.blobBaseFee()).isEqualTo(Wei.of(2L));
        assertThat(evm.feeRecipient()).isEqualTo(Address.fromHexString("0x0000000000000000000000000000000000000062"));
        assertThat(evm.gasLimit()).isEqualTo(15_000_000L);
        assertThat(evm.prevRandao()).isEqualTo(Bytes32.leftPad(Bytes.ofUnsignedLong(123_456_789L)));
        assertThat(context.evmBlockNumber(1L)).isEqualTo(80L);
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(DomainUtils.convertToNanosMax(1_793_642_747L, 0L));

        context.applyBlockOverride(null);
        assertThat(context.getEvmBlockOverride()).isEqualTo(EvmBlockOverride.EMPTY);
    }

    @Test
    void applyBlockOverrideNumberIsUsedInContext() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setBlockNumber(256L);

        context.applyBlockOverride(override);

        assertThat(context.getEvmBlockOverride().blockNumber()).isEqualTo(256L);
        assertThat(context.getEvmBlockOverride().timeNanos()).isNull();
        assertThat(context.evmBlockNumber(1L)).isEqualTo(256L);
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(9L);
    }

    @Test
    void applyBlockOverrideNumberAndTimeAreUsedTogether() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setBlockNumber(256L);
        override.setTime(1710874816L);

        context.applyBlockOverride(override);

        final var expectedNanos = DomainUtils.convertToNanosMax(1710874816L, 0);
        assertThat(context.getEvmBlockOverride().blockNumber()).isEqualTo(256L);
        assertThat(context.getEvmBlockOverride().timeNanos()).isEqualTo(expectedNanos);
        assertThat(context.evmBlockNumber(1L)).isEqualTo(256L);
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(expectedNanos);
    }

    @Test
    void applyBlockOverrideTimeIsUsedInContext() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setTime(1710874816L);

        context.applyBlockOverride(override);

        final var expectedNanos = DomainUtils.convertToNanosMax(1710874816L, 0);
        assertThat(context.getEvmBlockOverride().timeNanos()).isEqualTo(expectedNanos);
        assertThat(context.getEvmBlockOverride().blockNumber()).isNull();
        assertThat(context.evmBlockTimeNanos(9L)).isEqualTo(expectedNanos);
        assertThat(context.evmBlockNumber(1L)).isEqualTo(1L);
    }

    @Test
    void applyBlockOverrideRejectsNegativeValue() {
        final var context = ContractCallContext.get();
        final var override = new BlockOverride();
        override.setBlockNumber(-1L);

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
