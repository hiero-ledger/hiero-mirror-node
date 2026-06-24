// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
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
import lombok.CustomLog;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractResult;
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
import org.hiero.mirror.web3.repository.projections.EntitySnapshot;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@CustomLog
public class PrestateServiceImpl implements PrestateService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final ContractActionRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EntityRepository entityRepository;
    private final long treasuryAccountId;
    private final TransactionRepository transactionRepository;

    public PrestateServiceImpl(
            final AccountBalanceRepository accountBalanceRepository,
            final ContractActionRepository contractActionRepository,
            final ContractRepository contractRepository,
            final ContractResultRepository contractResultRepository,
            final ContractStateChangeRepository contractStateChangeRepository,
            final ContractTransactionHashRepository contractTransactionHashRepository,
            final EntityRepository entityRepository,
            final SystemEntity systemEntity,
            final TransactionRepository transactionRepository) {
        this.accountBalanceRepository = accountBalanceRepository;
        this.contractActionRepository = contractActionRepository;
        this.contractRepository = contractRepository;
        this.contractResultRepository = contractResultRepository;
        this.contractStateChangeRepository = contractStateChangeRepository;
        this.contractTransactionHashRepository = contractTransactionHashRepository;
        this.entityRepository = entityRepository;
        this.treasuryAccountId = systemEntity.treasuryAccount().getId();
        this.transactionRepository = transactionRepository;
    }

    @Override
    public PrestateResponse processPrestateCall(@NonNull final PrestateRequest prestateRequest) {
        final var consensusTimestamp = resolveConsensusTimestamp(prestateRequest.getTransactionIdOrHashParameter());
        final var contractResult = contractResultRepository
                .findById(consensusTimestamp)
                .orElseThrow(() -> new EntityNotFoundException("Contract result not found: " + consensusTimestamp));

        final var prestateContext = new PrestateContext(prestateRequest, consensusTimestamp);
        populateTouchedEntities(prestateContext, contractResult);
        loadAccountTraces(prestateContext);

        final var response = new PrestateResponse();
        if (prestateContext.isDiff()) {
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
            if (Objects.equals(entry.getValue(), postAccountTraceMap.get(entry.getKey()))) {
                iterator.remove();
                postAccountTraceMap.remove(entry.getKey());
            }
        }
    }

    private void loadAccountTraces(final PrestateContext prestateContext) {
        final var accounts = prestateContext.getAccounts();
        if (accounts.isEmpty()) {
            return;
        }

        final var consensusTimestamp = prestateContext.getConsensusTimestamp();
        final var preBlockTimestamp = consensusTimestamp - 1;

        final var entityIds = new ArrayList<Long>(accounts.size());
        for (final var account : accounts) {
            entityIds.add(account.getId());
        }

        final var preSnapshotById =
                toSnapshotById(entityRepository.findActiveSnapshotsByIdsAndTimestamp(entityIds, preBlockTimestamp));
        final Map<Long, EntitySnapshot> postSnapshotById = prestateContext.isDiff()
                ? toSnapshotById(entityRepository.findActiveSnapshotsByIdsAndTimestamp(entityIds, consensusTimestamp))
                : null;

        final var preBalances = loadBalances(entityIds, preBlockTimestamp);
        final Map<Long, Long> postBalances =
                prestateContext.isDiff() ? loadBalances(entityIds, consensusTimestamp) : null;

        Map<Long, byte[]> preBytecodes = null;
        Map<Long, byte[]> postBytecodes = null;
        if (prestateContext.isCode()) {
            final var loadedBytecodes = loadBytecodes(prestateContext);
            if (prestateContext.isDiff()) {
                preBytecodes = new HashMap<>(loadedBytecodes);
                for (final var contractId :
                        prestateContext.getPostBytecodeByContract().keySet()) {
                    preBytecodes.remove(contractId);
                }
                preBytecodes.putAll(prestateContext.getPreBytecodeByContract());

                postBytecodes = new HashMap<>(loadedBytecodes);
                postBytecodes.putAll(prestateContext.getPostBytecodeByContract());
            } else {
                preBytecodes = new HashMap<>(loadedBytecodes);
                preBytecodes.putAll(prestateContext.getPreBytecodeByContract());
            }
        }

        final Map<Long, Map<String, String>> preStorageByContract =
                prestateContext.isStorage() ? prestateContext.getPreStorageByContract() : null;
        final Map<Long, Map<String, String>> postStorageByContract =
                prestateContext.isDiff() && prestateContext.isStorage()
                        ? prestateContext.getPostStorageByContract()
                        : null;

        for (int i = 0, n = entityIds.size(); i < n; i++) {
            final var entityId = entityIds.get(i);
            final var preSnapshot = preSnapshotById.get(entityId);
            if (preSnapshot != null) {
                final var preAccountTrace = buildAccountTrace(
                        preSnapshot,
                        preBalances.get(entityId),
                        preBytecodes,
                        preStorageByContract,
                        prestateContext.isCreatedContract(entityId));
                prestateContext.getPreAccountTraces().put(preAccountTrace.getAddress(), preAccountTrace);
            }

            if (prestateContext.isDiff()) {
                final var postSnapshot = postSnapshotById.get(entityId);
                if (postSnapshot != null) {
                    final var postAccountTrace = buildAccountTrace(
                            postSnapshot, postBalances.get(entityId), postBytecodes, postStorageByContract, false);
                    prestateContext.getPostAccountTraces().put(postAccountTrace.getAddress(), postAccountTrace);
                }
            }
        }
    }

    private static Map<Long, EntitySnapshot> toSnapshotById(final List<EntitySnapshot> entitySnapshots) {
        final var snapshotById = new HashMap<Long, EntitySnapshot>(entitySnapshots.size());
        for (int i = 0, n = entitySnapshots.size(); i < n; i++) {
            final var snapshot = entitySnapshots.get(i);
            snapshotById.put(snapshot.getId(), snapshot);
        }
        return snapshotById;
    }

    private AccountTrace buildAccountTrace(
            final EntitySnapshot snapshot,
            final Long balance,
            final Map<Long, byte[]> bytecodes,
            final Map<Long, Map<String, String>> storageByContract,
            final boolean excludeBytecode) {
        final var entityId = snapshot.getId();
        final var accountTrace = new AccountTrace();
        accountTrace.setAddress(resolveAddress(snapshot));
        accountTrace.setBalance(HEX_PREFIX + Long.toHexString(balance != null ? balance : 0L));

        final var nonce = snapshot.getEthereumNonce();
        accountTrace.setNonce(nonce != null ? nonce : 0L);

        if (CONTRACT.name().equalsIgnoreCase(snapshot.getType())) {
            if (bytecodes != null && !excludeBytecode) {
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

    private Map<Long, byte[]> loadBytecodes(final PrestateContext prestateContext) {
        final var accounts = prestateContext.getAccounts();
        final var entityIds = new ArrayList<Long>(accounts.size());
        for (final var account : accounts) {
            entityIds.add(account.getId());
        }
        if (entityIds.isEmpty()) {
            return Collections.emptyMap();
        }

        final var snapshots = contractRepository.findRuntimeBytecodesByIds(entityIds);
        final var bytecodes = new HashMap<Long, byte[]>(snapshots.size());
        for (int i = 0, n = snapshots.size(); i < n; i++) {
            final var snapshot = snapshots.get(i);
            final var runtimeBytecode = snapshot.getRuntimeBytecode();
            if (runtimeBytecode != null) {
                bytecodes.put(snapshot.getId(), runtimeBytecode);
            }
        }
        return bytecodes;
    }

    private Map<Long, Long> loadBalances(final List<Long> accountIds, final long blockTimestamp) {
        final var snapshots = accountBalanceRepository.findHistoricalAccountBalancesUpToTimestamp(
                accountIds, blockTimestamp, treasuryAccountId);
        if (snapshots.isEmpty()) {
            return Collections.emptyMap();
        }

        final var balances = new HashMap<Long, Long>(snapshots.size());
        for (int i = 0, n = snapshots.size(); i < n; i++) {
            final var snapshot = snapshots.get(i);
            balances.put(snapshot.getAccountId(), snapshot.getBalance());
        }
        return balances;
    }

    private void populateTouchedEntities(final PrestateContext prestateContext, final ContractResult contractResult) {
        final var consensusTimestamp = prestateContext.getConsensusTimestamp();
        populateTouchedEntitiesFromActions(prestateContext, consensusTimestamp);
        populateTouchedEntitiesFromStateChanges(prestateContext, consensusTimestamp);
        populateTouchedEntitiesFromContractResult(prestateContext, contractResult);
        populateTouchedEntitiesFromBytecode(prestateContext, consensusTimestamp);
    }

    private void populateTouchedEntitiesFromContractResult(
            final PrestateContext prestateContext, final ContractResult contractResult) {
        prestateContext.addAccount(EntityId.of(contractResult.getContractId()));
        prestateContext.addAccount(contractResult.getSenderId());

        final var createdContractIds = contractResult.getCreatedContractIds();
        for (int i = 0, n = createdContractIds.size(); i < n; i++) {
            prestateContext.addCreatedContract(EntityId.of(createdContractIds.get(i)));
        }
    }

    private void populateTouchedEntitiesFromActions(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var actions = contractActionRepository.findByConsensusTimestamp(consensusTimestamp);
        for (var i = 0; i < actions.size(); i++) {
            final var action = actions.get(i);

            final var caller = action.getCaller();
            prestateContext.addAccount(caller);

            final var recipientAccount = action.getRecipientAccount();
            prestateContext.addAccount(recipientAccount);

            final var recipientContract = action.getRecipientContract();
            prestateContext.addAccount(recipientContract);

            addEntityFromRecipientAddress(prestateContext, action.getRecipientAddress());
        }
    }

    private void populateTouchedEntitiesFromStateChanges(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        final var stateChanges = contractStateChangeRepository.findByConsensusTimestamp(consensusTimestamp);
        for (var i = 0; i < stateChanges.size(); i++) {
            final var stateChange = stateChanges.get(i);
            final var contractId = stateChange.getContractId();
            prestateContext.addAccount(EntityId.of(contractId));
            if (prestateContext.isStorage()) {
                prestateContext.addPreStorageSlot(contractId, stateChange.getSlot(), stateChange.getValueRead());
                if (prestateContext.isDiff()) {
                    prestateContext.addPostStorageSlot(
                            contractId, stateChange.getSlot(), stateChange.getValueWritten());
                }
            }
        }
    }

    private void populateTouchedEntitiesFromBytecode(
            final PrestateContext prestateContext, final long consensusTimestamp) {
        if (prestateContext.isCode()) {
            final var contracts = contractRepository.findByConsensusTimestamp(consensusTimestamp);
            for (int i = 0, n = contracts.size(); i < n; i++) {
                final var contract = contracts.get(i);
                final var contractId = contract.getId();
                prestateContext.addAccount(EntityId.of(contractId));
                final var runtimeBytecode = contract.getRuntimeBytecode();
                if (prestateContext.isDiff()) {
                    prestateContext.addPostBytecode(contractId, runtimeBytecode);
                }
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

        entityOptional.ifPresent(entity -> prestateContext.addAccount(entity.toEntityId()));
    }

    private static String resolveAddress(final EntitySnapshot snapshot) {
        final var evmAddress = snapshot.getEvmAddress();
        if (evmAddress != null && evmAddress.length == EVM_ADDRESS_LENGTH) {
            return Bytes.wrap(evmAddress).toHex();
        }
        final var alias = snapshot.getAlias();
        if (alias != null && alias.length == EVM_ADDRESS_LENGTH) {
            return Bytes.wrap(alias).toHex();
        }
        return EntityId.of(snapshot.getId()).toString();
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
                final var transactionList = transactionRepository.findByPayerAccountIdAndValidStartNs(
                        payerAccountId.getId(),
                        validStartNs,
                        validStartNs,
                        validStartNs + TraceService.MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS);
                if (transactionList.isEmpty()) {
                    throw new EntityNotFoundException("Transaction not found: " + transactionId);
                }
                yield transactionList.getFirst().getConsensusTimestamp();
            }
        };
    }
}
