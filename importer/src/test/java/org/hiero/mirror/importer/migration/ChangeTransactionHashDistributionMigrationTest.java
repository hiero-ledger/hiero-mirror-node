// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Shorts;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV2;
import org.hiero.mirror.importer.ImporterIntegrationTest;
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

    private static final String REVERT_DDL = """
            select alter_distributed_table('transaction_hash', distribution_column := 'hash');
            alter table transaction_hash drop column distribution_id;
            """;

    private final DomainBuilder domainBuilder;

    @Value("classpath:db/migration/v2/V2.4.1__change_transaction_hash_distribution.sql")
    private final Resource migrationSql;

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(findAllTransactionHash()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // note the distributionId is already calculated from the hash
        var transactionHashes = List.of(getTransactionHash(32), getTransactionHash(32), getTransactionHash(48));
        persistTransactionHashes(transactionHashes);

        // when
        runMigration();

        // then
        assertThat(findAllTransactionHash()).containsExactlyInAnyOrderElementsOf(transactionHashes);
    }

    private void persistTransactionHashes(Collection<TransactionHashOld> transactionHashes) {
        ownerJdbcTemplate.batchUpdate("""
                    insert into transaction_hash (consensus_timestamp, hash, payer_account_id) values (?, ?, ?)
                    """, transactionHashes, transactionHashes.size(), (ps, transactionHash) -> {
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

    private TransactionHashOld getTransactionHash(int length) {
        return TransactionHashOld.builder()
                .consensusTimestamp(domainBuilder.timestamp())
                .hash(domainBuilder.bytes(length))
                .payerAccountId(domainBuilder.id())
                .build();
    }

    private List<TransactionHashOld> findAllTransactionHash() {
        return jdbcOperations.query(
                "select consensus_timestamp, hash, payer_account_id, distribution_id from transaction_hash",
                (rs, rowNum) -> {
                    return new TransactionHashOld(
                            rs.getLong("consensus_timestamp"), rs.getBytes("hash"), rs.getLong("payer_account_id"));
                });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TransactionHashOld {
        private long consensusTimestamp;
        private byte[] hash;
        private long payerAccountId;
        private short distributionId;

        @Builder
        public TransactionHashOld(long consensusTimestamp, byte[] hash, long payerAccountId) {
            this.consensusTimestamp = consensusTimestamp;
            this.hash = hash;
            this.payerAccountId = payerAccountId;

            if (ArrayUtils.isNotEmpty(hash) && hash.length >= 2) {
                this.distributionId = Shorts.fromByteArray(hash);
            }
        }
    }
}
