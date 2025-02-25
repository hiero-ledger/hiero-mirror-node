// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.contractlog;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;

public class TransferIndexedContractLog extends AbstractSyntheticContractLog {
    public TransferIndexedContractLog(
            RecordItem recordItem, EntityId tokenId, EntityId senderId, EntityId receiverId, long amount) {
        super(
                recordItem,
                tokenId,
                TRANSFER_SIGNATURE,
                entityIdToBytes(senderId),
                entityIdToBytes(receiverId),
                longToBytes(amount),
                null);
    }
}
