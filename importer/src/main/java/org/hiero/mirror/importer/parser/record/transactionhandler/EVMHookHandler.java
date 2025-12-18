// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.google.common.collect.Range;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails.HookCase;
import com.hedera.hapi.node.hooks.legacy.LambdaMappingEntries;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageSlot;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hedera.services.stream.proto.StorageChange;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.jcajce.provider.digest.Keccak.Digest256;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.CollectionUtils;

@Named
@NullMarked
@RequiredArgsConstructor
final class EVMHookHandler implements EvmHookStorageHandler {

    private final EntityListener entityListener;
    private final EntityIdService entityIdService;

    static byte[] keccak256(byte[] input) {
        final var d = new Digest256();
        return d.digest(input);
    }

    static byte[] keccak256(byte[] key, byte[] mappingSlot) {
        final var d = new Digest256();
        d.update(key);
        return d.digest(mappingSlot);
    }

    /**
     * Processes both hook deletions and hook creations for a given transaction. This method serves as the main entry
     * point for handling hook-related operations from any transaction type that supports hooks. Hook deletions are
     * processed first to ensure proper lifecycle management, followed by hook creations.
     *
     * @param recordItem              the record item containing the transaction
     * @param entityId                the entity ID that owns the hooks (account or contract)
     * @param hookCreationDetailsList the list of hooks to create, can be null or empty
     * @param hookIdsToDeleteList     the list of hook IDs to delete, can be null or empty
     */
    void process(
            RecordItem recordItem,
            long entityId,
            List<HookCreationDetails> hookCreationDetailsList,
            List<Long> hookIdsToDeleteList) {
        processHookDeletion(recordItem, entityId, hookIdsToDeleteList);
        processHookCreationDetails(recordItem, entityId, hookCreationDetailsList);
    }

    @Override
    public void processStorageUpdates(
            long consensusTimestamp, long hookId, long ownerId, List<LambdaStorageUpdate> storageUpdates) {
        final var context = new StorageUpdateContext(consensusTimestamp, hookId, ownerId);
        context.process(storageUpdates);
    }

    @Override
    public void processStorageUpdatesForSidecar(
            long consensusTimestamp, long hookId, long ownerId, List<StorageChange> storageUpdates) {
        for (var storageChange : storageUpdates) {
            byte[] valueWritten = storageChange.hasValueWritten()
                    ? toBytes(storageChange.getValueWritten().getValue())
                    : null;
            var hookStorageChange = HookStorageChange.builder()
                    .consensusTimestamp(consensusTimestamp)
                    .hookId(hookId)
                    .ownerId(ownerId)
                    .key(toBytes(storageChange.getSlot()))
                    .valueRead(toBytes(storageChange.getValueRead()))
                    .valueWritten(valueWritten)
                    .build();

            entityListener.onHookStorageChange(hookStorageChange);
        }
    }

    /**
     * Processes hook creation details from any transaction type and creates corresponding Hook entities. This method
     * can be used by CryptoCreate, CryptoUpdate, ContractCreate, and ContractUpdate handlers.
     *
     * @param recordItem              the record item containing the transaction
     * @param entityId                the entity ID that owns the hooks
     * @param hookCreationDetailsList the list of hook creation details from the transaction
     */
    private void processHookCreationDetails(
            RecordItem recordItem, long entityId, List<HookCreationDetails> hookCreationDetailsList) {
        hookCreationDetailsList.forEach(
                hookCreationDetails -> processHookCreationDetail(recordItem, entityId, hookCreationDetails));
    }

