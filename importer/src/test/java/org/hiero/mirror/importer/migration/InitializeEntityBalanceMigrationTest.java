// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.domain.entity.EntityType.TOPIC;
import static org.hiero.mirror.common.domain.entity.EntityType.UNKNOWN;

import com.google.common.collect.Range;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.ErrataType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
@Tag("migration")
class InitializeEntityBalanceMigrationTest extends ImporterIntegrationTest {

    private static final String DELETE_ACCOUNT_BALANCE_SQL =
            "delete from account_balance where consensus_timestamp <= ?";
    private static final String DELETE_ACCOUNT_BALANCE_FILE_SQL =
            "delete from account_balance_file where consensus_timestamp <= ?";

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final ImporterProperties importerProperties;
    private final NamedParameterJdbcOperations namedParameterJdbcOperations;
    private final RecordFileRepository recordFileRepository;
    private final TransactionTemplate transactionTemplate;

    private InitializeEntityBalanceMigration migration;
    private Entity account;
    private Entity account2;
    private AccountBalanceFile accountBalanceFile1;
    private AccountBalanceFile accountBalanceFile2;
    private Entity accountDeleted;
    private Entity contract;
    private RecordFile recordFile2;
    private AtomicLong timestamp;
    private Entity topic;

    @BeforeEach
    void beforeEach() {
        timestamp = new AtomicLong(0L);

        ObjectProvider<AccountBalanceFileRepository> accountBalanceFileRepositoryProvider =
                new ObjectProvider<AccountBalanceFileRepository>() {
                    @Override
                    public AccountBalanceFileRepository getObject() {
                        return accountBalanceFileRepository;
                    }
                };
        ObjectProvider<NamedParameterJdbcOperations> namedParameterJdbcOperationsProvider = new ObjectProvider<>() {
            @Override
            public NamedParameterJdbcOperations getObject() {
                return namedParameterJdbcOperations;
            }
        };
        ObjectProvider<RecordFileRepository> recordFileRepositoryProvider = new ObjectProvider<RecordFileRepository>() {
            @Override
            public RecordFileRepository getObject() {
                return recordFileRepository;
            }
        };
        ObjectProvider<TransactionTemplate> transactionTemplateProvider = new ObjectProvider<TransactionTemplate>() {
            @Override
            public TransactionTemplate getObject() {
                return transactionTemplate;
            }
        };

        migration = new InitializeEntityBalanceMigration(
                accountBalanceFileRepositoryProvider,
                importerProperties,
                namedParameterJdbcOperationsProvider,
                recordFileRepositoryProvider,
                transactionTemplateProvider);
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(4);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migrate(boolean hasSyntheticAccountBalanceFile) {
        // given
        setup();
        if (hasSyntheticAccountBalanceFile) {
            // Remove the non-synthetic account balance file and add a synthetic file before latest record file's
            // consensus end
            accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());
            // A synthetic account balance file
            long syntheticBalanceTimestamp = recordFile2.getConsensusEnd() - 1;
            domainBuilder
                    .accountBalanceFile()
                    .customize(
                            a -> a.consensusTimestamp(syntheticBalanceTimestamp).synthetic(true))
                    .persist();
        }

        // when
        migration.doMigrate();

        // then
        setExpectedBalance();
        assertThat(entityRepository.findAll())
                .containsExactlyInAnyOrder(account, account2, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenAccountBalanceFileHasOffset() {
        // given
        setup();
        // The account balance file has a +5ns offset, so the crypto transfer to account at +2ns shouldn't be included
        // in the account's balance change
        // Note the delete then insert is a workaround for citus
        accountBalanceFileRepository.deleteById(accountBalanceFile1.getConsensusTimestamp());
        accountBalanceFile1.setTimeOffset(5);
        accountBalanceFileRepository.save(accountBalanceFile1);
        persistCryptoTransfer(300, account.getId(), null, accountBalanceFile1.getConsensusTimestamp() + 2L);

        // when
        migration.doMigrate();

        // then
        setExpectedBalance();
        assertThat(entityRepository.findAll())
                .containsExactlyInAnyOrder(account, account2, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoAccountBalance() {
        // given
        setup();
        accountBalanceFileRepository.deleteAll();
        accountBalanceRepository.deleteAll();

        // when
        migration.doMigrate();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoAccountBalanceAtOrBeforeLastRecordFile() {
        // given
        setup();
        ownerJdbcTemplate.update(DELETE_ACCOUNT_BALANCE_SQL, recordFile2.getConsensusEnd());
        ownerJdbcTemplate.update(DELETE_ACCOUNT_BALANCE_FILE_SQL, recordFile2.getConsensusEnd());

        // when
        migration.doMigrate();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoRecordFile() {
        // given
        setup();
        cryptoTransferRepository.deleteAll();
        recordFileRepository.deleteAll();

        // when
        migration.doMigrate();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoEntities() {
        // given
        setup();
        entityRepository.deleteAll();

        // when
        migration.doMigrate();

        // then
        setExpectedBalance();
        account.setDeleted(false); // We only know for sure that entities in the balance file are not deleted
        accountDeleted.setDeleted(null);
        contract.setDeleted(null);
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "balance", "deleted", "id", "num", "realm", "shard")
                .containsExactlyInAnyOrder(account, account2, accountDeleted, contract)
                .allSatisfy(e -> assertThat(e)
                        .returns(Range.atLeast(0L), Entity::getTimestampRange)
                        .returns(UNKNOWN, Entity::getType));
    }

    @Test
    @Transactional
    void onEndWhenNotFirstFile() {
        // given
        setup();
        // when
        migration.onEnd(accountBalanceFile2);

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    @Transactional
    void onEndEarlyReturn() {
        // given
        var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(ownerJdbcTemplate.getDataSource()));
        var txnTemplate = new TransactionTemplate(transactionManager);
        setup();
        accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());

        txnTemplate.executeWithoutResult(s -> migration.onEnd(accountBalanceFile1));

        // then
        assertThat(entityRepository.findAll())
                .containsExactlyInAnyOrder(account, account2, accountDeleted, contract, topic);

        // given
        long accountBalanceTimestamp3 = timestamp(Duration.ofMinutes(10));
        var accountBalanceFile3 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp3))
                .persist();
        // when
        migration.onEnd(accountBalanceFile3);

        // then
        assertThat(entityRepository.findAll())
                .containsExactlyInAnyOrder(account, account2, accountDeleted, contract, topic);
    }

    @Test
    @Transactional
    void onEndWhenNoRecordFileAfterTimestamp() {
        // given
        setup();
        recordFileRepository.deleteById(recordFile2.getConsensusEnd());

        // when
        migration.onEnd(accountBalanceFile2);

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    @Transactional
    void onEnd() {
        // given
        setup();
        accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());

        // when
        migration.onEnd(accountBalanceFile1);

        // then
        assertThat(entityRepository.findAll())
                .containsExactlyInAnyOrder(account, account2, accountDeleted, contract, topic);
    }

    private void persistAccountBalance(long balance, EntityId entityId, long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(a -> a.balance(balance).id(new AccountBalance.Id(timestamp, entityId)))
                .persist();
    }

    private void persistCryptoTransfer(long amount, long entityId, ErrataType errata, long timestamp) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(amount)
                        .consensusTimestamp(timestamp)
                        .entityId(entityId)
                        .errata(errata))
                .persist();
    }

    private void setExpectedBalance() {
        account.setBalance(505L);
        account.setBalanceTimestamp(recordFile2.getConsensusEnd());
        accountDeleted.setBalance(20L);
        contract.setBalance(25L);
    }

    private void setup() {
        setupForFirstAccountBalanceFile();

        // Second record file
        recordFile2 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofSeconds(5)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();
        long consensusStart = recordFile2.getConsensusStart();
        persistCryptoTransfer(10L, account.getId(), null, consensusStart);
        persistCryptoTransfer(-5L, account.getId(), ErrataType.INSERT, consensusStart + 5L);
        persistCryptoTransfer(25L, contract.getId(), null, consensusStart + 10L);
        persistCryptoTransfer(10L, contract.getId(), ErrataType.DELETE, consensusStart + 10L);
        persistCryptoTransfer(20L, accountDeleted.getId(), null, consensusStart + 15L);
        account2.setBalanceTimestamp(recordFile2.getConsensusEnd());

        // Second account balance file
        long accountBalanceTimestamp2 = timestamp(Duration.ofMinutes(2));
        accountBalanceFile2 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp2))
                .persist();
        persistAccountBalance(750L, account.toEntityId(), accountBalanceTimestamp2);
        persistAccountBalance(450L, contract.toEntityId(), accountBalanceTimestamp2);
    }

    private void setupForFirstAccountBalanceFile() {
        // account2 not in db, its balance information is deduped and its account balance timestamp is before
        // accountBalanceFile1
        account2 = systemEntity.treasuryAccount().toEntity().toBuilder()
                .balance(2L)
                .declineReward(false)
                .deleted(false)
                .ethereumNonce(0L)
                .memo("")
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .timestampRange(Range.atLeast(0L))
                .type(UNKNOWN)
                .build(); // not in db
        persistAccountBalance(account2.getBalance(), account2.toEntityId(), timestamp(Duration.ZERO));

        account = domainBuilder
                .entity()
                .customize(e -> e.balance(0L)
                        .balanceTimestamp(null)
                        .deleted(null)
                        .createdTimestamp(timestamp(Duration.ofSeconds(1L))))
                .persist();
        accountDeleted = domainBuilder
                .entity()
                .customize(e -> e.balance(0L).deleted(true).createdTimestamp(timestamp(Duration.ofSeconds(1))))
                .persist();
        topic = domainBuilder
                .entity()
                .customize(e -> e.balance(null)
                        .balanceTimestamp(null)
                        .createdTimestamp(timestamp(Duration.ofSeconds(1)))
                        .type(TOPIC))
                .persist();

        // First record file, it's before account balance files
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofMinutes(10)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();

        // First account balance file
        long accountBalanceTimestamp1 = timestamp(Duration.ofMinutes(10));
        accountBalanceFile1 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp1))
                .persist();
        persistAccountBalance(500L, account.toEntityId(), accountBalanceTimestamp1);

        // A crypto transfer already accounted in the first account balance file, so it shouldn't be included to
        // initialize entity balance
        persistCryptoTransfer(300L, account.getId(), null, accountBalanceTimestamp1);

        // The contract is created after the first account balance file
        contract = domainBuilder
                .entity()
                .customize(e -> e.balance(0L)
                        .balanceTimestamp(accountBalanceTimestamp1 + 10)
                        .createdTimestamp(timestamp(Duration.ofSeconds(1)))
                        .type(CONTRACT))
                .persist();
    }

    private long timestamp(Duration delta) {
        return timestamp.addAndGet(delta.toNanos());
    }
}
