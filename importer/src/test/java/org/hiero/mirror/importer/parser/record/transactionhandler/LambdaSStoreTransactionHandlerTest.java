// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LambdaSStoreTransactionHandlerTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    private EvmHookStorageHandler storageHandler;
    private LambdaSStoreTransactionHandler handler;
    private RecordItemBuilder recordItemBuilder;

    @BeforeEach
    void setUp() {
        storageHandler = mock(EvmHookStorageHandler.class);
        handler = new LambdaSStoreTransactionHandler(storageHandler);
        recordItemBuilder = new RecordItemBuilder();
    }

    @Test
    void getType() {
        final var type = handler.getType();
        assertThat(type).isEqualTo(TransactionType.LAMBDA_SSTORE);
    }

    @Test
    void getEntityWithAccount() {
        final var recordItem = recordItemBuilder.lambdaSstore().build();
        final var account = recordItem
                .getTransactionBody()
                .getLambdaSstore()
                .getHookId()
                .getEntityId()
                .getAccountId();

        final var actual = handler.getEntity(recordItem);
        assertThat(actual).isEqualTo(EntityId.of(account));
    }

    @Test
    void getEntityWithContract() {
        final var contract = recordItemBuilder.contractId();

        final var recordItem = recordItemBuilder
                .lambdaSstore()
                .transactionBody(b -> b.setHookId(HookId.newBuilder()
                        .setEntityId(HookEntityId.newBuilder().setContractId(contract))))
                .build();

        final var actual = handler.getEntity(recordItem);
        assertThat(actual).isEqualTo(EntityId.of(contract));
    }

    @Test
    void processSlotUpdates() {
        final var recordItem = recordItemBuilder.lambdaSstore().build();
        final var body = recordItem.getTransactionBody().getLambdaSstore();
        final var hookIdEntityId = body.getHookId();

        final var ownerEntityId = EntityId.of(hookIdEntityId.getEntityId().getAccountId());
        final var expectedHookId = hookIdEntityId.getHookId();
        final var expectedStorageUpdates = body.getStorageUpdatesList();

        final var txn = txnFor(recordItem, ownerEntityId);
        handler.updateTransaction(txn, recordItem);

        final var tsCaptor = ArgumentCaptor.forClass(Long.class);
        final var hookIdCaptor = ArgumentCaptor.forClass(Long.class);
        final var ownerIdCaptor = ArgumentCaptor.forClass(Long.class);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<LambdaStorageUpdate>> updatesCaptor = ArgumentCaptor.forClass(List.class);

        verify(storageHandler, times(1))
                .processStorageUpdates(
                        tsCaptor.capture(), hookIdCaptor.capture(), ownerIdCaptor.capture(), updatesCaptor.capture());

        assertThat(tsCaptor.getValue()).isEqualTo(recordItem.getConsensusTimestamp());
        assertThat(hookIdCaptor.getValue()).isEqualTo(expectedHookId);
        assertThat(ownerIdCaptor.getValue()).isEqualTo(ownerEntityId.getId());

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
