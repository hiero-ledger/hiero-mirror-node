// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.services.stream.proto.CallOperationType.OP_CALL;
import static com.hedera.services.stream.proto.CallOperationType.OP_CREATE;
import static com.hedera.services.stream.proto.CallOperationType.OP_CREATE2;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.ByteUtils.wrapToWordSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction.ResultDataCase;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.controller.PrestateProperties;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractStateChangeRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TouchedAccountCollectorTest {

    private static final long CONSENSUS_TIMESTAMP = 1_000_000L;
    private static final EntityId CALLER = EntityId.of(1001L);
    private static final EntityId RECIPIENT_ACCOUNT = EntityId.of(1002L);
    private static final EntityId RECIPIENT_CONTRACT = EntityId.of(1003L);
    private static final EntityId SENDER = EntityId.of(2001L);
    private static final EntityId PAYER = EntityId.of(2002L);
    private static final EntityId CREATED_CONTRACT = EntityId.of(3001L);
    private static final byte[] STORAGE_SLOT = new byte[] {0x01};
    private static final byte[] VALUE_READ = new byte[] {0x14};
    private static final byte[] VALUE_WRITTEN = new byte[] {0x28};

    @Mock
    private AuthorizationExtractor authorizationExtractor;

    @Mock
    private ContractActionRepository contractActionRepository;

    @Mock
    private ContractResultRepository contractResultRepository;

    @Mock
    private ContractStateChangeRepository contractStateChangeRepository;

    @Mock
    private EthereumTransactionRepository ethereumTransactionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TouchedAccountCollector collector;

    @Test
    void collectAddsCallerAndRecipientsFromActions() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(OP_CALL, 0, 0L, OUTPUT)));

        final var context = context(true, false);
        collector.collect(context);

        assertThat(context.getAccounts())
                .containsExactlyInAnyOrder(CALLER.getId(), RECIPIENT_ACCOUNT.getId(), RECIPIENT_CONTRACT.getId());
        assertThat(context.getBalanceTransfers()).isEmpty();
        assertThat(context.getNonceDeltas()).isEmpty();
    }

    @Test
    void collectIgnoresEmptyActionParticipants() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(ContractAction.builder()
                        .caller(EntityId.EMPTY)
                        .recipientAccount(null)
                        .recipientContract(EntityId.EMPTY)
                        .callOperationType(OP_CALL.getNumber())
                        .callDepth(0)
                        .value(25L)
                        .resultDataType(OUTPUT.getNumber())
                        .build()));

        final var context = context(true, false);
        collector.collect(context);

        assertThat(context.getAccounts()).isEmpty();
        assertThat(context.getBalanceTransfers()).isEmpty();
    }

    @Test
    void collectStopsWhenAccountCapIsReached() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(
                        callAction(OP_CALL, 0, 0L, OUTPUT),
                        ContractAction.builder()
                                .caller(SENDER)
                                .recipientAccount(PAYER)
                                .callOperationType(OP_CALL.getNumber())
                                .build()));

        final var properties = new PrestateProperties();
        properties.setMaxTouchedAccounts(1);
        final var context = context(properties, true, false);
        collector.collect(context);

        assertThat(context.getAccounts()).containsExactly(CALLER.getId());
    }

    @Test
    void collectDoesNotApplyBalanceTransferWhenNotDiffMode() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(OP_CALL, 0, 25L, OUTPUT)));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getBalanceTransfers()).isEmpty();
    }

    @Test
    void collectAppliesBalanceTransferInDiffMode() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(OP_CALL, 0, 25L, OUTPUT)));

        final var context = context(true, false);
        collector.collect(context);

        assertThat(context.getBalanceTransfers())
                .containsEntry(CALLER.getId(), -25L)
                .containsEntry(RECIPIENT_ACCOUNT.getId(), 25L)
                .doesNotContainKey(RECIPIENT_CONTRACT.getId());
    }

    @Test
    void collectPrefersRecipientContractWhenRecipientAccountIsEmpty() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(ContractAction.builder()
                        .caller(CALLER)
                        .recipientAccount(EntityId.EMPTY)
                        .recipientContract(RECIPIENT_CONTRACT)
                        .callOperationType(OP_CALL.getNumber())
                        .value(10L)
                        .resultDataType(OUTPUT.getNumber())
                        .build()));

        final var context = context(true, false);
        collector.collect(context);

        assertThat(context.getBalanceTransfers()).containsEntry(RECIPIENT_CONTRACT.getId(), 10L);
    }

    @ParameterizedTest
    @EnumSource(
            value = ResultDataCase.class,
            names = {"REVERT_REASON", "ERROR"})
    void collectSkipsBalanceTransferOnFailedResult(final ResultDataCase resultDataCase) {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(OP_CALL, 0, 25L, resultDataCase)));

        final var context = context(true, false);
        collector.collect(context);

        assertThat(context.getBalanceTransfers()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = CallOperationType.class,
            names = {"OP_DELEGATECALL", "OP_STATICCALL"})
    void collectSkipsBalanceTransferOnNonValueCall(final CallOperationType operationType) {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(operationType, 0, 25L, OUTPUT)));

        final var context = context(true, false);
        collector.collect(context);

        assertThat(context.getBalanceTransfers()).isEmpty();
    }

    @Test
    void collectSkipsBalanceTransferWhenValueIsZero() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(OP_CALL, 0, 0L, OUTPUT)));

        final var context = context(true, false);
        collector.collect(context);

        assertThat(context.getBalanceTransfers()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = CallOperationType.class,
            names = {"OP_CREATE", "OP_CREATE2"})
    void collectAppliesCreateNonceDeltaForNestedCreate(final CallOperationType operationType) {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(operationType, 1, 0L, OUTPUT)));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).containsEntry(CALLER.getId(), 1L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void collectDoesNotApplyCreateNonceDeltaForNonPositiveDepth(final int callDepth) {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(OP_CREATE, callDepth, 0L, OUTPUT)));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).isEmpty();
    }

    @Test
    void collectDoesNotApplyCreateNonceDeltaForCall() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(callAction(OP_CALL, 1, 0L, OUTPUT)));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).isEmpty();
    }

    @Test
    void collectDoesNotApplyCreateNonceDeltaWhenCallerIsEmpty() {
        stubNoNonceSources();
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of(ContractAction.builder()
                        .caller(EntityId.EMPTY)
                        .callOperationType(OP_CREATE2.getNumber())
                        .callDepth(1)
                        .resultDataType(OUTPUT.getNumber())
                        .build()));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).isEmpty();
    }

    @Test
    void collectDoesNotLoadStateChangesWhenStorageDisabled() {
        stubNoActions();
        stubNoNonceSources();

        collector.collect(context(true, false));

        verify(contractStateChangeRepository, never()).findByConsensusTimestamp(anyLong(), anyInt(), anyInt());
        verify(contractStateChangeRepository, never()).findModifiedByConsensusTimestamp(anyLong(), anyInt(), anyInt());
    }

    @Test
    void collectLoadsAllStateChangesWhenNotDiffMode() {
        stubNoActions();
        stubNoNonceSources();
        when(contractStateChangeRepository.findByConsensusTimestamp(CONSENSUS_TIMESTAMP, 5000, 0))
                .thenReturn(List.of(stateChange(VALUE_READ, VALUE_WRITTEN)));

        final var context = context(false, true);
        collector.collect(context);

        assertThat(context.getPreStorageByContract().get(RECIPIENT_CONTRACT.getId()))
                .containsEntry(wrapToWordSize(STORAGE_SLOT), wrapToWordSize(VALUE_READ));
        assertThat(context.getPostStorageByContract().get(RECIPIENT_CONTRACT.getId()))
                .containsEntry(wrapToWordSize(STORAGE_SLOT), wrapToWordSize(VALUE_WRITTEN));
        verify(contractStateChangeRepository, never()).findModifiedByConsensusTimestamp(anyLong(), anyInt(), anyInt());
    }

    @Test
    void collectLoadsOnlyModifiedStateChangesInDiffMode() {
        stubNoActions();
        stubNoNonceSources();
        when(contractStateChangeRepository.findModifiedByConsensusTimestamp(CONSENSUS_TIMESTAMP, 5000, 0))
                .thenReturn(List.of(stateChange(VALUE_READ, VALUE_WRITTEN)));

        final var context = context(true, true);
        collector.collect(context);

        assertThat(context.getPreStorageByContract()).containsKey(RECIPIENT_CONTRACT.getId());
        verify(contractStateChangeRepository, never()).findByConsensusTimestamp(anyLong(), anyInt(), anyInt());
    }

    @Test
    void collectOmitsEmptyAndZeroStorageValues() {
        stubNoActions();
        stubNoNonceSources();
        when(contractStateChangeRepository.findModifiedByConsensusTimestamp(CONSENSUS_TIMESTAMP, 5000, 0))
                .thenReturn(List.of(
                        stateChange(new byte[0], VALUE_WRITTEN),
                        stateChange(VALUE_READ, null),
                        stateChange(new byte[] {0, 0}, new byte[] {0})));

        final var context = context(true, true);
        collector.collect(context);

        assertThat(context.getPreStorageByContract().get(RECIPIENT_CONTRACT.getId()))
                .containsOnlyKeys(wrapToWordSize(STORAGE_SLOT));
        assertThat(context.getPostStorageByContract().get(RECIPIENT_CONTRACT.getId()))
                .containsOnlyKeys(wrapToWordSize(STORAGE_SLOT));
        assertThat(context.getPreStorageByContract().get(RECIPIENT_CONTRACT.getId()))
                .doesNotContainValue(wrapToWordSize(new byte[0]));
    }

    @Test
    void collectPaginatesStateChanges() {
        stubNoActions();
        stubNoNonceSources();
        final var properties = new PrestateProperties();
        properties.setStateChangeMaxPages(2);
        properties.setStateChangePageSize(1);
        when(contractStateChangeRepository.findModifiedByConsensusTimestamp(CONSENSUS_TIMESTAMP, 1, 0))
                .thenReturn(List.of(stateChange(VALUE_READ, VALUE_WRITTEN)));
        when(contractStateChangeRepository.findModifiedByConsensusTimestamp(CONSENSUS_TIMESTAMP, 1, 1))
                .thenReturn(List.of(ContractStateChange.builder()
                        .contractId(CALLER.getId())
                        .slot(new byte[] {0x02})
                        .valueRead(VALUE_READ)
                        .valueWritten(VALUE_WRITTEN)
                        .build()));

        final var context = context(properties, true, true);
        collector.collect(context);

        assertThat(context.getPreStorageByContract()).containsKeys(RECIPIENT_CONTRACT.getId(), CALLER.getId());
    }

    @Test
    void collectMarksSuccessfulCryptoCreateChildrenAsCreated() {
        stubNoActions();
        when(transactionRepository.findSuccessfulCryptoCreateChildEntityIds(CONSENSUS_TIMESTAMP))
                .thenReturn(Arrays.asList(RECIPIENT_ACCOUNT.getId(), null, 0L));
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.empty());

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getCreatedIds()).containsExactly(RECIPIENT_ACCOUNT.getId());
        assertThat(context.getAccounts()).containsExactly(RECIPIENT_ACCOUNT.getId());
        assertThat(context.getPostNonces()).containsEntry(RECIPIENT_ACCOUNT.getId(), 0L);
    }

    @Test
    void collectDoesNothingWhenContractResultIsMissing() {
        stubNoActions();
        when(transactionRepository.findSuccessfulCryptoCreateChildEntityIds(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of());
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.empty());

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getAccounts()).isEmpty();
        verify(ethereumTransactionRepository, never()).findByConsensusTimestampAndPayerAccountId(anyLong(), any());
        verify(authorizationExtractor, never()).extractSigners(any(), any());
    }

    @Test
    void collectAddsSenderAndCreatedContractIds() {
        stubNoActions();
        when(transactionRepository.findSuccessfulCryptoCreateChildEntityIds(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of());
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(SENDER, PAYER, List.of(CREATED_CONTRACT.getId()), new byte[0])));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(CONSENSUS_TIMESTAMP, PAYER))
                .thenReturn(Optional.empty());

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getAccounts()).containsExactlyInAnyOrder(SENDER.getId(), CREATED_CONTRACT.getId());
        assertThat(context.getCreatedIds()).containsExactly(CREATED_CONTRACT.getId());
        assertThat(context.getPostNonces()).containsEntry(CREATED_CONTRACT.getId(), 0L);
    }

    @Test
    void collectAppliesFunctionResultContractNonces() {
        stubNoActions();
        stubNoCryptoCreates();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(
                        SENDER, EntityId.EMPTY, List.of(), createdContractNonceFunctionResult(CREATED_CONTRACT, 7L))));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getAccounts()).contains(CREATED_CONTRACT.getId());
        assertThat(context.getPostNonces()).containsEntry(CREATED_CONTRACT.getId(), 7L);
        verify(ethereumTransactionRepository, never()).findByConsensusTimestampAndPayerAccountId(anyLong(), any());
    }

    @Test
    void collectIgnoresNullAndEmptyFunctionResult() {
        stubNoActions();
        stubNoCryptoCreates();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(SENDER, EntityId.EMPTY, List.of(), null)))
                .thenReturn(Optional.of(contractResult(SENDER, EntityId.EMPTY, List.of(), new byte[0])));

        final var nullResultContext = context(false, false);
        collector.collect(nullResultContext);
        final var emptyResultContext = context(false, false);
        collector.collect(emptyResultContext);

        assertThat(nullResultContext.getPostNonces()).isEmpty();
        assertThat(emptyResultContext.getPostNonces()).isEmpty();
    }

    @Test
    void collectIgnoresEmptyContractIdInFunctionResultNonces() {
        stubNoActions();
        stubNoCryptoCreates();
        final var functionResult = ContractFunctionResult.newBuilder()
                .addContractNonces(ContractNonceInfo.newBuilder()
                        .setContractId(ContractID.getDefaultInstance())
                        .setNonce(3L))
                .build()
                .toByteArray();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(SENDER, EntityId.EMPTY, List.of(), functionResult)));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getPostNonces()).isEmpty();
        assertThat(context.getAccounts()).containsExactly(SENDER.getId());
    }

    @Test
    void collectDoesNotApplyEthereumSenderWhenEthTxIsMissing() {
        stubNoActions();
        stubNoCryptoCreates();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(SENDER, PAYER, List.of(), new byte[0])));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(CONSENSUS_TIMESTAMP, PAYER))
                .thenReturn(Optional.empty());

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).isEmpty();
        verify(authorizationExtractor, never()).extractSigners(any(), any());
    }

    @Test
    void collectAppliesEthereumSenderNonceAndExtractsAuthorizations() {
        stubNoActions();
        stubNoCryptoCreates();
        final var ethereumTransaction = EthereumTransaction.builder().nonce(9L).build();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(SENDER, PAYER, List.of(), new byte[0])));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(CONSENSUS_TIMESTAMP, PAYER))
                .thenReturn(Optional.of(ethereumTransaction));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).containsEntry(SENDER.getId(), 1L);
        assertThat(context.getPostNonces()).containsEntry(SENDER.getId(), 10L);
        verify(authorizationExtractor).extractSigners(context, ethereumTransaction);
    }

    @Test
    void collectAppliesSenderDeltaWhenEthNonceIsNull() {
        stubNoActions();
        stubNoCryptoCreates();
        final var ethereumTransaction =
                EthereumTransaction.builder().nonce(null).build();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(SENDER, PAYER, List.of(), new byte[0])));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(CONSENSUS_TIMESTAMP, PAYER))
                .thenReturn(Optional.of(ethereumTransaction));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).containsEntry(SENDER.getId(), 1L);
        assertThat(context.getPostNonces()).isEmpty();
        verify(authorizationExtractor).extractSigners(eq(context), any());
    }

    @Test
    void collectDoesNotApplyEthereumSenderNonceWhenSenderIsEmpty() {
        stubNoActions();
        stubNoCryptoCreates();
        final var ethereumTransaction = EthereumTransaction.builder().nonce(4L).build();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP))
                .thenReturn(Optional.of(contractResult(EntityId.EMPTY, PAYER, List.of(), new byte[0])));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(CONSENSUS_TIMESTAMP, PAYER))
                .thenReturn(Optional.of(ethereumTransaction));

        final var context = context(false, false);
        collector.collect(context);

        assertThat(context.getNonceDeltas()).isEmpty();
        assertThat(context.getPostNonces()).isEmpty();
        verify(authorizationExtractor).extractSigners(context, ethereumTransaction);
    }

    private void stubNoActions() {
        when(contractActionRepository.findByConsensusTimestampOrderByIndexAsc(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of());
    }

    private void stubNoNonceSources() {
        stubNoCryptoCreates();
        when(contractResultRepository.findById(CONSENSUS_TIMESTAMP)).thenReturn(Optional.empty());
    }

    private void stubNoCryptoCreates() {
        when(transactionRepository.findSuccessfulCryptoCreateChildEntityIds(CONSENSUS_TIMESTAMP))
                .thenReturn(List.of());
    }

    private static PrestateContext context(final boolean diffMode, final boolean storage) {
        return context(new PrestateProperties(), diffMode, storage);
    }

    private static PrestateContext context(
            final PrestateProperties properties, final boolean diffMode, final boolean storage) {
        return new PrestateContext(
                properties,
                CONSENSUS_TIMESTAMP,
                new PrestateRequest(
                        new TransactionHashParameter(Bytes.repeat((byte) 1, 32)), diffMode, false, storage));
    }

    private static ContractAction callAction(
            final CallOperationType operationType,
            final int callDepth,
            final long value,
            final ResultDataCase resultDataCase) {
        return ContractAction.builder()
                .caller(CALLER)
                .recipientAccount(RECIPIENT_ACCOUNT)
                .recipientContract(RECIPIENT_CONTRACT)
                .callOperationType(operationType.getNumber())
                .callDepth(callDepth)
                .value(value)
                .resultDataType(resultDataCase.getNumber())
                .build();
    }

    private static ContractStateChange stateChange(final byte[] valueRead, final byte[] valueWritten) {
        return ContractStateChange.builder()
                .contractId(RECIPIENT_CONTRACT.getId())
                .slot(STORAGE_SLOT)
                .valueRead(valueRead)
                .valueWritten(valueWritten)
                .build();
    }

    private static ContractResult contractResult(
            final EntityId senderId,
            final EntityId payerAccountId,
            final List<Long> createdContractIds,
            final byte[] functionResult) {
        return ContractResult.builder()
                .consensusTimestamp(CONSENSUS_TIMESTAMP)
                .senderId(senderId)
                .payerAccountId(payerAccountId)
                .createdContractIds(new ArrayList<>(createdContractIds))
                .functionResult(functionResult)
                .build();
    }

    private static byte[] createdContractNonceFunctionResult(final EntityId contractId, final long nonce) {
        return ContractFunctionResult.newBuilder()
                .addContractNonces(ContractNonceInfo.newBuilder()
                        .setContractId(ContractID.newBuilder()
                                .setShardNum(contractId.getShard())
                                .setRealmNum(contractId.getRealm())
                                .setContractNum(contractId.getNum()))
                        .setNonce(nonce))
                .build()
                .toByteArray();
    }
}
