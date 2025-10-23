// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hedera.hapi.node.hooks.legacy.EvmHookSpec;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails;
import com.hedera.hapi.node.hooks.legacy.LambdaEvmHook;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EVMHookHandlerTest {

    @Mock
    private EntityListener entityListener;

    @Mock
    private RecordItem recordItem;

    @InjectMocks
    private EVMHookHandler EVMHookHandler;

    @Test
    void processHookCreationDetailsSuccess() {
        // given
        var consensusTimestamp = 1234567890L;
        var entityId = EntityId.of(0, 0, 1000);
        var hookId = 1L;
        var contractId = EntityId.of(0, 0, 2000);
        var adminKey = "test-admin-key".getBytes();

        var evmHookSpec = EvmHookSpec.newBuilder()
                .setContractId(contractId.toContractID())
                .build();

        var lambdaEvmHook = LambdaEvmHook.newBuilder().setSpec(evmHookSpec).build();

        var hookCreationDetails = HookCreationDetails.newBuilder()
                .setExtensionPoint(com.hedera.hapi.node.hooks.legacy.HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .setHookId(hookId)
                .setLambdaEvmHook(lambdaEvmHook)
                .setAdminKey(com.hederahashgraph.api.proto.java.Key.newBuilder()
                        .setEd25519(com.google.protobuf.ByteString.copyFrom(adminKey))
                        .build())
                .build();

        var hookCreationDetailsList = List.of(hookCreationDetails);

        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        // when
        EVMHookHandler.processHookCreationDetails(recordItem, entityId, hookCreationDetailsList);

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener).onHook(hookCaptor.capture());

        var capturedHook = hookCaptor.getValue();
        assertAll(
                () -> assertThat(capturedHook.getHookId()).isEqualTo(hookId),
                () -> assertThat(capturedHook.getContractId()).isEqualTo(contractId),
                () -> assertThat(capturedHook.getAdminKey())
                        .isEqualTo(hookCreationDetails.getAdminKey().toByteArray()),
                () -> assertThat(capturedHook.getExtensionPoint()).isEqualTo(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK),
                () -> assertThat(capturedHook.getType()).isEqualTo(HookType.LAMBDA),
                () -> assertThat(capturedHook.getOwnerId()).isEqualTo(entityId.getId()),
                () -> assertThat(capturedHook.getCreatedTimestamp()).isEqualTo(consensusTimestamp),
                () -> assertThat(capturedHook.getTimestampRange()).isEqualTo(Range.atLeast(consensusTimestamp)),
                () -> assertThat(capturedHook.getDeleted()).isFalse());
    }

    @Test
    void processHookCreationDetailsWithMultipleHooks() {
        // given
        var consensusTimestamp = 1234567890L;
        var entityId = EntityId.of(0, 0, 1000);

        var hook1 = createHookCreationDetails(1L, EntityId.of(0, 0, 2000), "admin-key-1".getBytes());
        var hook2 = createHookCreationDetails(2L, EntityId.of(0, 0, 3000), "admin-key-2".getBytes());

        var hookCreationDetailsList = List.of(hook1, hook2);

        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        // when
        EVMHookHandler.processHookCreationDetails(recordItem, entityId, hookCreationDetailsList);

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener, times(2)).onHook(hookCaptor.capture());

        var capturedHooks = hookCaptor.getAllValues();
        assertThat(capturedHooks).hasSize(2);
        assertThat(capturedHooks.get(0).getHookId()).isEqualTo(1L);
        assertThat(capturedHooks.get(1).getHookId()).isEqualTo(2L);
    }

    @Test
    void processHookCreationDetailsWithNullList() {
        // given
        var entityId = EntityId.of(0, 0, 1000);

        // when
        EVMHookHandler.processHookCreationDetails(recordItem, entityId, null);

        // then
        verifyNoInteractions(entityListener);
    }

    @Test
    void processHookCreationDetailsWithEmptyList() {
        // given
        var entityId = EntityId.of(0, 0, 1000);

        // when
        EVMHookHandler.processHookCreationDetails(recordItem, entityId, Collections.emptyList());

        // then
        verifyNoInteractions(entityListener);
    }

    @Test
    void processHookDeletionSuccess() {
        // given
        var consensusTimestamp = 1234567890L;
        var entityId = EntityId.of(0, 0, 1000);
        var hookId1 = 1L;
        var hookId2 = 2L;
        var hookIdsToDelete = List.of(hookId1, hookId2);

        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        // when
        EVMHookHandler.processHookDeletion(recordItem, entityId, hookIdsToDelete);

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener, times(2)).onHook(hookCaptor.capture());

        var capturedHooks = hookCaptor.getAllValues();
        assertThat(capturedHooks).hasSize(2);

        var firstHook = capturedHooks.get(0);
        var secondHook = capturedHooks.get(1);

        assertAll(
                () -> assertThat(firstHook.getHookId()).isEqualTo(hookId1),
                () -> assertThat(firstHook.getOwnerId()).isEqualTo(entityId.getId()),
                () -> assertThat(firstHook.getDeleted()).isTrue(),
                () -> assertThat(firstHook.getTimestampRange()).isEqualTo(Range.atMost(consensusTimestamp)),
                () -> assertThat(firstHook.getAdminKey()).isNull(),
                () -> assertThat(firstHook.getContractId()).isNull(),
                () -> assertThat(firstHook.getCreatedTimestamp()).isNull(),
                () -> assertThat(firstHook.getExtensionPoint()).isNull(),
                () -> assertThat(firstHook.getType()).isNull(),
                () -> assertThat(secondHook.getHookId()).isEqualTo(hookId2),
                () -> assertThat(secondHook.getOwnerId()).isEqualTo(entityId.getId()),
                () -> assertThat(secondHook.getDeleted()).isTrue(),
                () -> assertThat(secondHook.getTimestampRange()).isEqualTo(Range.atMost(consensusTimestamp)));
    }

    @Test
    void processHookDeletionWithSingleHook() {
        // given
        var consensusTimestamp = 1234567890L;
        var entityId = EntityId.of(0, 0, 1000);
        var hookId = 5L;
        var hookIdsToDelete = List.of(hookId);

        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        // when
        EVMHookHandler.processHookDeletion(recordItem, entityId, hookIdsToDelete);

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener).onHook(hookCaptor.capture());

        var capturedHook = hookCaptor.getValue();
        assertAll(
                () -> assertThat(capturedHook.getHookId()).isEqualTo(hookId),
                () -> assertThat(capturedHook.getOwnerId()).isEqualTo(entityId.getId()),
                () -> assertThat(capturedHook.getDeleted()).isTrue(),
                () -> assertThat(capturedHook.getCreatedTimestamp()).isNull(),
                () -> assertThat(capturedHook.getTimestampRange()).isEqualTo(Range.atMost(consensusTimestamp)));
    }

    @Test
    void processHookDeletionWithNullList() {
        // given
        var entityId = EntityId.of(0, 0, 1000);

        // when
        EVMHookHandler.processHookDeletion(recordItem, entityId, null);

        // then
        verifyNoInteractions(entityListener);
    }

    @Test
    void processHookDeletionWithEmptyList() {
        // given
        var entityId = EntityId.of(0, 0, 1000);

        // when
        EVMHookHandler.processHookDeletion(recordItem, entityId, Collections.emptyList());

        // then
        verifyNoInteractions(entityListener);
    }

    private HookCreationDetails createHookCreationDetails(long hookId, EntityId contractId, byte[] adminKey) {
        var evmHookSpec = EvmHookSpec.newBuilder()
                .setContractId(contractId.toContractID())
                .build();

        var lambdaEvmHook = LambdaEvmHook.newBuilder().setSpec(evmHookSpec).build();

        return HookCreationDetails.newBuilder()
                .setExtensionPoint(com.hedera.hapi.node.hooks.legacy.HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .setHookId(hookId)
                .setLambdaEvmHook(lambdaEvmHook)
                .setAdminKey(com.hederahashgraph.api.proto.java.Key.newBuilder()
                        .setEd25519(com.google.protobuf.ByteString.copyFrom(adminKey))
                        .build())
                .build();
    }
}
