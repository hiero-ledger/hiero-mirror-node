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
    void insertsMissingContractZeroTransaction() {
        // given - a contract result for contract 0 whose transaction has no entity_id = 0 fan out row
        var timestamp = MIGRATION_TIMESTAMP + 1000;
        var payerId = domainBuilder.id();
        var otherEntityId = domainBuilder.id();
        var contractIds = List.of(1001L, 1002L);

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, payerId, contractIds, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);

        // when
        runMigration();

        // then - 0 is prepended, and the pre-existing rows keep their original arrays
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, List.of(0L, 1001L, 1002L), payerId),
                        new ContractTransactionRow(timestamp, payerId, contractIds, payerId),
                        new ContractTransactionRow(timestamp, otherEntityId, contractIds, payerId));
    }

    @Test
    void insertsForMultipleTransactions() {
        // given - two impacted transactions with different payers and arrays
        var firstTimestamp = MIGRATION_TIMESTAMP;
        var secondTimestamp = MIGRATION_TIMESTAMP + 5000;
        var firstPayerId = domainBuilder.id();
        var secondPayerId = domainBuilder.id();

        persistContractResult(firstTimestamp, CONTRACT_ZERO_ID, firstPayerId);
        persistContractTransaction(firstTimestamp, firstPayerId, List.of(2001L), firstPayerId);
        persistContractResult(secondTimestamp, CONTRACT_ZERO_ID, secondPayerId);
        persistContractTransaction(secondTimestamp, secondPayerId, List.of(3001L, 3002L), secondPayerId);

        // when
        runMigration();

        // then
        assertThat(findAllContractTransactions())
                .contains(
                        new ContractTransactionRow(firstTimestamp, CONTRACT_ZERO_ID, List.of(0L, 2001L), firstPayerId),
                        new ContractTransactionRow(
                                secondTimestamp, CONTRACT_ZERO_ID, List.of(0L, 3001L, 3002L), secondPayerId))
                .hasSize(4);
    }

    @Test
    void respectsTimestampLowerBound() {
        // given - one transaction immediately before the bound and one exactly on it
        var beforeTimestamp = MIGRATION_TIMESTAMP - 1;
        var atTimestamp = MIGRATION_TIMESTAMP;
        var beforePayerId = domainBuilder.id();
        var atPayerId = domainBuilder.id();

        persistContractResult(beforeTimestamp, CONTRACT_ZERO_ID, beforePayerId);
        persistContractTransaction(beforeTimestamp, beforePayerId, List.of(4001L), beforePayerId);
        persistContractResult(atTimestamp, CONTRACT_ZERO_ID, atPayerId);
        persistContractTransaction(atTimestamp, atPayerId, List.of(5001L), atPayerId);

        // when
        runMigration();

        // then - the bound is inclusive, and nothing before it is touched
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(beforeTimestamp, beforePayerId, List.of(4001L), beforePayerId),
                        new ContractTransactionRow(atTimestamp, atPayerId, List.of(5001L), atPayerId),
                        new ContractTransactionRow(atTimestamp, CONTRACT_ZERO_ID, List.of(0L, 5001L), atPayerId));
    }

    @Test
    void noopWhenContractZeroRowAlreadyExists() {
        // given - the fan out row already exists, so the insert must not overwrite its array
        var timestamp = MIGRATION_TIMESTAMP + 1000;
        var payerId = domainBuilder.id();
        var existingContractIds = List.of(6001L);

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, payerId, existingContractIds, payerId);
        persistContractTransaction(timestamp, CONTRACT_ZERO_ID, existingContractIds, payerId);

        // when
        runMigration();

        // then
        assertThat(findAllContractTransactions())
                .containsExactlyInAnyOrder(
                        new ContractTransactionRow(timestamp, payerId, existingContractIds, payerId),
                        new ContractTransactionRow(timestamp, CONTRACT_ZERO_ID, existingContractIds, payerId));
    }

    @Test
    void noopWhenPayerContractIdsAlreadyContainsZero() {
        // given - the payer row lists contract 0 but the entity_id = 0 row is still missing.
        // The migration guards on the payer's array rather than on the missing row, so this stays unfixed.
        // If the guard is changed to a not exists on entity_id = 0, this expectation must flip.
        var timestamp = MIGRATION_TIMESTAMP + 1000;
        var payerId = domainBuilder.id();
        var contractIds = List.of(0L, 7001L);

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, payerId, contractIds, payerId);

        // when
        runMigration();

        // then
        assertThat(findAllContractTransactions())
                .containsExactly(new ContractTransactionRow(timestamp, payerId, contractIds, payerId));
    }

    @Test
    void noopWhenPayerTransactionMissing() {
        // given - a contract result whose payer has no fan out row to copy contract_ids from
        var timestamp = MIGRATION_TIMESTAMP + 1000;
        var payerId = domainBuilder.id();
        var otherEntityId = domainBuilder.id();
        var contractIds = List.of(8001L);

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, otherEntityId, contractIds, payerId);

        // when
        runMigration();

        // then
        assertThat(findAllContractTransactions())
                .containsExactly(new ContractTransactionRow(timestamp, otherEntityId, contractIds, payerId));
    }

    @Test
    void noopWhenContractResultIsNotContractZero() {
        // given - a normal contract result, which the migration must ignore
        var timestamp = MIGRATION_TIMESTAMP + 1000;
        var payerId = domainBuilder.id();
        var contractId = domainBuilder.id();
        var contractIds = List.of(contractId);

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
        var timestamp = MIGRATION_TIMESTAMP + 1000;
        var payerId = domainBuilder.id();

        persistContractResult(timestamp, CONTRACT_ZERO_ID, payerId);
        persistContractTransaction(timestamp, payerId, List.of(9001L), payerId);

        // when - the payer row still lacks 0 after the first pass, so the second pass relies on the conflict clause
        runMigration();
        var afterFirstRun = findAllContractTransactions();
        runMigration();

        // then
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
