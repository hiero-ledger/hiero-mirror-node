// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ScheduleRepositoryTest extends Web3IntegrationTest {

    private final ScheduleRepository scheduleRepository;

    @Test
    void findByIdAndTimestampLessThanOrEqualReturnsSchedule() {
        final var schedule = domainBuilder.schedule().persist();

        assertThat(scheduleRepository.findByIdAndTimestamp(schedule.getScheduleId(), schedule.getConsensusTimestamp()))
                .contains(schedule);
        assertThat(scheduleRepository.findByIdAndTimestamp(
                        schedule.getScheduleId(), schedule.getConsensusTimestamp() + 1))
                .contains(schedule);
    }

    @Test
    void findByIdAndTimestampGreaterThanReturnsEmpty() {
        final var schedule = domainBuilder.schedule().persist();

        assertThat(scheduleRepository.findByIdAndTimestamp(
                        schedule.getScheduleId(), schedule.getConsensusTimestamp() - 1))
                .isEmpty();
    }
}
