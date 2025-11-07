// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.importer.parser.record.transactionhandler.EVMHookHandler.keccak256;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.hooks.legacy.EvmHookSpec;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails;
import com.hedera.hapi.node.hooks.legacy.LambdaEvmHook;
import com.hedera.hapi.node.hooks.legacy.LambdaMappingEntries;
import com.hedera.hapi.node.hooks.legacy.LambdaMappingEntry;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageSlot;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
final class EVMHookHandlerTest {
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private EntityListener entityListener;

    @Mock
    private EntityIdService entityIdService;

    @Mock
    private RecordItem recordItem;

    @InjectMocks
    private EVMHookHandler eVMHookHandler;

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

        final var key = domainBuilder.bytes(32);
        final var leadingZeros = new byte[16];
        final var trimmedValue = ByteBuffer.allocate(16)
                .put((byte) 0x01)
                .put(domainBuilder.bytes(15))
                .array();
        final var value =
                ByteBuffer.allocate(32).put(leadingZeros).put(trimmedValue).array();

        var lambdaEvmHook = LambdaEvmHook.newBuilder()
                .addStorageUpdates(LambdaStorageUpdate.newBuilder()
                        .setStorageSlot(LambdaStorageSlot.newBuilder()
                                .setKey(ByteString.copyFrom(key))
                                .setValue(ByteString.copyFrom(value)))
                        .build())
                .setSpec(evmHookSpec)
                .build();

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
        when(entityIdService.lookup(contractId.toContractID())).thenReturn(Optional.of(contractId));
        eVMHookHandler.process(recordItem, entityId.getId(), hookCreationDetailsList, List.of());

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener).onHook(hookCaptor.capture());

        ArgumentCaptor<HookStorageChange> hookStorageCaptor = forClass(HookStorageChange.class);
        verify(entityListener, times(1)).onHookStorageChange(hookStorageCaptor.capture());

        var capturedHook = hookCaptor.getValue();
        var capturedHookStorageChange = hookStorageCaptor.getValue();
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
                () -> assertThat(capturedHook.getDeleted()).isFalse(),
                () -> assertThat(capturedHookStorageChange.getHookId()).isEqualTo(hookId),
                () -> assertThat(capturedHookStorageChange.getOwnerId()).isEqualTo(entityId.getNum()),
                () -> assertThat(capturedHookStorageChange.getKey()).isEqualTo(key),
                () -> assertThat(capturedHookStorageChange.getValueRead()).isEqualTo(trimmedValue),
                () -> assertThat(capturedHookStorageChange.getValueWritten()).isEqualTo(trimmedValue));
    }

    @Test
    void processHookCreationDetailsWithEmptyContract() {
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
        when(entityIdService.lookup(contractId.toContractID())).thenReturn(Optional.empty());
        eVMHookHandler.process(recordItem, entityId.getId(), hookCreationDetailsList, List.of());

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener).onHook(hookCaptor.capture());

        var capturedHook = hookCaptor.getValue();
        assertAll(
                () -> assertThat(capturedHook.getHookId()).isEqualTo(hookId),
                () -> assertThat(capturedHook.getContractId()).isEqualTo(EntityId.EMPTY),
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
        eVMHookHandler.process(recordItem, entityId.getId(), hookCreationDetailsList, List.of());

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener, times(2)).onHook(hookCaptor.capture());

        var capturedHooks = hookCaptor.getAllValues();
        assertThat(capturedHooks).hasSize(2);
        assertThat(capturedHooks.get(0).getHookId()).isEqualTo(1L);
        assertThat(capturedHooks.get(1).getHookId()).isEqualTo(2L);
    }

    @Test
    void processHookCreationDetailsWithEmptyList() {
        // given
        var entityId = EntityId.of(0, 0, 1000);

        // when
        eVMHookHandler.process(recordItem, entityId.getId(), List.of(), List.of());

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
        eVMHookHandler.process(recordItem, entityId.getId(), List.of(), hookIdsToDelete);

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
                () -> assertThat(firstHook.getTimestampRange()).isEqualTo(Range.atLeast(consensusTimestamp)),
                () -> assertThat(firstHook.getAdminKey()).isNull(),
                () -> assertThat(firstHook.getContractId()).isNull(),
                () -> assertThat(firstHook.getCreatedTimestamp()).isNull(),
                () -> assertThat(firstHook.getExtensionPoint()).isNull(),
                () -> assertThat(firstHook.getType()).isNull(),
                () -> assertThat(secondHook.getHookId()).isEqualTo(hookId2),
                () -> assertThat(secondHook.getOwnerId()).isEqualTo(entityId.getId()),
                () -> assertThat(secondHook.getDeleted()).isTrue(),
                () -> assertThat(secondHook.getTimestampRange()).isEqualTo(Range.atLeast(consensusTimestamp)));
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
        eVMHookHandler.process(recordItem, entityId.getId(), List.of(), hookIdsToDelete);

        // then
        ArgumentCaptor<Hook> hookCaptor = forClass(Hook.class);
        verify(entityListener).onHook(hookCaptor.capture());
        verifyNoInteractions(entityIdService);

        var capturedHook = hookCaptor.getValue();
        assertAll(
                () -> assertThat(capturedHook.getHookId()).isEqualTo(hookId),
                () -> assertThat(capturedHook.getOwnerId()).isEqualTo(entityId.getId()),
                () -> assertThat(capturedHook.getDeleted()).isTrue(),
                () -> assertThat(capturedHook.getCreatedTimestamp()).isNull(),
                () -> assertThat(capturedHook.getTimestampRange()).isEqualTo(Range.atLeast(consensusTimestamp)));
    }

    @Test
    void processHookDeletionWithEmptyList() {
        // given
        var entityId = EntityId.of(0, 0, 1000);

        // when
        eVMHookHandler.process(recordItem, entityId.getId(), List.of(), List.of());

        // then
        verifyNoInteractions(entityListener);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    void processesStorageSlots(int numSlots, CapturedOutput output) {
        final var consensusTimestamp = 123L;
        final var hookId = 7L;
        final var ownerEntityId = domainBuilder.entityId();

        final var updates = new ArrayList<LambdaStorageUpdate>(numSlots);
        final var expectedKeys = new ArrayList<byte[]>(numSlots);
        final var expectedValues = new ArrayList<byte[]>(numSlots);

        updates.add(LambdaStorageUpdate.getDefaultInstance());

        for (int i = 0; i < numSlots; i++) {
            final var key = domainBuilder.bytes(32);
            final var leadingZeros = new byte[16];
            final var trimmedValue = ByteBuffer.allocate(16)
                    .put((byte) 0x01)
                    .put(domainBuilder.bytes(15))
                    .array();
            final var value =
                    ByteBuffer.allocate(32).put(leadingZeros).put(trimmedValue).array();

            expectedKeys.add(key);
            expectedValues.add(trimmedValue);

            updates.add(LambdaStorageUpdate.newBuilder()
                    .setStorageSlot(LambdaStorageSlot.newBuilder()
                            .setKey(ByteString.copyFrom(key))
                            .setValue(ByteString.copyFrom(value)))
                    .build());
        }

        eVMHookHandler.processStorageUpdates(consensusTimestamp, hookId, ownerEntityId, updates);

        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(numSlots)).onHookStorageChange(captor.capture());
        final var changes = captor.getAllValues();

        assertThat(changes).hasSize(numSlots);
        for (int i = 0; i < numSlots; i++) {
            final var change = changes.get(i);
            assertThat(change.getConsensusTimestamp()).isEqualTo(consensusTimestamp);
            assertThat(change.getHookId()).isEqualTo(hookId);
            assertThat(change.getOwnerId()).isEqualTo(ownerEntityId.getId());
            assertThat(change.getKey()).isEqualTo(expectedKeys.get(i));
            assertThat(change.getValueWritten()).isEqualTo(expectedValues.get(i));
            assertThat(change.getValueRead()).isEqualTo(expectedValues.get(i));
        }

        assertThat(output.getAll())
                .contains("Recoverable error. Ignoring LambdaStorageUpdate=UPDATE_NOT_SET at consensus_timestamp="
                        + consensusTimestamp);
    }

    @ParameterizedTest
    @CsvSource({"4, 1", "4, 5", "32, 1", "32, 5", "33, 1"})
    void processesMappingEntries(int keySize, int numEntries, CapturedOutput output) {
        final var consensusTimestamp = 100L;
        final var hookId = 8L;
        final var ownerEntityId = domainBuilder.entityId();
        final var mappingSlot = domainBuilder.bytes(2);
        final var expectedValues = new ArrayList<byte[]>(numEntries);
        final var expectedKeys = new ArrayList<byte[]>(numEntries);

        final var entriesBuilder = LambdaMappingEntries.newBuilder().setMappingSlot(ByteString.copyFrom(mappingSlot));

        for (int i = 0; i < numEntries; i++) {
            final var key = domainBuilder.bytes(keySize);
            final var leadingZeros = new byte[16];
            final var trimmedValue = ByteBuffer.allocate(16)
                    .put((byte) 0x01)
                    .put(domainBuilder.bytes(15))
                    .array();
            final var value =
                    ByteBuffer.allocate(32).put(leadingZeros).put(trimmedValue).array();
            entriesBuilder.addEntries(LambdaMappingEntry.newBuilder()
                    .setKey(ByteString.copyFrom(key))
                    .setValue(ByteString.copyFrom(value)));

            expectedKeys.add(key);
            expectedValues.add(trimmedValue);
        }

        final var update = LambdaStorageUpdate.newBuilder()
                .setMappingEntries(entriesBuilder.build())
                .build();

        eVMHookHandler.processStorageUpdates(
                consensusTimestamp, hookId, ownerEntityId, List.of(update, LambdaStorageUpdate.getDefaultInstance()));

        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(numEntries)).onHookStorageChange(captor.capture());
        final var changes = captor.getAllValues();

        for (int i = 0; i < numEntries; i++) {
            final var change = changes.get(i);

            final var expectedKey = keccak256(leftPadBytes(expectedKeys.get(i), 32), leftPadBytes(mappingSlot, 32));
            assertThat(change.getConsensusTimestamp()).isEqualTo(consensusTimestamp);
            assertThat(change.getHookId()).isEqualTo(hookId);
            assertThat(change.getOwnerId()).isEqualTo(ownerEntityId.getId());
            assertThat(change.isDeleted()).isFalse();
            assertThat(change.getKey()).isEqualTo(expectedKey);
            assertThat(change.getValueWritten()).isEqualTo(expectedValues.get(i));
            assertThat(change.getValueRead()).isEqualTo(expectedValues.get(i));
        }

        assertThat(output.getAll())
                .contains("Recoverable error. Ignoring LambdaStorageUpdate=UPDATE_NOT_SET at consensus_timestamp="
                        + consensusTimestamp);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    void processesMappingEntriesWithPreimage(int numEntries, CapturedOutput output) {
        final var consensusTimestamp = 321L;
        final var hookId = 5L;
        final var ownerEntityId = domainBuilder.entityId();
        final var mappingSlot = domainBuilder.bytes(4);
        final var entriesBuilder = LambdaMappingEntries.newBuilder().setMappingSlot(ByteString.copyFrom(mappingSlot));

        final var preimages = new ArrayList<byte[]>(numEntries);
        final var expectedValues = new ArrayList<byte[]>(numEntries);

        for (int i = 0; i < numEntries; i++) {
            final var preimage = domainBuilder.bytes(8);
            final var leadingZeros = new byte[15];
            final var trimmedValue = domainBuilder.bytes(17);
            final var value =
                    ByteBuffer.allocate(32).put(leadingZeros).put(trimmedValue).array();

            preimages.add(preimage);
            expectedValues.add(trimmedValue);

            entriesBuilder.addEntries(LambdaMappingEntry.newBuilder()
                    .setPreimage(ByteString.copyFrom(preimage))
                    .setValue(ByteString.copyFrom(value)));
        }

        final var update = LambdaStorageUpdate.newBuilder()
                .setMappingEntries(entriesBuilder.build())
                .build();

        eVMHookHandler.processStorageUpdates(
                consensusTimestamp, hookId, ownerEntityId, List.of(LambdaStorageUpdate.getDefaultInstance(), update));

        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(numEntries)).onHookStorageChange(captor.capture());
        final var changes = captor.getAllValues();
        assertThat(changes).hasSize(numEntries);

        for (int i = 0; i < numEntries; i++) {
            final var expectedSlot = keccak256(keccak256(preimages.get(i)), leftPadBytes(mappingSlot, 32));
            final var change = changes.get(i);

            assertThat(change.getConsensusTimestamp()).isEqualTo(consensusTimestamp);
            assertThat(change.getHookId()).isEqualTo(hookId);
            assertThat(change.getOwnerId()).isEqualTo(ownerEntityId.getId());
            assertThat(change.isDeleted()).isFalse();
            assertThat(change.getKey()).isEqualTo(expectedSlot);
            assertThat(change.getValueWritten()).isEqualTo(expectedValues.get(i));
            assertThat(change.getValueRead()).isEqualTo(expectedValues.get(i));
        }

        assertThat(output.getAll())
                .contains("Recoverable error. Ignoring LambdaStorageUpdate=UPDATE_NOT_SET at consensus_timestamp="
                        + consensusTimestamp);
    }

    @Test
    void handlesInvalidStorageUpdateLogsRecoverableError(CapturedOutput output) {
        final var consensusTimestamp = 123L;
        final var hookId = 7L;
        final var ownerId = domainBuilder.entityId();

        final var updates = List.of(LambdaStorageUpdate.getDefaultInstance());

        eVMHookHandler.processStorageUpdates(consensusTimestamp, hookId, ownerId, updates);

        assertThat(output.getAll())
                .contains("Recoverable error. Ignoring LambdaStorageUpdate=UPDATE_NOT_SET at consensus_timestamp="
                        + consensusTimestamp);
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
