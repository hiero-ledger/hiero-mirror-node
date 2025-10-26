// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails;
import jakarta.inject.Named;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.CollectionUtils;

@Named
@RequiredArgsConstructor
@NullMarked
@CustomLog
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
        // Check if lambda_evm_hook oneof field is set
        if (!hookCreationDetails.hasLambdaEvmHook()) {
            log.warn(
                    "Skipping hook creation for hookId {} - lambda_evm_hook not set in transaction at {}",
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            return;
        }

        final var lambdaEvmHook = hookCreationDetails.getLambdaEvmHook();

        // Check if spec is set
        if (!lambdaEvmHook.hasSpec()) {
            log.warn(
                    "Skipping hook creation for hookId {} - spec not set in lambda_evm_hook in transaction at {}",
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            return;
        }

        final var spec = lambdaEvmHook.getSpec();

        // Check if contract ID is set
        if (!spec.hasContractId()) {
            log.warn(
                    "Skipping hook creation for hookId {} - contract_id not set in spec in transaction at {}",
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            return;
        }

        final var hookBuilder = Hook.builder()
                .createdTimestamp(recordItem.getConsensusTimestamp())
                .contractId(EntityId.of(spec.getContractId()))
                .deleted(false)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .hookId(hookCreationDetails.getHookId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .type(HookType.LAMBDA)
                .ownerId(entityId);

        // Check if adminKey is set before accessing it
        if (hookCreationDetails.hasAdminKey()) {
            hookBuilder.adminKey(hookCreationDetails.getAdminKey().toByteArray());
        }

        final var hook = hookBuilder.build();
        recordItem.addEntityId(hook.getContractId());
        entityListener.onHook(hook);

        // Process storage updates if present
        if (!CollectionUtils.isEmpty(lambdaEvmHook.getStorageUpdatesList())) {
            log.debug(
                    "Processing {} storage updates for hookId {} in transaction at {}",
                    lambdaEvmHook.getStorageUpdatesList().size(),
                    hookCreationDetails.getHookId(),
                    recordItem.getConsensusTimestamp());
            // TODO: Process storage updates when hook storage functionality is implemented
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
        if (!CollectionUtils.isEmpty(hookIdsToDeleteList)) {
            hookIdsToDeleteList.forEach(hookId -> {
                Hook hook = Hook.builder()
                        .hookId(hookId)
                        .ownerId(entityId)
                        .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                        .deleted(true)
                        .build();
                entityListener.onHook(hook);
            });
        }
    }
}
