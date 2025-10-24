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
import org.jspecify.annotations.NullMarked;

@Named
@RequiredArgsConstructor
@NullMarked
final class EVMHookHandler {

    private final EntityListener entityListener;

    /**
     * Processes hook creation details from any transaction type and creates corresponding Hook entities. This method
     * can be used by CryptoCreate, CryptoUpdate, ContractCreate, and ContractUpdate handlers.
     *
     * @param recordItem              the record item containing the transaction
     * @param entityId                the entity ID that owns the hooks
     * @param hookCreationDetailsList the list of hook creation details from the transaction
     */
    void processHookCreationDetails(
            RecordItem recordItem, EntityId entityId, List<HookCreationDetails> hookCreationDetailsList) {
        if (hookCreationDetailsList != null && !hookCreationDetailsList.isEmpty()) {
            hookCreationDetailsList.forEach(hookCreationDetails -> {
                var spec = hookCreationDetails.getLambdaEvmHook().getSpec();
                Hook hook = Hook.builder()
                        .adminKey(hookCreationDetails.getAdminKey().toByteArray())
                        .createdTimestamp(recordItem.getConsensusTimestamp())
                        .contractId(EntityId.of(spec.getContractId()))
                        .deleted(false)
                        .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                        .hookId(hookCreationDetails.getHookId())
                        .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                        .type(HookType.LAMBDA)
                        .ownerId(entityId.getId())
                        .build();
                entityListener.onHook(hook);
            });
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
    public void processHookDeletion(RecordItem recordItem, EntityId entityId, List<Long> hookIdsToDeleteList) {
        if (hookIdsToDeleteList != null && !hookIdsToDeleteList.isEmpty()) {
            hookIdsToDeleteList.forEach(hookId -> {
                Hook hook = Hook.builder()
                        .hookId(hookId)
                        .ownerId(entityId.getId())
                        .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                        .deleted(true)
                        .build();
                entityListener.onHook(hook);
            });
        }
    }
}
