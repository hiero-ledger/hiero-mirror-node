// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractStateChangeRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.repository.projections.ContractBytecodeSnapshot;
import org.hiero.mirror.web3.repository.projections.EntitySnapshot;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrestateServiceTest {

    private static final EntityId PAYER_ACCOUNT_ID = EntityId.of(0L, 0L, 1001L);
    private static final EntityId ACCOUNT_ID = EntityId.of(0L, 0L, 100L);
    private static final EntityId CONTRACT_ID = EntityId.of(0L, 0L, 200L);
    private static final EntityId BYTECODE_CONTRACT_ID = EntityId.of(0L, 0L, 300L);
    private static final long CONSENSUS_TIMESTAMP = 1_000_000_000L;
    private static final long VALID_START_NS = 999_999_000L;
    private static final byte[] TRANSACTION_HASH = new byte[] {0x01, 0x02, 0x03, 0x04};

    private PrestateServiceImpl prestateService;

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private ContractActionRepository contractActionRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractResultRepository contractResultRepository;

    @Mock
    private ContractStateChangeRepository contractStateChangeRepository;

    @Mock
    private ContractTransactionHashRepository contractTransactionHashRepository;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        final var commonProperties = new CommonProperties();
        commonProperties.setShard(0L);
        commonProperties.setRealm(0L);
        final var systemEntity = new SystemEntity(commonProperties);
        prestateService = new PrestateServiceImpl(
                accountBalanceRepository,
                contractActionRepository,
                contractRepository,
                contractResultRepository,
                contractStateChangeRepository,
                contractTransactionHashRepository,
                entityRepository,
                systemEntity,
                transactionRepository);
        lenient().when(contractRepository.findByConsensusTimestamp(anyLong())).thenReturn(Collections.emptyList());
    }

    @Test
    void callWithDiffEnabledReturnsBothPreAndPost() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupSidecars();

        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(entitySnapshot(ACCOUNT_ID, 1L, 100L)));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP)))
                .thenReturn(List.of(entitySnapshot(ACCOUNT_ID, 2L, 100L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(100L));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPost().getFirst().getNonce()).isEqualTo(2L);
    }

    @Test
    void callWithDiffDisabledReturnsOnlyPre() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupSidecars();
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(entitySnapshot(ACCOUNT_ID, 1L, 100L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(100L));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).isNullOrEmpty();
    }

    @Test
    void callWithDiffEnabledExcludesUnchangedEntries() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupSidecars();

        final var unchangedSnapshot = entitySnapshot(ACCOUNT_ID, 5L, 100L);
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), anyLong()))
                .thenReturn(List.of(unchangedSnapshot));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(100L));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isEmpty();
        assertThat(response.getPost()).isEmpty();
    }

    @Test
    void callWithCodeEnabled() {
        final var request = createRequest(false, true, false);
        setupTransactionHashLookup();
        setupContractResultWithContract();
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(contractAction(CONTRACT_ID, ACCOUNT_ID)));
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(contractSnapshot(CONTRACT_ID, 1L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(50L));
        when(contractRepository.findRuntimeBytecodesByIds(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(bytecodeSnapshot(CONTRACT_ID.getId(), new byte[] {0x60, 0x40})));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode())
                .isEqualTo(Bytes.wrap(new byte[] {0x60, 0x40}).toHex());
    }

    @Test
    void callWithStorageEnabled() {
        final var request = createRequest(false, false, true);
        setupTransactionHashLookup();
        setupContractResultWithContract();
        final var slot = new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        };
        final var valueRead = new byte[] {0x14};
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(ContractStateChange.builder()
                        .consensusTimestamp(CONSENSUS_TIMESTAMP)
                        .contractId(CONTRACT_ID.getId())
                        .slot(slot)
                        .valueRead(valueRead)
                        .build()));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(contractSnapshot(CONTRACT_ID, 1L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(50L));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getStorage())
                .containsEntry(Bytes.wrap(slot).toHex(), Bytes.wrap(valueRead).toHex());
    }

    @Test
    void callWithDiffAndStorageEnabledPopulatesPreAndPostStorageFromStateChanges() {
        final var request = createRequest(true, false, true);
        setupTransactionHashLookup();
        setupContractResultWithContract();
        final var slot = new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        };
        final var valueRead = new byte[] {0x14};
        final var valueWritten = new byte[] {0x28};
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(ContractStateChange.builder()
                        .consensusTimestamp(CONSENSUS_TIMESTAMP)
                        .contractId(CONTRACT_ID.getId())
                        .slot(slot)
                        .valueRead(valueRead)
                        .valueWritten(valueWritten)
                        .build()));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(contractSnapshot(CONTRACT_ID, 1L)));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP)))
                .thenReturn(List.of(contractSnapshot(CONTRACT_ID, 2L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(50L));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getStorage())
                .containsEntry(Bytes.wrap(slot).toHex(), Bytes.wrap(valueRead).toHex());
        assertThat(response.getPost().getFirst().getStorage())
                .containsEntry(
                        Bytes.wrap(slot).toHex(), Bytes.wrap(valueWritten).toHex());
    }

    @Test
    void callWithContractTransactionHashNotFound() {
        final var request = new PrestateRequest(
                new TransactionHashParameter(org.apache.tuweni.bytes.Bytes.of(TRANSACTION_HASH)), false, false, false);
        when(contractTransactionHashRepository.findByHash(TRANSACTION_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prestateService.processPrestateCall(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Contract transaction hash not found");
    }

    @Test
    void callWithContractResultNotFound() {
        setupTransactionHashLookup();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prestateService.processPrestateCall(createRequest(false, false, false)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Contract result not found");
    }

    @Test
    void callWithDiffEnabledIncludesOnlyChangedEntriesInPreAndPost() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();

        final var changedAccount = EntityId.of(0L, 0L, 100L);
        final var unchangedAccount = EntityId.of(0L, 0L, 101L);
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(
                        contractAction(CONTRACT_ID, changedAccount), contractAction(CONTRACT_ID, unchangedAccount)));
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());

        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(
                        List.of(entitySnapshot(changedAccount, 1L, 100L), entitySnapshot(unchangedAccount, 2L, 200L)));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP)))
                .thenReturn(List.of(
                        entitySnapshot(changedAccount, 100L, 100L), entitySnapshot(unchangedAccount, 2L, 200L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    final long accountId = invocation.getArgument(0);
                    return Optional.of(accountId == changedAccount.getId() ? 100L : 200L);
                });

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(changedAccount.toString());
        assertThat(response.getPost().getFirst().getNonce()).isEqualTo(100L);
    }

    @Test
    void callIncludesContractsFromBytecodeSidecarAtConsensusTimestamp() {
        final byte[] runtimeBytecode = new byte[] {0x60, 0x40};
        final var request = createRequest(false, true, false);
        setupTransactionHashLookup();
        setupContractResult();
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
        when(contractRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(Contract.builder()
                        .id(BYTECODE_CONTRACT_ID.getId())
                        .runtimeBytecode(runtimeBytecode)
                        .build()));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(contractSnapshot(BYTECODE_CONTRACT_ID, 1L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(50L));
        when(contractRepository.findRuntimeBytecodesByIds(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(Collections.emptyList());

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(BYTECODE_CONTRACT_ID.toString());
        assertThat(response.getPre().getFirst().getCode())
                .isEqualTo(Bytes.wrap(runtimeBytecode).toHex());
    }

    @Test
    void callWithDiffAndCodePopulatesPostBytecodeFromConsensusTimestampContracts() {
        final byte[] runtimeBytecode = new byte[] {0x60, 0x40};
        final var request = createRequest(true, true, false);
        setupTransactionHashLookup();
        setupContractResult();
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
        when(contractRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(Contract.builder()
                        .id(BYTECODE_CONTRACT_ID.getId())
                        .runtimeBytecode(runtimeBytecode)
                        .build()));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(contractSnapshot(BYTECODE_CONTRACT_ID, 1L)));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP)))
                .thenReturn(List.of(contractSnapshot(BYTECODE_CONTRACT_ID, 1L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(50L));
        when(contractRepository.findRuntimeBytecodesByIds(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(Collections.emptyList());
        when(contractRepository.findRuntimeBytecodesByIds(anyCollection(), eq(CONSENSUS_TIMESTAMP)))
                .thenReturn(List.of(bytecodeSnapshot(BYTECODE_CONTRACT_ID.getId(), runtimeBytecode)));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode()).isNull();
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPost().getFirst().getCode())
                .isEqualTo(Bytes.wrap(runtimeBytecode).toHex());
    }

    @Test
    void callIncludesMirrorRecipientAddress() {
        final var mirrorAccount = EntityId.of(0L, 0L, 300L);
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(ContractAction.builder()
                        .consensusTimestamp(CONSENSUS_TIMESTAMP)
                        .caller(CONTRACT_ID)
                        .callerType(EntityType.CONTRACT)
                        .recipientAddress(DomainUtils.toEvmAddress(mirrorAccount))
                        .index(0)
                        .build()));
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());

        final var entity = mirrorAccount.toEntity();
        entity.setType(EntityType.ACCOUNT);
        when(entityRepository.findByIdAndDeletedIsFalse(mirrorAccount.getId())).thenReturn(Optional.of(entity));
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), eq(CONSENSUS_TIMESTAMP - 1)))
                .thenReturn(List.of(entitySnapshot(mirrorAccount, 1L, 100L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(100L));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(mirrorAccount.toString());
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, true",
        "false, true, true",
        "true, false, true",
        "true, true, false",
        "false, false, true",
        "false, true, false",
        "true, false, false",
        "false, false, false"
    })
    void callWithDifferentCombinationsOfFlags(final boolean diff, final boolean code, final boolean storage) {
        final var request = createRequest(diff, code, storage);
        setupTransactionHashLookup();
        setupContractResult();
        setupSidecars();
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), anyLong()))
                .thenReturn(List.of(entitySnapshot(ACCOUNT_ID, 1L, 100L)));
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(100L));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isNotNull();
        if (diff) {
            assertThat(response.getPost()).isNotNull();
        } else {
            assertThat(response.getPost()).isNullOrEmpty();
        }
    }

    private PrestateRequest createRequest(boolean diff, boolean code, boolean storage) {
        return new PrestateRequest(
                new TransactionHashParameter(org.apache.tuweni.bytes.Bytes.of(TRANSACTION_HASH)), diff, code, storage);
    }

    private void setupTransactionHashLookup() {
        final var contractTxHash = new ContractTransactionHash();
        contractTxHash.setHash(TRANSACTION_HASH);
        contractTxHash.setConsensusTimestamp(CONSENSUS_TIMESTAMP);
        contractTxHash.setPayerAccountId(PAYER_ACCOUNT_ID.getId());
        when(contractTransactionHashRepository.findByHash(TRANSACTION_HASH)).thenReturn(Optional.of(contractTxHash));
    }

    private void setupContractResult() {
        final var contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(CONSENSUS_TIMESTAMP);
        contractResult.setSenderId(PAYER_ACCOUNT_ID);
        contractResult.setContractId(CONTRACT_ID.getId());
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.of(contractResult));
    }

    private void setupContractResultWithContract() {
        final var contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(CONSENSUS_TIMESTAMP);
        contractResult.setSenderId(PAYER_ACCOUNT_ID);
        contractResult.setContractId(CONTRACT_ID.getId());
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.of(contractResult));
    }

    private void setupSidecars() {
        when(contractActionRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(contractAction(CONTRACT_ID, ACCOUNT_ID)));
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .thenReturn(Collections.emptyList());
    }

    private static ContractAction contractAction(final EntityId caller, final EntityId recipient) {
        return ContractAction.builder()
                .consensusTimestamp(CONSENSUS_TIMESTAMP)
                .caller(caller)
                .callerType(EntityType.CONTRACT)
                .recipientAccount(recipient)
                .index(0)
                .build();
    }

    private static EntitySnapshot entitySnapshot(final EntityId entityId, final long nonce, final long balanceIgnored) {
        return new EntitySnapshot() {
            @Override
            public byte[] getAlias() {
                return null;
            }

            @Override
            public Long getEthereumNonce() {
                return nonce;
            }

            @Override
            public byte[] getEvmAddress() {
                return null;
            }

            @Override
            public long getId() {
                return entityId.getId();
            }

            @Override
            public String getType() {
                return EntityType.ACCOUNT.name();
            }
        };
    }

    private static EntitySnapshot contractSnapshot(final EntityId entityId, final long nonce) {
        return new EntitySnapshot() {
            @Override
            public byte[] getAlias() {
                return null;
            }

            @Override
            public Long getEthereumNonce() {
                return nonce;
            }

            @Override
            public byte[] getEvmAddress() {
                return null;
            }

            @Override
            public long getId() {
                return entityId.getId();
            }

            @Override
            public String getType() {
                return EntityType.CONTRACT.name();
            }
        };
    }

    private static ContractBytecodeSnapshot bytecodeSnapshot(final long contractId, final byte[] bytecode) {
        return new ContractBytecodeSnapshot() {
            @Override
            public long getId() {
                return contractId;
            }

            @Override
            public byte[] getRuntimeBytecode() {
                return bytecode;
            }
        };
    }
}
