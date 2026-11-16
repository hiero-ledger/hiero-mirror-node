// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.RecordFileService;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.fluent.SimpleBlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({ContextExtension.class, MockitoExtension.class})
class MirrorBlockHashOperationTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private RecordFileService recordFileService;

    @InjectMocks
    private MirrorBlockHashOperation operation;

    @BeforeEach
    void setup() {
        lenient().when(evmProperties.getBlockHashWindow()).thenReturn(256L);
        final var earliest =
                domainBuilder.recordFile().customize(r -> r.index(0L)).get();
        lenient().when(recordFileService.findByBlockType(BlockType.EARLIEST)).thenReturn(Optional.of(earliest));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -155L, Long.MIN_VALUE})
    void negativeSoughtBlock(final long soughtBlock) {
        // Given
        final var blockValues = new SimpleBlockValues();
        blockValues.setNumber(100L);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(soughtBlock));
        given(messageFrame.getBlockValues()).willReturn(blockValues);

        // When
        final var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(UInt256.ZERO);
        verifyNoInteractions(recordFileService);
    }

    @Test
    void beforeEarliest() {
        // Given
        final var earliest =
                domainBuilder.recordFile().customize(r -> r.index(1000L)).get();
        given(recordFileService.findByBlockType(BlockType.EARLIEST)).willReturn(Optional.of(earliest));
        final var blockValues = new SimpleBlockValues();
        blockValues.setNumber(earliest.getIndex() + 10);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(earliest.getIndex() - 1));
        given(messageFrame.getBlockValues()).willReturn(blockValues);

        // When
        final var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(Hash.ZERO.getBytes());
        verify(recordFileService, never()).findByIndex(anyLong());
    }

    @Test
    void missingIndexQueriedOncePerCall() {
        // Given a stale earliest of 900 while retention has already removed everything below 1000
        final var earliest =
                domainBuilder.recordFile().customize(r -> r.index(900L)).get();
        given(recordFileService.findByBlockType(BlockType.EARLIEST)).willReturn(Optional.of(earliest));
        given(recordFileService.findByIndex(999L)).willReturn(Optional.empty());
        final var blockValues = new SimpleBlockValues();
        blockValues.setNumber(1010L);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(999L));
        given(messageFrame.getBlockValues()).willReturn(blockValues);

        // When
        for (int i = 0; i < 3; i++) {
            operation.execute(messageFrame, null);
        }

        // Then
        verify(messageFrame, times(3)).pushStackItem(Hash.ZERO.getBytes());
        verify(recordFileService, times(1)).findByIndex(999L);
    }

    @Test
    void earliestNotFound() {
        // Given
        given(recordFileService.findByBlockType(BlockType.EARLIEST)).willReturn(Optional.empty());
        final var blockValues = new SimpleBlockValues();
        blockValues.setNumber(10L);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(5L));
        given(messageFrame.getBlockValues()).willReturn(blockValues);

        // When
        final var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(Hash.ZERO.getBytes());
        verify(recordFileService, never()).findByIndex(anyLong());
    }

    @Test
    void invalid() {
        // Given
        given(messageFrame.popStackItem()).willReturn(Bytes.of(1, 1, 1, 1, 1, 1, 1, 1, 1));

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void negative() {
        // Given
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(-1L);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(1L));
        given(messageFrame.getBlockValues()).willReturn(blockValues);

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void future() {
        // Given
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(1L);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(blockValues.getNumber() + 1));
        given(messageFrame.getBlockValues()).willReturn(blockValues);

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void current() {
        // Given
        var recordFile = domainBuilder.recordFile().get();
        ContractCallContext.get().setBlockSupplier(() -> recordFile);
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(recordFile.getIndex());
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(recordFile.getIndex()));
        given(messageFrame.getBlockValues()).willReturn(blockValues);

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame)
                .pushStackItem(Hash.fromHexString(recordFile.getHash().substring(0, 64))
                        .getBytes());
    }

    @Test
    void past() {
        // Given
        var recordFile = domainBuilder.recordFile().get();
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(recordFile.getIndex() + 1);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(recordFile.getIndex()));
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(recordFileService.findByIndex(recordFile.getIndex())).willReturn(Optional.of(recordFile));

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame)
                .pushStackItem(Hash.fromHexString(recordFile.getHash().substring(0, 64))
                        .getBytes());
    }

    @ParameterizedTest
    @CsvSource({
        "10000, 256, 1, false",
        "10000, 256, -1, true",
        "10000, 256, -255, true",
        "10000, 256, -256, true",
        "10000, 256, -257, false",
        "256, 256, -256, true",
        "257, 256, -257, false",
        "100, 2, -1, true",
        "100, 2, -2, true",
        "100, 2, -3, false",
        "1, 1, -1, true",
        "100, 256, -100, true"
    })
    void lookbackWindow(long currentBlockNumber, long window, long offset, boolean withinWindow) {
        // Given
        lenient().when(evmProperties.getBlockHashWindow()).thenReturn(window);
        var soughtBlock = currentBlockNumber + offset;
        var recordFile =
                domainBuilder.recordFile().customize(r -> r.index(soughtBlock)).get();
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(currentBlockNumber);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(soughtBlock));
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        if (withinWindow) {
            given(recordFileService.findByIndex(soughtBlock)).willReturn(Optional.of(recordFile));
        }

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        if (withinWindow) {
            verify(recordFileService).findByIndex(soughtBlock);
            verify(messageFrame)
                    .pushStackItem(Hash.fromHexString(recordFile.getHash().substring(0, 64))
                            .getBytes());
        } else {
            verify(messageFrame).pushStackItem(UInt256.ZERO);
            verifyNoInteractions(recordFileService);
        }
    }

    @Test
    void notFound() {
        // Given
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(1);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(0));
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(recordFileService.findByIndex(0)).willReturn(Optional.empty());

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(Hash.ZERO.getBytes());
    }
}
