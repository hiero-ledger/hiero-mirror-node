// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AccountBalanceFileRepositoryTest extends ImporterIntegrationTest {

    private final AccountBalanceFileRepository accountBalanceFileRepository;

    @Test
    void findLatest() {
        domainBuilder.accountBalanceFile().persist();
        domainBuilder.accountBalanceFile().persist();
        var latest = domainBuilder.accountBalanceFile().persist();
        assertThat(accountBalanceFileRepository.findLatest()).get().isEqualTo(latest);
    }

    @Test
    void findLatestBefore() {
        assertThat(accountBalanceFileRepository.findLatestBefore(DomainUtils.now()))
                .isEmpty();

        domainBuilder.accountBalanceFile().persist();
        var accountBalanceFile2 = domainBuilder.accountBalanceFile().persist();

        assertThat(accountBalanceFileRepository.findLatestBefore(DomainUtils.now()))
                .get()
                .isEqualTo(accountBalanceFile2);

        var accountBalanceFile3 = domainBuilder.accountBalanceFile().persist();
        assertThat(accountBalanceFileRepository.findLatestBefore(DomainUtils.now()))
                .get()
                .isEqualTo(accountBalanceFile3);
    }

    @Test
    void findNextInRange() {
        var file1 = domainBuilder.accountBalanceFile().persist();
        var file2 = domainBuilder.accountBalanceFile().persist();
        domainBuilder.recordFile().persist();
        var file3 = domainBuilder.accountBalanceFile().persist();

        assertThat(accountBalanceFileRepository.findNextInRange(file3.getConsensusTimestamp(), Long.MAX_VALUE))
                .isEmpty();
        assertThat(accountBalanceFileRepository.findNextInRange(0L, file1.getConsensusTimestamp()))
                .get()
                .isEqualTo(file1);

        assertThat(accountBalanceFileRepository.findNextInRange(0L, Long.MAX_VALUE))
                .get()
                .isEqualTo(file1);

        assertThat(accountBalanceFileRepository.findNextInRange(file1.getConsensusTimestamp(), Long.MAX_VALUE))
                .get()
                .isEqualTo(file1);

        assertThat(accountBalanceFileRepository.findNextInRange(file2.getConsensusTimestamp(), Long.MAX_VALUE))
                .get()
                .isEqualTo(file2);

        assertThat(accountBalanceFileRepository.findNextInRange(
                        file1.getConsensusTimestamp(), file3.getConsensusTimestamp()))
                .get()
                .isEqualTo(file1);
    }
}
