// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class ClprMaintenanceTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRMAINTENANCE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        var manifest = recordItem.getClprEndpointManifest();
        if (manifest == null) {
            Utility.handleRecoverableError(
                    "No clpr_endpoint_manifest_value state change found for successful ClprMaintenance at {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        entityListener.onClprEndpointManifest(ClprEndpointManifest.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .version(manifest.getVersion())
                .serviceAddress(DomainUtils.toBytes(manifest.getServiceAddress()))
                .endpoints(ClprStateChangeHandler.toEndpoints(manifest.getEndpointsList()))
                .build());
    }
}
