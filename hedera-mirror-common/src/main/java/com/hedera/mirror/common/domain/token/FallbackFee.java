// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.token;

import com.hedera.mirror.common.domain.entity.EntityId;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class FallbackFee {

    private long amount;

    private EntityId denominatingTokenId;
}
