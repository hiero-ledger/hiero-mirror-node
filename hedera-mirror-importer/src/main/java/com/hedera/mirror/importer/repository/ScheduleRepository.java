// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.schedule.Schedule;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends CrudRepository<Schedule, Long> {
    @Modifying
    @Transactional
    @Query("update Schedule set executedTimestamp = :timestamp where scheduleId = :schedule")
    void updateExecutedTimestamp(@Param("schedule") Long scheduleId, @Param("timestamp") long executedTimestamp);
}
