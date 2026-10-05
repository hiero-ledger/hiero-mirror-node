// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class NetworkFreeze {
    @Id
    private Long consensusTimestamp;

    private Long endTime;
    private byte[] fileHash;
    private EntityId fileId;
    private EntityId payerAccountId;
    private long startTime;
    private int type;
}
