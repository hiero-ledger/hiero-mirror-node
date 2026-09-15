// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS_BIGINT;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.utils.Constants.MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.rest.model.PrestateAccountTrace;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.PrestateProperties;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Named
@RequiredArgsConstructor
@NullMarked
final class PrestateServiceImpl implements PrestateService {

    private static final Comparator<PrestateAccountTrace> ACCOUNT_TRACE_COMPARATOR =
            Comparator.comparing(PrestateAccountTrace::getAddress);

    private final AccountBalanceRepository accountBalanceRepository;
    private final ContractRepository contractRepository;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EntityRepository entityRepository;
    private final PrestateProperties prestateProperties;
    private final SystemEntity systemEntity;
    private final TouchedAccountCollector touchedAccountCollector;
    private final TransactionRepository transactionRepository;

    private enum DiffRole {
        SKIP,
        PRESTATE_ONLY,
        CREATED,
        DELETED,
        MODIFIED
    }

    private record AccountSnapshot(
            Map<Long, Entity> preEntities,
            Map<Long, Entity> currentEntities,
            Map<Long, Long> preBalances,
            Map<Long, byte[]> preBytecodes,
            Map<Long, byte[]> postBytecodes,
            Map<Long, Map<String, String>> preStorage,
            Map<Long, Map<String, String>> postStorage) {}

    @Override
    public PrestateResponse processPrestateCall(final PrestateRequest prestateRequest) {
        final var consensusTimestamp = resolveConsensusTimestamp(prestateRequest.transactionIdOrHashParameter());
        final var prestateContext = new PrestateContext(prestateProperties, consensusTimestamp, prestateRequest);
        touchedAccountCollector.collect(prestateContext);
        return loadAccountTraces(prestateContext);
    }

    private PrestateResponse loadAccountTraces(final PrestateContext prestateContext) {
        final var accounts = prestateContext.getAccounts();
        final boolean diffMode = prestateContext.getPrestateRequest().diffMode();
        final var preAccountTraces = new ArrayList<PrestateAccountTrace>(accounts.size());
        final List<PrestateAccountTrace> postAccountTraces =
                diffMode ? new ArrayList<>(accounts.size()) : new ArrayList<>();

        if (accounts.isEmpty()) {
            return buildResponse(preAccountTraces, postAccountTraces, diffMode);
        }

        final var snapshot = loadSnapshot(prestateContext);
        final var createdIds = prestateContext.getCreatedIds();
        final long consensusTimestamp = prestateContext.getConsensusTimestamp();

        for (final var accountId : accounts) {
            final boolean createdThisTx = createdIds.contains(accountId);
            final var currentEntity = snapshot.currentEntities().get(accountId);
            final var preEntity = createdThisTx ? null : snapshot.preEntities().get(accountId);
            if (!isAccountOrContract(preEntity) && !isAccountOrContract(currentEntity)) {
                continue;
            }
            final boolean deletedThisTx = isDeletedThisTransaction(currentEntity, consensusTimestamp);
            final var diffRole = resolveDiffRole(diffMode, createdThisTx, deletedThisTx, currentEntity, preEntity);
            if (diffRole == DiffRole.SKIP) {
                continue;
            }

            final long preBalance = snapshot.preBalances().getOrDefault(accountId, 0L);
            final long postBalance =
                    preBalance + prestateContext.getBalanceTransfers().getOrDefault(accountId, 0L);
            final long preNonce = prestateContext.preNonce(accountId);
            final long postNonce = prestateContext.postNonce(accountId);

            switch (diffRole) {
                case PRESTATE_ONLY, DELETED ->
                    preAccountTraces.add(buildAccountTrace(
                            Objects.requireNonNull(preEntity),
                            preBalance,
                            snapshot.preBytecodes(),
                            storage(snapshot.preStorage(), accountId),
                            preNonce));
                case CREATED ->
                    postAccountTraces.add(buildAccountTrace(
                            Objects.requireNonNull(currentEntity),
                            postBalance,
                            snapshot.postBytecodes(),
                            storage(snapshot.postStorage(), accountId),
                            postNonce));
                case MODIFIED ->
                    emitIfChanged(
                            preAccountTraces,
                            postAccountTraces,
                            buildAccountTrace(
                                    Objects.requireNonNull(preEntity),
                                    preBalance,
                                    snapshot.preBytecodes(),
                                    storage(snapshot.preStorage(), accountId),
                                    preNonce),
                            buildAccountTrace(
                                    preEntity,
                                    postBalance,
                                    snapshot.postBytecodes(),
                                    storage(snapshot.postStorage(), accountId),
                                    postNonce));
                case SKIP -> {
                    // resolved above
                }
            }
        }

        return buildResponse(preAccountTraces, postAccountTraces, diffMode);
    }

