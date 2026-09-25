// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ScheduleRepository extends CrudRepository<Schedule, Long> {

    @Query(
            value = "select * from schedule where schedule_id = :id and consensus_timestamp <= :timestamp",
            nativeQuery = true)
    Optional<Schedule> findByIdAndTimestamp(final long id, final long timestamp);
}
