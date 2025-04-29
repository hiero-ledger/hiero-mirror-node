package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.schedule.Schedule;
import org.springframework.data.repository.CrudRepository;

public interface ScheduleRepository extends CrudRepository<Schedule, Long> {
}
