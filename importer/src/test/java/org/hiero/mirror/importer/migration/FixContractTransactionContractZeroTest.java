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
import org.hiero.mirror.common.domain.entity.EntityId;
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

@ContextConfiguration(initializers = FixContractTransactionContractZeroTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixContractTransactionContractZeroTest extends ImporterIntegrationTest {

    private static final long MIGRATION_TIMESTAMP = 1784869200000000000L;
    private static final long CONTRACT_ZERO_ID = 0L;

    @Test
    void empty() {
        runMigration();
        assertThat(findAllContractTransactions()).isEmpty();
    }

    @Test
    void updatesAndInsertsContractZeroRow() {
        // given - a contract result for contract 0 whose fan out omits 0 entirely
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();
        final var contractIds = List.of(payerId, otherEntityId);

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);

        // when
        runMigration();

        // then - every row for the transaction carries 0, including the inserted one, which derives its array from the
        // pre-update snapshot and so must prepend 0 itself
        final var expected = List.of(CONTRACT_ZERO_ID, payerId, otherEntityId);
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, expected, payerId),
                        new ContractTransactionRow(timestamp, payerId, expected, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, expected, payerId));
    }

    @Test
    void updatesMultipleTransactions() {
        // given - two impacted transactions with different payers and arrays
        final long timestamp1 = MIGRATION_TIMESTAMP;
        final long timestamp2 = MIGRATION_TIMESTAMP + 5000;
        final var payer1 = domainBuilder.id();
        final var payer2 = domainBuilder.id();

        persistContractResult(timestamp1, CONTRACT_ZERO_ID, payer1);
        persistContractTransaction(timestamp1, payer1, List.of(payer1), payer1);
        persistContractResult(timestamp2, CONTRACT_ZERO_ID, payer2);
        persistContractTransaction(timestamp2, payer2, List.of(payer2), payer2);

        // when
        runMigration();

        // then
        final var expected1 = List.of(CONTRACT_ZERO_ID, payer1);
        final var expected2 = List.of(CONTRACT_ZERO_ID, payer2);
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp1, payer1, expected1, payer1),
                        new ContractTransactionRow(timestamp1, CONTRACT_ZERO_ID, expected1, payer1),
                        new ContractTransactionRow(timestamp2, payer2, expected2, payer2),
                        new ContractTransactionRow(timestamp2, CONTRACT_ZERO_ID, expected2, payer2));
    }

    @Test
    void respectsTimestampLowerBound() {
        // given - one transaction immediately before the bound and one exactly on it
        final long beforeTimestamp = MIGRATION_TIMESTAMP - 1;
        final long atTimestamp = MIGRATION_TIMESTAMP;
        final var beforePayerId = domainBuilder.id();
        final var atPayerId = domainBuilder.id();
        final var beforeContractIds = List.of(CONTRACT_ZERO_ID, beforePayerId);

        persistContractResult(beforeTimestamp, CONTRACT_ZERO_ID, beforePayerId);
        persistContractTransaction(beforeTimestamp, beforePayerId, beforeContractIds, beforePayerId);
        persistContractResult(atTimestamp, CONTRACT_ZERO_ID, atPayerId);
        persistContractTransaction(atTimestamp, atPayerId, List.of(atPayerId), atPayerId);

        // when
        runMigration();

        // then - the bound is inclusive, and neither the update nor the insert reaches before it
        final var expected = List.of(CONTRACT_ZERO_ID, atPayerId);
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(beforeTimestamp, beforePayerId, beforeContractIds, beforePayerId),
                        new ContractTransactionRow(atTimestamp, atPayerId, expected, atPayerId),
                        new ContractTransactionRow(atTimestamp, CONTRACT_ZERO_ID, expected, atPayerId));
    }

    @Test
    void noopWhenAlreadyCorrect() {
        // given - a transaction ingested after the importer fix: entity 0 row present and 0 in every array
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();
        final var contractIds = List.of(CONTRACT_ZERO_ID, payerId, otherEntityId);

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);
        persistContractTransaction(timestamp, CONTRACT_ZERO_ID, contractIds, payerId);

        // when
        runMigration();

        // then - no second 0 is prepended and no duplicate row is inserted
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, payerId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, contractIds, payerId));
    }

    @Test
    void updatesWhenPayerRowMissing() {
        // given - no fan out row for the payer, so the insert has nothing to copy from. The update joins on
        // consensus_timestamp only, so it is broader than the insert and still runs.
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, otherEntityId, List.of(payerId, otherEntityId), payerId);

        // when
        runMigration();

        // then
        final var expected = List.of(CONTRACT_ZERO_ID, payerId, otherEntityId);
        assertThat(findAllContractTransactions())
                .containsExactly(new ContractTransactionRow(timestamp, otherEntityId, expected, payerId));
    }

    @Test
    void noopWhenContractResultIsNotContractZero() {
        // given - a normal contract result, which neither sub-statement may touch
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var contractId = domainBuilder.id();
        final var contractIds = List.of(contractId);

        persistContractResult(timestamp, contractId, payerId);
        persistContractTransaction(timestamp, payerId, contractIds, payerId);

        // when
        runMigration();

        // then
        assertThat(findAllContractTransactions())
                .containsExactly(new ContractTransactionRow(timestamp, payerId, contractIds, payerId));
    }

    @Test
    void isIdempotent() {
        // given
        final long timestamp = MIGRATION_TIMESTAMP + 1000;
        final var payerId = domainBuilder.id();
        final var otherEntityId = domainBuilder.id();
        final var contractIds = List.of(payerId, otherEntityId);

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);

        // when - the first pass leaves 0 in every array, so the second matches nothing and never reaches the conflict
        // clause. A second 0 must not be prepended.
        runMigration();
        final var afterFirstRun = findAllContractTransactions();
        runMigration();

        // then
        final var expected = List.of(CONTRACT_ZERO_ID, payerId, otherEntityId);
        assertThat(afterFirstRun)
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, payerId, expected, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, expected, payerId),
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, expected, payerId));
        assertThat(findAllContractTransactions()).containsExactlyInAnyOrderElementsOf(afterFirstRun);
    }

    private void persistContractResult(final long consensusTimestamp, final long contractId, final long payerId) {
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .payerAccountId(EntityId.of(payerId)))
                .persist();
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
                ? "v1/V1.127.0__fix_empty_contract_transaction.sql"
                : "v2/V2.32.0__fix_empty_contract_transaction.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.update(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    /** Ordering of contract_ids is significant: the migration prepends rather than appends. */
    private record ContractTransactionRow(
            long consensusTimestamp, long entityId, List<Long> contractIds, long payerAccountId) {}

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            final var environment = configurableApplicationContext.getEnvironment();
            final var version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.31.0" : "1.126.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
