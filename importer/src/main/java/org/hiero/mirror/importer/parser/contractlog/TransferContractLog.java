// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import com.google.protobuf.ByteString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TrimmedTopicsAndData;

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
     * Converts the topics and data fields to a {@link TrimmedTopicsAndData}
     *
     * @return TrimmedTopicsAndData the converted object
     */
    public TrimmedTopicsAndData getTrimmedTopicsAndData() {
        var topic1AsByteString = getTopic0() != null ? ByteString.copyFrom(getTopic0()) : ByteString.EMPTY;
        var topic2AsByteString = getTopic1() != null ? ByteString.copyFrom(getTopic1()) : ByteString.EMPTY;
        var topic3AsByteString = getTopic2() != null ? ByteString.copyFrom(getTopic2()) : ByteString.EMPTY;
        var topic4AsByteString = getTopic3() != null ? ByteString.copyFrom(getTopic3()) : ByteString.EMPTY;
        var dataAsByteString = getData() != null ? ByteString.copyFrom(getData()) : ByteString.EMPTY;
        return new TrimmedTopicsAndData(
                topic1AsByteString, topic2AsByteString, topic3AsByteString, topic4AsByteString, dataAsByteString);
    }
}
