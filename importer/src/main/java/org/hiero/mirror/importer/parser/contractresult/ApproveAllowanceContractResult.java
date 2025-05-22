// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractresult;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public class ApproveAllowanceContractResult extends AbstractSyntheticContractResult {
    public ApproveAllowanceContractResult(RecordItem recordItem, EntityId entityId, EntityId senderId) {
        super(recordItem, entityId, senderId, APPROVE_SIGNATURE);
    }
}
