// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.services.stream.proto.CallOperationType.OP_CREATE;
import static com.hedera.services.stream.proto.CallOperationType.OP_CREATE2;
import static com.hedera.services.stream.proto.CallOperationType.OP_DELEGATECALL;
import static com.hedera.services.stream.proto.CallOperationType.OP_STATICCALL;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.ERROR;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import jakarta.inject.Named;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractStateChangeRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Named
@CustomLog
@RequiredArgsConstructor
@NullMarked
final class TouchedAccountCollector {

    private final AuthorizationExtractor authorizationExtractor;
    private final ContractActionRepository contractActionRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final TransactionRepository transactionRepository;

    void collect(final PrestateContext prestateContext) {
        final long consensusTimestamp = prestateContext.getConsensusTimestamp();
        collectFromActions(prestateContext, consensusTimestamp);
        collectFromStateChanges(prestateContext, consensusTimestamp);
        collectFromNonceSources(prestateContext, consensusTimestamp);
    }

    private void collectFromActions(final PrestateContext prestateContext, final long consensusTimestamp) {
        final var actions = contractActionRepository.findByConsensusTimestampOrderByIndexAsc(consensusTimestamp);
        final boolean diffMode = prestateContext.getPrestateRequest().diffMode();

        for (final var action : actions) {
            if (prestateContext.isFull()) {
                break;
            }
            prestateContext.addAccount(action.getCaller());
            prestateContext.addAccount(action.getRecipientAccount());
            prestateContext.addAccount(action.getRecipientContract());
            applyCreateNonceDelta(prestateContext, action);
            if (diffMode) {
                applyBalanceTransfer(prestateContext, action);
            }
        }
    }

    private static void applyCreateNonceDelta(final PrestateContext prestateContext, final ContractAction action) {
        final int opType = action.getCallOperationType();
        if (opType != OP_CREATE.getNumber() && opType != OP_CREATE2.getNumber()) {
            return;
        }
        // Depth-0 CREATE is either an Ethereum deployment (already counted as the sender +1) or a HAPI
        // ContractCreate (does not increment the payer ethereum nonce).
        if (action.getCallDepth() <= 0) {
            return;
        }
        final var caller = action.getCaller();
        if (EntityId.isEmpty(caller)) {
            return;
        }
        prestateContext.addNonceDelta(caller.getId(), 1L);
    }

    private static void applyBalanceTransfer(final PrestateContext prestateContext, final ContractAction action) {
        final int resultType = action.getResultDataType();
        if (resultType == REVERT_REASON.getNumber() || resultType == ERROR.getNumber()) {
            return;
        }

        final int opType = action.getCallOperationType();
        if (opType == OP_DELEGATECALL.getNumber() || opType == OP_STATICCALL.getNumber()) {
            return;
        }

        final long value = action.getValue();
        if (value == 0) {
            return;
        }

        addBalanceTransfer(prestateContext, action.getCaller(), -value);
        addBalanceTransfer(prestateContext, recipient(action), value);
    }

    private static void addBalanceTransfer(
            final PrestateContext prestateContext, final @Nullable EntityId accountId, final long value) {
        if (accountId == null || EntityId.isEmpty(accountId)) {
            return;
        }
        prestateContext.addBalanceTransfer(accountId.getId(), value);
    }

    private static @Nullable EntityId recipient(final ContractAction action) {
        if (!EntityId.isEmpty(action.getRecipientAccount())) {
            return action.getRecipientAccount();
        }
        if (!EntityId.isEmpty(action.getRecipientContract())) {
            return action.getRecipientContract();
        }
        return null;
    }

    private void collectFromStateChanges(final PrestateContext prestateContext, final long consensusTimestamp) {
        if (!prestateContext.getPrestateRequest().storage()) {
            return;
        }

        final var properties = prestateContext.getPrestateProperties();
        final int maxPages = properties.getStateChangeMaxPages();
        final int pageSize = properties.getStateChangePageSize();
        final boolean diffMode = prestateContext.getPrestateRequest().diffMode();

        for (int page = 0; page < maxPages; page++) {
            final int offset = page * pageSize;
            final var stateChanges = diffMode
                    ? contractStateChangeRepository.findModifiedByConsensusTimestamp(
                            consensusTimestamp, pageSize, offset)
                    : contractStateChangeRepository.findByConsensusTimestamp(consensusTimestamp, pageSize, offset);

            for (final var stateChange : stateChanges) {
                final long contractId = stateChange.getContractId();
                prestateContext.addPreStorageSlot(contractId, stateChange.getSlot(), stateChange.getValueRead());
                prestateContext.addPostStorageSlot(contractId, stateChange.getSlot(), stateChange.getValueWritten());
            }
        }
    }

    private void collectFromNonceSources(final PrestateContext prestateContext, final long consensusTimestamp) {
        markCreated(
                prestateContext, transactionRepository.findSuccessfulCryptoCreateChildEntityIds(consensusTimestamp));

        final var contractResult =
                contractResultRepository.findById(consensusTimestamp).orElse(null);
        if (contractResult == null) {
            return;
        }

        prestateContext.addAccount(contractResult.getSenderId());
        markCreated(prestateContext, contractResult.getCreatedContractIds());
        applyFunctionResultNonces(prestateContext, contractResult);
        applyEthereumSenderNonce(prestateContext, contractResult);
    }

    private static void markCreated(final PrestateContext prestateContext, final List<Long> accountIds) {
        for (final var accountId : accountIds) {
            if (accountId != null) {
                prestateContext.markCreated(accountId);
            }
        }
    }

    private static void applyFunctionResultNonces(
            final PrestateContext prestateContext, final ContractResult contractResult) {
        final var functionResultBytes = contractResult.getFunctionResult();
        if (functionResultBytes == null || functionResultBytes.length == 0) {
            return;
        }

        final ContractFunctionResult functionResult;
        try {
            functionResult = ContractFunctionResult.parseFrom(functionResultBytes);
        } catch (final InvalidProtocolBufferException e) {
            log.debug("Unable to parse contract function result at {}", contractResult.getConsensusTimestamp(), e);
            return;
        }

        for (final var nonceInfo : functionResult.getContractNoncesList()) {
            final var contractId = EntityId.of(nonceInfo.getContractId());
            if (EntityId.isEmpty(contractId)) {
                continue;
            }
            prestateContext.addAccount(contractId);
            prestateContext.putPostNonce(contractId.getId(), nonceInfo.getNonce());
        }
    }

    private void applyEthereumSenderNonce(final PrestateContext prestateContext, final ContractResult contractResult) {
        final var payerAccountId = contractResult.getPayerAccountId();
        if (EntityId.isEmpty(payerAccountId)) {
            return;
        }

        final var ethereumTransaction = ethereumTransactionRepository
                .findByConsensusTimestampAndPayerAccountId(prestateContext.getConsensusTimestamp(), payerAccountId)
                .orElse(null);
        if (ethereumTransaction == null) {
            return;
        }

        if (!EntityId.isEmpty(contractResult.getSenderId())) {
            final long senderId = contractResult.getSenderId().getId();
            prestateContext.addNonceDelta(senderId, 1L);
            if (ethereumTransaction.getNonce() != null) {
                prestateContext.putPostNonce(senderId, ethereumTransaction.getNonce() + 1);
            }
        }

        authorizationExtractor.extractSigners(prestateContext, ethereumTransaction);
    }
}
