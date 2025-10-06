// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Shorts;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.Flyway;
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
@RequiredArgsConstructor
@Tag("migration")
@EnabledIfV2
@TestPropertySource(properties = "spring.flyway.target=2.15.0")
public class PartitionTransactionHashMigrationTest extends ImporterIntegrationTest {

    @Value("classpath:db/migration/v2/V2.16.0__partition_transaction_hash.sql")
    private final Resource migrationSql;

    private final TransactionHashRepository transactionHashRepository;
    private final Flyway flyway;

    private static final String REVERT_DDL =
            """
        drop table transaction_hash;
        create table transaction_hash
        (
            consensus_timestamp bigint not null,
            payer_account_id    bigint not null,
            distribution_id     smallint not null,
            hash                bytea  not null
        );
        select create_distributed_table('transaction_hash', 'distribution_id', shard_count := 6);
    """;

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
    void migration() {
        var transactionHashes = List.of(
                domainBuilder.transactionHash().get(),
                domainBuilder.transactionHash().get(),
                domainBuilder
                        .transactionHash()
                        .customize(th -> th.hash(domainBuilder.bytes(32)))
                        .get());
        persistTransactionHashes(transactionHashes);
        runMigration();
        assertThat(transactionHashRepository.findAll()).hasSize(transactionHashes.size());
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8);

            for (var entry : flyway.getConfiguration().getPlaceholders().entrySet()) {
                var key = "${" + entry.getKey() + "}";
                script = script.replace(key, entry.getValue());
            }

            ownerJdbcTemplate.execute(script);
        }
    }

    private void persistTransactionHashes(Collection<TransactionHash> transactionHashes) {
        ownerJdbcTemplate.batchUpdate(
                """
                    insert into transaction_hash (consensus_timestamp, hash, distribution_id, payer_account_id) values (?, ?, ?, ?)
                    """,
                transactionHashes,
                transactionHashes.size(),
                (ps, transactionHash) -> {
                    ps.setLong(1, transactionHash.getConsensusTimestamp());
                    ps.setBytes(2, transactionHash.getHash());
                    ps.setShort(3, Shorts.fromByteArray(transactionHash.getHash()));
                    ps.setLong(4, transactionHash.getPayerAccountId());
                });
    }
}
