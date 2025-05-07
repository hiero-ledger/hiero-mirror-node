// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
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
