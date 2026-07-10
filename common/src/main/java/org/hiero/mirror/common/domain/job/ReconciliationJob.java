// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.PersistedTracking;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table
@NoArgsConstructor
public class ReconciliationJob implements PersistedTracking<Instant> {

    private long consensusTimestamp;

    private long count;

    private String error;

    private ReconciliationStatus status;

    private Instant timestampEnd;

    @Id
    private Instant timestampStart;

    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @ToString.Exclude
    @Transient
    private boolean persisted;

    @JsonIgnore
    @Override
    public Instant getId() {
        return timestampStart;
    }

    public boolean hasErrors() {
        return status.ordinal() > ReconciliationStatus.SUCCESS.ordinal();
    }

    public void increment() {
        ++count;
    }
}
