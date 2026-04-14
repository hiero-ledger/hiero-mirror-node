// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public class TransferContractLog extends AbstractSyntheticContractLog {
    public TransferContractLog(
            RecordItem recordItem, EntityId tokenId, byte[] senderId, byte[] receiverId, long amount) {
        super(recordItem, tokenId, TRANSFER_SIGNATURE, senderId, receiverId, null, longToBytes(amount));
    }
}
