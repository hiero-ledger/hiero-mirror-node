// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static com.hederahashgraph.api.proto.java.ContractCreateTransactionBody.InitcodeSourceCase.INITCODE;
import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_27_0;

import com.hedera.services.stream.proto.ContractBytecode;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.service.ContractInitcodeService;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
class ContractCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final ContractInitcodeService contractInitcodeService;
    private final EntityProperties entityProperties;

    ContractCreateTransactionHandler(
            ContractInitcodeService contractInitcodeService,
            EntityIdService entityIdService,
            EntityListener entityListener,
            EntityProperties entityProperties) {
        super(entityIdService, entityListener, TransactionType.CONTRACTCREATEINSTANCE);
        this.contractInitcodeService = contractInitcodeService;
        this.entityProperties = entityProperties;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return entityIdService
                .lookup(recordItem.getTransactionRecord().getReceipt().getContractID())
                .orElse(EntityId.EMPTY);
    }

    /*
     * Insert contract results even for failed transactions since they could fail during execution, and we want to
     * know how much gas was used and the call result regardless.
     */
    @Override
    public void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        transaction.setInitialBalance(transactionBody.getInitialBalance());
    }

    @Override
    @SuppressWarnings({"deprecation", "java:S1874"})
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        if (!entityProperties.getPersist().isContracts()) {
            return;
        }

        var contractCreateResult = recordItem.getTransactionRecord().getContractCreateResult();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewAccountId()) {
            var autoRenewAccount = entityIdService
                    .lookup(transactionBody.getAutoRenewAccountId())
                    .orElse(EntityId.EMPTY);
            if (!EntityId.isEmpty(autoRenewAccount)) {
                entity.setAutoRenewAccountId(autoRenewAccount.getId());
                recordItem.addEntityId(autoRenewAccount);
            } else {
                Utility.handleRecoverableError("Invalid autoRenewAccountId at {}", recordItem.getConsensusTimestamp());
            }
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (contractCreateResult.hasEvmAddress()) {
            entity.setEvmAddress(
                    DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()));
        }

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        } else {
            // Consensus nodes fall back to an implicit `ContractID(shard, realm, num)` key if one is not provided
            var contractId = entity.toEntityId().toContractID();
            var key = Key.newBuilder().setContractID(contractId).build().toByteArray();
            entity.setKey(key);
        }

        if (transactionBody.hasProxyAccountID()) {
            var proxyAccountId = EntityId.of(transactionBody.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountId);
            recordItem.addEntityId(proxyAccountId);
        }

        entity.setBalance(0L);
        entity.setBalanceTimestamp(recordItem.getConsensusTimestamp());
        entity.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations());
        entity.setMemo(transactionBody.getMemo());
        entity.setType(EntityType.CONTRACT);
        updateStakingInfo(recordItem, entity);
        createContract(recordItem, entity);
        entityListener.onEntity(entity);
    }

    private void createContract(RecordItem recordItem, Entity entity) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        var contract = new Contract();
        contract.setId(entity.getId());

        if (transactionBody.hasFileID()) {
            var fileId = EntityId.of(transactionBody.getFileID());
            contract.setFileId(fileId);
            recordItem.addEntityId(fileId);
        }

        var contractId = recordItem.getTransactionRecord().getReceipt().getContractID();
        ContractBytecode contractBytecode = null;

        for (var sidecar : recordItem.getSidecarRecords()) {
            if (sidecar.hasBytecode() && !sidecar.getMigration()) {
                var bytecode = sidecar.getBytecode();
                if (contractId.equals(bytecode.getContractId())) {
                    contractBytecode = bytecode;
                    contract.setRuntimeBytecode(DomainUtils.toBytes(bytecode.getRuntimeBytecode()));
                    break;
                }
            }
        }

        contract.setInitcode(contractInitcodeService.get(contractBytecode, recordItem));

        // for child transactions FileID is located in parent ContractCreate/EthereumTransaction types
        // and initcode is located in the sidecar
        updateChildFromParent(contract, recordItem);
        entityListener.onContract(contract);
    }

    private void updateStakingInfo(RecordItem recordItem, Entity contract) {
        if (recordItem.getHapiVersion().isLessThan(HAPI_VERSION_0_27_0)) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        contract.setDeclineReward(transactionBody.getDeclineReward());

        switch (transactionBody.getStakedIdCase()) {
            case STAKEDID_NOT_SET:
                return;
            case STAKED_NODE_ID:
                contract.setStakedNodeId(transactionBody.getStakedNodeId());
                break;
            case STAKED_ACCOUNT_ID:
                var accountId = EntityId.of(transactionBody.getStakedAccountId());
                contract.setStakedAccountId(accountId.getId());
                recordItem.addEntityId(accountId);
                break;
        }

        contract.setStakePeriodStart(Utility.getEpochDay(recordItem.getConsensusTimestamp()));
    }

    @Override
    public void updateContractResult(ContractResult contractResult, RecordItem recordItem) {
        if (recordItem.getTransactionBody().hasContractCreateInstance()) {
            var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
            contractResult.setAmount(transactionBody.getInitialBalance());
            contractResult.setFunctionParameters(DomainUtils.toBytes(transactionBody.getConstructorParameters()));
            contractResult.setGasLimit(transactionBody.getGas());
            if (!recordItem.isSuccessful() && transactionBody.getInitcodeSourceCase() == INITCODE) {
                contractResult.setFailedInitcode(DomainUtils.toBytes(transactionBody.getInitcode()));
            }
        }
    }

    private void updateChildFromParent(Contract contract, RecordItem recordItem) {
        if (!recordItem.isChild() || recordItem.getParent() == null) {
            return;
        }

        // Parents may be either ContractCreate or EthereumTransaction
        var parentRecordItem = recordItem.getParent();
        var type = TransactionType.of(parentRecordItem.getTransactionType());

        switch (type) {
            case CONTRACTCREATEINSTANCE -> updateChildFromContractCreateParent(contract, parentRecordItem);
            case ETHEREUMTRANSACTION -> updateChildFromEthereumTransactionParent(contract, parentRecordItem);
            default -> {
                // no-op
            }
        }
    }

    private void updateChildFromContractCreateParent(Contract contract, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        switch (transactionBody.getInitcodeSourceCase()) {
            case FILEID:
                if (contract.getFileId() == null) {
                    var fileId = EntityId.of(transactionBody.getFileID());
                    contract.setFileId(fileId);
                    recordItem.addEntityId(fileId);
                }
                break;
            case INITCODE:
                if (contract.getInitcode() == null) {
                    contract.setInitcode(DomainUtils.toBytes(transactionBody.getInitcode()));
                }
                break;
            default:
                Utility.handleRecoverableError(
                        "Invalid InitcodeSourceCase {} at {}",
                        transactionBody.getInitcodeSourceCase(),
                        recordItem.getConsensusTimestamp());
                break;
        }
    }

    private void updateChildFromEthereumTransactionParent(Contract contract, RecordItem recordItem) {
        var body = recordItem.getTransactionBody().getEthereumTransaction();

        // use callData FileID if present
        if (body.hasCallData() && contract.getFileId() == null) {
            var fileId = EntityId.of(body.getCallData());
            contract.setFileId(fileId);
            recordItem.addEntityId(fileId);
            return;
        }

        if (contract.getInitcode() == null && recordItem.getEthereumTransaction() != null) {
            contract.setInitcode(recordItem.getEthereumTransaction().getCallData());
        }
    }
}
