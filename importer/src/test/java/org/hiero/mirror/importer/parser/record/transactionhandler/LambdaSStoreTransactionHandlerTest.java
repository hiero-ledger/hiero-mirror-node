// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.hooks.legacy.LambdaSStoreTransactionBody;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LambdaSStoreTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private static final long HOOK_ID = 7L;

    private AccountID ownerAccount;

    @BeforeEach
    void init() {
        ownerAccount = domainBuilder.entityNum(DEFAULT_ENTITY_NUM).toAccountID();
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new LambdaSStoreTransactionHandler(storageHandler);
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        final var hookId = HookId.newBuilder()
                .setHookId(HOOK_ID)
                .setEntityId(HookEntityId.newBuilder().setAccountId(ownerAccount))
                .build();

        return TransactionBody.newBuilder()
                .setLambdaSstore(LambdaSStoreTransactionBody.newBuilder().setHookId(hookId));
    }

    @Test
    void getTypeIsLambdaSstore() {
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.LAMBDA_SSTORE);
    }

    @Test
    void getEntityWhenBodyPresent() {
        final var recordItem = recordItemBuilder
                .lambdaSstore()
                .transactionBody((LambdaSStoreTransactionBody.Builder b) -> b.setHookId(HookId.newBuilder()
                        .setHookId(HOOK_ID)
                        .setEntityId(HookEntityId.newBuilder().setAccountId(ownerAccount))))
                .build();

        final var entity = transactionHandler.getEntity(recordItem);
        assertThat(entity).isEqualTo(EntityId.of(ownerAccount));
    }

    @Test
    void getEntityWhenBodyAbsentIsEmpty() {
        final var recordItem = recordItemBuilder.contractCall().build();
        final var entity = transactionHandler.getEntity(recordItem);
        assertThat(entity).isEqualTo(EntityId.EMPTY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesToHelperWithExpectedArgs_usesSidecarsFromBuilder() {
        final var recordItem = recordItemBuilder
                .lambdaSstore()
                .transactionBody((LambdaSStoreTransactionBody.Builder b) -> b.setHookId(HookId.newBuilder()
                        .setHookId(HOOK_ID)
                        .setEntityId(HookEntityId.newBuilder().setAccountId(ownerAccount))))
                .build();

        final var txn = txnFor(recordItem);
        transactionHandler.updateTransaction(txn, recordItem);

        final var sidecarsCaptor = ArgumentCaptor.forClass(List.class);

        verify(storageHandler, times(1))
                .processStorageUpdates(
                        eq(recordItem.getConsensusTimestamp()),
                        eq(HOOK_ID),
                        eq(EntityId.of(ownerAccount).getId()),
                        sidecarsCaptor.capture());

        assertThat(sidecarsCaptor.getValue()).containsExactlyElementsOf(recordItem.getSidecarRecords());
    }

    @Test
    void ignoresWhenNoLambdaSstore() {
        final var recordItem = recordItemBuilder.contractCall().build();
        final var txn = new Transaction();
        txn.setConsensusTimestamp(recordItem.getConsensusTimestamp());

        transactionHandler.updateTransaction(txn, recordItem);

        verifyNoInteractions(storageHandler);
    }

    private Transaction txnFor(RecordItem recordItem) {
        final var txn = new Transaction();
        txn.setEntityId(transactionHandler.getEntity(recordItem));
        txn.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        return txn;
    }
}
