// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.node.hooks.legacy.LambdaSStoreTransactionBody;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EVMHookStorageHandlerTest {

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final DomainBuilder domainBuilder = new DomainBuilder();

    private EVMHookStorageHandler handler;
    private EntityListener entityListener;

    @BeforeEach
    void setUp() {
        entityListener = mock(EntityListener.class);
        handler = new EVMHookStorageHandler(entityListener);
    }

    @Test
    void processesStateChangesFromSidecar() {
        final var hookId = 42L;
        final var owner = domainBuilder.entityId();

        final var slot1 = domainBuilder.bytes(32);
        final var val1 = domainBuilder.bytes(32);
        final var slot2 = domainBuilder.bytes(32);
        final var val2 = domainBuilder.bytes(32);

        final var recordItem = recordItemBuilder
                .lambdaSstore()
                .transactionBody((LambdaSStoreTransactionBody.Builder b) -> b.setHookId(HookId.newBuilder()
                        .setHookId(hookId)
                        .setEntityId(HookEntityId.newBuilder().setAccountId(owner.toAccountID()))))
                .sidecarRecords(rs -> {
                    rs.clear();
                    rs.add(stateChangesSidecar(sc(slot1, val1), sc(slot2, val2)));
                })
                .build();

        final var consensusTs = recordItem.getConsensusTimestamp();
        final var ownerId = owner.getId();

        handler.processStorageUpdates(consensusTs, hookId, ownerId, recordItem.getSidecarRecords());

        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(2)).onHookStorageChange(captor.capture());
        final var changes = captor.getAllValues();

        assertThat(changes.getFirst().getConsensusTimestamp()).isEqualTo(consensusTs);
        assertThat(changes.getFirst().getHookId()).isEqualTo(hookId);
        assertThat(changes.getFirst().getOwnerId()).isEqualTo(ownerId);
        assertThat(changes.getFirst().getKey()).isEqualTo(slot1);
        assertThat(changes.getFirst().getValueRead()).isEqualTo(val1);
        assertThat(changes.getFirst().getValueWritten()).isEqualTo(val1);
        assertThat(changes.getLast().getKey()).isEqualTo(slot2);
        assertThat(changes.getLast().getValueRead()).isEqualTo(val2);
        assertThat(changes.getLast().getValueWritten()).isEqualTo(val2);
    }

    @Test
    void ignoresSidecarWithoutStateChanges() {
        final var emptySidecar = TransactionSidecarRecord.newBuilder().build();
        handler.processStorageUpdates(123L, 7L, 55L, List.of(emptySidecar));
        verifyNoInteractions(entityListener);
    }

    @Test
    void ignoresUpdatesWithNoSidecars() {
        handler.processStorageUpdates(123L, 7L, 55L, List.of());
        verifyNoInteractions(entityListener);
    }

    @Test
    void handlesStorageChangeWithoutValueWritten() {
        final var hookId = 99L;
        final var owner = domainBuilder.entityId();
        final var key = domainBuilder.bytes(32);

        final var recordItem = recordItemBuilder
                .lambdaSstore()
                .transactionBody((LambdaSStoreTransactionBody.Builder b) -> b.setHookId(HookId.newBuilder()
                        .setHookId(hookId)
                        .setEntityId(HookEntityId.newBuilder().setAccountId(owner.toAccountID()))))
                .sidecarRecords(rs -> {
                    rs.clear();
                    rs.add(stateChangesSidecar(sc(key, null)));
                })
                .build();

        final var consensusTs = recordItem.getConsensusTimestamp();
        final var ownerId = owner.getId();

        handler.processStorageUpdates(consensusTs, hookId, ownerId, recordItem.getSidecarRecords());

        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener).onHookStorageChange(captor.capture());
        final var change = captor.getValue();

        assertThat(change.getKey()).isEqualTo(key);
        assertThat(change.getValueRead()).isEqualTo(new byte[0]);
        assertThat(change.getValueWritten()).isNull();
    }

    private TransactionSidecarRecord.Builder stateChangesSidecar(StorageChange... changes) {
        final var csc = ContractStateChange.newBuilder();
        for (final var sc : changes) {
            csc.addStorageChanges(sc);
        }
        return TransactionSidecarRecord.newBuilder()
                .setStateChanges(ContractStateChanges.newBuilder().addContractStateChanges(csc));
    }

    private StorageChange sc(byte[] slot, byte[] valueWrittenOrNull) {
        final var b = StorageChange.newBuilder().setSlot(ByteString.copyFrom(slot));
        if (valueWrittenOrNull != null) {
            b.setValueWritten(BytesValue.of(ByteString.copyFrom(valueWrittenOrNull)));
        }
        return b.build();
    }
}
