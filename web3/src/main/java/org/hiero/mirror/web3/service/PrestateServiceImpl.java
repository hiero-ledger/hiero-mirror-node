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
import com.hedera.node.app.hapi.utils.ethereum.CodeDelegation;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
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
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
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
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final Comparator<PrestateAccountTrace> ACCOUNT_TRACE_COMPARATOR =
            Comparator.comparing(PrestateAccountTrace::getAddress);

    @FunctionalInterface
    private interface StateChangePageQuery {
        List<ContractStateChange> find(int limit, int offset);
    }

    @Override
    public PrestateResponse processPrestateCall(final PrestateRequest prestateRequest) {
        final var consensusTimestamp = resolveConsensusTimestamp(prestateRequest.transactionIdOrHashParameter());
        final var prestateContext = new PrestateContext(consensusTimestamp, prestateRequest);
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
        final var createdEntityById = loadCreatedEntities(createdIds);

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
            final var createdEntity = createdThisTx ? createdEntityById.get(accountId) : null;
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

            if (createdThisTx) {
                if (createdEntity == null) {
                    continue;
                }
                final var postAccountTrace = buildAccountTrace(
                        createdEntity,
                        postBalance,
                        postBytecodes,
                        takeStorage(postStorageByContract, accountId),
                        postNonce);
                postAccountTraces.add(postAccountTrace);
                continue;
            }

            if (preEntity == null) {
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

        final var entityById = new HashMap<Long, Entity>(entities.size());
        for (final var entity : entities) {
            entityById.put(entity.getId(), entity);
        }
        return entityById;
    }

    private Map<Long, Entity> loadCreatedEntities(final Set<Long> createdIds) {
        if (createdIds.isEmpty()) {
            return Map.of();
        }

        final var entityById = new HashMap<Long, Entity>(createdIds.size());
        for (final var entity : entityRepository.findAllById(createdIds)) {
            entityById.put(entity.getId(), entity);
        }
        return entityById;
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
        final var bytecodes = new HashMap<Long, byte[]>(contracts.size());
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

        final var balances = new HashMap<Long, Long>(accountIds.size());
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
        addHollowCreatedAccounts(prestateContext, consensusTimestamp);

        final var contractResult =
                contractResultRepository.findById(consensusTimestamp).orElse(null);
        if (contractResult == null) {
            return;
        }

        addCreatedContractIds(prestateContext, contractResult);
        prestateContext.addAccount(contractResult.getSenderId());
        applyFunctionResultNonces(prestateContext, contractResult);

        final var payerAccountId = contractResult.getPayerAccountId();
        if (payerAccountId == null) {
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
            prestateContext.addNonceDelta(senderId, 1L);
            if (ethereumTransaction.getNonce() != null) {
                prestateContext.mergePostNonce(senderId, ethereumTransaction.getNonce() + 1L);
            }
        }
        applyAuthorizationList(prestateContext, ethereumTransaction);
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

        if (functionResult.hasSignerNonce() && !EntityId.isEmpty(contractResult.getSenderId())) {
            prestateContext.putPostNonce(
                    contractResult.getSenderId().getId(),
                    functionResult.getSignerNonce().getValue());
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

    private void addCreatedContractIds(final PrestateContext prestateContext, final ContractResult contractResult) {
        final var createdContractIds = contractResult.getCreatedContractIds();
        if (createdContractIds == null || createdContractIds.isEmpty()) {
            return;
        }

        final int maxTouchedAccounts = prestateProperties.getMaxTouchedAccounts();
        for (final var createdContractId : createdContractIds) {
            if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
                return;
            }
            if (createdContractId != null) {
                prestateContext.addCreatedAccount(createdContractId);
            }
        }
    }

    private void addHollowCreatedAccounts(final PrestateContext prestateContext, final long consensusTimestamp) {
        final var hollowIds = transactionRepository.findSuccessfulCryptoCreateChildEntityIds(consensusTimestamp);
        if (hollowIds.isEmpty()) {
            return;
        }

        final int maxTouchedAccounts = prestateProperties.getMaxTouchedAccounts();
        for (final var hollowId : hollowIds) {
            if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
                return;
            }
            if (hollowId != null) {
                prestateContext.addCreatedAccount(hollowId);
            }
        }
    }

    private void applyAuthorizationList(
            final PrestateContext prestateContext, final EthereumTransaction ethereumTransaction) {
        final var authorizations = ethereumTransaction.getAuthorizationList();
        if (authorizations == null || authorizations.isEmpty()) {
            return;
        }

        final int maxTouchedAccounts = prestateProperties.getMaxTouchedAccounts();
        if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
            return;
        }

        final var recoveredAuthorizations = recoverAuthorizations(authorizations);
        if (recoveredAuthorizations.isEmpty()) {
            return;
        }

        final var recoveredAddresses = new ArrayList<byte[]>(recoveredAuthorizations.size());
        for (final var recovered : recoveredAuthorizations) {
            recoveredAddresses.add(recovered.address());
        }

        final var entities = entityRepository.findActiveByEvmAddressesOrAliasesAndTimestamp(
                recoveredAddresses, prestateContext.getConsensusTimestamp());
        if (entities.isEmpty()) {
            return;
        }

        final var entityByAddress = toEntityByAddress(entities);
        for (final var recoveredAuthorization : recoveredAuthorizations) {
            if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
                break;
            }
            final var entity = entityByAddress.get(Bytes.wrap(recoveredAuthorization.address()));
            if (entity == null) {
                continue;
            }
            final long authorityId = entity.getId();
            prestateContext.addAccount(entity.toEntityId());
            prestateContext.addNonceDelta(authorityId, 1L);
            prestateContext.mergePostNonce(authorityId, recoveredAuthorization.nonce() + 1L);
        }
    }

    private List<RecoveredAuthorization> recoverAuthorizations(final List<Authorization> authorizations) {
        final var recovered = new ArrayList<RecoveredAuthorization>(authorizations.size());
        for (final var authorization : authorizations) {
            final var recoveredAuthorization = recoverAuthorization(authorization);
            if (recoveredAuthorization != null) {
                recovered.add(recoveredAuthorization);
            }
        }
        return recovered;
    }

    private @Nullable RecoveredAuthorization recoverAuthorization(final Authorization authorization) {
        final var nonce = authorization.getNonce();
        if (nonce == null) {
            return null;
        }

        final var codeDelegation = toCodeDelegation(authorization);
        if (codeDelegation == null) {
            return null;
        }

        final var recovered = EthTxSigs.extractAuthoritySignature(codeDelegation);
        if (recovered.isEmpty()) {
            return null;
        }

        final var address = recovered.get().address();
        if (address == null || address.length != EVM_ADDRESS_LENGTH) {
            return null;
        }
        return new RecoveredAuthorization(address, nonce);
    }

    private record RecoveredAuthorization(byte[] address, long nonce) {}

    private Map<Bytes, Entity> toEntityByAddress(final List<Entity> entities) {
        final var entityByAddress = new HashMap<Bytes, Entity>(entities.size() * 2);
        for (final var entity : entities) {
            final var evmAddress = entity.getEvmAddress();
            if (evmAddress != null) {
                entityByAddress.put(Bytes.wrap(evmAddress), entity);
            }
            final var alias = entity.getAlias();
            if (alias != null) {
                entityByAddress.put(Bytes.wrap(alias), entity);
            }
        }
        return entityByAddress;
    }

    private static @Nullable CodeDelegation toCodeDelegation(final Authorization authorization) {
        if (authorization.getNonce() == null) {
            return null;
        }
        try {
            return new CodeDelegation(
                    parseHex(authorization.getChainId()),
                    parseHex(authorization.getAddress()),
                    authorization.getNonce(),
                    parseYParity(authorization.getYParity()),
                    parseHex(authorization.getR()),
                    parseHex(authorization.getS()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] parseHex(final @Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        var stripped = hex;
        if (stripped.startsWith(HEX_PREFIX) || stripped.startsWith("0X")) {
            stripped = stripped.substring(HEX_PREFIX.length());
        }
        if (stripped.isEmpty()) {
            return new byte[0];
        }
        if ((stripped.length() & 1) == 1) {
            stripped = "0" + stripped;
        }
        return HEX_FORMAT.parseHex(stripped);
    }

    private static int parseYParity(final @Nullable String yParity) {
        final var bytes = parseHex(yParity);
        if (bytes.length == 0) {
            return 0;
        }
        return bytes[bytes.length - 1] & 0x01;
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

        final StateChangePageQuery query = diffMode
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
