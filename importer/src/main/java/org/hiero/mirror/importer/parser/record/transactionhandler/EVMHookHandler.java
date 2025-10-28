// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.CollectionUtils;

@Named
@NullMarked
@RequiredArgsConstructor
final class EVMHookHandler {

    private final EntityListener entityListener;

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
        if (!CollectionUtils.isEmpty(hookCreationDetailsList)) {
            hookCreationDetailsList.forEach(
                    hookCreationDetails -> processHookCreationDetail(recordItem, entityId, hookCreationDetails));
        }
    }

    private void processHookCreationDetail(
            RecordItem recordItem, long entityId, HookCreationDetails hookCreationDetails) {
        // Check if any hook oneof field is set
        if (!hookCreationDetails.hasLambdaEvmHook() && !hookCreationDetails.hasPureEvmHook()) {
            Utility.handleRecoverableError(
                    "Skipping hook creation for hookId {} - no hook implementation set in transaction at {}",
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            return;
        }

        // Get the spec from whichever hook type is set
        final var spec = hookCreationDetails.hasLambdaEvmHook()
                ? hookCreationDetails.getLambdaEvmHook().getSpec()
                : hookCreationDetails.getPureEvmHook().getSpec();

        // Check if contract ID is set in spec
        if (!spec.hasContractId()) {
            Utility.handleRecoverableError(
                    "Skipping hook creation for hookId {} - contract_id not set in hook spec in transaction at {}",
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            return;
        }

        final var hookBuilder = Hook.builder()
                .contractId(EntityId.of(spec.getContractId()))
                .createdTimestamp(recordItem.getConsensusTimestamp())
                .deleted(false)
                .extensionPoint(translateHookExtensionPoint(hookCreationDetails.getExtensionPoint()))
                .hookId(hookCreationDetails.getHookId())
                .ownerId(entityId)
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .type(translateHookType(hookCreationDetails));

        // Check if adminKey is set before accessing it
        if (hookCreationDetails.hasAdminKey()) {
            hookBuilder.adminKey(hookCreationDetails.getAdminKey().toByteArray());
        }

        final var hook = hookBuilder.build();
        recordItem.addEntityId(hook.getContractId());
        entityListener.onHook(hook);

        // Process storage updates if present (only for LambdaEvmHook)
        if (hookCreationDetails.hasLambdaEvmHook()) {
            final var lambdaEvmHook = hookCreationDetails.getLambdaEvmHook();
            if (!CollectionUtils.isEmpty(lambdaEvmHook.getStorageUpdatesList())) {
                // TODO: Process storage updates when hook storage functionality is implemented
            }
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
            Hook hook = Hook.builder()
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
            case UNRECOGNIZED -> {
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
     * @param hookCreationDetails the HookCreationDetails from the transaction protobuf
     * @return the corresponding domain HookType
     */
    private HookType translateHookType(HookCreationDetails hookCreationDetails) {
        if (hookCreationDetails.hasLambdaEvmHook()) {
            return HookType.LAMBDA;
        } else if (hookCreationDetails.hasPureEvmHook()) {
            Utility.handleRecoverableError(
                    "PureEvmHook not yet supported for hookId {}, treating as LAMBDA", hookCreationDetails.getHookId());
            return HookType.PURE;
        } else {
            Utility.handleRecoverableError(
                    "Unknown hook type in HookCreationDetails for hookId {}, defaulting to LAMBDA",
                    hookCreationDetails.getHookId());
            return HookType.LAMBDA;
        }
    }
}
