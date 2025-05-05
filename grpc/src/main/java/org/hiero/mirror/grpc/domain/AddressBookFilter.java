// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.domain;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AddressBookFilter {
    @NotNull
    private final EntityId fileId;

    @Min(0)
    private final int limit;
}
