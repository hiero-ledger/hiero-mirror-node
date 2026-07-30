// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.common.util.DomainUtils.fromEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.isLongZeroAddress;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.AccountTrace;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.PrestateContext;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractStateChangeRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public final class PrestateServiceImpl implements PrestateService {

    public static final long MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS = 35 * 60 * NANOS_PER_SECOND;

    private final AccountBalanceRepository accountBalanceRepository;
    private final ContractActionRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EntityRepository entityRepository;
    private final TransactionRepository transactionRepository;
    private final SystemEntity systemEntity;
    private static final String ZERO_BALANCE = "0x0";

    @Override
    public PrestateResponse processPrestateCall(final PrestateRequest prestateRequest) {
        final var consensusTimestamp = resolveConsensusTimestamp(prestateRequest.transactionIdOrHashParameter());
        final var contractResult = contractResultRepository
                .findById(consensusTimestamp)
                .orElseThrow(() -> new EntityNotFoundException("Contract result not found: " + consensusTimestamp));

        final var prestateContext = new PrestateContext(prestateRequest, consensusTimestamp);
        markTouchedAccounts(prestateContext, contractResult);
        loadAccountTraces(prestateContext);

        final var response = new PrestateResponse();
        if (prestateContext.getPrestateRequest().diffMode()) {
            applyDiffFilter(prestateContext.getPreAccountTraces(), prestateContext.getPostAccountTraces());
            response.setPre(
                    new ArrayList<>(prestateContext.getPreAccountTraces().values()));
            response.setPost(
                    new ArrayList<>(prestateContext.getPostAccountTraces().values()));
        } else {
            response.setPre(
                    new ArrayList<>(prestateContext.getPreAccountTraces().values()));
        }

        return response;
    }

    private void applyDiffFilter(
            final Map<String, AccountTrace> preAccountTraceMap, final Map<String, AccountTrace> postAccountTraceMap) {
        final var iterator = preAccountTraceMap.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            final var key = entry.getKey();
            if (Objects.equals(entry.getValue(), postAccountTraceMap.get(key))) {
                iterator.remove();
                postAccountTraceMap.remove(key);
            }
        }
    }

    private void loadAccountTraces(final PrestateContext prestateContext) {
        final var accounts = prestateContext.getAccounts();
        if (accounts.isEmpty()) {
            return;
        }

        final var consensusTimestamp = prestateContext.getConsensusTimestamp();
        final var timestampBeforeTransaction = consensusTimestamp - 1;

        final var preEntityById = toEntityById(entityRepository
                .findActiveByIdsAndTimestamp(accounts, timestampBeforeTransaction)
                .orElse(null));
        final var postEntityById = prestateContext.getPrestateRequest().diffMode()
                ? toEntityById(entityRepository
                        .findActiveByIdsAndTimestamp(accounts, consensusTimestamp)
                        .orElse(null))
                : null;

        final var preBalances = loadBalances(accounts, timestampBeforeTransaction);
        final var postBalances =
                prestateContext.getPrestateRequest().diffMode() ? loadBalances(accounts, consensusTimestamp) : null;

        final var preBytecodes = prestateContext.getPrestateRequest().code()
                ? loadBytecodes(accounts, timestampBeforeTransaction)
                : null;
        final var preStorageByContract =
                prestateContext.getPrestateRequest().storage() ? prestateContext.getPreStorageByContract() : null;

        for (final var accountId : accounts) {
            final var preEntity = preEntityById.get(accountId);
            if (preEntity != null) {
                final var preAccountTrace =
                        buildAccountTrace(preEntity, preBalances.get(accountId), preBytecodes, preStorageByContract);
                prestateContext.getPreAccountTraces().put(preAccountTrace.getAddress(), preAccountTrace);
            }

            if (prestateContext.getPrestateRequest().diffMode()) {
                final var postEntity = postEntityById != null ? postEntityById.get(accountId) : null;
                if (postEntity != null) {

                    final var postBytecodes =
                            prestateContext.getPrestateRequest().code()
                                    ? loadBytecodes(accounts, consensusTimestamp)
                                    : null;
                    final var postStorageByContract =
                            prestateContext.getPrestateRequest().storage()
                                    ? prestateContext.getPostStorageByContract()
                                    : null;
                    final var postAccountTrace = buildAccountTrace(
                            postEntity,
                            postBalances != null ? postBalances.get(accountId) : null,
                            postBytecodes,
                            postStorageByContract);
                    prestateContext.getPostAccountTraces().put(postAccountTrace.getAddress(), postAccountTrace);
                }
            }
        }
    }

    private Map<Long, Entity> toEntityById(final List<Entity> entities) {
        if (entities == null) {
            return Collections.emptyMap();
        }

        final var entityById = new HashMap<Long, Entity>(entities.size());
        for (final var entity : entities) {
            entityById.put(entity.getId(), entity);
        }
        return entityById;
    }

    private AccountTrace buildAccountTrace(
            final Entity entity,
            final Long balance,
            final Map<Long, byte[]> bytecodes,
            final Map<Long, Map<String, String>> storageByContract) {
        final var entityId = entity.getId();
        final var accountTrace = new AccountTrace();
        accountTrace.setAddress(resolveAddress(entity));
        accountTrace.setBalance(balance != null ? HEX_PREFIX + Long.toHexString(balance) : ZERO_BALANCE);

        final var nonce = entity.getEthereumNonce();
        accountTrace.setNonce(nonce != null ? nonce : 0L);

        if (CONTRACT.name().equalsIgnoreCase(entity.getType().name())) {
            if (bytecodes != null) {
                final var bytecode = bytecodes.get(entityId);
                if (bytecode != null) {
                    accountTrace.setCode(Bytes.wrap(bytecode).toHex());
                }
            }
            if (storageByContract != null) {
                final var contractStorage = storageByContract.get(entityId);
                if (contractStorage != null && !contractStorage.isEmpty()) {
                    accountTrace.setStorage(contractStorage);
                }
            }
        }

        return accountTrace;
    }

    private Map<Long, byte[]> loadBytecodes(final Set<Long> entityIds, final long timestamp) {
        if (entityIds.isEmpty()) {
            return new HashMap<>();
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
            return Collections.emptyMap();
        }

        final var balances = new HashMap<Long, Long>(accountIds.size());
        for (final var accountId : accountIds) {
            balances.put(
                    accountId,
                    accountBalanceRepository
                            .findHistoricalAccountBalanceUpToTimestamp(
                                    accountId,
                                    blockTimestamp,
                                    systemEntity.treasuryAccount().getId())
                            .orElse(0L));
        }
        return balances;
    }

    private void markTouchedAccounts(final PrestateContext prestateContext, final ContractResult contractResult) {
        final var consensusTimestamp = prestateContext.getConsensusTimestamp();
        populateTouchedEntitiesFromActions(prestateContext, consensusTimestamp);
        populateTouchedEntitiesFromStateChanges(prestateContext, consensusTimestamp);
        prestateContext.addAccount(contractResult.getContractId());
        prestateContext.addAccount(contractResult.getSenderId().getId());
    }

    private void populateTouchedEntitiesFromActions(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var actions = contractActionRepository.findByConsensusTimestamp(consensusTimestamp);
        for (final var action : actions) {
            final var caller = action.getCaller();
            final var recipientAccount = action.getRecipientAccount();
            final var recipientContract = action.getRecipientContract();

            prestateContext.addAccount(caller);
            prestateContext.addAccount(recipientAccount);
            prestateContext.addAccount(recipientContract);

            addEntityFromRecipientAddress(prestateContext, action.getRecipientAddress());
        }
    }

    private void populateTouchedEntitiesFromStateChanges(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var stateChanges = contractStateChangeRepository.findByConsensusTimestamp(consensusTimestamp);
        for (final var stateChange : stateChanges) {
            final var contractId = stateChange.getContractId();
            prestateContext.addAccount(contractId);
            prestateContext.addPreStorageSlot(contractId, stateChange.getSlot(), stateChange.getValueRead());
            if (prestateContext.getPrestateRequest().diffMode()) {
                prestateContext.addPostStorageSlot(contractId, stateChange.getSlot(), stateChange.getValueWritten());
            }
        }
    }

    private void addEntityFromRecipientAddress(final PrestateContext prestateContext, final byte[] recipientAddress) {
        if (recipientAddress == null || recipientAddress.length != EVM_ADDRESS_LENGTH) {
            return;
        }

        final var entityOptional = isLongZeroAddress(recipientAddress)
                ? Optional.ofNullable(fromEvmAddress(recipientAddress))
                        .flatMap(entityId -> entityRepository.findByIdAndDeletedIsFalse(entityId.getId()))
                : entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(recipientAddress);

        entityOptional.ifPresent(entity -> prestateContext.addAccount(entity.getId()));
    }

    private String resolveAddress(final Entity entity) {
        final var evmAddress = entity.getEvmAddress();
        if (evmAddress != null && evmAddress.length == EVM_ADDRESS_LENGTH) {
            return Bytes.wrap(evmAddress).toHex();
        }
        final var alias = entity.getAlias();
        if (alias != null && alias.length == EVM_ADDRESS_LENGTH) {
            return Bytes.wrap(alias).toHex();
        }
        return EntityId.of(entity.getId()).toString();
    }

    private long resolveConsensusTimestamp(final TransactionIdOrHashParameter transactionIdOrHash) {
        return switch (transactionIdOrHash) {
            case TransactionHashParameter transactionHash ->
                contractTransactionHashRepository
                        .findByHash(transactionHash.hash().toArray())
                        .orElseThrow(() ->
                                new EntityNotFoundException("Contract transaction hash not found: " + transactionHash))
                        .getConsensusTimestamp();
            case TransactionIdParameter transactionId -> {
                final var validStartNs = convertToNanosMax(transactionId.validStart());
                final var payerAccountId = transactionId.payerAccountId();
                final var transaction = transactionRepository
                        .findByPayerAccountIdAndValidStartNs(
                                payerAccountId.getId(),
                                validStartNs,
                                validStartNs,
                                validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS)
                        .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));

                yield transaction.getConsensusTimestamp();
            }
        };
    }
}
