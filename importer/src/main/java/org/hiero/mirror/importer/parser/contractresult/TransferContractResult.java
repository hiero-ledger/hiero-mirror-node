// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractresult;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public class TransferContractResult extends AbstractSyntheticContractResult {
    public TransferContractResult(RecordItem recordItem, EntityId entityId, EntityId senderId) {
        super(recordItem, entityId, senderId, TRANSFER_SIGNATURE);
    }
}
