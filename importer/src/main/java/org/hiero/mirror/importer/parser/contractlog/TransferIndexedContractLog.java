// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

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