    private AccountSnapshot loadSnapshot(final PrestateContext prestateContext) {
        final var accounts = prestateContext.getAccounts();
        final long consensusTimestamp = prestateContext.getConsensusTimestamp();
        final long timestampBeforeTransaction = consensusTimestamp - 1;
        final var request = prestateContext.getPrestateRequest();
        final boolean diffMode = request.diffMode();

        final var preEntities =
                indexById(entityRepository.findActiveByIdsAndTimestamp(accounts, timestampBeforeTransaction));
        final var currentEntities = indexById(entityRepository.findAllById(accounts));
        final var preBalances = loadBalances(accounts, timestampBeforeTransaction);
        final var preBytecodes =
                request.code() ? loadBytecodes(accounts, timestampBeforeTransaction) : Map.<Long, byte[]>of();
        final var postBytecodes =
                diffMode && request.code() ? loadBytecodes(accounts, consensusTimestamp) : Map.<Long, byte[]>of();
        final var preStorage =
                request.storage() ? prestateContext.getPreStorageByContract() : Map.<Long, Map<String, String>>of();
        final var postStorage = diffMode && request.storage()
                ? prestateContext.getPostStorageByContract()
                : Map.<Long, Map<String, String>>of();
        return new AccountSnapshot(
                preEntities, currentEntities, preBalances, preBytecodes, postBytecodes, preStorage, postStorage);
    }

    private static DiffRole resolveDiffRole(
            final boolean diffMode,
            final boolean createdThisTx,
            final boolean deletedThisTx,
            final @Nullable Entity currentEntity,
            final @Nullable Entity preEntity) {
        if (!diffMode) {
            return preEntity == null ? DiffRole.SKIP : DiffRole.PRESTATE_ONLY;
        }
        if (createdThisTx) {
            return currentEntity == null || deletedThisTx ? DiffRole.SKIP : DiffRole.CREATED;
        }
        if (preEntity == null) {
            return DiffRole.SKIP;
        }
        return deletedThisTx ? DiffRole.DELETED : DiffRole.MODIFIED;
    }

    private static void emitIfChanged(
            final List<PrestateAccountTrace> preAccountTraces,
            final List<PrestateAccountTrace> postAccountTraces,
            final PrestateAccountTrace preAccountTrace,
            final PrestateAccountTrace postAccountTrace) {
        if (!Objects.equals(preAccountTrace, postAccountTrace)) {
            preAccountTraces.add(preAccountTrace);
            postAccountTraces.add(postAccountTrace);
        }
    }

    private static PrestateResponse buildResponse(
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

    private static Map<String, String> storage(
            final Map<Long, Map<String, String>> storageByContract, final long accountId) {
        final var storage = storageByContract.get(accountId);
        return storage != null ? storage : Map.of();
    }

    private static Map<Long, Entity> indexById(final Iterable<Entity> entities) {
        final var entityById = new HashMap<Long, Entity>();
        for (final var entity : entities) {
            entityById.put(entity.getId(), entity);
        }
        return entityById.isEmpty() ? Map.of() : entityById;
    }

    private static boolean isAccountOrContract(final @Nullable Entity entity) {
        return entity != null && (entity.getType() == ACCOUNT || entity.getType() == CONTRACT);
    }

    private static boolean isDeletedThisTransaction(final @Nullable Entity entity, final long consensusTimestamp) {
        if (entity == null || !Boolean.TRUE.equals(entity.getDeleted())) {
            return false;
        }
        final var deletedAt = entity.getTimestampLower();
        return deletedAt != null && deletedAt == consensusTimestamp;
    }

    private static PrestateAccountTrace buildAccountTrace(
            final Entity entity,
            final long balance,
            final Map<Long, byte[]> bytecodes,
            final Map<String, String> storage,
            final long nonce) {
        final var accountTrace = new PrestateAccountTrace();
        accountTrace.setAddress(resolveAddress(entity));
        accountTrace.setBalance(toWeibarHex(balance));
        accountTrace.setNonce(nonce);

        if (entity.getType() == CONTRACT) {
            final var bytecode = bytecodes.get(entity.getId());
            if (bytecode != null && bytecode.length > 0) {
                accountTrace.setCode(Bytes.wrap(bytecode).toHexString());
            }
            accountTrace.setStorage(storage);
        }

        return accountTrace;
    }

    private static String toWeibarHex(final long tinybars) {
        return HEX_PREFIX
                + BigInteger.valueOf(tinybars)
                        .multiply(WEIBARS_TO_TINYBARS_BIGINT)
                        .toString(16);
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

    private static String resolveAddress(final Entity entity) {
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
