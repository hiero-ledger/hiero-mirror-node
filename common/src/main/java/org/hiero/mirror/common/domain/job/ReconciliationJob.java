// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.job;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table
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
