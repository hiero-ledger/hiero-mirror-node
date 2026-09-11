// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS_BIGINT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.utils.Constants.MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.PrestateAccountTrace;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.PrestateProperties;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractStateChangeRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Named
@CustomLog
@RequiredArgsConstructor
@NullMarked
final class PrestateServiceImpl implements PrestateService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final AuthorizationExtractor authorizationExtractor;
    private final ContractActionRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EntityRepository entityRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final SystemEntity systemEntity;
    private final PrestateProperties prestateProperties;

    private static final int RESULT_REVERT = 12;
    private static final int RESULT_ERROR = 13;
    private static final int OP_DELEGATECALL = 3;
    private static final int OP_STATICCALL = 4;
    private static final int OP_CREATE = 5;
    private static final int OP_CREATE2 = 6;
    private static final Comparator<PrestateAccountTrace> ACCOUNT_TRACE_COMPARATOR =
            Comparator.comparing(PrestateAccountTrace::getAddress);

    @FunctionalInterface
    private interface StateChangePageQuery {
        List<ContractStateChange> find(int limit, int offset);
    }

    @Override
    public PrestateResponse processPrestateCall(final PrestateRequest prestateRequest) {
        final var consensusTimestamp = resolveConsensusTimestamp(prestateRequest.transactionIdOrHashParameter());
        final var prestateContext = new PrestateContext(prestateProperties, consensusTimestamp, prestateRequest);
        markTouchedAccounts(prestateContext);
        return loadAccountTraces(prestateContext);
    }

    private PrestateResponse loadAccountTraces(final PrestateContext prestateContext) {
        final var accounts = prestateContext.getAccounts();
        final var consensusTimestamp = prestateContext.getConsensusTimestamp();
        final var timestampBeforeTransaction = consensusTimestamp - 1;
        final var diffMode = prestateContext.getPrestateRequest().diffMode();
        final var preAccountTraces = new ArrayList<PrestateAccountTrace>(accounts.size());
        final List<PrestateAccountTrace> postAccountTraces = diffMode ? new ArrayList<>(accounts.size()) : List.of();

        if (accounts.isEmpty()) {
            return buildResponse(preAccountTraces, postAccountTraces, diffMode);
        }

        final var createdIds = prestateContext.getCreatedIds();
        final var preEntityById =
                toEntityById(entityRepository.findActiveByIdsAndTimestamp(accounts, timestampBeforeTransaction));
        final var currentEntityById = loadEntitiesById(accounts);

        final var preBalances = loadBalances(accounts, timestampBeforeTransaction);
        final var balanceTransfers = prestateContext.getBalanceTransfers();

        final var preBytecodes = prestateContext.getPrestateRequest().code()
                ? loadBytecodes(accounts, timestampBeforeTransaction)
                : Map.<Long, byte[]>of();
        final var postBytecodes =
                diffMode && prestateContext.getPrestateRequest().code()
                        ? loadBytecodes(accounts, consensusTimestamp)
                        : Map.<Long, byte[]>of();
        final var preStorageByContract = prestateContext.getPrestateRequest().storage()
                ? prestateContext.getPreStorageByContract()
                : Map.<Long, Map<String, String>>of();
        final var postStorageByContract =
                diffMode && prestateContext.getPrestateRequest().storage()
                        ? prestateContext.getPostStorageByContract()
                        : Map.<Long, Map<String, String>>of();

        for (final var accountId : accounts) {
            final boolean createdThisTx = createdIds.contains(accountId);
            final var currentEntity = currentEntityById.get(accountId);
            final var preEntity = createdThisTx ? null : preEntityById.get(accountId);
            final var preBalance = preBalances.getOrDefault(accountId, 0L);
            final long postNonce = resolvePostNonce(prestateContext, accountId);
            final long preNonce =
                    Math.max(0L, postNonce - prestateContext.getNonceDeltas().getOrDefault(accountId, 0L));

            if (!diffMode) {
                if (preEntity == null) {
                    continue;
                }
                preAccountTraces.add(buildAccountTrace(
                        preEntity, preBalance, preBytecodes, takeStorage(preStorageByContract, accountId), preNonce));
                continue;
            }

            final var transfer = balanceTransfers.getOrDefault(accountId, 0L);
            final var postBalance = preBalance + transfer;
            final boolean deletedThisTx = isDeletedThisTransaction(currentEntity, consensusTimestamp);

            if (createdThisTx) {
                if (currentEntity == null || deletedThisTx) {
                    continue;
                }
                postAccountTraces.add(buildAccountTrace(
                        currentEntity,
                        postBalance,
                        postBytecodes,
                        takeStorage(postStorageByContract, accountId),
                        postNonce));
                continue;
            }

            if (preEntity == null) {
                continue;
            }

            if (deletedThisTx) {
                preAccountTraces.add(buildAccountTrace(
                        preEntity, preBalance, preBytecodes, takeStorage(preStorageByContract, accountId), preNonce));
                continue;
            }

            final var hasBalanceChange = transfer != 0;
            final var hasStorageChange =
                    preStorageByContract.containsKey(accountId) || postStorageByContract.containsKey(accountId);
            final var hasBytecodeChange = prestateContext.getPrestateRequest().code()
                    && !Arrays.equals(preBytecodes.get(accountId), postBytecodes.get(accountId));
            final var hasNonceChange = preNonce != postNonce;

            if (hasBalanceChange || hasStorageChange || hasBytecodeChange || hasNonceChange) {
                final var preAccountTrace = buildAccountTrace(
                        preEntity, preBalance, preBytecodes, takeStorage(preStorageByContract, accountId), preNonce);
                final var postAccountTrace = buildAccountTrace(
                        preEntity,
                        postBalance,
                        postBytecodes,
                        takeStorage(postStorageByContract, accountId),
                        postNonce);
                if (!Objects.equals(preAccountTrace, postAccountTrace)) {
                    preAccountTraces.add(preAccountTrace);
                    postAccountTraces.add(postAccountTrace);
                }
            }
        }

        return buildResponse(preAccountTraces, postAccountTraces, diffMode);
    }

    private PrestateResponse buildResponse(
            final List<PrestateAccountTrace> preAccountTraces,
            final List<PrestateAccountTrace> postAccountTraces,
            final boolean diffMode) {
        preAccountTraces.sort(ACCOUNT_TRACE_COMPARATOR);
        final var response = new PrestateResponse();
        response.setPre(preAccountTraces);
        response.setPost(List.of());

        if (diffMode) {
            postAccountTraces.sort(ACCOUNT_TRACE_COMPARATOR);
            response.setPost(postAccountTraces);
        }

        return response;
    }

    private Map<String, String> takeStorage(
            final Map<Long, Map<String, String>> storageByContract, final long accountId) {
        if (storageByContract.isEmpty()) {
            return Map.of();
        }
        final var storage = storageByContract.remove(accountId);
        return storage != null ? storage : Map.of();
    }

    private Map<Long, Entity> toEntityById(final List<Entity> entities) {
        if (entities.isEmpty()) {
            return Map.of();
        }

        final var entityById = HashMap.<Long, Entity>newHashMap(entities.size());
        for (final var entity : entities) {
            entityById.put(entity.getId(), entity);
        }
        return entityById;
    }

    private Map<Long, Entity> loadEntitiesById(final Set<Long> entityIds) {
        if (entityIds.isEmpty()) {
            return Map.of();
        }

        final var entityById = HashMap.<Long, Entity>newHashMap(entityIds.size());
        for (final var entity : entityRepository.findAllById(entityIds)) {
            entityById.put(entity.getId(), entity);
        }
        return entityById;
    }

    private static boolean isDeletedThisTransaction(final @Nullable Entity entity, final long consensusTimestamp) {
        if (entity == null || !Boolean.TRUE.equals(entity.getDeleted())) {
            return false;
        }
        final var deletedAt = entity.getTimestampLower();
        return deletedAt != null && deletedAt == consensusTimestamp;
    }

    private PrestateAccountTrace buildAccountTrace(
            final Entity entity,
            final Long balance,
            final Map<Long, byte[]> bytecodes,
            final Map<String, String> storage,
            final long nonce) {
        final var entityId = entity.getId();
        final var accountTrace = new PrestateAccountTrace();
        accountTrace.setAddress(resolveAddress(entity));
        accountTrace.setBalance(HEX_PREFIX
                + BigInteger.valueOf(balance)
                        .multiply(WEIBARS_TO_TINYBARS_BIGINT)
                        .toString(16));

        accountTrace.setNonce(nonce);

        if (entity.getType() == CONTRACT) {
            final var bytecode = bytecodes.get(entityId);
            if (bytecode != null && bytecode.length > 0) {
                accountTrace.setCode(Bytes.wrap(bytecode).toHexString());
            }
            accountTrace.setStorage(storage);
        }

        return accountTrace;
    }

    private Map<Long, byte[]> loadBytecodes(final Set<Long> entityIds, final long timestamp) {
        if (entityIds.isEmpty()) {
            return Map.of();
        }

        final var contracts = contractRepository.findByIdsAndConsensusTimestamp(entityIds, timestamp);
        final var bytecodes = HashMap.<Long, byte[]>newHashMap(contracts.size());
        for (final var contract : contracts) {
            final var runtimeBytecode = contract.getRuntimeBytecode();
            if (runtimeBytecode != null) {
                bytecodes.put(contract.getId(), runtimeBytecode);
            }
        }
        return bytecodes;
    }

    private Map<Long, Long> loadBalances(final Set<Long> accountIds, final long blockTimestamp) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }

        final var balances = HashMap.<Long, Long>newHashMap(accountIds.size());
        final long treasuryAccountId = systemEntity.treasuryAccount().getId();
        for (final long accountId : accountIds) {
            final long balance = accountBalanceRepository
                    .findHistoricalAccountBalanceUpToTimestamp(accountId, blockTimestamp, treasuryAccountId)
                    .orElse(0L);
            balances.put(accountId, balance);
        }
        return balances;
    }

    private void markTouchedAccounts(final PrestateContext prestateContext) {
        final var consensusTimestamp = prestateContext.getConsensusTimestamp();
        populateTouchedEntitiesFromActions(prestateContext, consensusTimestamp);
        populateTouchedEntitiesFromStateChanges(prestateContext, consensusTimestamp);
        populateTouchedEntitiesFromNonceSources(prestateContext, consensusTimestamp);
    }

    private void populateTouchedEntitiesFromNonceSources(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var hollowIds = transactionRepository.findSuccessfulCryptoCreateChildEntityIds(consensusTimestamp);
        populateCreatedAccountsWithNonce(prestateContext, hollowIds);

        final var contractResult =
                contractResultRepository.findById(consensusTimestamp).orElse(null);
        if (contractResult == null) {
            return;
        }

        prestateContext.addAccount(contractResult.getSenderId());
        populateCreatedAccountsWithNonce(prestateContext, contractResult.getCreatedContractIds());
        applyFunctionResultNonces(prestateContext, contractResult);

        final var payerAccountId = contractResult.getPayerAccountId();
        if (EntityId.isEmpty(payerAccountId)) {
            return;
        }

        final var ethereumTransaction = ethereumTransactionRepository
                .findByConsensusTimestampAndPayerAccountId(consensusTimestamp, payerAccountId)
                .orElse(null);
        if (ethereumTransaction == null) {
            return;
        }

        if (!EntityId.isEmpty(contractResult.getSenderId())) {
            final long senderId = contractResult.getSenderId().getId();
            // ethereumTransaction.getNonce() has the nonce before the transaction execution and we increment
            // this nonce by 1 for the postAccountTrace tracking, so we should add a delta of 1 to decrement, to get
            // the proper preAccountTrace tracking
            prestateContext.addNonceDelta(senderId, 1L);
            if (ethereumTransaction.getNonce() != null) {
                prestateContext.putPostNonce(senderId, ethereumTransaction.getNonce() + 1);
            }
        }

        authorizationExtractor.extractSigners(prestateContext, ethereumTransaction);
    }

    private void applyFunctionResultNonces(final PrestateContext prestateContext, final ContractResult contractResult) {
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

        final var contractNonces = functionResult.getContractNoncesList();
        for (final var nonceInfo : contractNonces) {
            final var contractId = EntityId.of(nonceInfo.getContractId());
            if (EntityId.isEmpty(contractId)) {
                continue;
            }
            prestateContext.addAccount(contractId);
            prestateContext.putPostNonce(contractId.getId(), nonceInfo.getNonce());
        }
    }

    private void populateCreatedAccountsWithNonce(final PrestateContext prestateContext, final List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return;
        }

        final int maxTouchedAccounts = prestateProperties.getMaxTouchedAccounts();
        for (final var accountId : accountIds) {
            if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
                return;
            }
            if (accountId != null) {
                prestateContext.addCreatedAccount(accountId);
                prestateContext.putPostNonce(accountId, 0L);
            }
        }
    }

    private long resolvePostNonce(final PrestateContext prestateContext, final long accountId) {
        return prestateContext.getPostNonces().getOrDefault(accountId, 0L);
    }

    private void applyCreateNonceDelta(final PrestateContext prestateContext, final ContractAction action) {
        final int opType = action.getCallOperationType();
        if (opType != OP_CREATE && opType != OP_CREATE2) {
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

    private void populateTouchedEntitiesFromActions(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var actions = contractActionRepository.findByConsensusTimestampOrderByIndexAsc(consensusTimestamp);
        final boolean diffMode = prestateContext.getPrestateRequest().diffMode();
        final int maxTouchedAccounts = prestateProperties.getMaxTouchedAccounts();

        for (final var action : actions) {
            if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
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

    private void applyBalanceTransfer(final PrestateContext prestateContext, final ContractAction action) {
        final int resultType = action.getResultDataType();
        if (resultType == RESULT_REVERT || resultType == RESULT_ERROR) {
            return;
        }

        final int opType = action.getCallOperationType();
        if (opType == OP_DELEGATECALL || opType == OP_STATICCALL) {
            return;
        }

        final long value = action.getValue();
        if (value == 0) {
            return;
        }

        addBalanceTransfer(prestateContext, action.getCaller(), -value);
        addBalanceTransfer(prestateContext, getRecipient(action), value);
    }

    private void addBalanceTransfer(
            final PrestateContext prestateContext, final @Nullable EntityId accountId, final long value) {
        if (accountId == null || EntityId.isEmpty(accountId)) {
            return;
        }
        prestateContext.addBalanceTransfer(accountId.getId(), value);
    }

    private @Nullable EntityId getRecipient(final ContractAction action) {
        if (!EntityId.isEmpty(action.getRecipientAccount())) {
            return action.getRecipientAccount();
        }
        if (!EntityId.isEmpty(action.getRecipientContract())) {
            return action.getRecipientContract();
        }
        return null;
    }

    private void populateTouchedEntitiesFromStateChanges(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var includeStorage = prestateContext.getPrestateRequest().storage();

        if (!includeStorage) {
            return;
        }

        final var diffMode = prestateContext.getPrestateRequest().diffMode();

        final PrestateServiceImpl.StateChangePageQuery query = diffMode
                ? (limit, offset) -> contractStateChangeRepository.findModifiedByConsensusTimestamp(
                        consensusTimestamp, limit, offset)
                : (limit, offset) ->
                        contractStateChangeRepository.findByConsensusTimestamp(consensusTimestamp, limit, offset);
        populateStateChanges(prestateContext, query);
    }

    private void populateStateChanges(
            final PrestateContext prestateContext, final StateChangePageQuery stateChangePageQuery) {
        final int maxPages = prestateProperties.getStateChangeMaxPages();
        final int pageSize = prestateProperties.getStateChangePageSize();
        for (int page = 0; page < maxPages; page++) {
            final int offset = page * pageSize;
            final var stateChanges = stateChangePageQuery.find(pageSize, offset);

            for (final var stateChange : stateChanges) {
                final var contractId = stateChange.getContractId();
                prestateContext.addPreStorageSlot(contractId, stateChange.getSlot(), stateChange.getValueRead());
                prestateContext.addPostStorageSlot(contractId, stateChange.getSlot(), stateChange.getValueWritten());
            }
        }
    }

    private String resolveAddress(final Entity entity) {
        final var evmAddress = entity.getEvmAddress();
        if (evmAddress != null && evmAddress.length == EVM_ADDRESS_LENGTH) {
            return HEX_PREFIX + bytesToHex(evmAddress);
        }
        final var alias = entity.getAlias();
        if (alias != null && alias.length == EVM_ADDRESS_LENGTH) {
            return HEX_PREFIX + bytesToHex(alias);
        }
        return HEX_PREFIX + bytesToHex(toEvmAddress(entity.toEntityId()));
    }

    private long resolveConsensusTimestamp(final TransactionIdOrHashParameter transactionIdOrHash) {
        return switch (transactionIdOrHash) {
            case TransactionHashParameter transactionHash ->
                contractTransactionHashRepository
                        .findByHash(transactionHash.hash().toArrayUnsafe())
                        .orElseThrow(() -> new EntityNotFoundException("Contract transaction hash not found."))
                        .getConsensusTimestamp();
            case TransactionIdParameter transactionId -> {
                final var validStartNs = convertToNanosMax(transactionId.validStart());
                final var payerAccountId = transactionId.payerAccountId();
                final var transaction = transactionRepository
                        .findByTransactionId(
                                payerAccountId.getId(),
                                validStartNs,
                                validStartNs,
                                validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS)
                        .orElseThrow(() -> new EntityNotFoundException("Transaction not found."));

                yield transaction.getConsensusTimestamp();
            }
        };
    }
}
