// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.util.Predicates.negate;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

class ContractDeleteTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    private final EntityId obtainerId = domainBuilder.entityNum(99);

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(ContractID.getDefaultInstance(), contractId))
                .thenReturn(Optional.of(defaultEntityId));
        when(entityIdService.lookup(obtainerId.toContractID())).thenReturn(Optional.of(obtainerId));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractDeleteTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractDeleteInstance(ContractDeleteTransactionBody.newBuilder()
                        .setContractID(defaultEntityId.toContractID())
                        .setTransferAccountID(obtainerId.toAccountID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecs() {
        List<UpdateEntityTestSpec> specs = new ArrayList<>();
        Entity expected = getExpectedEntityWithTimestamp();
        expected.setDeleted(true);
        expected.setObtainerId(obtainerId);
        expected.setPermanentRemoval(false);

        specs.add(UpdateEntityTestSpec.builder()
                .description("Delete with account obtainer")
                .expected(expected)
                .recordItem(getRecordItem(
                        getDefaultTransactionBody().build(),
                        getDefaultTransactionRecord().build()))
                .build());

        TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
                .setContractDeleteInstance(ContractDeleteTransactionBody.newBuilder()
                        .setContractID(defaultEntityId.toContractID())
                        .setPermanentRemoval(true)
                        .setTransferContractID(obtainerId.toContractID()));

        expected = TestUtils.clone(expected);
        expected.setPermanentRemoval(true);
        specs.add(UpdateEntityTestSpec.builder()
                .description("Delete with contract obtainer and permanent removal")
                .expected(expected)
                .recordItem(getRecordItem(
                        transactionBody.build(), getDefaultTransactionRecord().build()))
                .build());
        return specs;
    }

    @Test
    void testGetEntityIdReceipt() {
        var recordItem = recordItemBuilder.contractDelete().build();
        ContractID contractIdBody =
                recordItem.getTransactionBody().getContractDeleteInstance().getContractID();
        ContractID contractIdReceipt =
                recordItem.getTransactionRecord().getReceipt().getContractID();
        var expectedEntityId = EntityId.of(contractIdReceipt);

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(Optional.of(expectedEntityId));
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void testEmptyEntityIdReceipt(EntityId entityId) {
        var recordItem = recordItemBuilder.contractDelete().build();
        ContractID contractIdBody =
                recordItem.getTransactionBody().getContractDeleteInstance().getContractID();
        ContractID contractIdReceipt =
                recordItem.getTransactionRecord().getReceipt().getContractID();

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(Optional.ofNullable(entityId));
        EntityId receivedEntityId = transactionHandler.getEntity(recordItem);
        assertThat(receivedEntityId).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.contractDelete().build();
        var body = recordItem.getTransactionBody().getContractDeleteInstance();
        var contractId = EntityId.of(body.getContractID());
        var recipientId = Optional.of(EntityId.of(body.getTransferAccountID()))
                .filter(negate(EntityId::isEmpty))
                .orElse(EntityId.of(body.getTransferContractID()));
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var expectedEntity = contractId.toEntity().toBuilder()
                .deleted(true)
                .obtainerId(recipientId)
                .permanentRemoval(body.getPermanentRemoval())
                .timestampRange(Range.atLeast(timestamp))
                .type(getExpectedEntityIdType())
                .build();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction, recipientId);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }
}
