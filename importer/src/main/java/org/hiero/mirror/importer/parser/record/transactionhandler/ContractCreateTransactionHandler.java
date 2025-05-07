// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_27_0;
import static com.hederahashgraph.api.proto.java.ContractCreateTransactionBody.InitcodeSourceCase.INITCODE;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
class ContractCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EntityProperties entityProperties;

    ContractCreateTransactionHandler(
            EntityIdService entityIdService, EntityListener entityListener, EntityProperties entityProperties) {
        super(entityIdService, entityListener, TransactionType.CONTRACTCREATEINSTANCE);
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
        Contract contract = new Contract();
        contract.setId(entity.getId());

        switch (transactionBody.getInitcodeSourceCase()) {
            case FILEID:
                var fileId = EntityId.of(transactionBody.getFileID());
                contract.setFileId(fileId);
                recordItem.addEntityId(fileId);
                break;
            case INITCODE:
                contract.setInitcode(DomainUtils.toBytes(transactionBody.getInitcode()));
                break;
            default:
                break;
        }

        var contractId = recordItem.getTransactionRecord().getReceipt().getContractID();
        var sidecarRecords = recordItem.getSidecarRecords();

        for (var sidecar : sidecarRecords) {
            if (sidecar.hasBytecode() && !sidecar.getMigration()) {
                var bytecode = sidecar.getBytecode();
                if (contractId.equals(bytecode.getContractId())) {
                    if (contract.getInitcode() == null) {
                        contract.setInitcode(DomainUtils.toBytes(bytecode.getInitcode()));
                    }

                    contract.setRuntimeBytecode(DomainUtils.toBytes(bytecode.getRuntimeBytecode()));
                    break;
                }
            }
        }

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
