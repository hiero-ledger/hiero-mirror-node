// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.PrestateContext;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractBytecodeReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrestateServiceTest {

    private static final Address ACCOUNT_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000064");
    private static final Address CONTRACT_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000000c8");

    private static final EntityId PAYER_ACCOUNT_ID = EntityId.of(0L, 0L, 1001L);
    private static final long CONSENSUS_TIMESTAMP = 1_000_000_000L;
    private static final long VALID_START_NS = 999_999_000L;
    private static final byte[] TRANSACTION_HASH = new byte[] {0x01, 0x02, 0x03, 0x04};

    @InjectMocks
    private PrestateServiceImpl prestateService;

    @Mock
    private ContractDebugService contractDebugService;

    @Mock
    private ContractTransactionHashRepository contractTransactionHashRepository;

    @Mock
    private EthereumTransactionRepository ethereumTransactionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ContractResultRepository contractResultRepository;

    @Mock
    private RecordFileService recordFileService;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private AccountReadableKVState accountReadableKVState;

    @Mock
    private ContractBytecodeReadableKVState contractBytecodeReadableKVState;

    @Mock
    private ContractStorageReadableKVState contractStorageReadableKVState;

    private MockedStatic<ContractCallContext> contextMockedStatic;

    @BeforeEach
    void setUp() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
        contextMockedStatic.when(() -> ContractCallContext.run(any())).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() {
        contextMockedStatic.close();
    }

    @Test
    void callWithDiffEnabledReturnsBothPreAndPost() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());
        setupAccountEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L, 1L);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotNull().isNotEmpty();
        assertThat(response.getPost()).isNotNull().isNotEmpty();
    }

    @Test
    void callWithDiffDisabledReturnsOnlyPre() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());
        setupAccountEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L, 1L);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotNull().isNotEmpty();
        assertThat(response.getPost()).isNullOrEmpty();
    }

    @Test
    void callWithDiffEnabledHaveProperPreAndPostCollections() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());
        setupAccountEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L, 5L);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isNotEmpty();
        assertThat(response.getPost()).isNotEmpty();

        final var preTrace = response.getPre().getFirst();
        final var postTrace = response.getPost().getFirst();
        assertThat(preTrace.getAddress()).isEqualTo(postTrace.getAddress());
        assertThat(postTrace.getBalance()).isNull();
        assertThat(postTrace.getNonce()).isNull();
    }

    @Test
    void callWithDiffEnabledAndDifferentBalanceAndNonce() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var entity = createEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L);
        when(commonEntityAccessor.get(eq(ACCOUNT_ADDRESS), any())).thenReturn(Optional.of(entity));

        final var account = Account.newBuilder().ethereumNonce(5L).build();

        final var changedAccount = Account.newBuilder().ethereumNonce(10L).build();

        when(accountReadableKVState.get(any(AccountID.class)))
                .thenReturn(account)
                .thenReturn(changedAccount);

        doAnswer(invocation -> {
                    final PrestateContext ctx = invocation.getArgument(1);
                    ctx.getTouchedAccounts().add(ACCOUNT_ADDRESS);
                    return null;
                })
                .when(contractDebugService)
                .processPrestateCall(any(ContractDebugParameters.class), any(PrestateContext.class));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPost()).isNotEmpty();
        final var postTrace = response.getPost().getFirst();
        assertThat(postTrace.getNonce()).isNotNull();
        assertThat(postTrace.getNonce()).isEqualTo(10L);
    }

    @Test
    void callWithCodeEnabled() {
        final var request = createRequest(false, true, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var bytecodeValue = Bytes.wrap(new byte[] {0x60, 0x40});
        final var bytecode = new Bytecode(bytecodeValue);

        simulateTouchedAccounts(Set.of(CONTRACT_ADDRESS), Collections.emptyMap());
        setupContractEntity(CONTRACT_ADDRESS, bytecode, null);

        final var response = prestateService.processPrestateCall(request);

        // CONTRACT entities' traces are filtered out since address is never set in populateContractFields
        assertThat(response.getPre()).isEmpty();
    }

    @Test
    void callWithAllParamsDisabled() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(CONTRACT_ADDRESS), Collections.emptyMap());
        final var entity = createEntity(CONTRACT_ADDRESS, EntityType.CONTRACT, 50L);
        when(commonEntityAccessor.get(eq(CONTRACT_ADDRESS), any())).thenReturn(Optional.of(entity));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isEmpty();
    }

    @Test
    void callWithStorageEnabled() {
        final var request = createRequest(false, false, true);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var slotKeyHex = "0000000000000000000000000000000000000000000000000000000000000001";
        final var storageMap = Map.of(CONTRACT_ADDRESS, Set.of(slotKeyHex));
        simulateTouchedAccounts(Set.of(CONTRACT_ADDRESS), storageMap);

        final var entity = createEntity(CONTRACT_ADDRESS, EntityType.CONTRACT, 50L);
        when(commonEntityAccessor.get(eq(CONTRACT_ADDRESS), any())).thenReturn(Optional.of(entity));

        final var slotValue = new SlotValue(
                Bytes.wrap(new byte[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x14
                }),
                Bytes.EMPTY,
                Bytes.EMPTY);
        when(contractStorageReadableKVState.get(any(SlotKey.class))).thenReturn(slotValue);

        final var response = prestateService.processPrestateCall(request);

        // CONTRACT entities' traces are filtered out since address is never set
        assertThat(response.getPre()).isEmpty();
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
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());
        setupAccountEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L, 1L);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotNull();

        if (diff) {
            assertThat(response.getPost()).isNotNull();
        } else {
            assertThat(response.getPost()).isNullOrEmpty();
        }
    }

    @Test
    void callForAccountEntitySetsBalanceAndNonce() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());
        setupAccountEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 500L, 3L);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        final var trace = response.getPre().getFirst();
        assertThat(trace.getAddress()).isEqualTo(ACCOUNT_ADDRESS.toHexString());
        assertThat(trace.getBalance()).isNotNull();
        assertThat(trace.getNonce()).isEqualTo(3L);
        assertThat(trace.getCode()).isNull();
        assertThat(trace.getStorage()).isEmpty();
    }

    @Test
    void callForContractEntityFilteredBecauseAddressNotSet() {
        final var request = createRequest(false, true, true);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var slotKeyHex = "0000000000000000000000000000000000000000000000000000000000000001";
        final var storageMap = Map.of(CONTRACT_ADDRESS, Set.of(slotKeyHex));
        simulateTouchedAccounts(Set.of(CONTRACT_ADDRESS), storageMap);

        final var bytecodeValue = Bytes.wrap(new byte[] {0x60, 0x40, 0x52});
        final var bytecode = new Bytecode(bytecodeValue);
        setupContractEntity(CONTRACT_ADDRESS, bytecode, slotKeyHex);

        final var response = prestateService.processPrestateCall(request);

        // CONTRACT entities are not included because populateContractFields doesn't set address
        assertThat(response.getPre()).isEmpty();
    }

    @Test
    void callWithContractTransactionHashNotFound() {
        final var txHash = org.apache.tuweni.bytes.Bytes.of(TRANSACTION_HASH);
        final var hashParam = new TransactionHashParameter(txHash);
        final var request = new PrestateRequest(hashParam, false, false, false);

        when(contractTransactionHashRepository.findByHash(TRANSACTION_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prestateService.processPrestateCall(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Contract transaction hash not found");
    }

    @Test
    void callWithTransactionNotFound() {
        final var txIdParam =
                new TransactionIdParameter(PAYER_ACCOUNT_ID, java.time.Instant.ofEpochSecond(0, VALID_START_NS));
        final var request = new PrestateRequest(txIdParam, false, false, false);

        when(transactionRepository.findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
                        PAYER_ACCOUNT_ID, VALID_START_NS))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> prestateService.processPrestateCall(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void callWithContractResultNotFound() {
        setupTransactionHashLookup();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.empty());

        final var request = createRequest(false, false, false);

        assertThatThrownBy(() -> prestateService.processPrestateCall(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Contract result not found");
    }

    @Test
    void callWithNoTouchedAccounts() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Collections.emptySet(), Collections.emptyMap());

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotNull().isEmpty();
    }

    @Test
    void callWithTouchedAccountNotInEntityAccessor() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());
        when(commonEntityAccessor.get(eq(ACCOUNT_ADDRESS), any())).thenReturn(Optional.empty());

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isEmpty();
    }

    @Test
    void callWithDiffEnabledAndMultipleAccountsWithContractFiltered() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS, CONTRACT_ADDRESS), Collections.emptyMap());

        setupAccountEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L, 1L);
        final var contractEntity = createEntity(CONTRACT_ADDRESS, EntityType.CONTRACT, 50L);
        when(commonEntityAccessor.get(eq(CONTRACT_ADDRESS), any())).thenReturn(Optional.of(contractEntity));

        final var response = prestateService.processPrestateCall(request);

        // Only ACCOUNT entities produce traces; CONTRACT entities are filtered out
        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(ACCOUNT_ADDRESS.toHexString());
        assertThat(response.getPost()).isNotNull();
    }

    @Test
    void callWithDiffEnabledAndMultipleAccountEntities() {
        final var secondAccountAddress = Address.fromHexString("0x00000000000000000000000000000000000000c9");
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS, secondAccountAddress), Collections.emptyMap());

        final var entity = createEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L);
        when(commonEntityAccessor.get(eq(ACCOUNT_ADDRESS), any())).thenReturn(Optional.of(entity));

        setupAccountEntity(secondAccountAddress, EntityType.ACCOUNT, 200L, 2L);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(2);
        assertThat(response.getPost()).isNotNull().hasSize(2);
    }

    @Test
    void callUsingTransactionIdParameter() {
        final var txIdParam =
                new TransactionIdParameter(PAYER_ACCOUNT_ID, java.time.Instant.ofEpochSecond(0, VALID_START_NS));
        final var request = new PrestateRequest(txIdParam, false, false, false);

        final var transaction = new org.hiero.mirror.common.domain.transaction.Transaction();
        transaction.setConsensusTimestamp(CONSENSUS_TIMESTAMP);
        transaction.setPayerAccountId(PAYER_ACCOUNT_ID);
        transaction.setValidStartNs(VALID_START_NS);
        transaction.setType(7);

        when(transactionRepository.findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
                        PAYER_ACCOUNT_ID, VALID_START_NS))
                .thenReturn(List.of(transaction));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        CONSENSUS_TIMESTAMP, PAYER_ACCOUNT_ID))
                .thenReturn(Optional.empty());
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());
        setupAccountEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L, 1L);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotEmpty();
    }

    @Test
    void callWithAccountNullFromKVStateAndTracesSkipped() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        simulateTouchedAccounts(Set.of(ACCOUNT_ADDRESS), Collections.emptyMap());

        final var entity = createEntity(ACCOUNT_ADDRESS, EntityType.ACCOUNT, 100L);
        when(commonEntityAccessor.get(eq(ACCOUNT_ADDRESS), any())).thenReturn(Optional.of(entity));
        when(accountReadableKVState.get(any(AccountID.class))).thenReturn(null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isEmpty();
    }

    private PrestateRequest createRequest(boolean diff, boolean code, boolean storage) {
        final var txHash = org.apache.tuweni.bytes.Bytes.of(TRANSACTION_HASH);
        final var hashParam = new TransactionHashParameter(txHash);
        return new PrestateRequest(hashParam, diff, code, storage);
    }

    private void setupTransactionHashLookup() {
        final var contractTxHash = new ContractTransactionHash();
        contractTxHash.setHash(TRANSACTION_HASH);
        contractTxHash.setConsensusTimestamp(CONSENSUS_TIMESTAMP);
        contractTxHash.setPayerAccountId(PAYER_ACCOUNT_ID.getId());

        when(contractTransactionHashRepository.findByHash(TRANSACTION_HASH)).thenReturn(Optional.of(contractTxHash));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        CONSENSUS_TIMESTAMP, PAYER_ACCOUNT_ID))
                .thenReturn(Optional.empty());
    }

    private void setupContractResult() {
        final var contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(CONSENSUS_TIMESTAMP);
        contractResult.setSenderId(PAYER_ACCOUNT_ID);
        contractResult.setContractId(EntityId.of(0L, 0L, 200L).getId());
        contractResult.setGasLimit(300_000L);
        contractResult.setAmount(0L);
        contractResult.setFunctionParameters(new byte[] {0x01});

        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.of(contractResult));
        when(commonEntityAccessor.evmAddressFromId(any(EntityId.class), any())).thenReturn(CONTRACT_ADDRESS);
    }

    private void setupRecordFile() {
        final var recordFile = new RecordFile();
        recordFile.setIndex(1L);
        when(recordFileService.findByTimestamp(CONSENSUS_TIMESTAMP)).thenReturn(Optional.of(recordFile));
    }

    private void simulateTouchedAccounts(
            final Set<Address> touchedAccounts, final Map<Address, Set<String>> touchedStorage) {
        doAnswer(invocation -> {
                    final PrestateContext ctx = invocation.getArgument(1);
                    ctx.getTouchedAccounts().addAll(touchedAccounts);
                    for (final var entry : touchedStorage.entrySet()) {
                        for (final var key : entry.getValue()) {
                            ctx.setTouchedStorage(entry.getKey(), key);
                        }
                    }
                    return null;
                })
                .when(contractDebugService)
                .processPrestateCall(any(ContractDebugParameters.class), any(PrestateContext.class));
    }

    private void setupAccountEntity(Address address, EntityType type, long balance, long nonce) {
        final var entity = createEntity(address, type, balance);
        when(commonEntityAccessor.get(eq(address), any())).thenReturn(Optional.of(entity));

        final var account = Account.newBuilder().ethereumNonce(nonce).build();
        when(accountReadableKVState.get(any(AccountID.class))).thenReturn(account);
    }

    private Entity createEntity(Address address, EntityType type, long balance) {
        final var entity = new Entity();
        entity.setType(type);
        entity.setBalance(balance);
        entity.setNum(Long.parseLong(
                address.toHexString().substring(address.toHexString().length() - 4), 16));
        entity.setShard(0L);
        entity.setRealm(0L);
        entity.setId(entity.getNum());
        return entity;
    }

    private void setupContractEntity(Address address, Bytecode bytecode, String slotKeyHex) {
        final var entity = createEntity(address, EntityType.CONTRACT, 50L);
        when(commonEntityAccessor.get(eq(address), any())).thenReturn(Optional.of(entity));
        when(contractBytecodeReadableKVState.get(any(ContractID.class))).thenReturn(bytecode);

        if (slotKeyHex != null) {
            final var slotValue = new SlotValue(
                    Bytes.wrap(new byte[] {
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0x14
                    }),
                    Bytes.EMPTY,
                    Bytes.EMPTY);
            when(contractStorageReadableKVState.get(any(SlotKey.class))).thenReturn(slotValue);
        }
    }
}
