// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;

public class ApproveAllowanceIndexedContractLog extends AbstractSyntheticContractLog {
    public ApproveAllowanceIndexedContractLog(
            RecordItem recordItem, EntityId tokenId, EntityId ownerId, EntityId spenderId, long amount) {
        super(
                recordItem,
                tokenId,
                APPROVE_SIGNATURE,
                entityIdToBytes(ownerId),
                entityIdToBytes(spenderId),
                longToBytes(amount),
                null);
    }
}
