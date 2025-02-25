// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.token;

import com.hedera.mirror.common.domain.entity.EntityId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // for Builder
@Builder
@Data
@NoArgsConstructor
public class NftTransfer {

    public static final long WILDCARD_SERIAL_NUMBER = -1;

    private Boolean isApproval;

    private EntityId receiverAccountId;

    private EntityId senderAccountId;

    private Long serialNumber;

    private EntityId tokenId;
}
