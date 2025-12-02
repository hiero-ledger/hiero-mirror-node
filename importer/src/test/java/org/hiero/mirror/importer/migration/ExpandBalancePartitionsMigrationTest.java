// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.importer.db.TimePartition;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@RequiredArgsConstructor
@Tag("migration")
@ExtendWith(OutputCaptureExtension.class)
class ExpandBalancePartitionsMigrationTest extends AbstractAsyncJavaMigrationTest<ExpandBalancePartitionsMigration> {

    private static final String REVERT_DDL =
            """
                    create table if not exists account_balance_old (
                      consensus_timestamp bigint not null,
                      balance             bigint not null,
                      account_id          bigint not null,
                      primary key (consensus_timestamp, account_id)
                    );

                    create table if not exists token_balance_old (
                      consensus_timestamp bigint not null,
                      account_id          bigint not null,
                      balance             bigint not null,
                      token_id            bigint not null,
                      primary key (account_id, token_id, consensus_timestamp)
                    );
                    """;

    private final AccountBalanceRepository accountBalanceRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TimePartitionService timePartitionService;

    private final @Getter ExpandBalancePartitionsMigration migration;
    private final @Getter Class<ExpandBalancePartitionsMigration> migrationClass =
            ExpandBalancePartitionsMigration.class;

    private List<TimePartition> partitions;

    @BeforeEach
    void setup() {
        migration.migrationProperties.setEnabled(true);
        partitions = timePartitionService.getTimePartitions("account_balance").stream()
                .sorted(Comparator.comparing(
                                (TimePartition p) -> p.getTimestampRange().upperEndpoint())
                        .reversed())
                .toList();
    }

    @AfterEach
    void teardown() {
        // Recreate old tables that the migration drops so other tests continue to work
        ownerJdbcTemplate.execute(REVERT_DDL);
        migration.migrationProperties.setEnabled(false);
        migration.migrationProperties.getParams().clear();
    }

    @Test
    void emptyNoBalanceFiles() {
        // given
        // empty tables

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSchema();

        // No data should exist in new tables either
        assertThat(accountBalanceRepository.findAll()).isEmpty();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();
    }

