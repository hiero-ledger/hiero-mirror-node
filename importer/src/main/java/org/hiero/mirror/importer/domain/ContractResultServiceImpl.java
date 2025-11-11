// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import com.google.common.base.Stopwatch;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.migration.SidecarContractMigration;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.transactionhandler.EvmHookStorageHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import org.hiero.mirror.importer.service.ContractInitcodeService;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NonNull;

@CustomLog
@Named
@RequiredArgsConstructor
final class ContractResultServiceImpl implements ContractResultService {

    public static final int HOOK_CONTRACT_NUM = 365;
    private final ContractInitcodeService contractInitcodeService;
    private final EntityProperties entityProperties;
    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final ImporterProperties importerProperties;
    private final SidecarContractMigration sidecarContractMigration;
    private final TransactionHandlerFactory transactionHandlerFactory;
    private final EvmHookStorageHandler evmHookStorageHandler;

    @Override
    @SuppressWarnings("java:S2259")
    public void process(@NonNull RecordItem recordItem, Transaction transaction) {
        if (!entityProperties.getPersist().isContracts()) {
            return;
        }

        var transactionRecord = recordItem.getTransactionRecord();
        var functionResult = transactionRecord.hasContractCreateResult()
                ? transactionRecord.getContractCreateResult()
                : transactionRecord.getContractCallResult();

        final var sidecarProcessingResult = processSidecarRecords(recordItem);

        // handle non create/call transactions
        boolean contractCallOrCreate = isContractCreateOrCall(transaction);
        if (!contractCallOrCreate && !isContractFunctionResultSet(functionResult)) {
            addDefaultEthereumTransactionContractResult(recordItem, transaction);
            // skip any other transaction which is neither a create/call and has no valid ContractFunctionResult
            return;
        }

        var transactionHandler = transactionHandlerFactory.get(TransactionType.of(transaction.getType()));
        var resultContractId = functionResult.getContractID();

        // in pre-compile case transaction is not a contract type and entityId will be of a different type
        var contractId = (contractCallOrCreate
                        ? Optional.ofNullable(transaction.getEntityId())
                        : entityIdService.lookup(resultContractId, !resultContractId.hasEvmAddress()))
                .orElse(EntityId.EMPTY);

        recordItem.addEntityId(contractId);

        processContractResult(
                recordItem, contractId, functionResult, transaction, transactionHandler, sidecarProcessingResult);
    }

    private void addDefaultEthereumTransactionContractResult(RecordItem recordItem, Transaction transaction) {
        var status = recordItem.getTransactionRecord().getReceipt().getStatus();
        if (recordItem.isSuccessful()
                || status == ResponseCodeEnum.DUPLICATE_TRANSACTION
                || status == ResponseCodeEnum.WRONG_NONCE
                || !recordItem.getTransactionBody().hasEthereumTransaction()) {
            // Don't add default contract result for the transaction if it's successful, or the result is
            // DUPLICATE_TRANSACTION, or the result is WRONG_NONCE, or it's not an ethereum transaction
            return;
        }

        var ethereumTransaction = recordItem.getEthereumTransaction();
        var functionParameters = ethereumTransaction.getCallData() != null
                ? ethereumTransaction.getCallData()
                : DomainUtils.EMPTY_BYTE_ARRAY;
        var payerAccountId = transaction.getPayerAccountId();
        var contractResult = ContractResult.builder()
                .callResult(DomainUtils.EMPTY_BYTE_ARRAY)
                .consensusTimestamp(transaction.getConsensusTimestamp())
                .contractId(0)
                .functionParameters(functionParameters)
                .gasLimit(ethereumTransaction.getGasLimit())
                .gasUsed(0L)
                .payerAccountId(payerAccountId)
                .senderId(payerAccountId)
                .transactionHash(ethereumTransaction.getHash())
                .transactionIndex(transaction.getIndex())
                .transactionNonce(transaction.getNonce())
                .transactionResult(transaction.getResult())
                .build();
        entityListener.onContractResult(contractResult);
    }

