// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.fluent.SimpleBlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
@ExtendWith(ContextExtension.class)
class MirrorBlockHashOperationTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private RecordFileRepository recordFileRepository;

    @InjectMocks
    private MirrorBlockHashOperation operation;

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
        ContractCallContext.get().setRecordFile(recordFile);
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
                .pushStackItem(Hash.fromHexString(recordFile.getHash().substring(0, 64)));
    }

    @Test
    void past() {
        // Given
        var recordFile = domainBuilder.recordFile().get();
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(recordFile.getIndex() + 1);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(recordFile.getIndex()));
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(recordFileRepository.findByIndex(recordFile.getIndex())).willReturn(Optional.of(recordFile));

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame)
                .pushStackItem(Hash.fromHexString(recordFile.getHash().substring(0, 64)));
    }

    @Test
    void notFound() {
        // Given
        var blockValues = new SimpleBlockValues();
        blockValues.setNumber(1);
        given(messageFrame.popStackItem()).willReturn(Bytes.ofUnsignedLong(0));
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(recordFileRepository.findByIndex(0)).willReturn(Optional.empty());

        // When
        var result = operation.execute(messageFrame, null);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(OperationResult::getHaltReason)
                .isNull();
        verify(messageFrame).pushStackItem(Hash.ZERO);
    }
}
