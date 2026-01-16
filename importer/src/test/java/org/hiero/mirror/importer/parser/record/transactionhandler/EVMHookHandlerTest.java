// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.importer.parser.record.transactionhandler.EVMHookHandler.keccak256;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.hooks.legacy.EvmHookSpec;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails;
import com.hedera.hapi.node.hooks.legacy.LambdaEvmHook;
import com.hedera.hapi.node.hooks.legacy.LambdaMappingEntries;
import com.hedera.hapi.node.hooks.legacy.LambdaMappingEntry;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageSlot;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hedera.services.stream.proto.StorageChange;
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
import org.hiero.mirror.common.util.DomainUtils;
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
        final var trimmedValue = Bytes.concat(new byte[] {0x1}, domainBuilder.bytes(15));
        final var value = leftPadBytes(trimmedValue, 32);

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
        // given
        final var consensusTimestamp = domainBuilder.timestamp();
        final var hookId = domainBuilder.id();
        final var ownerId = domainBuilder.entityId();

        final var updates = new ArrayList<LambdaStorageUpdate>(numSlots);
        final var expectedStorageChanges = new ArrayList<HookStorageChange>(numSlots);
        final var hookStorageChangeBuilder = HookStorageChange.builder()
                .consensusTimestamp(consensusTimestamp)
                .hookId(hookId)
                .ownerId(ownerId.getId());

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

            updates.add(LambdaStorageUpdate.newBuilder()
                    .setStorageSlot(LambdaStorageSlot.newBuilder()
                            .setKey(ByteString.copyFrom(key))
                            .setValue(ByteString.copyFrom(value)))
                    .build());
            expectedStorageChanges.add(hookStorageChangeBuilder
                    .key(key)
                    .valueRead(trimmedValue)
                    .valueWritten(trimmedValue)
                    .build());
        }

        // when
        eVMHookHandler.processStorageUpdates(consensusTimestamp, hookId, ownerId, updates);

        // then
        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(numSlots)).onHookStorageChange(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrderElementsOf(expectedStorageChanges);
        assertThat(output.getAll())
                .contains("Recoverable error. Ignoring LambdaStorageUpdate=UPDATE_NOT_SET at consensus_timestamp="
                        + consensusTimestamp);
    }

    @ParameterizedTest
    @CsvSource({"4, 1", "4, 5", "32, 1", "32, 5", "33, 1"})
    void processesMappingEntries(int keySize, int numEntries, CapturedOutput output) {
        // given
        final var consensusTimestamp = domainBuilder.timestamp();
        final var hookId = domainBuilder.id();
        final var ownerId = domainBuilder.entityId();
        final var mappingSlot = domainBuilder.bytes(2);
        final var expectedStorageChanges = new ArrayList<HookStorageChange>();
        final var hookStorageChangeBuilder = HookStorageChange.builder()
                .consensusTimestamp(consensusTimestamp)
                .hookId(hookId)
                .ownerId(ownerId.getId());

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
            expectedStorageChanges.add(hookStorageChangeBuilder
                    .key(keccak256(leftPadBytes(key, 32), leftPadBytes(mappingSlot, 32)))
                    .valueRead(trimmedValue)
                    .valueWritten(trimmedValue)
                    .build());
        }

        final var update = LambdaStorageUpdate.newBuilder()
                .setMappingEntries(entriesBuilder.build())
                .build();

        // when
        eVMHookHandler.processStorageUpdates(
                consensusTimestamp, hookId, ownerId, List.of(update, LambdaStorageUpdate.getDefaultInstance()));

        // then
        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(numEntries)).onHookStorageChange(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrderElementsOf(expectedStorageChanges);
        assertThat(output.getAll())
                .contains("Recoverable error. Ignoring LambdaStorageUpdate=UPDATE_NOT_SET at consensus_timestamp="
                        + consensusTimestamp);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    void processesMappingEntriesWithPreimage(int numEntries, CapturedOutput output) {
        // given
        final var consensusTimestamp = domainBuilder.timestamp();
        final var hookId = domainBuilder.id();
        final var ownerId = domainBuilder.entityId();
        final var mappingSlot = domainBuilder.bytes(4);
        final var entriesBuilder = LambdaMappingEntries.newBuilder().setMappingSlot(ByteString.copyFrom(mappingSlot));
        final var hookStorageChangeBuilder = HookStorageChange.builder()
                .consensusTimestamp(consensusTimestamp)
                .hookId(hookId)
                .ownerId(ownerId.getId());
        final var expectedStorageChanges = new ArrayList<HookStorageChange>();

        for (int i = 0; i < numEntries; i++) {
            final var preimage = domainBuilder.bytes(8);
            final var trimmedValue = Bytes.concat(new byte[] {0x1}, domainBuilder.bytes(16));
            final var value = leftPadBytes(trimmedValue, 32);

            entriesBuilder.addEntries(LambdaMappingEntry.newBuilder()
                    .setPreimage(ByteString.copyFrom(preimage))
                    .setValue(ByteString.copyFrom(value)));
            expectedStorageChanges.add(hookStorageChangeBuilder
                    .key(keccak256(keccak256(preimage), leftPadBytes(mappingSlot, 32)))
                    .valueRead(trimmedValue)
                    .valueWritten(trimmedValue)
                    .build());
        }

        final var update = LambdaStorageUpdate.newBuilder()
                .setMappingEntries(entriesBuilder.build())
                .build();

        // when
        eVMHookHandler.processStorageUpdates(
                consensusTimestamp, hookId, ownerId, List.of(LambdaStorageUpdate.getDefaultInstance(), update));

        // then
        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(numEntries)).onHookStorageChange(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrderElementsOf(expectedStorageChanges);
        assertThat(output.getAll())
                .contains("Recoverable error. Ignoring LambdaStorageUpdate=UPDATE_NOT_SET at consensus_timestamp="
                        + consensusTimestamp);
    }

    @Test
    void processStorageUpdatesWithDuplicates() {
        // given
        final long consensusTimestamp = domainBuilder.timestamp();
        final long hookId = domainBuilder.id();
        final var ownerId = domainBuilder.entityId();
        final var expectedStorageChanges = new ArrayList<HookStorageChange>();
        final var hookStorageChangeBuilder = HookStorageChange.builder()
                .consensusTimestamp(consensusTimestamp)
                .hookId(hookId)
                .ownerId(ownerId.getId());
        final var storageUpdates = new ArrayList<LambdaStorageUpdate>();

        final byte[] key = domainBuilder.nonZeroBytes(16);
        storageUpdates.add(LambdaStorageUpdate.newBuilder()
                .setStorageSlot(LambdaStorageSlot.newBuilder()
                        .setKey(DomainUtils.fromBytes(key))
                        .setValue(ByteString.copyFrom(domainBuilder.nonZeroBytes(32))))
                .build());
        final byte[] value = domainBuilder.nonZeroBytes(32);
        storageUpdates.add((LambdaStorageUpdate.newBuilder()
                .setStorageSlot(LambdaStorageSlot.newBuilder()
                        .setKey(DomainUtils.fromBytes(key))
                        .setValue(DomainUtils.fromBytes(value)))
                .build()));
        expectedStorageChanges.add(hookStorageChangeBuilder
                .key(key)
                .valueRead(value)
                .valueWritten(value)
                .build());

        // Three mapping entries with the same mapping slot
        // - the second entry overrides the first
        // - the last entry is overridden by an explicit key slot
        final byte[] mappingSlot = domainBuilder.bytes(4);
        byte[] preimage = domainBuilder.bytes(8);
        final var mappingEntriesBuilder =
                LambdaMappingEntries.newBuilder().setMappingSlot(DomainUtils.fromBytes(mappingSlot));
        mappingEntriesBuilder
                .addEntries(LambdaMappingEntry.newBuilder()
                        .setPreimage(DomainUtils.fromBytes(preimage))
                        .setValue(DomainUtils.fromBytes(domainBuilder.nonZeroBytes(24))))
                .addEntries(LambdaMappingEntry.newBuilder()
                        .setPreimage(DomainUtils.fromBytes(preimage))
                        .setValue(DomainUtils.fromBytes(value)));
        expectedStorageChanges.add(hookStorageChangeBuilder
                .key(keccak256(keccak256(preimage), leftPadBytes(mappingSlot, 32)))
                .valueRead(value)
                .valueWritten(value)
                .build());
        preimage = domainBuilder.bytes(16);
        mappingEntriesBuilder.addEntries(LambdaMappingEntry.newBuilder()
                .setPreimage(DomainUtils.fromBytes(preimage))
                .setValue(DomainUtils.fromBytes(domainBuilder.nonZeroBytes(24))));
        storageUpdates.add(LambdaStorageUpdate.newBuilder()
                .setMappingEntries(mappingEntriesBuilder)
                .build());

        // An explicit key slot overrides the last mapping entry
        final byte[] mappedKey = keccak256(keccak256(preimage), leftPadBytes(mappingSlot, 32));
        storageUpdates.add(LambdaStorageUpdate.newBuilder()
                .setStorageSlot(LambdaStorageSlot.newBuilder()
                        .setKey(DomainUtils.fromBytes(mappedKey))
                        .setValue(DomainUtils.fromBytes(value)))
                .build());
        expectedStorageChanges.add(hookStorageChangeBuilder
                .key(mappedKey)
                .valueRead(value)
                .valueWritten(value)
                .build());

        // when
        eVMHookHandler.processStorageUpdates(consensusTimestamp, hookId, ownerId, storageUpdates);

        // then
        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(3)).onHookStorageChange(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrderElementsOf(expectedStorageChanges);
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

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void processStorageUpdatesForSidecar(int numStorageChanges) {
        // Given
        final var consensusTimestamp = 456L;
        final var hookId = 10L;
        final var ownerId = 2000L;

        final var storageChanges = new ArrayList<StorageChange>(numStorageChanges);
        final var expectedKeys = new ArrayList<byte[]>(numStorageChanges);
        final var expectedValuesRead = new ArrayList<byte[]>(numStorageChanges);
        final var expectedValuesWritten = new ArrayList<byte[]>(numStorageChanges);

        for (int i = 0; i < numStorageChanges; i++) {
            final var key = domainBuilder.bytes(32);
            final var valueRead = domainBuilder.bytes(32);
            final var valueWritten = domainBuilder.bytes(32);

            expectedKeys.add(key);
            expectedValuesRead.add(valueRead);
            expectedValuesWritten.add(valueWritten);

            var storageChangeBuilder = StorageChange.newBuilder()
                    .setSlot(ByteString.copyFrom(key))
                    .setValueRead(ByteString.copyFrom(valueRead));

            // Add valueWritten for some storage changes
            if (i % 2 == 0) {
                storageChangeBuilder.setValueWritten(
                        com.google.protobuf.BytesValue.of(ByteString.copyFrom(valueWritten)));
            }

            storageChanges.add(storageChangeBuilder.build());
        }

        // When
        eVMHookHandler.processStorageUpdatesForSidecar(consensusTimestamp, hookId, ownerId, storageChanges);

        // Then
        final var captor = ArgumentCaptor.forClass(HookStorageChange.class);
        verify(entityListener, times(numStorageChanges)).onHookStorageChange(captor.capture());
        final var changes = captor.getAllValues();

        assertThat(changes).hasSize(numStorageChanges);
        for (int i = 0; i < numStorageChanges; i++) {
            final var change = changes.get(i);
            assertThat(change.getConsensusTimestamp()).isEqualTo(consensusTimestamp);
            assertThat(change.getHookId()).isEqualTo(hookId);
            assertThat(change.getOwnerId()).isEqualTo(ownerId);
            assertThat(change.getKey()).isEqualTo(expectedKeys.get(i));
            assertThat(change.getValueRead()).isEqualTo(expectedValuesRead.get(i));

            // Check valueWritten - should be null for odd indices, set for even indices
            if (i % 2 == 0) {
                assertThat(change.getValueWritten()).isEqualTo(expectedValuesWritten.get(i));
            } else {
                assertThat(change.getValueWritten()).isNull();
            }
        }
    }

    @Test
    void processStorageUpdatesForSidecarWithEmptyList() {
        // Given
        final var consensusTimestamp = 789L;
        final var hookId = 15L;
        final var ownerId = 3000L;
        final var emptyStorageChanges = List.<StorageChange>of();

        // When
        eVMHookHandler.processStorageUpdatesForSidecar(consensusTimestamp, hookId, ownerId, emptyStorageChanges);

        // Then
        verify(entityListener, times(0)).onHookStorageChange(any());
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