    private boolean isContractFunctionResultSet(ContractFunctionResult contractFunctionResult) {
        return !contractFunctionResult.equals(ContractFunctionResult.getDefaultInstance());
    }

    private boolean isContractCreateOrCall(Transaction transaction) {
        return transaction.getType() == TransactionType.CONTRACTCALL.getProtoId()
                || transaction.getType() == TransactionType.CONTRACTCREATEINSTANCE.getProtoId();
    }

    private void processContractAction(ContractAction action, int index, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var contractAction = new org.hiero.mirror.common.domain.contract.ContractAction();

        try {
            switch (action.getCallerCase()) {
                case CALLING_CONTRACT -> {
                    contractAction.setCallerType(EntityType.CONTRACT);
                    contractAction.setCaller(EntityId.of(action.getCallingContract()));
                }
                case CALLING_ACCOUNT -> {
                    contractAction.setCallerType(EntityType.ACCOUNT);
                    contractAction.setCaller(EntityId.of(action.getCallingAccount()));
                }
                default ->
                    Utility.handleRecoverableError(
                            "Invalid caller for contract action at {}: {}", consensusTimestamp, action.getCallerCase());
            }
        } catch (InvalidEntityException e) {
            Utility.handleRecoverableError("Invalid caller for contract action at {}: {}", consensusTimestamp, action);
        }

        try {
            switch (action.getRecipientCase()) {
                case RECIPIENT_ACCOUNT -> contractAction.setRecipientAccount(EntityId.of(action.getRecipientAccount()));
                case RECIPIENT_CONTRACT ->
                    contractAction.setRecipientContract(EntityId.of(action.getRecipientContract()));
                case TARGETED_ADDRESS ->
                    contractAction.setRecipientAddress(DomainUtils.toBytes(action.getTargetedAddress()));
                default -> {
                    // ContractCreate transaction has no recipient
                }
            }
        } catch (InvalidEntityException e) {
            // In some cases, consensus nodes can send entity IDs with negative numbers.
            Utility.handleRecoverableError(
                    "Invalid recipient for contract action at {}: {}", consensusTimestamp, action);
        }

        switch (action.getResultDataCase()) {
            case ERROR -> contractAction.setResultData(DomainUtils.toBytes(action.getError()));
            case REVERT_REASON -> contractAction.setResultData(DomainUtils.toBytes(action.getRevertReason()));
            case OUTPUT -> contractAction.setResultData(DomainUtils.toBytes(action.getOutput()));
            default ->
                Utility.handleRecoverableError(
                        "Invalid result data for contract action at {}: {}",
                        consensusTimestamp,
                        action.getResultDataCase());
        }

        contractAction.setCallDepth(action.getCallDepth());
        contractAction.setCallOperationType(action.getCallOperationTypeValue());
        contractAction.setCallType(action.getCallTypeValue());
        contractAction.setConsensusTimestamp(consensusTimestamp);
        contractAction.setGas(action.getGas());
        contractAction.setGasUsed(action.getGasUsed());
        contractAction.setIndex(index);
        contractAction.setInput(DomainUtils.toBytes(action.getInput()));
        contractAction.setPayerAccountId(recordItem.getPayerAccountId());
        contractAction.setResultDataType(action.getResultDataCase().getNumber());
        contractAction.setValue(action.getValue());

        entityListener.onContractAction(contractAction);

        recordItem.addEntityId(contractAction.getCaller());
        recordItem.addEntityId(contractAction.getRecipientAccount());
        recordItem.addEntityId(contractAction.getRecipientContract());
    }

