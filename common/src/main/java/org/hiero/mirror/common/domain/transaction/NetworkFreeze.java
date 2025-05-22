// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;

@Data
@Entity
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
