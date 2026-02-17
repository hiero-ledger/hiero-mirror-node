// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import com.hederahashgraph.api.proto.java.ContractLoginfo;
import java.util.Arrays;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.util.Utility;

public class TransferContractLog extends AbstractSyntheticContractLog {

    public TransferContractLog(
            RecordItem recordItem, EntityId tokenId, EntityId senderId, EntityId receiverId, long amount) {
        super(
                recordItem,
                tokenId,
                TRANSFER_SIGNATURE,
                entityIdToBytes(senderId),
                entityIdToBytes(receiverId),
                null,
                longToBytes(amount));
    }

    /**
     * Checks if this TransferContractLog is equal to a ContractLoginfo by comparing topics and data.
     *
     * @param contractLoginfo the ContractLoginfo to compare against
     * @return true if the logs are equal, false otherwise
     */
    public boolean equalsContractLoginfo(ContractLoginfo contractLoginfo) {
        return Arrays.equals(getTopic0(), Utility.getTopic(contractLoginfo, 0))
                && Arrays.equals(getTopic1(), Utility.getTopic(contractLoginfo, 1))
                && Arrays.equals(getTopic2(), Utility.getTopic(contractLoginfo, 2))
                && Arrays.equals(getTopic3(), Utility.getTopic(contractLoginfo, 3))
                && Arrays.equals(getData(), Utility.getDataTrimmed(contractLoginfo));
    }
}
