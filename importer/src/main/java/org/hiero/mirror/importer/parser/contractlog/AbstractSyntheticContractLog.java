// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.hiero.mirror.common.util.DomainUtils.trim;

import lombok.Data;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;

@Data
public abstract class AbstractSyntheticContractLog implements SyntheticContractLog {

    private static final byte[] FALSE = new byte[] {0};
    private static final byte[] TRUE = new byte[] {1};

    private final RecordItem recordItem;
    private final EntityId entityId;
    private final byte[] topic0;
    private final byte[] topic1;
    private final byte[] topic2;
    private final byte[] topic3;
    private final byte[] data;

    AbstractSyntheticContractLog(
            RecordItem recordItem,
            EntityId tokenId,
            byte[] topic0,
            byte[] topic1,
            byte[] topic2,
            byte[] topic3,
            byte[] data) {
        this.recordItem = recordItem;
        this.entityId = tokenId;
        this.topic0 = topic0;
        this.topic1 = topic1;
        this.topic2 = topic2;
        this.topic3 = topic3;
        this.data = data;
    }

    public static final byte[] TRANSFER_SIGNATURE = Bytes.fromHexString(
                    "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
            .toArray();
    static final byte[] APPROVE_FOR_ALL_SIGNATURE = Bytes.fromHexString(
                    "17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31")
            .toArray();
    static final byte[] APPROVE_SIGNATURE = Bytes.fromHexString(
                    "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925")
            .toArray();

    static byte[] entityIdToBytes(EntityId entityId) {
        if (EntityId.isEmpty(entityId)) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        return trim(DomainUtils.toEvmAddress(entityId));
    }

    static byte[] longToBytes(long value) {
        return trim(Bytes.ofUnsignedLong(value).toArrayUnsafe());
    }

    static byte[] booleanToBytes(boolean value) {
        return value ? TRUE : FALSE;
    }
}
