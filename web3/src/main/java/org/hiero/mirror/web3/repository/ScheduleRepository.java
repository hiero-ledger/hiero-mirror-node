// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ScheduleRepository extends CrudRepository<Schedule, Long> {

    @Query(value = "select * from schedule where schedule_id = :id and consensus_timestamp <= :timestamp")
    Optional<Schedule> findByIdAndTimestamp(final long id, final long timestamp);
}