    private void processContractResult(
            final RecordItem recordItem,
            final EntityId contractEntityId,
            final ContractFunctionResult functionResult,
            final Transaction transaction,
            final TransactionHandler transactionHandler,
            final SidecarProcessingResult sidecarProcessingResult) {
        // create child contracts regardless of contractResults support
        List<Long> contractIds = getCreatedContractIds(functionResult, recordItem, contractEntityId);
        processContractNonce(functionResult);

        if (!entityProperties.getPersist().isContractResults()) {
            return;
        }

        // Normalize the two distinct hashes into one 32 byte hash
        var transactionHash = recordItem.getTransactionHash();
        var payerAccountId = recordItem.getPayerAccountId();

        var contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        contractResult.setContractId(contractEntityId.getId());
        contractResult.setPayerAccountId(payerAccountId);
        // senderId defaults to payerAccountId
        contractResult.setSenderId(payerAccountId);
        contractResult.setTransactionHash(transactionHash);
        contractResult.setTransactionIndex(transaction.getIndex());
        contractResult.setTransactionNonce(transaction.getNonce());
        contractResult.setTransactionResult(transaction.getResult());
        transactionHandler.updateContractResult(contractResult, recordItem);

        if (sidecarProcessingResult.initcode() != null
                && !recordItem.isSuccessful()
                && contractResult.getFailedInitcode() == null
                && sidecarProcessingResult.isContractCreation()) {
            contractResult.setFailedInitcode(sidecarProcessingResult.initcode());
        }

        if (isContractFunctionResultSet(functionResult)) {
            contractResult.setBloom(DomainUtils.toBytes(functionResult.getBloom()));
            contractResult.setCallResult(DomainUtils.toBytes(functionResult.getContractCallResult()));
            contractResult.setCreatedContractIds(contractIds);
            contractResult.setErrorMessage(functionResult.getErrorMessage());
            contractResult.setFunctionResult(functionResult.toByteArray());
            contractResult.setGasUsed(functionResult.getGasUsed());
            updateGasConsumed(contractResult, sidecarProcessingResult, recordItem);

            if (functionResult.hasSenderId()) {
                var senderId = EntityId.of(functionResult.getSenderId());
                contractResult.setSenderId(senderId);
                recordItem.addEntityId(senderId);
            }

            processContractLogs(functionResult, contractResult, recordItem);
        }
        recordItem.addContractTransaction(contractEntityId);
        // Add the transaction payer account id to enable proper lookup by transaction id
        recordItem.addContractTransaction(contractResult.getPayerAccountId());
        entityListener.onContractResult(contractResult);
    }

    private void processContractNonce(ContractFunctionResult functionResult) {
        if (entityProperties.getPersist().isTrackNonce()) {
            functionResult.getContractNoncesList().forEach(nonceInfo -> {
                var contractId = EntityId.of(nonceInfo.getContractId());
                var entity = contractId.toEntity();
                entity.setEthereumNonce(nonceInfo.getNonce());
                entity.setTimestampRange(null); // Don't trigger a history row
                entity.setType(EntityType.CONTRACT);
                entityListener.onEntity(entity);
            });
        }
    }

    private void processContractLogs(
            ContractFunctionResult functionResult, ContractResult contractResult, RecordItem recordItem) {
        for (final var contractLogInfo : functionResult.getLogInfoList()) {
            var contractLog = new ContractLog();
            var contractId = EntityId.of(contractLogInfo.getContractID());
            var rootContractId = EntityId.of(contractResult.getContractId());
            contractLog.setBloom(DomainUtils.toBytes(contractLogInfo.getBloom()));
            contractLog.setConsensusTimestamp(contractResult.getConsensusTimestamp());
            contractLog.setContractId(contractId);
            contractLog.setData(DomainUtils.toBytes(contractLogInfo.getData()));
            contractLog.setIndex(recordItem.getAndIncrementLogIndex());
            contractLog.setRootContractId(rootContractId);
            contractLog.setPayerAccountId(contractResult.getPayerAccountId());
            contractLog.setTopic0(Utility.getTopic(contractLogInfo, 0));
            contractLog.setTopic1(Utility.getTopic(contractLogInfo, 1));
            contractLog.setTopic2(Utility.getTopic(contractLogInfo, 2));
            contractLog.setTopic3(Utility.getTopic(contractLogInfo, 3));
            contractLog.setTransactionHash(recordItem.getTransactionHash());
            contractLog.setTransactionIndex(recordItem.getTransactionIndex());
            entityListener.onContractLog(contractLog);

            recordItem.addContractTransaction(contractId);
            recordItem.addEntityId(contractId);
        }
    }

