// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.schedule;

import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@NoArgsConstructor
@Upsertable
public class Schedule {

    @Column(updatable = false)
    private Long consensusTimestamp;

    @Column(updatable = false)
    private EntityId creatorAccountId;

    private Long executedTimestamp;

    @Column(updatable = false)
    private Long expirationTime;

    @Column(updatable = false)
    private EntityId payerAccountId;

    @Id
    private Long scheduleId;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] transactionBody;

    @Column(updatable = false)
    private boolean waitForExpiry;

    public void setScheduleId(EntityId scheduleId) {
        this.scheduleId = scheduleId != null ? scheduleId.getId() : null;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }
}
