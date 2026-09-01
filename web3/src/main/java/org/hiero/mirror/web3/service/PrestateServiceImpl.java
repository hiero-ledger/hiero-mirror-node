// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS_BIGINT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.utils.ByteUtils.wrapToWordSize;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.PrestateAccountTrace;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.repository.ContractStateChangeRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.jspecify.annotations.NullMarked;

@Named
@CustomLog
@RequiredArgsConstructor
@NullMarked
public final class PrestateServiceImpl implements PrestateService {

    public static final long MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS = 35 * 60 * NANOS_PER_SECOND;

    private final AccountBalanceRepository accountBalanceRepository;
    private final ContractActionRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EntityRepository entityRepository;
    private final TransactionRepository transactionRepository;
    private final SystemEntity systemEntity;
    private final Web3Properties web3Properties;

    private static final int RESULT_REVERT = 12;
    private static final int RESULT_ERROR = 13;
    private static final int OP_DELEGATECALL = 3;
    private static final int OP_STATICCALL = 4;
    private static final int STATE_CHANGE_PAGE_SIZE = 5000;
    private static final int STATE_CHANGE_MAX_PAGES = 10;
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

        final var preEntityById =
                toEntityById(entityRepository.findActiveByIdsAndTimestamp(accounts, timestampBeforeTransaction));
        final var postEntityById = diffMode
                ? toEntityById(entityRepository.findActiveByIdsAndTimestamp(accounts, consensusTimestamp))
                : Map.<Long, Entity>of();

        final var preBalances = loadBalances(accounts, timestampBeforeTransaction);
        final var balanceTransfers =
                diffMode ? calculateBalanceTransfers(prestateContext.getActions()) : Map.<Long, Long>of();

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
            final var preEntity = preEntityById.get(accountId);
            final var preBalance = preBalances.getOrDefault(accountId, 0L);

