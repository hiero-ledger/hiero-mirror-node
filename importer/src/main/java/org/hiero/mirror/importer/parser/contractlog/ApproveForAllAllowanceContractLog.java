// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public class ApproveForAllAllowanceContractLog extends AbstractSyntheticContractLog {
    public ApproveForAllAllowanceContractLog(
            RecordItem recordItem, EntityId tokenId, EntityId ownerId, EntityId spenderId, boolean approved) {
        super(
                recordItem,
                tokenId,
                APPROVE_FOR_ALL_SIGNATURE,
                entityIdToBytes(ownerId),
                entityIdToBytes(spenderId),
                null,
                booleanToBytes(approved));
    }
}
