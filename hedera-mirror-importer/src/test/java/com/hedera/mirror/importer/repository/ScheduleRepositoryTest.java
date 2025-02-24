// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ScheduleRepositoryTest extends ImporterIntegrationTest {

    private final ScheduleRepository scheduleRepository;

    @Test
    void save() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        assertThat(scheduleRepository.findById(schedule.getScheduleId())).get().isEqualTo(schedule);
    }

    @Test
    void updateExecutedTimestamp() {
        Schedule schedule = scheduleRepository.save(schedule(1));
        long newExecutedTimestamp = 1000L;
        scheduleRepository.updateExecutedTimestamp(schedule.getScheduleId(), newExecutedTimestamp);
        assertThat(scheduleRepository.findById(schedule.getScheduleId()))
                .get()
                .returns(newExecutedTimestamp, from(Schedule::getExecutedTimestamp));
    }

    private Schedule schedule(long consensusTimestamp) {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(EntityId.of("0.0.123"));
        schedule.setPayerAccountId(EntityId.of("0.0.456"));
        schedule.setScheduleId(EntityId.of("0.0.789"));
        schedule.setTransactionBody("transaction body".getBytes());
        return schedule;
    }
}
