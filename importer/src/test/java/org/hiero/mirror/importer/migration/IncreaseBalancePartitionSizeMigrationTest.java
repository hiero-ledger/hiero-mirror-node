// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.db.TimePartition;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.114.0")
final class IncreaseBalancePartitionSizeMigrationTest extends ImporterIntegrationTest {

    private static final String CLEANUP_SQL =
            """
                drop table account_balance;
                drop table token_balance;
                alter table account_balance_old rename to account_balance;
                alter table token_balance_old rename to token_balance;
                create table account_balance_old as table account_balance with no data;
                create table token_balance_old as table token_balance with no data;
            """;

    @Value("classpath:db/migration/v1/V1.115.0__increase_balance_partition_size.sql")
    private final Resource migrationSql;

    private final TimePartitionService timePartitionService;
    private final AccountBalanceRepository accountBalanceRepository;
    private final TokenBalanceRepository tokenBalanceRepository;

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(CLEANUP_SQL);
    }

    @ParameterizedTest
    @CsvSource(
            quoteCharacter = '"',
            textBlock =
                    """
                    "'1 month'", P1M
                    "'6 months'", P6M
                    """)
    void empty(String interval, Period period) {
        // given
        final var startDate = LocalDate.now(ZoneOffset.UTC).minusMonths(12);

        // when
        runMigration(String.format("'%s'", startDate), interval);

        // then
        assertSchema(startDate, period);
        assertThat(queryAccountBalanceOld()).isEmpty();
        assertThat(queryTokenBalanceOld()).isEmpty();
        assertThat(accountBalanceRepository.findAll()).isEmpty();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();
    }

    @Test
    void migrateCopiesNewerRowsFromPartitionedTablesToOldTables() {
        // given: scenario where *_old tables exist (from prior async migration not completing),
        // and partitioned tables created in v1.89.2 have newer data

        final var oldAccountBalance = domainBuilder.accountBalance().persist();
        final var newAccountBalance = domainBuilder.accountBalance().persist();

        final var oldTokenBalance = domainBuilder.tokenBalance().persist();
        final var newTokenBalance = domainBuilder.tokenBalance().persist();

        // Seed *_old with the "old" rows
        ownerJdbcTemplate.update(
                "insert into account_balance_old (account_id, balance, consensus_timestamp) values (?, ?, ?)",
                oldAccountBalance.getId().getAccountId().getId(),
                oldAccountBalance.getBalance(),
                oldAccountBalance.getId().getConsensusTimestamp());

        ownerJdbcTemplate.update(
                "insert into token_balance_old (account_id, balance, consensus_timestamp, token_id) values (?, ?, ?, ?)",
                oldTokenBalance.getId().getAccountId().getId(),
                oldTokenBalance.getBalance(),
                oldTokenBalance.getId().getConsensusTimestamp(),
                oldTokenBalance.getId().getTokenId().getId());

        // when
        var startDate = LocalDate.now(ZoneOffset.UTC).minusMonths(12);

        assertThat(queryAccountBalanceOld()).containsExactlyInAnyOrder(oldAccountBalance);
        assertThat(queryTokenBalanceOld()).containsExactlyInAnyOrder(oldTokenBalance);
        runMigration(String.format("'%s'", startDate), "'6 months'");

        // then
        assertSchema(startDate, Period.ofMonths(6));
        assertThat(queryAccountBalanceOld()).containsExactlyInAnyOrder(oldAccountBalance, newAccountBalance);
        assertThat(queryTokenBalanceOld()).containsExactlyInAnyOrder(oldTokenBalance, newTokenBalance);
        assertThat(accountBalanceRepository.findAll()).isEmpty();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenOldTablesDoNotExist() {
        // given: case where only partitioned tables exist, *_old tables do not
        ownerJdbcTemplate.execute("drop table account_balance_old");
        ownerJdbcTemplate.execute("drop table token_balance_old");

        final var expectedAccountBalances = List.of(
                domainBuilder.accountBalance().persist(),
                domainBuilder.accountBalance().persist());

        final var expectedTokenBalances = List.of(
                domainBuilder.tokenBalance().persist(),
                domainBuilder.tokenBalance().persist());

        // when: run migration; since *_old tables don't exist, revert_partitioned_table() no-ops,
        // and the script renames account_balance -> account_balance_old, then recreates new partitioned tables
        final var startDate = LocalDate.now(ZoneOffset.UTC).minusMonths(12);
        runMigration(String.format("'%s'", startDate), "'1 month'");

        // then: schema is correct and *_old tables now exist
        assertSchema(startDate, Period.ofMonths(1));
        assertThat(queryAccountBalanceOld()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(queryTokenBalanceOld()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
        assertThat(accountBalanceRepository.findAll()).isEmpty();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();
    }

    private void assertSchema(LocalDate startDate, Period period) {
        assertThat(timePartitionService.getTimePartitions("account_balance"))
                .containsExactlyInAnyOrderElementsOf(getTimePartitions("account_balance", startDate, period));
        assertThat(timePartitionService.getTimePartitions("token_balance"))
                .containsExactlyInAnyOrderElementsOf(getTimePartitions("token_balance", startDate, period));

        assertThat(tableExists("account_balance_old")).isTrue();
        assertThat(tableExists("token_balance_old")).isTrue();
    }

    private TimePartition getTimePartition(String parent, LocalDate date, Period interval) {
        final var from = date.withDayOfMonth(1);
        final var to = from.plus(interval);
        final var fromNs = from.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC) * 1_000_000_000;
        final var toNs = to.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC) * 1_000_000_000;
        final var name = String.format("%s_p%s", parent, from.format(DateTimeFormatter.ofPattern("yyyy_MM")));
        return TimePartition.builder()
                .name(name)
                .parent(parent)
                .timestampRange(Range.closedOpen(fromNs, toNs))
                .build();
    }

    private Collection<TimePartition> getTimePartitions(String parent, LocalDate startDate, Period interval) {
        final var alignedStartDate = startDate.withDayOfMonth(1);
        final var endDate = LocalDate.now(ZoneOffset.UTC).plus(interval);
        return IntStream.iterate(
                        0, i -> !alignedStartDate.plus(interval.multipliedBy(i)).isAfter(endDate), i -> i + 1)
                .mapToObj(i -> getTimePartition(parent, alignedStartDate.plus(interval.multipliedBy(i)), interval))
                .toList();
    }

    @SneakyThrows
    private void runMigration(String partitionStartDate, String balancePartitionTimeInterval) {
        try (final var is = migrationSql.getInputStream()) {
            final var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8)
                    .replaceAll("\\$\\{partitionStartDate}", partitionStartDate)
                    .replaceAll("\\$\\{balancePartitionTimeInterval}", balancePartitionTimeInterval);
            ownerJdbcTemplate.execute(script);
        }
    }

    private List<AccountBalance> queryAccountBalanceOld() {
        final var sql = "select account_id, balance, consensus_timestamp from account_balance_old";
        return ownerJdbcTemplate.query(sql, (rs, rowNum) -> {
            final var accountId = EntityId.of(rs.getLong("account_id"));
            final var id = new AccountBalance.Id(rs.getLong("consensus_timestamp"), accountId);
            final var accountBalance = new AccountBalance();
            accountBalance.setId(id);
            accountBalance.setBalance(rs.getLong("balance"));
            return accountBalance;
        });
    }

    private List<TokenBalance> queryTokenBalanceOld() {
        final var sql = "select account_id, balance, consensus_timestamp, token_id from token_balance_old";
        return ownerJdbcTemplate.query(sql, (rs, rowNum) -> {
            final var accountId = EntityId.of(rs.getLong("account_id"));
            final var tokenId = EntityId.of(rs.getLong("token_id"));
            final var id = new TokenBalance.Id(rs.getLong("consensus_timestamp"), accountId, tokenId);
            final var tokenBalance = new TokenBalance();
            tokenBalance.setId(id);
            tokenBalance.setBalance(rs.getLong("balance"));
            return tokenBalance;
        });
    }
}
