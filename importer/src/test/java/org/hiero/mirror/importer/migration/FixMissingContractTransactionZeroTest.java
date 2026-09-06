// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FixMissingContractTransactionZeroTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixMissingContractTransactionZeroTest extends ImporterIntegrationTest {

    private static final long MIGRATION_TIMESTAMP = 1784869200000000000L;
    private static final long CONTRACT_ZERO_ID = 0L;

    @Test
    void empty() {
        runMigration();
        assertThat(findAllContractTransactions()).isEmpty();
    }

    @Test
    void insertsMissingContractZeroRow() {
        // given - 0 is already in contract_ids but the entity 0 fan-out row is missing
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();
        final var contractIds = List.of(CONTRACT_ZERO_ID, payerId, otherEntityId);

        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);

        // when
        runMigration();

        // then - the missing entity 0 row is inserted and existing arrays are left unchanged
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, contractIds, payerId),
                        new ContractTransactionRow(timestamp, payerId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, contractIds, payerId));
    }

    @Test
    void insertsMultipleTransactions() {
        // given - two impacted transactions with different payers and arrays
        final long timestamp1 = MIGRATION_TIMESTAMP;
        final long timestamp2 = MIGRATION_TIMESTAMP + 5000;
        final var payer1 = domainBuilder.id();
        final var payer2 = domainBuilder.id();
        final var contractIds1 = List.of(CONTRACT_ZERO_ID, payer1);
        final var contractIds2 = List.of(CONTRACT_ZERO_ID, payer2);

        persistContractTransaction(timestamp1, payer1, contractIds1, payer1);
        persistContractTransaction(timestamp2, payer2, contractIds2, payer2);

        // when
        runMigration();

        // then
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp1, payer1, contractIds1, payer1),
                        new ContractTransactionRow(timestamp1, CONTRACT_ZERO_ID, contractIds1, payer1),
                        new ContractTransactionRow(timestamp2, payer2, contractIds2, payer2),
                        new ContractTransactionRow(timestamp2, CONTRACT_ZERO_ID, contractIds2, payer2));
    }

    @Test
    void respectsTimestampLowerBound() {
        // given - one transaction immediately before the bound and one exactly on it
        final long beforeTimestamp = MIGRATION_TIMESTAMP - 1;
        final long atTimestamp = MIGRATION_TIMESTAMP;
        final var beforePayerId = domainBuilder.id();
        final var atPayerId = domainBuilder.id();
        final var beforeContractIds = List.of(CONTRACT_ZERO_ID, beforePayerId);
        final var atContractIds = List.of(CONTRACT_ZERO_ID, atPayerId);

        persistContractTransaction(beforeTimestamp, beforePayerId, beforeContractIds, beforePayerId);
        persistContractTransaction(atTimestamp, atPayerId, atContractIds, atPayerId);

        // when
        runMigration();

        // then - the bound is inclusive, and the insert does not reach before it
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(beforeTimestamp, beforePayerId, beforeContractIds, beforePayerId),
                        new ContractTransactionRow(atTimestamp, atPayerId, atContractIds, atPayerId),
                        new ContractTransactionRow(atTimestamp, CONTRACT_ZERO_ID, atContractIds, atPayerId));
    }

    @Test
    void noopWhenAlreadyCorrect() {
        // given - entity 0 row is already present and 0 is in every array
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();
        final var contractIds = List.of(CONTRACT_ZERO_ID, payerId, otherEntityId);

        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);
        persistContractTransaction(timestamp, CONTRACT_ZERO_ID, contractIds, payerId);

        // when
        runMigration();

        // then - no duplicate row is inserted and existing rows are unchanged
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, payerId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, contractIds, payerId));
    }

    @Test
    void noopWhenContractIdsDoesNotContainZero() {
        // given - arrays omit 0, so this insert-only migration must not create an entity 0 row
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();
        final var contractIds = List.of(payerId, otherEntityId);

        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);

        // when
        runMigration();

        // then
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, payerId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, contractIds, payerId));
    }

    @Test
    void isIdempotent() {
        // given
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();
        final var contractIds = List.of(CONTRACT_ZERO_ID, payerId, otherEntityId);

        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);

        // when - the first pass inserts entity 0, so the second hits the conflict clause and is a no-op
        runMigration();
        final var afterFirstRun = findAllContractTransactions();
        runMigration();

        // then
        assertThat(afterFirstRun)
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, payerId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, contractIds, payerId));
        assertThat(findAllContractTransactions()).containsExactlyInAnyOrderElementsOf(afterFirstRun);
    }

    private void persistContractTransaction(
            final long consensusTimestamp, final long entityId, final List<Long> contractIds, final long payerId) {
        domainBuilder
                .contractTransaction()
                .customize(ct -> ct.consensusTimestamp(consensusTimestamp)
                        .contractIds(contractIds)
                        .entityId(entityId)
                        .payerAccountId(payerId))
                .persist();
    }

    private List<ContractTransactionRow> findAllContractTransactions() {
        return jdbcOperations.query(
                """
                select consensus_timestamp, entity_id, contract_ids, payer_account_id
                from contract_transaction
                order by consensus_timestamp, entity_id
                """,
                (rs, rowNum) -> new ContractTransactionRow(
                        rs.getLong("consensus_timestamp"),
                        rs.getLong("entity_id"),
                        toList(rs.getArray("contract_ids")),
                        rs.getLong("payer_account_id")));
    }

    @SneakyThrows
    private List<Long> toList(final Array array) {
        return array == null ? List.of() : Arrays.asList((Long[]) array.getArray());
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath = isV1()
                ? "v1/V1.128.0__fix_missing_contract_transaction_zero.sql"
                : "v2/V2.33.0__fix_missing_contract_transaction_zero.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.update(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    private record ContractTransactionRow(
            long consensusTimestamp, long entityId, List<Long> contractIds, long payerAccountId) {}

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            final var environment = configurableApplicationContext.getEnvironment();
            final var version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.32.0" : "1.127.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