    private void processContractStateChange(boolean migration, RecordItem recordItem, ContractStateChange stateChange) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var contractId = EntityId.of(stateChange.getContractId());
        var payerAccountId = recordItem.getPayerAccountId();

        // Check if this is hook storage change (contract 365 = 0x16d)
        if (isHookExecution(recordItem) && isHookExecution(stateChange)) {
            processHookStorageChanges(recordItem, stateChange);
            return;
        }

        for (var storageChange : stateChange.getStorageChangesList()) {
            var contractStateChange = new org.hiero.mirror.common.domain.contract.ContractStateChange();
            contractStateChange.setConsensusTimestamp(consensusTimestamp);
            contractStateChange.setContractId(contractId);
            contractStateChange.setMigration(migration);
            contractStateChange.setPayerAccountId(payerAccountId);
            contractStateChange.setSlot(DomainUtils.toBytes(storageChange.getSlot()));
            contractStateChange.setValueRead(DomainUtils.toBytes(storageChange.getValueRead()));

            // If a value of zero is written the valueWritten will be present but the inner value will be
            // absent. If a value was read and not written this value will not be present.
            if (storageChange.hasValueWritten()) {
                contractStateChange.setValueWritten(
                        DomainUtils.toBytes(storageChange.getValueWritten().getValue()));
            }

            entityListener.onContractStateChange(contractStateChange);
            recordItem.addContractTransaction(contractId);
        }

