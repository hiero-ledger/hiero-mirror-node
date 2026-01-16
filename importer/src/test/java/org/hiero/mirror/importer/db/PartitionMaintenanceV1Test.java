// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_TIME_PARTITION;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

@RequiredArgsConstructor
@EnabledIfV1
class PartitionMaintenanceV1Test extends ImporterIntegrationTest {

    private static final String ACCOUNT_BALANCE_TABLE_NAME = "account_balance";
    private static final String TOKEN_BALANCE_TABLE_NAME = "token_balance";

    private final @Qualifier(CACHE_TIME_PARTITION) CacheManager cacheManager;
    private final PartitionMaintenance partitionMaintenance;
    private final TimePartitionService timePartitionService;

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void createNewPartitions(int numPartitionsToDrop) {
        // given
        // there should be a lot more than 2 partitions using the testing partition start date and interval
        var accountBalancePartitions = timePartitionService.getTimePartitions(ACCOUNT_BALANCE_TABLE_NAME);
        var tokenBalancePartitions = timePartitionService.getTimePartitions(TOKEN_BALANCE_TABLE_NAME);
        for (int i = 0; i < numPartitionsToDrop; i++) {
            dropPartitionBackwards(accountBalancePartitions, i);
            dropPartitionBackwards(tokenBalancePartitions, i);
        }

        resetCacheManager(cacheManager);
        assertThat(timePartitionService.getTimePartitions(ACCOUNT_BALANCE_TABLE_NAME))
                .size()
                .isEqualTo(accountBalancePartitions.size() - numPartitionsToDrop);
        assertThat(timePartitionService.getTimePartitions(TOKEN_BALANCE_TABLE_NAME))
                .size()
                .isEqualTo(tokenBalancePartitions.size() - numPartitionsToDrop);

        // when
        partitionMaintenance.runMaintenance();

        // then
        resetCacheManager(cacheManager);
        assertThat(timePartitionService.getTimePartitions(ACCOUNT_BALANCE_TABLE_NAME))
                .containsExactlyInAnyOrderElementsOf(accountBalancePartitions);
        assertThat(timePartitionService.getTimePartitions(TOKEN_BALANCE_TABLE_NAME))
                .containsExactlyInAnyOrderElementsOf(tokenBalancePartitions);
    }

    @Test
    void noNewPartitionsCreated() {
        // given
        var accountBalancePartitions = timePartitionService.getTimePartitions(ACCOUNT_BALANCE_TABLE_NAME);
        var tokenBalancePartitions = timePartitionService.getTimePartitions(TOKEN_BALANCE_TABLE_NAME);

        // when
        partitionMaintenance.runMaintenance();

        // then
        // reset cache
        resetCacheManager(cacheManager);
        assertThat(timePartitionService.getTimePartitions(ACCOUNT_BALANCE_TABLE_NAME))
                .containsExactlyInAnyOrderElementsOf(accountBalancePartitions);
        assertThat(timePartitionService.getTimePartitions(TOKEN_BALANCE_TABLE_NAME))
                .containsExactlyInAnyOrderElementsOf(tokenBalancePartitions);
    }

    private void dropPartitionBackwards(List<TimePartition> partitions, int index) {
        var sql = String.format(
                "drop table %s", partitions.get(partitions.size() - index - 1).getName());
        ownerJdbcTemplate.execute(sql);
    }
}