    @Test
    void migrateAndDeduplicate() {
        // given
        final var account = domainBuilder.entityId();
        final var token = domainBuilder.entityId();

        var partition = partitions.getFirst();

        final var t1 = partition.getTimestampRange().lowerEndpoint();
        final var t2 = t1 + 1_000_000L;
        final var t3 = partition.getTimestampRange().upperEndpoint() - 1_000_000L;
        final var t4 = partition.getTimestampRange().upperEndpoint();
        final var t5 = partition.getTimestampRange().lowerEndpoint() - 1;

        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(t1))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(t2))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(t3))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(t4))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(t5))
                .persist();

        var oldAccountBalances = List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(t1, account)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(t2, account)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(t3, account)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(t4, account)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(t5, account)))
                        .get());

        var oldTokenBalances = List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(10).id(new TokenBalance.Id(t1, account, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(10).id(new TokenBalance.Id(t2, account, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(20).id(new TokenBalance.Id(t3, account, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(20).id(new TokenBalance.Id(t4, account, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(20).id(new TokenBalance.Id(t5, account, token)))
                        .get());

        persistOldAccountBalances(oldAccountBalances);
        persistOldTokenBalances(oldTokenBalances);

        var expectedAccountBalances =
                List.of(oldAccountBalances.get(0), oldAccountBalances.get(2), oldAccountBalances.get(4));
        var expectedTokenBalances = List.of(oldTokenBalances.get(0), oldTokenBalances.get(2), oldTokenBalances.get(4));

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSchema();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
    }

    @Test
    void skipAlreadyMigratedPartitionOnRestart() {
        // given
        final var account = domainBuilder.entityId();
        final var token = domainBuilder.entityId();

        final var migratedPartition = partitions.getFirst();
        final var t1 = migratedPartition.getTimestampRange().lowerEndpoint();
        final var t2 = migratedPartition.getTimestampRange().upperEndpoint() - 1;
        final var t3 = migratedPartition.getTimestampRange().lowerEndpoint() - 1;

        var oldAccountBalances = List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(t1, account)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(t2, account)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(t3, account)))
                        .get()); // Persisted by migration run
        var oldTokenBalances = List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(10).id(new TokenBalance.Id(t1, account, token)))
                        .persist(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(20).id(new TokenBalance.Id(t2, account, token)))
                        .persist(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(20).id(new TokenBalance.Id(t3, account, token)))
                        .get()); // Persisted by migration run
        persistOldAccountBalances(oldAccountBalances);
        persistOldTokenBalances(oldTokenBalances);

        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(t2))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSchema();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(oldAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(oldTokenBalances);
    }

    @Test
    void migratesPartitionsOverlappingLastSixHoursSynchronously(CapturedOutput capturedOutput) {
        // given
        final var migrationStartPartition = partitions.getFirst();
        final var nextMigrationPartition = partitions.get(1);
        final var firstAsyncPartition = partitions.get(2);

        final var sixHoursNS = 6L * 60 * 60 * 1_000_000_000L;
        final var t1 = migrationStartPartition.getTimestampRange().lowerEndpoint();
        final var lastBalanceFileTimestamp = t1 + sixHoursNS - 1;

        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(lastBalanceFileTimestamp))
                .persist();

        var oldAccountBalances = List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.id(new AccountBalance.Id(t1, domainBuilder.entityId())))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.id(new AccountBalance.Id(t1 - 1, domainBuilder.entityId())))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.id(new AccountBalance.Id(
                                nextMigrationPartition.getTimestampRange().lowerEndpoint(), domainBuilder.entityId())))
                        .get());

        var oldTokenBalances = List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(ab ->
                                ab.id(new TokenBalance.Id(t1, domainBuilder.entityId(), domainBuilder.entityId())))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(ab ->
                                ab.id(new TokenBalance.Id(t1 - 1, domainBuilder.entityId(), domainBuilder.entityId())))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(ab -> ab.id(new TokenBalance.Id(
                                nextMigrationPartition.getTimestampRange().lowerEndpoint(),
                                domainBuilder.entityId(),
                                domainBuilder.entityId())))
                        .get());
        persistOldAccountBalances(oldAccountBalances);
        persistOldTokenBalances(oldTokenBalances);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSchema();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(oldAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(oldTokenBalances);

        assertThat(capturedOutput.getAll())
                .contains(
                        "Synchronously backfilling partitions overlapping [%d, %d]"
                                .formatted(lastBalanceFileTimestamp - sixHoursNS, lastBalanceFileTimestamp),
                        "Synchronously processing partition " + migrationStartPartition.getTimestampRange(),
                        "Synchronously processing partition " + nextMigrationPartition.getTimestampRange());
        assertThat(capturedOutput.getAll())
                .doesNotContain("Synchronously processing partition " + firstAsyncPartition.getTimestampRange());
    }

    private void assertSchema() {
        assertThat(tableExists("account_balance_old")).isFalse();
        assertThat(tableExists("token_balance_old")).isFalse();
    }

    private void persistOldAccountBalances(Collection<AccountBalance> accountBalances) {
        jdbcOperations.batchUpdate(
                """
                        insert into account_balance_old (account_id, balance, consensus_timestamp)
                        values (?, ?, ?)
                        """,
                accountBalances,
                accountBalances.size(),
                (ps, accountBalance) -> {
                    ps.setLong(1, accountBalance.getId().getAccountId().getId());
                    ps.setLong(2, accountBalance.getBalance());
                    ps.setLong(3, accountBalance.getId().getConsensusTimestamp());
                });
    }

    private void persistOldTokenBalances(Collection<TokenBalance> tokenBalances) {
        jdbcOperations.batchUpdate(
                """
                        insert into token_balance_old (account_id, balance, consensus_timestamp, token_id)
                        values (?, ?, ?, ?)
                        """,
                tokenBalances,
                tokenBalances.size(),
                (ps, tokenBalance) -> {
                    ps.setLong(1, tokenBalance.getId().getAccountId().getId());
                    ps.setLong(2, tokenBalance.getBalance());
                    ps.setLong(3, tokenBalance.getId().getConsensusTimestamp());
                    ps.setLong(4, tokenBalance.getId().getTokenId().getId());
                });
    }
}