        recordItem.addEntityId(contractId);
    }

    @SuppressWarnings("deprecation")
    private List<Long> getCreatedContractIds(
            ContractFunctionResult functionResult, RecordItem recordItem, EntityId parentEntityContractId) {
        List<Long> createdContractIds = new ArrayList<>();
        boolean persist = shouldPersistCreatedContractIDs(recordItem);
        for (var createdContractId : functionResult.getCreatedContractIDsList()) {
            var contractId = entityIdService.lookup(createdContractId).orElse(EntityId.EMPTY);
            if (!EntityId.isEmpty(contractId)) {
                createdContractIds.add(contractId.getId());
                // The parent contract ID can also sometimes appear in the created contract IDs list, so exclude it
                if (persist && !contractId.equals(parentEntityContractId)) {
                    processCreatedContractEntity(recordItem, contractId);
                    recordItem.addEntityId(contractId);
                }
            }
        }

        return createdContractIds;
    }

    private void processCreatedContractEntity(RecordItem recordItem, EntityId contractEntityId) {
        Entity entity = contractEntityId.toEntity();
        entity.setBalance(0L);
        entity.setBalanceTimestamp(recordItem.getConsensusTimestamp());
        entity.setCreatedTimestamp(recordItem.getConsensusTimestamp());
        entity.setDeleted(false);
        entity.setMaxAutomaticTokenAssociations(0);
        entity.setTimestampLower(recordItem.getConsensusTimestamp());
        entity.setType(EntityType.CONTRACT);

        if (recordItem.getTransactionBody().hasContractCreateInstance()) {
            updateContractEntityOnCreate(entity, recordItem);
        }

        entityListener.onEntity(entity);
    }

    @SuppressWarnings("java:S3776")
    private SidecarProcessingResult processSidecarRecords(final RecordItem recordItem) {
        final var sidecarRecords = recordItem.getSidecarRecords();
        if (sidecarRecords.isEmpty()) {
            return new SidecarProcessingResult(null, false, null);
        }

        var contractBytecodes = new ArrayList<ContractBytecode>();
        int migrationCount = 0;
        var stopwatch = Stopwatch.createStarted();

        Long topLevelActionSidecarGasUsed = null;
        byte[] payloadBytes = null;
        boolean isContractCreation = false;

        for (final var sidecarRecord : sidecarRecords) {
            final boolean migration = sidecarRecord.getMigration();
            if (sidecarRecord.hasStateChanges()) {
                var stateChanges = sidecarRecord.getStateChanges();
                for (var stateChange : stateChanges.getContractStateChangesList()) {
                    processContractStateChange(migration, recordItem, stateChange);
                }
            } else if (sidecarRecord.hasActions()) {
                var actions = sidecarRecord.getActions();
                for (int actionIndex = 0; actionIndex < actions.getContractActionsCount(); actionIndex++) {
                    final var action = actions.getContractActions(actionIndex);
                    if (action.getCallDepth() == 0) {
                        topLevelActionSidecarGasUsed = action.getGasUsed();
                        isContractCreation = action.getCallType().equals(ContractActionType.CREATE);
                    }
                    processContractAction(action, actionIndex, recordItem);
                }
            } else if (sidecarRecord.hasBytecode()) {
                if (migration) {
                    contractBytecodes.add(sidecarRecord.getBytecode());
                } else {
                    payloadBytes = contractInitcodeService.get(sidecarRecord.getBytecode(), recordItem);
                    isContractCreation = true;
                }
            }
            if (migration) {
                ++migrationCount;
            }
        }

        sidecarContractMigration.migrate(contractBytecodes);
        if (migrationCount > 0) {
            log.info(
                    "{} Sidecar records processed with {} migrations in {}",
                    sidecarRecords.size(),
                    migrationCount,
                    stopwatch);
        }

        return new SidecarProcessingResult(payloadBytes, isContractCreation, topLevelActionSidecarGasUsed);
    }

    /**
     * Updates gas consumed based on HAPI version and smart contract throttling version. If the RecordItem's HAPI
     * version is equal or greater than the smart contract throttling version, uses the gas used field from
     * {@link ContractResult}. Otherwise, uses the existing logic with manual calculation.
     *
     * @param contractResult          The contract result to update
     * @param sidecarProcessingResult The sidecar processing result containing gas usage information
     * @param recordItem              The record item being processed
     */
    private void updateGasConsumed(
            ContractResult contractResult, SidecarProcessingResult sidecarProcessingResult, RecordItem recordItem) {
        final var throttlingVersion = importerProperties.getSmartContractThrottlingVersion();
        final var hapiVersion = recordItem.getHapiVersion();

        if (hapiVersion.isGreaterThanOrEqualTo(throttlingVersion)) {
            // Use directly gas used field
            contractResult.setGasConsumed(contractResult.getGasUsed());
        } else {
            // Use legacy logic for manual calculation of gas consumed for older HAPI versions
            sidecarProcessingResult.calculateGasConsumed(contractResult);
        }
    }

    /**
     * Updates the contract entities in ContractCreateResult.CreatedContractIDs list. The method should only be called
     * for such contract entities in pre services 0.23 contract create transactions. Since services 0.23, the child
     * contract creation is externalized into its own synthesized contract create transaction and should be processed by
     * ContractCreateTransactionHandler.
     *
     * @param entity     The contract entity
     * @param recordItem The recordItem in which the contract is created
     */
    @SuppressWarnings("deprecation")
    private void updateContractEntityOnCreate(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasProxyAccountID()) {
            entity.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        entity.setMemo(transactionBody.getMemo());

        Contract contract = new Contract();
        contract.setId(entity.getId());

        // No need to check initcode and other newer fields since they weren't available in older HAPI versions
        if (transactionBody.hasFileID()) {
            contract.setFileId(EntityId.of(transactionBody.getFileID()));
        }

        entityListener.onContract(contract);
    }

    /**
     * Persist contract entities in createdContractIDs if it's prior to HAPI 0.23.0. After that the createdContractIDs
     * list is also externalized as contract create child records so we only need to persist the complete contract
     * entity from the child record.
     *
     * @param recordItem to check
     * @return Whether the createdContractIDs list should be persisted.
     */
    private boolean shouldPersistCreatedContractIDs(RecordItem recordItem) {
        return recordItem.isSuccessful()
                && entityProperties.getPersist().isContracts()
                && recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_23_0);
    }

    /**
     * Determines if a RecordItem represents a hook execution. Hook executions are ContractCall transactions to the
     * system hook contract (0.0.365) that have a parent consensus timestamp.
     *
     * @param recordItem the record item to check
     * @return true if this is a hook execution, false otherwise
     */
    private boolean isHookExecution(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        // Must be a ContractCall transaction to 0.0.365 with a parent consensus timestamp
        return transactionBody.hasContractCall()
                && transactionRecord.hasParentConsensusTimestamp()
                && entityIdService
                        .lookup(transactionBody.getContractCall().getContractID())
                        .map(entityId -> entityId.getNum() == HOOK_CONTRACT_NUM)
                        .orElse(false);
    }

    /**
     * Determines if a StateChange represents a hook execution. Hook executions are ContractCall transactions to the
     * system hook contract (0.0.365).
     *
     * @param stateChange the state change from the sidecar
     * @return true if this is a hook execution, false otherwise
     */
    private boolean isHookExecution(ContractStateChange stateChange) {
        // Must be a ContractCall transaction to 0.0.365 with a parent consensus timestamp
        return entityIdService
                .lookup(stateChange.getContractId())
                .map(entityId -> entityId.getNum() == HOOK_CONTRACT_NUM)
                .orElse(false);
    }

    /**
     * Processes hook storage changes from contract state changes targeting the hook system contract (0.0.365). Uses the
     * transient queue in the parent RecordItem to track sequential hook execution context.
     *
     * @param recordItem  the record item containing the hook execution transaction
     * @param stateChange the contract state change containing hook storage updates
     */
    private void processHookStorageChanges(RecordItem recordItem, ContractStateChange stateChange) {
        // Get one hook context for this ContractStateChange
        var hookContext = recordItem.nextHookContext();
        if (hookContext == null || hookContext.getOwnerId() == EntityId.EMPTY.getId()) {
            Utility.handleRecoverableError(
                    "No hook context available in parent transaction for hook execution at consensus timestamp {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        // Process each storage change with the same hook context
        evmHookStorageHandler.processStorageUpdatesForSidecar(
                recordItem.getConsensusTimestamp(),
                hookContext.getHookId(),
                hookContext.getOwnerId(),
                stateChange.getStorageChangesList());
    }

    /**
     * Record representing the result of processing sidecar records.
     *
     * @param initcode           The payload of the contract transaction.
     * @param isContractCreation Whether the transaction is a contract creation.
     * @param totalGasUsed       The gas used by the contract actions in the sidecar records.
     */
    @SuppressWarnings("java:S6218")
    private record SidecarProcessingResult(byte[] initcode, boolean isContractCreation, Long totalGasUsed) {
        private static final long TX_DATA_ZERO_COST = 4L;
        private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
        private static final long TX_BASE_COST = 21_000L;
        private static final long TX_CREATE_EXTRA = 32_000L;

        /**
         * Update the gas consumed for a contract transaction.
         */
        void calculateGasConsumed(ContractResult contractResult) {
            if (totalGasUsed == null) {
                return;
            }

            int nonZeros = 0;
            var payload = initcode;
            int zeros = 0;

            if (payload == null) {
                payload = contractResult.getFunctionParameters();
            }

            if (payload != null) {
                for (byte b : payload) {
                    if (b == 0) {
                        ++zeros;
                    }
                }
                nonZeros = payload.length - zeros;
            }

            long cost = TX_BASE_COST + TX_DATA_ZERO_COST * zeros + ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros;
            long intrinsicGas = isContractCreation ? (cost + TX_CREATE_EXTRA) : cost;
            contractResult.setGasConsumed(totalGasUsed + intrinsicGas);
        }
    }
}
