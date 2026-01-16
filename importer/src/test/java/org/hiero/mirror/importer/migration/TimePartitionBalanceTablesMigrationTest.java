// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.db.TimePartition;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.89.1")
class TimePartitionBalanceTablesMigrationTest extends ImporterIntegrationTest {

    private static final String CLEANUP_SQL = """
            drop table account_balance;
            drop table token_balance;
            alter table account_balance_old rename to account_balance;
            alter table token_balance_old rename to token_balance;
            """;

    private final AccountBalanceRepository accountBalanceRepository;

    @Value("classpath:db/migration/v1/V1.89.2__time_partition_balance_tables.sql")
    private final Resource migrationSql;

    private final TimePartitionService timePartitionService;

    private final TokenBalanceRepository tokenBalanceRepository;

    @BeforeEach
    void setup() {
        // The migration dedups account balance using treasury account 0.0.2 as the sentinel account. It should only
        // apply to past network with shard=0 and realm=0
        commonProperties.setRealm(0);
        commonProperties.setShard(0);
    }

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(CLEANUP_SQL);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            "'1 month'", P1M
            "'3 months'", P3M
            """)
    void empty(String interval, Period period) {
        // given, when
        var startDate = LocalDate.now(ZoneOffset.UTC).minusMonths(4);
        runMigration(String.format("'%s'", startDate), interval);

        // then
        assertSchema(startDate, period);
        assertThat(accountBalanceRepository.findAll()).isEmpty();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();
        assertThat(getSentinelTimestamp()).isEmpty();
    }

    private void assertSchema(LocalDate startDate, Period period) {
        assertThat(timePartitionService.getTimePartitions("account_balance"))
                .containsExactlyInAnyOrderElementsOf(getTimePartitions("account_balance", startDate, period));
        assertThat(timePartitionService.getTimePartitions("token_balance"))
                .containsExactlyInAnyOrderElementsOf(getTimePartitions("token_balance", startDate, period));
        assertThat(tableExists("account_balance_old")).isTrue();
        assertThat(tableExists("token_balance_old")).isTrue();
    }

    @Test
    void migrate() {
        // given
        var treasury = domainBuilder.entityNum(2);
        var account2 = domainBuilder.entityId();
        var account3 = domainBuilder.entityId();
        var account4 = domainBuilder.entityId();
        var token = domainBuilder.entityId();

        var startDate = LocalDate.now(ZoneOffset.UTC).minusMonths(2);
        // Last snapshot timestamp is 10 minutes after the start of the month, so the 6 hours of full snapshots
        // processed by the migration will fall into two monthly time partitions
        long lastSnapshotTimestamp =
                LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).toEpochSecond(LocalTime.of(0, 10), ZoneOffset.UTC)
                        * 1_000_000_000L;

        // One balance snapshot before the last 6-hour, the values don't matter
        var timestamp =
                new AtomicLong(lastSnapshotTimestamp - Duration.ofHours(7).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(200).id(new AccountBalance.Id(timestamp.get(), account2)))
                .persist();

        // First balance snapshot in the 6-hour period
        // account2 and token's association will dissociate then re-associate
        // account3 and token will get associated later and the account gets deleted right before the next partition,
        // the account's balance also changes in every snapshot
        // account4's balance and token balance stay the same in all snapshots
        long firstProcessedTimestamp = timestamp.get() + Duration.ofHours(1).toNanos();
        timestamp.set(firstProcessedTimestamp);
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        var expectedAccountBalances = new ArrayList<>(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(timestamp.get(), account2)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(300).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .persist()));
        var expectedTokenBalances = new ArrayList<>(List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(20).id(new TokenBalance.Id(timestamp.get(), account2, token)))
                        .persist(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .persist()));

        // Second balance snapshot in the 6-hour period
        timestamp.addAndGet(Duration.ofHours(1).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        expectedAccountBalances.add(domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                .persist());
        expectedAccountBalances.add(domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(201).id(new AccountBalance.Id(timestamp.get(), account2)))
                .persist());
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(300).id(new AccountBalance.Id(timestamp.get(), account3)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                .persist();
        // account2 dissociated itself from token before the snapshot, note there is no balance info in the original
        // snapshot
        expectedTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(0).id(new TokenBalance.Id(timestamp.get(), account2, token)))
                .get());
        expectedTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                .persist());
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                .persist();

        // Third balance snapshot in the 6-hour period
        timestamp.addAndGet(Duration.ofHours(1).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        expectedAccountBalances.add(domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                .persist());
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(201).id(new AccountBalance.Id(timestamp.get(), account2)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(300).id(new AccountBalance.Id(timestamp.get(), account3)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                .persist();
        // account2 reassociated itself with token before the snapshot
        expectedTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(21).id(new TokenBalance.Id(timestamp.get(), account2, token)))
                .persist());
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                .persist();

        // Forth balance snapshot in the 6-hour period, note account2 was deleted before this snapshot
        timestamp.addAndGet(Duration.ofHours(1).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        expectedAccountBalances.add(domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                .persist());
        expectedAccountBalances.add(domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(0).id(new AccountBalance.Id(timestamp.get(), account2)))
                .get());
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(300).id(new AccountBalance.Id(timestamp.get(), account3)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                .persist();
        expectedTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(0).id(new TokenBalance.Id(timestamp.get(), account2, token)))
                .get());
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                .persist();

        // Last snapshot, account3 was deleted before this snapshot
        timestamp.set(lastSnapshotTimestamp);
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        expectedAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(0).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .persist()));
        expectedTokenBalances.addAll(List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(0).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .persist()));

        // when
        runMigration(String.format("'%s'", startDate), "'1 month'");

        // then
        assertSchema(startDate, Period.ofMonths(1));
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
        assertThat(getSentinelTimestamp()).contains(firstProcessedTimestamp);
    }

    private Optional<Long> getSentinelTimestamp() {
        try {
            return Optional.ofNullable(jdbcOperations.queryForObject(
                    "select balance from account_balance_old where consensus_timestamp = -1 and account_id = -1",
                    Long.class));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private TimePartition getTimePartition(String parent, LocalDate date, Period interval) {
        var from = date.withDayOfMonth(1);
        var to = from.plus(interval);
        long fromNs = from.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC) * 1_000_000_000;
        long toNs = to.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC) * 1_000_000_000;
        var name = String.format("%s_p%s", parent, from.format(DateTimeFormatter.ofPattern("yyyy_MM")));
        return TimePartition.builder()
                .name(name)
                .parent(parent)
                .timestampRange(Range.closedOpen(fromNs, toNs))
                .build();
    }

    private Collection<TimePartition> getTimePartitions(String parent, LocalDate startDate, Period interval) {
        var alignedStartDate = startDate.withDayOfMonth(1);
        var endDate = LocalDate.now(ZoneOffset.UTC).plus(interval);
        return IntStream.iterate(
                        0, i -> !alignedStartDate.plus(interval.multipliedBy(i)).isAfter(endDate), i -> i + 1)
                .mapToObj(i -> getTimePartition(parent, alignedStartDate.plus(interval.multipliedBy(i)), interval))
                .toList();
    }

    @SneakyThrows
    private void runMigration(String partitionStartDate, String partitionTimeInterval) {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8)
                    .replaceAll("\\$\\{partitionStartDate}", partitionStartDate)
                    .replaceAll("\\$\\{partitionTimeInterval}", partitionTimeInterval);
            ownerJdbcTemplate.execute(script);
        }
    }
}
