// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.hooks.legacy.LambdaSStoreTransactionBody;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class LambdaSStoreTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EvmHookStorageHandler storageHandler = mock(EvmHookStorageHandler.class);
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @BeforeEach
    void setUp() {
        reset(storageHandler);
        when(entityIdService.lookup(defaultEntityId.toAccountID())).thenReturn(Optional.of(defaultEntityId));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new LambdaSStoreTransactionHandler(storageHandler, entityIdService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setLambdaSstore(LambdaSStoreTransactionBody.newBuilder()
                        .setHookId(HookId.newBuilder()
                                .setEntityId(HookEntityId.newBuilder().setAccountId(defaultEntityId.toAccountID()))));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Test
    void getType() {
        final var type = transactionHandler.getType();
        assertThat(type).isEqualTo(TransactionType.LAMBDA_SSTORE);
    }

    @Test
    void getEntityWithAccount() {
        final var recordItem = recordItemBuilder.lambdaSStore().build();
        final var account = recordItem
                .getTransactionBody()
                .getLambdaSstore()
                .getHookId()
                .getEntityId()
                .getAccountId();

        when(entityIdService.lookup(account)).thenReturn(Optional.of(EntityId.of(account)));

        final var actual = transactionHandler.getEntity(recordItem);
        assertThat(actual).isEqualTo(EntityId.of(account));
    }

    @Test
    void getEntityWithContract() {
        final var contract = recordItemBuilder.contractId();

        final var recordItem = recordItemBuilder
                .lambdaSStore()
                .transactionBody(b -> b.setHookId(HookId.newBuilder()
                        .setEntityId(HookEntityId.newBuilder().setContractId(contract))))
                .build();

        when(entityIdService.lookup(contract)).thenReturn(Optional.of(EntityId.of(contract)));

        final var actual = transactionHandler.getEntity(recordItem);
        assertThat(actual).isEqualTo(EntityId.of(contract));
    }

    @Test
    void processSlotUpdates() {
        final var recordItem = recordItemBuilder.lambdaSStore().build();
        final var body = recordItem.getTransactionBody().getLambdaSstore();
        final var hookIdEntityId = body.getHookId();

        final var ownerEntityId = EntityId.of(hookIdEntityId.getEntityId().getAccountId());
        final var expectedHookId = hookIdEntityId.getHookId();
        final var expectedStorageUpdates = body.getStorageUpdatesList();

        final var txn = txnFor(recordItem, ownerEntityId);
        transactionHandler.updateTransaction(txn, recordItem);

        final var tsCaptor = ArgumentCaptor.forClass(Long.class);
        final var hookIdCaptor = ArgumentCaptor.forClass(Long.class);
        final var ownerIdCaptor = ArgumentCaptor.forClass(EntityId.class);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<LambdaStorageUpdate>> updatesCaptor = ArgumentCaptor.forClass(List.class);

        verify(storageHandler, times(1))
                .processStorageUpdates(
                        tsCaptor.capture(), hookIdCaptor.capture(), ownerIdCaptor.capture(), updatesCaptor.capture());

        assertThat(tsCaptor.getValue()).isEqualTo(recordItem.getConsensusTimestamp());
        assertThat(hookIdCaptor.getValue()).isEqualTo(expectedHookId);
        assertThat(ownerIdCaptor.getValue()).isEqualTo(ownerEntityId);

        final var updates = updatesCaptor.getValue();

        assertThat(updates.isEmpty()).isFalse();
        assertThat(updates).hasSize(expectedStorageUpdates.size());

        for (var i = 0; i < updates.size(); i++) {
            final var actualSlot = updates.get(i).getStorageSlot();
            final var expectedSlot = expectedStorageUpdates.get(i).getStorageSlot();

            assertThat(actualSlot.getKey()).isEqualTo(expectedSlot.getKey());
            assertThat(actualSlot.getValue()).isEqualTo(expectedSlot.getValue());
        }
    }

    private static Transaction txnFor(final RecordItem recordItem, EntityId entityId) {
        final var txn = new Transaction();
        txn.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        txn.setEntityId(entityId);
        return txn;
    }
}
