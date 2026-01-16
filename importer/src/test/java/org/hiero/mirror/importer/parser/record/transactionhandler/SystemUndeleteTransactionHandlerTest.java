// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

class SystemUndeleteTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    SystemUndeleteTransactionHandlerTest() {
        super(false);
    }

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(contractId)).thenReturn(Optional.of(defaultEntityId));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new SystemUndeleteTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setSystemUndelete(SystemUndeleteTransactionBody.newBuilder().setFileID(defaultEntityId.toFileID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }

    // SystemUndelete for file is tested by common test case in AbstractTransactionHandlerTest.
    // Test SystemUndelete for contract here.
    @Test
    void testSystemUndeleteForContract() {
        TransactionBody transactionBody = TransactionBody.newBuilder()
                .setSystemUndelete(
                        SystemUndeleteTransactionBody.newBuilder().setContractID(defaultEntityId.toContractID()))
                .build();

        testGetEntityIdHelper(transactionBody, getDefaultTransactionRecord().build(), defaultEntityId);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void undeleteEmptyEntityIds(EntityId entityId) {
        TransactionBody transactionBody = TransactionBody.newBuilder()
                .setSystemUndelete(
                        SystemUndeleteTransactionBody.newBuilder().setContractID(defaultEntityId.toContractID()))
                .build();

        when(entityIdService.lookup(contractId)).thenReturn(Optional.ofNullable(entityId));

        testGetEntityIdHelper(transactionBody, getDefaultTransactionRecord().build(), EntityId.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(
            value = EntityType.class,
            names = {"CONTRACT", "FILE"})
    void updateTransactionSuccessful(EntityType type) {
        long id = domainBuilder.id();
        var entityId = EntityId.of(id);
        var recordItem = recordItemBuilder
                .systemUndelete()
                .transactionBody(b -> {
                    b.clearId();
                    switch (type) {
                        case CONTRACT -> b.setContractID(ContractID.newBuilder().setContractNum(id));
                        case FILE -> b.setFileID(FileID.newBuilder().setFileNum(id));
                    }
                })
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(entityId))
                .get();
        var expectedEntity = entityId.toEntity().toBuilder()
                .deleted(false)
                .timestampRange(Range.atLeast(timestamp))
                .type(type)
                .build();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulUndeleteNothing() {
        var recordItem = recordItemBuilder
                .systemUndelete()
                .transactionBody(Builder::clearId)
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
