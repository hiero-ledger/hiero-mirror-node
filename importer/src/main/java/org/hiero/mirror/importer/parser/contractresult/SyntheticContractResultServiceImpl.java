// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractresult;

import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
public class SyntheticContractResultServiceImpl implements SyntheticContractResultService {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public void create(SyntheticContractResult result) {
        if (isContract(result.getRecordItem()) || !entityProperties.getPersist().isSyntheticContractResults()) {
            return;
        }

        RecordItem recordItem = result.getRecordItem();
        TransactionBody transactionBody = recordItem.getTransactionBody();
        TransactionRecord transactionRecord = recordItem.getTransactionRecord();

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        ContractResult contractResult = new ContractResult();

        contractResult.setCallResult(Bytes.of(recordItem.isSuccessful() ? 1 : 0).toArrayUnsafe());
        contractResult.setConsensusTimestamp(consensusTimestamp);
        contractResult.setContractId(result.getEntityId().getId());
        contractResult.setFunctionParameters(result.getFunctionParameters());
        contractResult.setGasLimit(0L);
        contractResult.setPayerAccountId(recordItem.getPayerAccountId());
        contractResult.setSenderId(result.getSenderId());
        contractResult.setTransactionHash(recordItem.getTransactionHash());
        contractResult.setTransactionIndex(recordItem.getTransactionIndex());
        contractResult.setTransactionNonce(transactionBody.getTransactionID().getNonce());
        contractResult.setTransactionResult(transactionRecord.getReceipt().getStatusValue());

        entityListener.onContractResult(contractResult);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }
}
