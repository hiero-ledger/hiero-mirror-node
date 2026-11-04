// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractCallTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(ContractID.getDefaultInstance(), contractId))
                .thenReturn(Optional.of(defaultEntityId));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractCallTransactionHandler(entityIdService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractCall(ContractCallTransactionBody.newBuilder().setContractID(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void testGetEntityIdReceipt() {
        var recordItem = recordItemBuilder.contractCall().build();
        ContractID contractIdBody =
                recordItem.getTransactionBody().getContractCall().getContractID();
        ContractID contractIdReceipt =
                recordItem.getTransactionRecord().getReceipt().getContractID();
        EntityId expectedEntityId = EntityId.of(contractIdReceipt);

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(Optional.of(expectedEntityId));
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }

    @Test
    void updateContractResultEmptyContractCallFunctionParams() {
        ContractResult contractResult = new ContractResult();
        var recordItem = recordItemBuilder.contractCall().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        var transaction = recordItem.getTransactionBody().getContractCall();
        assertThat(contractResult)
                .returns(transaction.getAmount(), ContractResult::getAmount)
                .returns(transaction.getGas(), ContractResult::getGasLimit)
                .returns(
                        DomainUtils.toBytes(transaction.getFunctionParameters()),
                        ContractResult::getFunctionParameters);
        assertThat(recordItem.getEntityTransactions()).isEmpty();
    }

    @Test
    void updateContractResultNonContractCallTransaction() {
        ContractResult contractResult = ContractResult.builder().build();
        var recordItem = recordItemBuilder.contractCreate().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        assertThat(contractResult)
                .returns(null, ContractResult::getAmount)
                .returns(null, ContractResult::getGasLimit)
                .returns(null, ContractResult::getFunctionParameters);
        assertThat(recordItem.getEntityTransactions()).isEmpty();
    }
}
