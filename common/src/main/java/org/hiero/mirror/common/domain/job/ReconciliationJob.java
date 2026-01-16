// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.job;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
public class ReconciliationJob {

    private long consensusTimestamp;

    private long count;

    private String error;

    private ReconciliationStatus status;

    private Instant timestampEnd;

    @Id
    private Instant timestampStart;

    public boolean hasErrors() {
        return status.ordinal() > ReconciliationStatus.SUCCESS.ordinal();
    }

    public void increment() {
        ++count;
    }
}
