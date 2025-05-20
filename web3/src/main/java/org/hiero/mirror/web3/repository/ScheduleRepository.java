// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import com.hedera.mirror.common.domain.schedule.Schedule;
import org.springframework.data.repository.CrudRepository;

public interface ScheduleRepository extends CrudRepository<Schedule, Long> {}
