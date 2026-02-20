// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import com.hederahashgraph.api.proto.java.ContractLoginfo;
import java.util.function.Supplier;
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
        return Utility.byteArrayCompare(
                        getByteArrayOrDefault(() -> Utility.getTopic(contractLoginfo, 0)),
                        getByteArrayOrDefault(this::getTopic0))
                && Utility.byteArrayCompare(
                        getByteArrayOrDefault(() -> Utility.getTopic(contractLoginfo, 1)),
                        getByteArrayOrDefault(this::getTopic1))
                && Utility.byteArrayCompare(
                        getByteArrayOrDefault(() -> Utility.getTopic(contractLoginfo, 2)),
                        getByteArrayOrDefault(this::getTopic2))
                && Utility.byteArrayCompare(
                        getByteArrayOrDefault(() -> Utility.getTopic(contractLoginfo, 3)),
                        getByteArrayOrDefault(this::getTopic3))
                && Utility.byteArrayCompare(
                        getByteArrayOrDefault(() -> Utility.getDataTrimmed(contractLoginfo)),
                        getByteArrayOrDefault(this::getData));
    }

    private byte[] getByteArrayOrDefault(Supplier<byte[]> supplier) {
        byte[] result = supplier.get();
        return result != null ? result : new byte[0];
    }
}
