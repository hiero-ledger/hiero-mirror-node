// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ScheduleRepositoryTest extends ImporterIntegrationTest {

    private final ScheduleRepository scheduleRepository;

    @Test
    void save() {
        var schedule = domainBuilder.schedule().persist();
        assertThat(scheduleRepository.findById(schedule.getScheduleId())).get().isEqualTo(schedule);
    }
}
