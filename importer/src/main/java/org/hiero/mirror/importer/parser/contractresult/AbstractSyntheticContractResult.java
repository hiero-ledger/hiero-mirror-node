// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractresult;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

@Data
@RequiredArgsConstructor
public abstract class AbstractSyntheticContractResult implements SyntheticContractResult {
    private final RecordItem recordItem;
    private final EntityId entityId;
    private final EntityId senderId;
    private final byte[] functionParameters;

    static final byte[] TRANSFER_SIGNATURE = hexToBytes("a9059cbb");

    static final byte[] APPROVE_SIGNATURE = hexToBytes("095ea7b3");

    static byte[] hexToBytes(String hex) {
        return Bytes.fromHexString(hex).toArrayUnsafe();
    }
}
