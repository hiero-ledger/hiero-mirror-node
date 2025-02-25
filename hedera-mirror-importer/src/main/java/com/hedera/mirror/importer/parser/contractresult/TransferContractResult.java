// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.contractresult;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;

public class TransferContractResult extends AbstractSyntheticContractResult {
    public TransferContractResult(RecordItem recordItem, EntityId entityId, EntityId senderId) {
        super(recordItem, entityId, senderId, TRANSFER_SIGNATURE);
    }
}
