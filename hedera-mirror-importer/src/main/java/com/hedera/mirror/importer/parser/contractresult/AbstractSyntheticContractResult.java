// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.contractresult;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

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
