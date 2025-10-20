// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.converter.WeiBarTinyBarConverter;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import org.hiero.mirror.importer.service.ContractBytecodeService;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
final class EthereumTransactionHandler extends AbstractTransactionHandler {

    private final ContractBytecodeService contractBytecodeService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final EthereumTransactionParser ethereumTransactionParser;

    /**
     * Attempts to extract the contract ID from the ethereumTransaction.
     *
     * @param recordItem to check
     * @return The contract ID associated with this ethereum transaction call
     */
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();

        // pull entity from ContractResult
        var contractFunctionResult = transactionRecord.hasContractCreateResult()
                ? transactionRecord.getContractCreateResult()
                : transactionRecord.getContractCallResult();

        return EntityId.of(contractFunctionResult.getContractID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }

    @Override
    public void updateContractResult(ContractResult contractResult, RecordItem recordItem) {
        if (!recordItem.isBlockstream()) {
            super.updateContractResult(contractResult, recordItem);
            return;
        }

        if (recordItem.getEthereumTransaction() == null) {
            return;
        }

        // In blockstreams, no EvmTransactionResult.internal_call_context is populated for ethereum transactions.
        // The values for the fields amount / gasLimit / functionParameters should get populated from the transaction
        // body and the call data file if offloaded.
        var ethereumTransaction = recordItem.getEthereumTransaction();
        contractResult.setAmount(new BigInteger(ethereumTransaction.getValue()).longValue());
        contractResult.setGasLimit(ethereumTransaction.getGasLimit());

        if (!ArrayUtils.isEmpty(ethereumTransaction.getCallData())) {
            contractResult.setFunctionParameters(ethereumTransaction.getCallData());
        } else if (ethereumTransaction.getCallDataId() != null) {
            byte[] bytecode = contractBytecodeService.get(ethereumTransaction.getCallDataId());
            contractResult.setFunctionParameters(bytecode);
        }
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isEthereumTransactions()) {
            return;
        }

        var body = recordItem.getTransactionBody().getEthereumTransaction();
        var ethereumDataBytes = DomainUtils.toBytes(body.getEthereumData());
        try {
            var ethereumTransaction = ethereumTransactionParser.decode(ethereumDataBytes);

            // update ethereumTransaction with body values
            if (body.hasCallData()) {
                ethereumTransaction.setCallDataId(EntityId.of(body.getCallData()));
            }

            // update ethereumTransaction with record values
            var transactionRecord = recordItem.getTransactionRecord();
            ethereumTransaction.setConsensusTimestamp(recordItem.getConsensusTimestamp());
            ethereumTransaction.setData(ethereumDataBytes);
            ethereumTransaction.setHash(DomainUtils.toBytes(transactionRecord.getEthereumHash()));
            ethereumTransaction.setMaxGasAllowance(body.getMaxGasAllowance());
            ethereumTransaction.setPayerAccountId(recordItem.getPayerAccountId());

            if (ArrayUtils.isEmpty(ethereumTransaction.getHash())) {
                var hash = ethereumTransactionParser.getHash(
                        ethereumTransaction.getCallData(),
                        ethereumTransaction.getCallDataId(),
                        ethereumTransaction.getConsensusTimestamp(),
                        ethereumTransaction.getData(),
                        true);
                ethereumTransaction.setHash(hash);
            }

            // EVM logic uses weibar for gas values, convert transaction body gas values to tinybars
            convertGasWeiToTinyBars(ethereumTransaction);

            entityListener.onEthereumTransaction(ethereumTransaction);
            updateAccountNonce(recordItem, ethereumTransaction);
            recordItem.setEthereumTransaction(ethereumTransaction);

            recordItem.addEntityId(ethereumTransaction.getCallDataId());
        } catch (RuntimeException e) {
            Utility.handleRecoverableError(
                    "Error decoding Ethereum transaction data at {}", recordItem.getConsensusTimestamp(), e);
        }
    }

    private void updateAccountNonce(RecordItem recordItem, EthereumTransaction ethereumTransaction) {
        if (!entityProperties.getPersist().isTrackNonce()) {
            return;
        }

        var transactionRecord = recordItem.getTransactionRecord();
        if (!transactionRecord.hasContractCallResult() && !transactionRecord.hasContractCreateResult()) {
            return;
        }

        var functionResult = transactionRecord.hasContractCreateResult()
                ? transactionRecord.getContractCreateResult()
                : transactionRecord.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());
        if (EntityId.isEmpty(senderId)) {
            return;
        }

        Long nonce = null;
        if (functionResult.hasSignerNonce()) {
            nonce = functionResult.getSignerNonce().getValue();
        } else if (recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_47_0)) {
            var status = transactionRecord.getReceipt().getStatus();
            if (!recordItem.isSuccessful()
                    && status != ResponseCodeEnum.CONTRACT_REVERT_EXECUTED
                    && status != ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED) {
                return;
            }

            // Increment the nonce for backwards compatibility
            nonce = ethereumTransaction.getNonce() + 1;
        }

        if (nonce != null) {
            var entity = senderId.toEntity();
            entity.setEthereumNonce(nonce);
            entity.setTimestampRange(null); // Don't trigger a history row
            entityListener.onEntity(entity);
            recordItem.addEntityId(senderId);
        }
    }

    private void convertGasWeiToTinyBars(EthereumTransaction transaction) {
        var converter = WeiBarTinyBarConverter.INSTANCE;
        transaction.setGasPrice(converter.convert(transaction.getGasPrice(), false));
        transaction.setMaxFeePerGas(converter.convert(transaction.getMaxFeePerGas(), false));
        transaction.setMaxPriorityFeePerGas(converter.convert(transaction.getMaxPriorityFeePerGas(), false));
        transaction.setValue(converter.convert(transaction.getValue(), true));
    }
}
