// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.historicalbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.common.domain.balance.AccountBalanceFile.INVALID_NODE_ID;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.domain.entity.EntityType.FILE;
import static org.hiero.mirror.common.domain.entity.EntityType.UNKNOWN;

import com.google.common.collect.Lists;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalance.Id;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.parser.record.RecordFileParsedEvent;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
@RequiredArgsConstructor
class HistoricalBalanceServiceIntegrationTest extends ImporterIntegrationTest {

    private static final String[] ACCOUNT_BALANCE_FILE_IGNORE_FIELDS = new String[] {"loadStart", "loadEnd", "name"};

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EntityRepository entityRepository;
    private final FlywayProperties flywayProperties;
    private final HistoricalBalanceProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final TimePartitionService timePartitionService;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TransactionTemplate transactionTemplate;
    private final HistoricalBalanceService historicalBalanceService;

    private Entity account;
    private long prevPartitionBalanceTimestamp;
    private long partitionLowerBound;
    private TokenAccount tokenAccount;
    private Entity treasuryAccount;

    private List<Entity> entities;
    private List<TokenAccount> tokenAccounts;

    void setup() {
        // common database setup
        long now = DomainUtils.convertToNanosMax(Instant.now());
        var partitions = timePartitionService.getOverlappingTimePartitions("account_balance", now, now);
        partitionLowerBound = partitions.get(0).getTimestampRange().lowerEndpoint();
        prevPartitionBalanceTimestamp = partitionLowerBound
                - properties.getMinFrequency().minusMinutes(1).toNanos();
        long balanceTimestamp = partitionLowerBound + Duration.ofMinutes(1).toNanos();

        treasuryAccount = domainBuilder
                .entity(systemEntity.treasuryAccount(), prevPartitionBalanceTimestamp - 200)
                .persist();
        account = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(
                        e -> e.balanceTimestamp(balanceTimestamp).deleted(null).type(CONTRACT))
                .persist();
        var fileWithBalance = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp).type(FILE))
                .persist();
        var unknownWithBalance = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp).type(UNKNOWN))
                .persist();
        // deleted after the balance snapshot in previous partition
        var deletedAccount1 = domainBuilder
                .entity()
                .customize(e -> e.balance(0L)
                        .balanceTimestamp(prevPartitionBalanceTimestamp + 100)
                        .deleted(true))
                .persist();
        // deleted before the balance snapshot in previous partition
        var deletedAccount2 = domainBuilder
                .entity()
                .customize(e -> e.balance(0L)
                        .balanceTimestamp(prevPartitionBalanceTimestamp - 100)
                        .deleted(true))
                .persist();
        domainBuilder.topicEntity().persist();
        tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()).balanceTimestamp(balanceTimestamp))
                .persist();
        var dissociatedTokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.associated(false).balance(0).balanceTimestamp(balanceTimestamp))
                .persist();

        // Only entities with valid balance, including deleted
        entities = List.of(
                treasuryAccount,
                account,
                contract,
                fileWithBalance,
                unknownWithBalance,
                deletedAccount1,
                deletedAccount2);
        tokenAccounts = List.of(tokenAccount, dissociatedTokenAccount);

        // Reset the bean state
        historicalBalanceService.getTreasuryExists().set(false);
    }

    @AfterEach
    void resetProperties() {
        properties.setTokenBalances(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void generate(boolean tokenBalances) {
        // given
        setup();
        properties.setTokenBalances(tokenBalances);
        var existinigAccountBalanceFile = domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(prevPartitionBalanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(prevPartitionBalanceTimestamp, treasuryAccount.toEntityId())))
                .persist();

        // when, process a record file whose consensusEnd is partitionLowerBound, which is before last account balance
        // file timestamp + min frequency
        parseRecordFile(partitionLowerBound);

        // then
        verifyNoNewAccountBalanceFile(List.of(existinigAccountBalanceFile));

        // when, then
        long balanceTimestamp =
                prevPartitionBalanceTimestamp + properties.getMinFrequency().toNanos();
        // this is the first snapshot in a partition, so it includes all non-deleted entities, and those deleted after
        // prevPartitionBalanceTimestamp
        var updatedEntities = entities.stream()
                .filter(e ->
                        !Boolean.TRUE.equals(e.getDeleted()) || e.getBalanceTimestamp() > prevPartitionBalanceTimestamp)
                .toList();
        verifyGeneratedBalances(balanceTimestamp, updatedEntities, tokenAccounts);

        // when
        // balance changes
        account.setBalance(account.getBalance() + 5);
        account.setBalanceTimestamp(balanceTimestamp + 1);
        entityRepository.save(account);
        tokenAccount.setBalance(tokenAccount.getBalance() + 5);
        tokenAccount.setBalanceTimestamp(balanceTimestamp + 1);
        tokenAccountRepository.save(tokenAccount);
        // new entity, tokenAccount
        var newAccount = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(account.getBalanceTimestamp()))
                .persist();
        var newTokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()).balanceTimestamp(account.getBalanceTimestamp()))
                .persist();
        updatedEntities = List.of(treasuryAccount, account, newAccount);
        var updatedTokenAccounts = List.of(tokenAccount, newTokenAccount);

        // process a record file which doesn't reach the next balances snapshot interval
        var existingAccountBalanceFiles = Lists.newArrayList(accountBalanceFileRepository.findAll());
        parseRecordFile(
                balanceTimestamp + properties.getMinFrequency().minusSeconds(2).toNanos());

        // then
        verifyNoNewAccountBalanceFile(existingAccountBalanceFiles);

        // when, then
        // process a record file which should trigger the next balance snapshot
        balanceTimestamp += properties.getMinFrequency().plusSeconds(1).toNanos();
        verifyGeneratedBalances(balanceTimestamp, updatedEntities, updatedTokenAccounts);
    }

    @Test
    void generateWhenTreasuryAccountDoesntExist() {
        // given
        setup();

        // treasury account will be auto created with 0 balance
        entityRepository.delete(treasuryAccount);
        treasuryAccount.setBalance(0L);

        var existinigAccountBalanceFile = domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(prevPartitionBalanceTimestamp))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(prevPartitionBalanceTimestamp, treasuryAccount.toEntityId())))
                .persist();

        // when, process a record file whose consensusEnd is partitionLowerBound, which is before last account balance
        // file timestamp + min frequency
        parseRecordFile(partitionLowerBound);

        // then
        verifyNoNewAccountBalanceFile(List.of(existinigAccountBalanceFile));

        // when, then
        long balanceTimestamp =
                prevPartitionBalanceTimestamp + properties.getMinFrequency().toNanos();
        // this is the first snapshot in a partition, so it includes all non-deleted entities, and those deleted after
        // prevPartitionBalanceTimestamp
        var updatedEntities = entities.stream()
                .filter(e ->
                        !Boolean.TRUE.equals(e.getDeleted()) || e.getBalanceTimestamp() > prevPartitionBalanceTimestamp)
                .toList();

        verifyGeneratedBalances(balanceTimestamp, updatedEntities, tokenAccounts);

        // when
        // balance changes
        account.setBalance(account.getBalance() + 5);
        account.setBalanceTimestamp(balanceTimestamp + 1);
        entityRepository.save(account);
        tokenAccount.setBalance(tokenAccount.getBalance() + 5);
        tokenAccount.setBalanceTimestamp(balanceTimestamp + 1);
        tokenAccountRepository.save(tokenAccount);
        // new entity, tokenAccount
        var newAccount = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(account.getBalanceTimestamp()))
                .persist();
        var newTokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()).balanceTimestamp(account.getBalanceTimestamp()))
                .persist();
        updatedEntities = List.of(treasuryAccount, account, newAccount);
        var updatedTokenAccounts = List.of(tokenAccount, newTokenAccount);

        // process a record file which doesn't reach the next balances snapshot interval
        var existingAccountBalanceFiles = Lists.newArrayList(accountBalanceFileRepository.findAll());
        parseRecordFile(
                balanceTimestamp + properties.getMinFrequency().minusSeconds(2).toNanos());

        // then
        verifyNoNewAccountBalanceFile(existingAccountBalanceFiles);

        // when, then
        // process a record file which should trigger the next balance snapshot
        balanceTimestamp += properties.getMinFrequency().plusSeconds(1).toNanos();
        verifyGeneratedBalances(balanceTimestamp, updatedEntities, updatedTokenAccounts);
    }

    @Test
    void generateWhenAccountBalanceFileTableNotEmpty() {
        // given
        setup();
        // account balance file table isn't empty before the run
        var firstAccountBalanceFile = domainBuilder.accountBalanceFile().persist();
        var latestAccountBalanceFile = domainBuilder.accountBalanceFile().persist();

        // when
        parseRecordFile(null);

        // then
        verifyNoNewAccountBalanceFile(List.of(firstAccountBalanceFile, latestAccountBalanceFile));
        long balanceTimestamp = latestAccountBalanceFile.getConsensusTimestamp()
                + properties.getMinFrequency().toNanos();
        verifyGeneratedBalances(balanceTimestamp, entities, tokenAccounts);
    }

    @Test
    void generateWhenNoAccountBalanceFiles() {
        // given
        setup();
        // there's no account balance files at all. The first balances snapshot will be generated in min frequency +
        // initial delay relative to the first record file's consensus end

        // when
        // parse the fist record file and a following record file which doesn't hit the required interval
        var firstRecordFile = parseRecordFile(null);
        long consensusEnd = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .minusSeconds(3)
                        .toNanos();
        parseRecordFile(consensusEnd);

        // then
        verifyNoNewAccountBalanceFile(Collections.emptyList());

        // when, then
        long balanceTimestamp = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .toNanos();
        verifyGeneratedBalances(balanceTimestamp, entities, tokenAccounts);
    }

    @Test
    void generateWhenNoAccountBalanceFilesAndFirstRecordFileInDb() {
        // given
        setup();
        var firstRecordFile = domainBuilder.recordFile().persist();

        // when
        // next record file doesn't hit the required interval
        long consensusEnd = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .minusSeconds(3)
                        .toNanos();
        parseRecordFile(consensusEnd);

        // then
        verifyNoNewAccountBalanceFile(Collections.emptyList());

        // when, then
        long balanceTimestamp = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .toNanos();
        verifyGeneratedBalances(balanceTimestamp, entities, tokenAccounts);
    }

    @Test
    void noPartitionFound(CapturedOutput output) {
        // given, first record file
        var recordFile = domainBuilder.recordFile().persist();

        // when, next record file at 21 years later parsed, which is guaranteed to be in a partition not created yet
        long consensusEnd =
                recordFile.getConsensusEnd() + Duration.ofDays(365 * 21).toNanos();
        var oldPartitionEnd = createRecordFilePartitions(consensusEnd);
        parseRecordFile(consensusEnd);

        // then
        var expectedMessage = String.format("No account_balance partition found for timestamp %d", consensusEnd);
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.FIVE_SECONDS)
                .untilAsserted(() -> assertThat(output.getOut()).contains(expectedMessage));
        assertThat(accountBalanceFileRepository.findAll()).isEmpty();

        // cleanup
        deleteRecordFilePartitions(oldPartitionEnd);
    }

    /**
     * Create record file partitions when the table is partitioned, to avoid the failure when test cases try to insert
     * a row out of the partition range otherwise.
     */
    private Long createRecordFilePartitions(long endTimestamp) {
        Long partitionEnd = Long.MAX_VALUE;
        try {
            partitionEnd = ownerJdbcTemplate.queryForObject("""
                select to_timestamp
                from mirror_node_time_partitions
                where parent = 'record_file'
                order by to_timestamp desc
                limit 1
                """, Long.class);
        } catch (EmptyResultDataAccessException ex) {
            return partitionEnd;
        }

        var partitionStartDate = flywayProperties.getPlaceholders().get("partitionStartDate");
        var partitionTimeInterval = flywayProperties.getPlaceholders().get("partitionTimeInterval");
        var partitionEndDate = String.format(
                "'%s'",
                Instant.ofEpochSecond(endTimestamp / 1_000_000_000)
                        .atZone(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE));
        ownerJdbcTemplate.queryForObject(
                """
                select create_time_partitions(table_name := 'public.record_file',
                  partition_interval := ?::interval,
                  start_from := ?::timestamptz,
                  end_at := ?::timestamptz)
                """, Boolean.class, partitionTimeInterval, partitionStartDate, partitionEndDate);

        return partitionEnd;
    }

    private void deleteRecordFilePartitions(Long fromTimestamp) {
        var partitions = ownerJdbcTemplate.queryForList("""
                select name
                from mirror_node_time_partitions
                where parent = 'record_file' and from_timestamp >= ?
                order by from_timestamp
                """, String.class, fromTimestamp);
        if (partitions.isEmpty()) {
            return;
        }

        var sql =
                partitions.stream().map(p -> String.format("drop table %s", p)).collect(Collectors.joining(";"));
        ownerJdbcTemplate.execute(sql);
    }

    private RecordFile parseRecordFile(final Long consensusEnd) {
        return transactionTemplate.execute(t -> {
            var recordFile = domainBuilder
                    .recordFile()
                    .customize(rf -> {
                        if (consensusEnd != null) {
                            rf.consensusEnd(consensusEnd).consensusStart(consensusEnd - 100);
                        }
                    })
                    .get();
            recordFileRepository.save(recordFile);
            applicationEventPublisher.publishEvent(new RecordFileParsedEvent(this, recordFile.getConsensusEnd()));
            return recordFile;
        });
    }

    private void verifyNoNewAccountBalanceFile(List<AccountBalanceFile> existingAccountBalanceFiles) {
        // best effort to check that no new account balance file for 500ms
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .during(Durations.FIVE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(accountBalanceFileRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ACCOUNT_BALANCE_FILE_IGNORE_FIELDS)
                        .containsExactlyInAnyOrderElementsOf(existingAccountBalanceFiles));
    }

    private void verifyGeneratedBalances(
            long balanceTimestamp, Collection<Entity> updatedEntities, Collection<TokenAccount> updatedTokenAccounts) {
        var expectedAccountBalanceFiles = Lists.newArrayList(accountBalanceFileRepository.findAll());
        var expectedAccountBalances = Lists.newArrayList(accountBalanceRepository.findAll());
        var expectedTokenBalances = Lists.newArrayList(tokenBalanceRepository.findAll());

        // when, a record file with consensus end signaling min frequency has passed
        parseRecordFile(balanceTimestamp);

        // then, account balance, a synthetic account balance file, and token balance should generate
        expectedAccountBalanceFiles.add(AccountBalanceFile.builder()
                .consensusTimestamp(balanceTimestamp)
                .count((long) updatedEntities.size())
                .nodeId(INVALID_NODE_ID)
                .synthetic(true)
                .build());
        updatedEntities.forEach(e -> expectedAccountBalances.add(getAccountBalance(balanceTimestamp, e)));
        if (properties.isTokenBalances()) {
            updatedTokenAccounts.forEach(ta -> expectedTokenBalances.add(getTokenBalance(balanceTimestamp, ta)));
        }
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.FIVE_SECONDS)
                .untilAsserted(() -> assertThat(accountBalanceFileRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ACCOUNT_BALANCE_FILE_IGNORE_FIELDS)
                        .containsExactlyInAnyOrderElementsOf(expectedAccountBalanceFiles));
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
    }

    private static AccountBalance getAccountBalance(long consensusTimestamp, Entity entity) {
        return AccountBalance.builder()
                .balance(entity.getBalance())
                .id(new Id(consensusTimestamp, entity.toEntityId()))
                .build();
    }

    private static TokenBalance getTokenBalance(long consensusTimestamp, TokenAccount tokenAccount) {
        var accountId = EntityId.of(tokenAccount.getAccountId());
        var tokenId = EntityId.of(tokenAccount.getTokenId());
        return TokenBalance.builder()
                .balance(tokenAccount.getBalance())
                .id(new TokenBalance.Id(consensusTimestamp, accountId, tokenId))
                .build();
    }
}
