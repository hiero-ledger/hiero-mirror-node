// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // for Builder
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
public class NftTransfer {

    public static final long WILDCARD_SERIAL_NUMBER = -1;

    private Boolean isApproval;

    // Converter removed. Handled by global EntityId Reading/Writing converters.
    private EntityId receiverAccountId;

    private EntityId senderAccountId;

    private Long serialNumber;

    private EntityId tokenId;
}
