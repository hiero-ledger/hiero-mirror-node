// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.service.OpcodeServiceImpl.EXECUTED_OPCODES_METRIC;
import static org.hiero.mirror.web3.service.OpcodeServiceImpl.MEMORY_WORDS_METRIC;
import static org.hiero.mirror.web3.service.OpcodeServiceImpl.STACK_METRIC;
import static org.hiero.mirror.web3.service.OpcodeServiceImpl.STORAGE_METRIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TraceMemoryBudget;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.junit.jupiter.api.Test;

final class OpcodeServiceImplTest {

    private static final DomainBuilder DOMAIN_BUILDER = new DomainBuilder();

    @Test
    void metricsAndBudgetAreRecordedEvenWhenTraceFails() {
        // Given
        final var transaction = DOMAIN_BUILDER.transaction().get();
        final var contractResult = DOMAIN_BUILDER.contractResult().get();
        final var transactionRepository = mock(TransactionRepository.class);
        when(transactionRepository.findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(any(), anyLong()))
                .thenReturn(List.of(transaction));
        final var contractResultRepository = mock(ContractResultRepository.class);
        when(contractResultRepository.findById(transaction.getConsensusTimestamp()))
                .thenReturn(Optional.of(contractResult));
        final var recordFileService = mock(RecordFileService.class);
        when(recordFileService.findByTimestamp(transaction.getConsensusTimestamp()))
                .thenReturn(Optional.empty());
        final var ethereumTransactionRepository = mock(EthereumTransactionRepository.class);
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(anyLong(), any()))
                .thenReturn(Optional.empty());
        final var contractTransactionHashRepository = mock(ContractTransactionHashRepository.class);
        final var commonEntityAccessor = mock(CommonEntityAccessor.class);

        final var meterRegistry = new SimpleMeterRegistry();
        final var opcodesProperties = new OpcodesProperties();
        final var traceMemoryBudget = new TraceMemoryBudget(opcodesProperties);
        final var contractDebugService = mock(ContractDebugService.class);
        when(contractDebugService.processOpcodeCall(any(), any())).thenAnswer(invocation -> {
            final OpcodeContext opcodeContext = invocation.getArgument(1);
            opcodeContext.addOpcodes(new Opcode()
                    .memory(Collections.nCopies(3, "0x00"))
                    .stack(Collections.nCopies(2, "0x00"))
                    .storage(Map.of("key", "value")));
            throw new RuntimeException("Simulated trace failure");
        });

        final var opcodeService = new OpcodeServiceImpl(
                recordFileService,
                contractDebugService,
                contractTransactionHashRepository,
                ethereumTransactionRepository,
                transactionRepository,
                contractResultRepository,
                commonEntityAccessor,
                opcodesProperties,
                traceMemoryBudget,
                meterRegistry);
        opcodeService.init();

        final var request = new OpcodeRequest(
                new TransactionIdParameter(
                        transaction.getPayerAccountId(), Instant.ofEpochSecond(0, transaction.getValidStartNs())),
                true,
                true,
                true);

        // When
        assertThatThrownBy(() -> opcodeService.processOpcodeCall(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated trace failure");

        // Then
        assertThat(meterRegistry.get(EXECUTED_OPCODES_METRIC).counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MEMORY_WORDS_METRIC).counter().count()).isEqualTo(3.0);
        assertThat(meterRegistry.get(STACK_METRIC).counter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.get(STORAGE_METRIC).counter().count()).isEqualTo(1.0);
    }
}