    private void processHookCreationDetail(
            RecordItem recordItem, long entityId, HookCreationDetails hookCreationDetails) {
        // Check if lambda_evm_hook oneof field is set
        if (!hookCreationDetails.hasLambdaEvmHook()) {
            Utility.handleRecoverableError(
                    "Skipping hook creation for hookId {} - lambda_evm_hook not set in transaction at {}",
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            return;
        }

        final var lambdaEvmHook = hookCreationDetails.getLambdaEvmHook();
        final var spec = lambdaEvmHook.getSpec();

        // Check if contract ID is set in spec
        if (!spec.hasContractId()) {
            Utility.handleRecoverableError(
                    "Skipping hook creation for hookId {} - contract_id not set in hook spec in transaction at {}",
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            return;
        }

        final var hookBuilder = Hook.builder()
                .contractId(entityIdService.lookup(spec.getContractId()).orElse(EntityId.EMPTY))
                .createdTimestamp(recordItem.getConsensusTimestamp())
                .deleted(false)
                .extensionPoint(translateHookExtensionPoint(hookCreationDetails.getExtensionPoint()))
                .hookId(hookCreationDetails.getHookId())
                .ownerId(entityId)
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .type(translateHookType(hookCreationDetails.getHookCase()));

        // Check if adminKey is set before accessing it
        if (hookCreationDetails.hasAdminKey()) {
            hookBuilder.adminKey(hookCreationDetails.getAdminKey().toByteArray());
        }

        final var hook = hookBuilder.build();
        recordItem.addEntityId(hook.getContractId());
        entityListener.onHook(hook);

        // Process storage updates if present
        if (!CollectionUtils.isEmpty(lambdaEvmHook.getStorageUpdatesList())) {
            processStorageUpdates(
                    recordItem.getConsensusTimestamp(),
                    hookCreationDetails.getHookId(),
                    entityId,
                    lambdaEvmHook.getStorageUpdatesList());
        }
    }

    /**
     * Processes hook deletion by performing soft delete. Creates Hook entities with deleted=true and uses upsert
     * functionality to update the database.
     *
     * @param recordItem          the record item containing the transaction
     * @param entityId            the entity ID that owns the hooks
     * @param hookIdsToDeleteList the list of hook IDs to delete
     */
    private void processHookDeletion(RecordItem recordItem, long entityId, List<Long> hookIdsToDeleteList) {
        hookIdsToDeleteList.forEach(hookId -> {
            final var hook = Hook.builder()
                    .deleted(true)
                    .hookId(hookId)
                    .ownerId(entityId)
                    .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                    .build();
            entityListener.onHook(hook);
        });
    }

    /**
     * Translates the protobuf HookExtensionPoint from the transaction body to the domain HookExtensionPoint.
     *
     * @param protoExtensionPoint the HookExtensionPoint from the transaction protobuf
     * @return the corresponding domain HookExtensionPoint
     */
    private HookExtensionPoint translateHookExtensionPoint(
            com.hedera.hapi.node.hooks.legacy.HookExtensionPoint protoExtensionPoint) {
        return switch (protoExtensionPoint) {
            case ACCOUNT_ALLOWANCE_HOOK -> HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK;
            default -> {
                Utility.handleRecoverableError(
                        "Unrecognized HookExtensionPoint: {}, defaulting to ACCOUNT_ALLOWANCE_HOOK",
                        protoExtensionPoint);
                yield HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK;
            }
        };
    }

    /**
     * Translates the hook type from the oneof case in the transaction body to the domain HookType.
     *
     * @param hookCase the HookCase from the transaction protobuf
     * @return the corresponding domain HookType
     */
    private HookType translateHookType(HookCase hookCase) {
        return switch (hookCase) {
            case LAMBDA_EVM_HOOK -> HookType.LAMBDA;
            default -> {
                Utility.handleRecoverableError(
                        "Unrecognized HookCase: {}, defaulting to ACCOUNT_ALLOWANCE_HOOK", hookCase);
                yield HookType.LAMBDA;
            }
        };
    }

    @RequiredArgsConstructor
    private class StorageUpdateContext {

        private final long consensusTimestamp;
        private final long hookId;
        private final long ownerEntityId;

        private final Set<Integer> processed = new HashSet<>();

        void process(final List<LambdaStorageUpdate> storageUpdates) {
            for (int index = storageUpdates.size() - 1; index >= 0; index--) {
                // process the storage updates in reversed order to honor the last value in case of duplicate slot keys
                final var update = storageUpdates.get(index);
                switch (update.getUpdateCase()) {
                    case MAPPING_ENTRIES -> process(update.getMappingEntries());
                    case STORAGE_SLOT -> process(update.getStorageSlot());
                    default ->
                        Utility.handleRecoverableError(
                                "Ignoring LambdaStorageUpdate={} at consensus_timestamp={}",
                                update.getUpdateCase(),
                                consensusTimestamp);
                }
            }
        }

        private void persistChange(final byte[] key, final byte[] valueWritten) {
            if (!processed.add(Arrays.hashCode(key))) {
                return;
            }

            final var change = HookStorageChange.builder()
                    .consensusTimestamp(consensusTimestamp)
                    .hookId(hookId)
                    .key(key)
                    .ownerId(ownerEntityId)
                    .valueRead(valueWritten)
                    .valueWritten(valueWritten)
                    .build();
            entityListener.onHookStorageChange(change);
        }

        private void process(final LambdaMappingEntries mappingEntries) {
            final var mappingSlot = leftPadBytes(toBytes(mappingEntries.getMappingSlot()), 32);
            final var entries = mappingEntries.getEntriesList();

            for (int index = entries.size() - 1; index >= 0; index--) {
                // process the entries in reversed order to honor the last value in case of duplicate slot keys
                final var entry = entries.get(index);
                final var mappingKey = entry.hasKey()
                        ? leftPadBytes(toBytes(entry.getKey()), 32)
                        : keccak256(toBytes(entry.getPreimage()));
                final var derivedSlot = keccak256(mappingKey, mappingSlot);
                final var valueWritten = toBytes(entry.getValue());
                persistChange(derivedSlot, valueWritten);
            }
        }

        private void process(final LambdaStorageSlot storageSlot) {
            final var slotKey = toBytes(storageSlot.getKey());
            final var valueWritten = toBytes(storageSlot.getValue());
            persistChange(slotKey, valueWritten);
        }
    }
}
