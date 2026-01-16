// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public class TransferContractLog extends AbstractSyntheticContractLog {

    boolean hasMultiPartyOrigin;

    public TransferContractLog(
            RecordItem recordItem,
            EntityId tokenId,
            EntityId senderId,
            EntityId receiverId,
            long amount,
            boolean hasMultiPartyOrigin) {
        super(
                recordItem,
                tokenId,
                TRANSFER_SIGNATURE,
                entityIdToBytes(senderId),
                entityIdToBytes(receiverId),
                null,
                longToBytes(amount));
        this.hasMultiPartyOrigin = hasMultiPartyOrigin;
    }

    public boolean hasMultiPartyOrigin() {
        return hasMultiPartyOrigin;
    }
}