            if (!diffMode) {
                if (preEntity == null) {
                    continue;
                }
                preAccountTraces.add(buildAccountTrace(
                        preEntity, preBalance, preBytecodes, takeStorage(preStorageByContract, accountId)));
            } else {
                final var transfer = balanceTransfers.getOrDefault(accountId, 0L);
                final var postBalance = preBalance + transfer;
                final var postEntity = postEntityById.get(accountId);

                // Handle newly created accounts (exist in post but not in pre)
                if (preEntity == null && postEntity != null) {
                    final var postAccountTrace = buildAccountTrace(
                            postEntity, postBalance, postBytecodes, takeStorage(postStorageByContract, accountId));
                    final var emptyPreAccountTrace = new PrestateAccountTrace();
                    emptyPreAccountTrace.setAddress(postAccountTrace.getAddress());
                    preAccountTraces.add(emptyPreAccountTrace);
                    postAccountTraces.add(postAccountTrace);
                    continue;
                }

                if (preEntity == null) {
                    continue;
                }

                final var hasBalanceChange = transfer != 0;
                final var hasStorageChange = postStorageByContract.containsKey(accountId);
                final var hasBytecodeChange =
                        prestateContext.getPrestateRequest().code()
                                && !Arrays.equals(preBytecodes.get(accountId), postBytecodes.get(accountId));

                if (hasBalanceChange || hasStorageChange || hasBytecodeChange) {
                    final var preAccountTrace = buildAccountTrace(
                            preEntity, preBalance, preBytecodes, takeStorage(preStorageByContract, accountId));
                    final var postAccountTrace = buildAccountTrace(
                            preEntity, postBalance, postBytecodes, takeStorage(postStorageByContract, accountId));
                    if (!Objects.equals(preAccountTrace, postAccountTrace)) {
                        preAccountTraces.add(preAccountTrace);
                        postAccountTraces.add(postAccountTrace);
                    }
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

    private PrestateAccountTrace buildAccountTrace(
            final Entity entity,
            final Long balance,
            final Map<Long, byte[]> bytecodes,
            final Map<String, String> storage) {
        final var entityId = entity.getId();
        final var accountTrace = new PrestateAccountTrace();
        accountTrace.setAddress(resolveAddress(entity));
        accountTrace.setBalance(HEX_PREFIX
                + BigInteger.valueOf(balance)
                        .multiply(WEIBARS_TO_TINYBARS_BIGINT)
                        .toString(16));

        final var nonce = entity.getEthereumNonce();
        accountTrace.setNonce(nonce != null ? nonce : 0L);

        if (entity.getType() == CONTRACT) {
            final var bytecode = bytecodes.get(entityId);
            if (bytecode != null) {
                accountTrace.setCode(wrapToWordSize(bytecode));
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

    private Map<Long, Long> calculateBalanceTransfers(final List<ContractAction> actions) {
        final var transfers = new HashMap<Long, Long>();
        for (final var action : actions) {
            final int resultType = action.getResultDataType();
            if (resultType == RESULT_REVERT || resultType == RESULT_ERROR) {
                continue;
            }

            final int opType = action.getCallOperationType();
            if (opType == OP_DELEGATECALL || opType == OP_STATICCALL) {
                continue;
            }

            final long value = action.getValue();
            if (value == 0) {
                continue;
            }

            final Long callerId =
                    action.getCaller() != null ? action.getCaller().getId() : null;
            final Long recipientId = getRecipientId(action).orElse(null);

            if (callerId != null) {
                transfers.merge(callerId, -value, Long::sum);
            }
            if (recipientId != null) {
                transfers.merge(recipientId, value, Long::sum);
            }
        }
        return transfers;
    }

    private Optional<Long> getRecipientId(final ContractAction action) {
        if (action.getRecipientAccount() != null) {
            return Optional.of(action.getRecipientAccount().getId());
        }
        if (action.getRecipientContract() != null) {
            return Optional.of(action.getRecipientContract().getId());
        }
        return Optional.empty();
    }

    private void markTouchedAccounts(final PrestateContext prestateContext) {
        final var consensusTimestamp = prestateContext.getConsensusTimestamp();
        populateTouchedEntitiesFromActions(prestateContext, consensusTimestamp);
        populateTouchedEntitiesFromStateChanges(prestateContext, consensusTimestamp);
    }

    private void populateTouchedEntitiesFromActions(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var actions = contractActionRepository.findByConsensusTimestamp(consensusTimestamp);
        prestateContext.setActions(actions);

        for (final var action : actions) {
            prestateContext.addAccount(action.getCaller());
            prestateContext.addAccount(action.getRecipientAccount());
            prestateContext.addAccount(action.getRecipientContract());
        }
    }

    private void populateTouchedEntitiesFromStateChanges(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var includeStorage = prestateContext.getPrestateRequest().storage();

        if (!includeStorage) {
            return;
        }

        final var accountLimit = web3Properties.getMaxTouchedAccounts()
                - prestateContext.getAccounts().size();
        if (accountLimit <= 0) {
            return;
        }

        final var diffMode = prestateContext.getPrestateRequest().diffMode();

        final StateChangePageQuery query = diffMode
                ? (limit, offset) -> contractStateChangeRepository.findModifiedByConsensusTimestamp(
                        consensusTimestamp, accountLimit, limit, offset)
                : (limit, offset) -> contractStateChangeRepository.findByConsensusTimestamp(
                        consensusTimestamp, accountLimit, limit, offset);
        populateStateChanges(prestateContext, query);
    }

    private void populateStateChanges(
            final PrestateContext prestateContext, final StateChangePageQuery stateChangePageQuery) {
        for (int page = 0; page < STATE_CHANGE_MAX_PAGES; page++) {
            final int offset = page * STATE_CHANGE_PAGE_SIZE;
            final var stateChanges = stateChangePageQuery.find(STATE_CHANGE_PAGE_SIZE, offset);

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
        return HEX_PREFIX + bytesToHex(toEvmAddress(EntityId.of(entity.getId())));
    }

    private long resolveConsensusTimestamp(final TransactionIdOrHashParameter transactionIdOrHash) {
        return switch (transactionIdOrHash) {
            case TransactionHashParameter transactionHash ->
                contractTransactionHashRepository
                        .findByHash(transactionHash.hash().toArrayUnsafe())
                        .orElseThrow(() ->
                                new EntityNotFoundException("Contract transaction hash not found: " + transactionHash))
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
