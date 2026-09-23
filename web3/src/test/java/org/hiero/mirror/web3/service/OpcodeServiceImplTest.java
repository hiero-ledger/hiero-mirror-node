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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TraceMemoryBudget;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.ContractTransactionRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.repository.projections.ContractTransactionHashLookup;
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
        final var contractTransactionRepository = mock(ContractTransactionRepository.class);
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
                contractTransactionRepository,
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

    @Test
    void resolvesHashToGenuineExecutionOverLaterStub() {
        // Given two results sharing a hash: a genuine execution at T1 (has a contract_transaction row) and a later
        // pre-execution stub at T2 (entity 0, no contract_transaction row). The repository returns them latest-first.
        final var hash = DOMAIN_BUILDER.bytes(32);
        final long contractId = DOMAIN_BUILDER.entityId().getId();
        final var executed = lookup(1L, contractId, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED_VALUE);
        final var stub = lookup(2L, 0L, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE);

        final var hashRepository = mock(ContractTransactionHashRepository.class);
        when(hashRepository.findAllByHash(hash)).thenReturn(List.of(stub, executed));
        final var transactionRepository = mock(ContractTransactionRepository.class);
        when(transactionRepository.existsById(new ContractTransaction.Id(1L, contractId)))
                .thenReturn(true);

        // The genuine execution at T1 is preferred over the later stub at T2.
        assertResolvesToTimestamp(hashRepository, transactionRepository, hash, 1L);
    }

    @Test
    void resolvesHashToFailedContractCreateOverLaterStubWithSameEntity() {
        // The reviewer's original concern: a failed contract create executed (its constructor reverted) so it has a
        // contract_transaction row, but its entity id is 0 because no contract was created. It must still be preferred
        // over a later stub that also has entity 0 - the executed check must match on entity 0, not skip it.
        final var hash = DOMAIN_BUILDER.bytes(32);
        final var failedCreate = lookup(1L, 0L, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED_VALUE);
        final var stub = lookup(2L, 0L, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE);

        final var hashRepository = mock(ContractTransactionHashRepository.class);
        when(hashRepository.findAllByHash(hash)).thenReturn(List.of(stub, failedCreate));
        final var transactionRepository = mock(ContractTransactionRepository.class);
        when(transactionRepository.existsById(new ContractTransaction.Id(1L, 0L)))
                .thenReturn(true);

        assertResolvesToTimestamp(hashRepository, transactionRepository, hash, 1L);
    }

    @Test
    void resolvesHashToSuccessWithoutCheckingExecution() {
        // A successful result always wins (the repository sorts it first), so the executed check is skipped entirely.
        final var hash = DOMAIN_BUILDER.bytes(32);
        final long contractId = DOMAIN_BUILDER.entityId().getId();
        final var success = lookup(1L, contractId, ResponseCodeEnum.SUCCESS_VALUE);
        final var laterRevert = lookup(2L, contractId, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED_VALUE);

        final var hashRepository = mock(ContractTransactionHashRepository.class);
        when(hashRepository.findAllByHash(hash)).thenReturn(List.of(success, laterRevert));
        final var transactionRepository = mock(ContractTransactionRepository.class);

        assertResolvesToTimestamp(hashRepository, transactionRepository, hash, 1L);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void resolvesHashToLatestWhenOnlyPreExecutionFailuresShareHash() {
        // Two attempts of the same eth transaction that never executed (INSUFFICIENT_GAS at T1,
        // DUPLICATE_TRANSACTION at T2), neither with a contract_transaction row. With no genuine execution to prefer,
        // resolution must still return one - the latest - rather than nothing.
        final var hash = DOMAIN_BUILDER.bytes(32);
        final var insufficientGas = lookup(1L, 0L, ResponseCodeEnum.INSUFFICIENT_GAS_VALUE);
        final var duplicate = lookup(2L, 0L, ResponseCodeEnum.DUPLICATE_TRANSACTION_VALUE);

        final var hashRepository = mock(ContractTransactionHashRepository.class);
        // The repository sorts non-successful results latest-first.
        when(hashRepository.findAllByHash(hash)).thenReturn(List.of(duplicate, insufficientGas));
        final var transactionRepository = mock(ContractTransactionRepository.class);

        // No contract_transaction row exists for either candidate, so the latest (T2) is chosen.
        assertResolvesToTimestamp(hashRepository, transactionRepository, hash, 2L);
    }

    @Test
    void resolvesHashWhenTopCandidateHasNullTransactionResult() {
        final var hash = DOMAIN_BUILDER.bytes(32);
        final var nullResult = new ContractTransactionHashLookupRecord(
                2L, 0L, DOMAIN_BUILDER.entityId().getId(), null);
        final var other = lookup(1L, 0L, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE);

        final var hashRepository = mock(ContractTransactionHashRepository.class);
        when(hashRepository.findAllByHash(hash)).thenReturn(List.of(nullResult, other));
        final var transactionRepository = mock(ContractTransactionRepository.class);

        // Null is treated as non-successful, and with nothing executed the latest (T2) is returned.
        assertResolvesToTimestamp(hashRepository, transactionRepository, hash, 2L);
    }

    private ContractTransactionHashLookupRecord lookup(
            final long consensusTimestamp, final long entityId, final int transactionResult) {
        return new ContractTransactionHashLookupRecord(
                consensusTimestamp, entityId, DOMAIN_BUILDER.entityId().getId(), transactionResult);
    }

    // Drives the hash path and asserts which candidate was chosen: its consensus timestamp surfaces via the (empty)
    // contract result lookup, which throws "Contract result not found: <timestamp>".
    private static void assertResolvesToTimestamp(
            final ContractTransactionHashRepository hashRepository,
            final ContractTransactionRepository transactionRepository,
            final byte[] hash,
            final long expectedTimestamp) {
        final var opcodeService = new OpcodeServiceImpl(
                mock(RecordFileService.class),
                mock(ContractDebugService.class),
                hashRepository,
                transactionRepository,
                mock(EthereumTransactionRepository.class),
                mock(TransactionRepository.class),
                mock(ContractResultRepository.class),
                mock(CommonEntityAccessor.class),
                new OpcodesProperties(),
                new TraceMemoryBudget(new OpcodesProperties()),
                new SimpleMeterRegistry());
        opcodeService.init();

        final var request = new OpcodeRequest(new TransactionHashParameter(Bytes.of(hash)), true, true, true);

        assertThatThrownBy(() -> opcodeService.processOpcodeCall(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Contract result not found: " + expectedTimestamp);
    }

    private record ContractTransactionHashLookupRecord(
            long consensusTimestamp, long entityId, long payerAccountId, Integer transactionResult)
            implements ContractTransactionHashLookup {
        @Override
        public long getConsensusTimestamp() {
            return consensusTimestamp;
        }

        @Override
        public long getEntityId() {
            return entityId;
        }

        @Override
        public long getPayerAccountId() {
            return payerAccountId;
        }

        @Override
        public Integer getTransactionResult() {
            return transactionResult;
        }
    }
}
