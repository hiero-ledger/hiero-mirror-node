// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV2;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.TransactionHashRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV2
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=2.4.0")
class ChangeTransactionHashDistributionMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL =
            """
            select alter_distributed_table('transaction_hash', distribution_column := 'hash');
            alter table transaction_hash drop column distribution_id;
            """;

    private final TransactionHashRepository transactionHashRepository;

    @Value("classpath:db/migration/v2/V2.4.1__change_transaction_hash_distribution.sql")
    private final Resource migrationSql;

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // note the distributionId is already calculated from the hash
        var transactionHashes = List.of(
                domainBuilder.transactionHash().get(),
                domainBuilder.transactionHash().get(),
                domainBuilder
                        .transactionHash()
                        .customize(th -> th.hash(domainBuilder.bytes(32)))
                        .get());
        persistTransactionHashes(transactionHashes);

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactionHashes);
    }

    private void persistTransactionHashes(Collection<TransactionHash> transactionHashes) {
        ownerJdbcTemplate.batchUpdate(
                """
                    insert into transaction_hash (consensus_timestamp, hash, payer_account_id) values (?, ?, ?)
                    """,
                transactionHashes,
                transactionHashes.size(),
                (ps, transactionHash) -> {
                    ps.setLong(1, transactionHash.getConsensusTimestamp());
                    ps.setBytes(2, transactionHash.getHash());
                    ps.setLong(3, transactionHash.getPayerAccountId());
                });
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            ownerJdbcTemplate.execute(script);
        }
    }
}
