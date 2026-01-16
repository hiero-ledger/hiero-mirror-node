// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@NoArgsConstructor
public class ItemizedTransfer {

    private Long amount;

    private EntityId entityId;

    private Boolean isApproval;
}
