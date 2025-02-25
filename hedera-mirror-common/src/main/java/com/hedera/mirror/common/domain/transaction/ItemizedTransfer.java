// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import com.hedera.mirror.common.domain.entity.EntityId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@NoArgsConstructor
public class ItemizedTransfer {

    private Long amount;

    private EntityId entityId;

    private Boolean isApproval;
}
