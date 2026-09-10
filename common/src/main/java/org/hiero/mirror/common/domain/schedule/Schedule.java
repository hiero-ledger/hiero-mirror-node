// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.schedule;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
@Table
@NoArgsConstructor
@Upsertable
public class Schedule {

    @InsertOnlyProperty
    private Long consensusTimestamp;

    @InsertOnlyProperty
    private EntityId creatorAccountId;

    private Long executedTimestamp;

    @InsertOnlyProperty
    private Long expirationTime;

    @InsertOnlyProperty
    private EntityId payerAccountId;

    @Id
    private Long scheduleId;

    @ToString.Exclude
    @InsertOnlyProperty
    private byte[] transactionBody;

    @InsertOnlyProperty
    private boolean waitForExpiry;

    public void setScheduleId(EntityId scheduleId) {
        this.scheduleId = scheduleId != null ? scheduleId.getId() : null;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }
}
