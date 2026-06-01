// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.rest.model.AccountTrace;
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

    @Mock
    private CommonProperties commonProperties;

    private MockedStatic<ContractCallContext> contextMockedStatic;

    @BeforeEach
    void setUp() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
        contextMockedStatic.when(() -> ContractCallContext.run(any())).thenCallRealMethod();
        contextMockedStatic.when(ContractCallContext::get).thenCallRealMethod();

        lenient().when(commonProperties.getShard()).thenReturn(0L);
        lenient().when(commonProperties.getRealm()).thenReturn(0L);
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

        final var acctId = accountIdOf(100L);
        final var preAccount = buildAccount(100L, 100L, 1L);
        final var postAccount = buildAccount(100L, 100L, 2L);

        setupCaches(Map.of(acctId, preAccount), Map.of(acctId, postAccount));

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

        setupCaches(Map.of(accountIdOf(100L), buildAccount(100L, 100L, 1L)), null);

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

        final var acctId = accountIdOf(100L);
        final var account = buildAccount(100L, 100L, 5L);
        setupCaches(Map.of(acctId, account), Map.of(acctId, account));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isNotEmpty();
        assertThat(response.getPost()).isEmpty();
    }

    @Test
    void callWithDiffEnabledAndDifferentBalanceAndNonce() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var acctId = accountIdOf(100L);
        final var preAccount = buildAccount(100L, 100L, 5L);
        final var postAccount = buildAccount(100L, 100L, 10L);

        setupCaches(Map.of(acctId, preAccount), Map.of(acctId, postAccount));

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
        final var contractIdKey = EntityId.of(0L, 0L, 200L).toContractID();

        setupCaches(
                Map.of(accountIdOf(200L), buildContract(200L, 50L, 1L)),
                null,
                Map.of(contractIdKey, bytecode),
                null,
                null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        final var trace = response.getPre().getFirst();
        assertThat(trace.getAddress()).isEqualTo("0.0.200");
        assertThat(trace.getCode()).isEqualTo(bytecodeValue.toHex());
    }

    @Test
    void callWithAllParamsDisabled() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        setupCaches(Map.of(accountIdOf(200L), buildContract(200L, 50L, 1L)), null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        final var trace = response.getPre().getFirst();
        assertThat(trace.getAddress()).isEqualTo("0.0.200");
        assertThat(trace.getCode()).isNull();
        assertThat(trace.getStorage()).isNullOrEmpty();
    }

    @Test
    void callWithStorageEnabled() {
        final var request = createRequest(false, false, true);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var slotKeyBytes = Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001");
        final var contractId = ContractID.newBuilder().contractNum(200L).build();
        final var slotKey =
                SlotKey.newBuilder().contractID(contractId).key(slotKeyBytes).build();
        final var slotValue = new SlotValue(
                Bytes.wrap(new byte[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x14
                }),
                Bytes.EMPTY,
                Bytes.EMPTY);

        setupCaches(
                Map.of(accountIdOf(200L), buildContract(200L, 50L, 1L)), null, null, Map.of(slotKey, slotValue), null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        final var trace = response.getPre().getFirst();
        assertThat(trace.getAddress()).isEqualTo("0.0.200");
        assertThat(trace.getStorage()).isNotEmpty();
        assertThat(trace.getStorage()).containsKey(slotKeyBytes.toHex());
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

        final var acctId = accountIdOf(100L);
        final var account = buildAccount(100L, 100L, 1L);
        setupCaches(Map.of(acctId, account), diff ? Map.of(acctId, account) : null);

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

        setupCaches(Map.of(accountIdOf(100L), buildAccount(100L, 500L, 3L)), null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(1);
        final var trace = response.getPre().getFirst();
        assertThat(trace.getAddress()).isEqualTo("0.0.100");
        assertThat(trace.getBalance()).isNotNull();
        assertThat(trace.getNonce()).isEqualTo(3L);
        assertThat(trace.getCode()).isNull();
        assertThat(trace.getStorage()).isNullOrEmpty();
    }

    @Test
    void callWithEmptyCacheReturnsEmptyPre() {
        final var request = createRequest(false, true, true);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        setupCaches(Collections.emptyMap(), null);

        final var response = prestateService.processPrestateCall(request);

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

        when(transactionRepository.findByPayerAccountIdAndValidStartNs(
                        eq(PAYER_ACCOUNT_ID.getId()), eq(VALID_START_NS), anyLong(), anyLong()))
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
    void callWithNoAccountsInCacheReturnsEmptyPre() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        setupCaches(Collections.emptyMap(), null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotNull().isEmpty();
    }

    @Test
    void callWithNonAccountValuesInCacheReturnsEmptyPre() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    ctx.getReadCacheState(AccountReadableKVState.STATE_ID).put(accountIdOf(100L), "not-an-account");
                    return null;
                })
                .when(contractDebugService)
                .processPrestateCall(any(ContractDebugParameters.class), any(PrestateContext.class));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).isEmpty();
    }

    @Test
    void callWithDiffEnabledAndMultipleAccountEntities() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var acctId1 = accountIdOf(100L);
        final var acctId2 = accountIdOf(201L);
        final var account1 = buildAccount(100L, 200L, 2L);
        final var account2 = buildAccount(201L, 200L, 2L);

        setupCaches(Map.of(acctId1, account1, acctId2, account2), Map.of(acctId1, account1, acctId2, account2));

        final var response = prestateService.processPrestateCall(request);

        assertThat(response.getPre()).hasSize(2);
        assertThat(response.getPost()).isNotNull().isEmpty();
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

        when(transactionRepository.findByPayerAccountIdAndValidStartNs(
                        eq(PAYER_ACCOUNT_ID.getId()), eq(VALID_START_NS), anyLong(), anyLong()))
                .thenReturn(List.of(transaction));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        CONSENSUS_TIMESTAMP, PAYER_ACCOUNT_ID))
                .thenReturn(Optional.empty());
        setupContractResult();
        setupRecordFile();

        setupCaches(Map.of(accountIdOf(100L), buildAccount(100L, 100L, 1L)), null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).isNotEmpty();
    }

    @Test
    void callWithEmptyReadCacheReturnsEmptyPre() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        setupCaches(Collections.emptyMap(), null);

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

    private void setupCaches(final Map<AccountID, Account> readAccounts, final Map<AccountID, Account> writeAccounts) {
        setupCaches(readAccounts, writeAccounts, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private void setupCaches(
            final Map<AccountID, Account> readAccounts,
            final Map<AccountID, Account> writeAccounts,
            final Map<?, ?> bytecodes,
            final Map<SlotKey, SlotValue> readStorage,
            final Map<SlotKey, SlotValue> writeStorage) {
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();

                    readAccounts.forEach(ctx.getReadCacheState(AccountReadableKVState.STATE_ID)::put);
                    ctx.getReadCacheState(ContractBytecodeReadableKVState.STATE_ID);
                    ctx.getReadCacheState(ContractStorageReadableKVState.STATE_ID);

                    if (bytecodes != null) {
                        bytecodes.forEach(ctx.getReadCacheState(ContractBytecodeReadableKVState.STATE_ID)::put);
                    }
                    if (readStorage != null) {
                        readStorage.forEach(ctx.getReadCacheState(ContractStorageReadableKVState.STATE_ID)::put);
                    }

                    if (writeAccounts != null) {
                        writeAccounts.forEach(ctx.getWriteCacheState(AccountReadableKVState.STATE_ID)::put);
                        ctx.getWriteCacheState(ContractBytecodeReadableKVState.STATE_ID);
                        ctx.getWriteCacheState(ContractStorageReadableKVState.STATE_ID);

                        if (bytecodes != null) {
                            bytecodes.forEach(ctx.getWriteCacheState(ContractBytecodeReadableKVState.STATE_ID)::put);
                        }
                        if (writeStorage != null) {
                            writeStorage.forEach(ctx.getWriteCacheState(ContractStorageReadableKVState.STATE_ID)::put);
                        }
                    }

                    return null;
                })
                .when(contractDebugService)
                .processPrestateCall(any(ContractDebugParameters.class), any(PrestateContext.class));
    }

    private static AccountID accountIdOf(long num) {
        return AccountID.newBuilder().accountNum(num).build();
    }

    private static AccountID accountIdOf(Address alias) {
        return AccountID.newBuilder().alias(Bytes.wrap(alias.toArray())).build();
    }

    private static Account buildAccount(long num, long balance, long nonce) {
        return Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(num).build())
                .ethereumNonce(nonce)
                .tinybarBalance(balance)
                .build();
    }

    private static Account buildAliasedAccount(Address alias, long balance, long nonce) {
        return Account.newBuilder()
                .alias(Bytes.wrap(alias.toArray()))
                .ethereumNonce(nonce)
                .tinybarBalance(balance)
                .build();
    }

    private static Account buildContract(long num, long balance, long nonce) {
        return Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(num).build())
                .ethereumNonce(nonce)
                .tinybarBalance(balance)
                .smartContract(true)
                .build();
    }

    private static Account buildAliasedContract(Address alias, long balance, long nonce) {
        return Account.newBuilder()
                .alias(Bytes.wrap(alias.toArray()))
                .ethereumNonce(nonce)
                .tinybarBalance(balance)
                .smartContract(true)
                .build();
    }

    @Test
    void transactionTouches3AccountsAnd2ContractsWithAliasesInDiffModeFalse() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var alias1 = Address.fromHexString("0x1234567890abcdef1234567890abcdef12345678");
        final var alias2 = Address.fromHexString("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd");
        final var alias3 = Address.fromHexString("0xfedcba9876543210fedcba9876543210fedcba98");
        final var contractAlias1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
        final var contractAlias2 = Address.fromHexString("0x2222222222222222222222222222222222222222");

        setupCaches(
                Map.of(
                        accountIdOf(alias1), buildAliasedAccount(alias1, 500L, 5L),
                        accountIdOf(alias2), buildAliasedAccount(alias2, 500L, 5L),
                        accountIdOf(alias3), buildAliasedAccount(alias3, 500L, 5L),
                        accountIdOf(contractAlias1), buildAliasedContract(contractAlias1, 500L, 5L),
                        accountIdOf(contractAlias2), buildAliasedContract(contractAlias2, 500L, 5L)),
                null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).hasSize(5);
        assertThat(response.getPost()).isNullOrEmpty();

        final var preAddresses =
                response.getPre().stream().map(AccountTrace::getAddress).toList();
        assertThat(preAddresses)
                .containsExactlyInAnyOrder(
                        Bytes.wrap(alias1.toArray()).toHex(),
                        Bytes.wrap(alias2.toArray()).toHex(),
                        Bytes.wrap(alias3.toArray()).toHex(),
                        Bytes.wrap(contractAlias1.toArray()).toHex(),
                        Bytes.wrap(contractAlias2.toArray()).toHex());
    }

    @Test
    void transactionTouches2AccountsAnd3ContractsWithMixedAddressTypesInDiffModeFalse() {
        final var request = createRequest(false, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var accountAlias = Address.fromHexString("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd");
        final var contractAlias = Address.fromHexString("0x3333333333333333333333333333333333333333");

        setupCaches(
                Map.of(
                        accountIdOf(accountAlias), buildAliasedAccount(accountAlias, 500L, 5L),
                        accountIdOf(100L), buildAccount(100L, 500L, 5L),
                        accountIdOf(contractAlias), buildAliasedContract(contractAlias, 500L, 5L),
                        accountIdOf(200L), buildContract(200L, 500L, 5L),
                        accountIdOf(201L), buildContract(201L, 500L, 5L)),
                null);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).hasSize(5);
        assertThat(response.getPost()).isNullOrEmpty();

        final var preAddresses =
                response.getPre().stream().map(AccountTrace::getAddress).toList();
        assertThat(preAddresses)
                .containsExactlyInAnyOrder(
                        Bytes.wrap(accountAlias.toArray()).toHex(),
                        "0.0.100",
                        Bytes.wrap(contractAlias.toArray()).toHex(),
                        "0.0.200",
                        "0.0.201");
    }

    @Test
    void transactionTouchesMultipleAccountsAndContractsWithPartialChangesInDiffModeTrue() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var alias3 = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        final var alias4 = Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        final var acctId1 = accountIdOf(100L);
        final var acctId2 = accountIdOf(101L);
        final var acctId3 = accountIdOf(alias3);
        final var acctId4 = accountIdOf(alias4);

        final var preAccounts = Map.of(
                acctId1, buildAccount(100L, 100L, 1L),
                acctId2, buildAccount(101L, 200L, 2L),
                acctId3, buildAliasedAccount(alias3, 300L, 3L),
                acctId4, buildAliasedAccount(alias4, 400L, 4L));

        final var postAccounts = Map.of(
                acctId1, buildAccount(100L, 100L, 100L),
                acctId2, buildAccount(101L, 200L, 2L),
                acctId3, buildAliasedAccount(alias3, 300L, 300L),
                acctId4, buildAliasedAccount(alias4, 400L, 4L));

        setupCaches(preAccounts, postAccounts);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).hasSize(4);

        assertThat(response.getPost()).hasSize(2);

        final var postAddresses =
                response.getPost().stream().map(AccountTrace::getAddress).toList();
        assertThat(postAddresses)
                .containsExactlyInAnyOrder(
                        "0.0.100", Bytes.wrap(alias3.toArray()).toHex());

        final var account1Trace = response.getPost().stream()
                .filter(trace -> trace.getAddress().equals("0.0.100"))
                .findFirst()
                .orElseThrow();
        assertThat(account1Trace.getNonce()).isEqualTo(100L);

        final var account3Trace = response.getPost().stream()
                .filter(trace ->
                        trace.getAddress().equals(Bytes.wrap(alias3.toArray()).toHex()))
                .findFirst()
                .orElseThrow();
        assertThat(account3Trace.getNonce()).isEqualTo(300L);
    }

    @Test
    void transactionCreatesNewAccountInPostState() {
        final var request = createRequest(true, false, false);
        setupTransactionHashLookup();
        setupContractResult();
        setupRecordFile();

        final var newAccountAlias = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        final var acctId1 = accountIdOf(100L);
        final var acctId2 = accountIdOf(101L);
        final var newAcctId = accountIdOf(newAccountAlias);

        final var preAccounts = Map.of(
                acctId1, buildAccount(100L, 100L, 1L),
                acctId2, buildAccount(101L, 200L, 2L));

        final var postAccounts = Map.of(
                acctId1, buildAccount(100L, 100L, 1L),
                acctId2, buildAccount(101L, 200L, 2L),
                newAcctId, buildAliasedAccount(newAccountAlias, 500L, 1L));

        setupCaches(preAccounts, postAccounts);

        final var response = prestateService.processPrestateCall(request);

        assertThat(response).isNotNull();
        assertThat(response.getPre()).hasSize(2);

        assertThat(response.getPost()).hasSize(1);

        final var newAccountTrace = response.getPost().getFirst();
        assertThat(newAccountTrace.getAddress())
                .isEqualTo(Bytes.wrap(newAccountAlias.toArray()).toHex());
        assertThat(newAccountTrace.getBalance()).isNotNull();
        assertThat(newAccountTrace.getNonce()).isNotNull();
    }
}
