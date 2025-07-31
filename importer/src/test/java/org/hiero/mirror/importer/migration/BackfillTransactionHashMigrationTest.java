// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONSENSUSSUBMITMESSAGE;
import static org.hiero.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.TransactionHashRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
@Tag("migration")
class BackfillTransactionHashMigrationTest extends ImporterIntegrationTest {

    private static final long DEFAULT_START_TIMESTAMP = 10_000_000_000L;
    private static final String MIGRATION_NAME = "backfillTransactionHashMigration";

    private final EntityProperties entityProperties;
    private final Environment environment;
    private final ImporterProperties importerProperties;
    private final TimePartitionService timePartitionService;
    private final TransactionHashRepository transactionHashRepository;

    private Set<TransactionType> defaultTransactionHashTypes;
    private BackfillTransactionHashMigration migration;
    private MigrationProperties migrationProperties;

    @BeforeEach
    void setup() {
        defaultTransactionHashTypes = entityProperties.getPersist().getTransactionHashTypes();
        migrationProperties = new MigrationProperties();
        migrationProperties = importerProperties.getMigration().get(MIGRATION_NAME);
        migrationProperties.getParams().put("startTimestamp", String.valueOf(DEFAULT_START_TIMESTAMP));
        importerProperties.getMigration().put(MIGRATION_NAME, migrationProperties);

        ObjectProvider<JdbcTemplate> jdbcTemplateProvider = new ObjectProvider<>() {
            @Override
            public JdbcTemplate getObject() {
                return ownerJdbcTemplate;
            }
        };
        ObjectProvider<TimePartitionService> timePartitionServiceProvider = new ObjectProvider<>() {
            @Override
            public TimePartitionService getObject() {
                return timePartitionService;
            }
        };

        migration = new BackfillTransactionHashMigration(
                entityProperties, environment, jdbcTemplateProvider, importerProperties, timePartitionServiceProvider);
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setTransactionHash(true);
        entityProperties.getPersist().setTransactionHashTypes(defaultTransactionHashTypes);
        migrationProperties.getParams().clear();
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(2);
    }

    @Test
    void empty() {
        runMigration();
        assertTransactionHashes(Collections.emptyList());
    }

    @Test
    void invalidStrategy() {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP))
                .persist();
        migrationProperties.getParams().put("strategy", "invalid");
        assertThatThrownBy(this::runMigration).isInstanceOf(IllegalArgumentException.class);
        assertTransactionHashes(Collections.emptyList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migrate(boolean persistTransactionHash) {
        // given
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP - 1))
                .persist();
        var expected = Stream.concat(
                        // The second transaction should reside in a different partition in v2
                        Stream.of(
                                persistTransaction(DEFAULT_START_TIMESTAMP),
                                persistTransaction(domainBuilder.timestamp())),
                        persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NOTHING).stream())
                .filter(t -> persistTransactionHash)
                .toList();
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);
        persistEthereumTransactionWithEmptyHash(DEFAULT_START_TIMESTAMP + 2);

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @Test
    void migrateWhenTransactionHashTypesEmpty() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(Collections.emptySet());
        var expected = List.of(
                persistTransaction(DEFAULT_START_TIMESTAMP),
                persistTransaction(DEFAULT_START_TIMESTAMP + 1, CONSENSUSSUBMITMESSAGE));
        persistEthereumTransactionWithEmptyHash(DEFAULT_START_TIMESTAMP + 2);

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @Test
    void migrateWhenSomeTransactionTypesExcluded() {
        // given
        var expected = persistTransaction(DEFAULT_START_TIMESTAMP);
        persistTransaction(DEFAULT_START_TIMESTAMP + 1, CONSENSUSSUBMITMESSAGE);

        // when
        runMigration();

        // then
        assertTransactionHashes(List.of(expected));
    }

    @Test
    void migrateWhenTransactionTypesCustomized() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(EnumSet.complementOf(EnumSet.of(ETHEREUMTRANSACTION)));
        var expected = List.of(
                persistTransaction(DEFAULT_START_TIMESTAMP),
                persistTransaction(DEFAULT_START_TIMESTAMP + 1, CONSENSUSSUBMITMESSAGE));
        persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 2, SkipTransaction.NOTHING);

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @Test
    void migrateWhenStartTimestampNotSet() {
        // given
        domainBuilder.transaction().persist();
        migrationProperties.getParams().clear();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWithCaseInsensitiveStartTimestamp() {
        // given
        migrationProperties.getParams().clear();
        migrationProperties.getParams().put("STARTTIMESTAMP", String.valueOf(DEFAULT_START_TIMESTAMP));
        var expected = Lists.newArrayList(persistTransaction(DEFAULT_START_TIMESTAMP));
        expected.addAll(persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NOTHING));

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "auto", "AUTO"})
    void migrateWhenTableNotEmpty(String strategy) {
        // given
        if (StringUtils.isNotEmpty(strategy)) {
            migrationProperties.getParams().put("strategy", strategy);
        }

        var existing = domainBuilder.transactionHash().persist();
        var expected = Lists.newArrayList(existing);
        persistTransaction(DEFAULT_START_TIMESTAMP);

        // For auto (or the default, since it's just auto) strategy with existing data in the database, only ethereum
        // transaction info should be backfilled
        expected.addAll(persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NATIVE));
        persistEthereumTransactionWithEmptyHash(DEFAULT_START_TIMESTAMP + 2);

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @Test
    void migrateWhenTableNotEmptyAndEthereumTransactionExcluded() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(EnumSet.complementOf(EnumSet.of(ETHEREUMTRANSACTION)));
        var existing = domainBuilder.transactionHash().persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP))
                .persist();
        persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NOTHING);

        // when
        runMigration();

        // then
        assertTransactionHashes(List.of(existing));
    }

    @Test
    void migrateWhenStrategyIsBoth() {
        // given
        migrationProperties.getParams().put("strategy", "both");
        domainBuilder.transactionHash().persist();
        var expected = Lists.newArrayList(persistTransaction(DEFAULT_START_TIMESTAMP));
        expected.addAll(persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NOTHING));
        persistEthereumTransactionWithEmptyHash(DEFAULT_START_TIMESTAMP + 2);

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @Test
    void migrateWhenStrategyIsBothAndEthereumTransactionExcluded() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(EnumSet.complementOf(EnumSet.of(ETHEREUMTRANSACTION)));
        migrationProperties.getParams().put("strategy", "both");
        domainBuilder.transactionHash().persist();
        var expected = List.of(persistTransaction(DEFAULT_START_TIMESTAMP));
        persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NOTHING);

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @Test
    void migrateWhenStrategyIsEthereumHash() {
        // given
        migrationProperties.getParams().put("strategy", "ethereum_hash");
        var existing = domainBuilder.transactionHash().persist();
        var expected = Lists.newArrayList(existing);
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP))
                .persist()
                .toTransactionHash();
        expected.addAll(persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NATIVE));
        persistEthereumTransactionWithEmptyHash(DEFAULT_START_TIMESTAMP + 2);

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    @Test
    void migrateWhenStrategyIsEthereumHashAndExcluded() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(EnumSet.complementOf(EnumSet.of(ETHEREUMTRANSACTION)));
        migrationProperties.getParams().put("strategy", "ethereum_hash");
        var existing = domainBuilder.transactionHash().persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP))
                .persist()
                .toTransactionHash();
        persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 1, SkipTransaction.NOTHING);

        // when
        runMigration();

        // then
        assertTransactionHashes(List.of(existing));
    }

    @Test
    void migrateWhenStrategyIsTransactionHash() {
        // given
        migrationProperties.getParams().put("strategy", "transaction_hash");
        domainBuilder.transactionHash().persist();
        var expected = Lists.newArrayList(
                persistTransaction(DEFAULT_START_TIMESTAMP), persistTransaction(DEFAULT_START_TIMESTAMP + 1));
        expected.addAll(persistEthereumTransaction(DEFAULT_START_TIMESTAMP + 2, SkipTransaction.ETHEREUM));

        // when
        runMigration();

        // then
        assertTransactionHashes(expected);
    }

    private void assertTransactionHashes(Collection<TransactionHash> expected) {
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private List<TransactionHash> persistEthereumTransaction(long consensusTimestamp, SkipTransaction skipTransaction) {
        var transactionHashList = new ArrayList<TransactionHash>();
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(true)
                .customize(t -> t.consensusTimestamp(consensusTimestamp))
                .persist();
        if (skipTransaction != SkipTransaction.ETHEREUM) {
            transactionHashList.add(ethereumTransaction.toTransactionHash());
        }

        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .type(ETHEREUMTRANSACTION.getProtoId())
                        .payerAccountId(ethereumTransaction.getPayerAccountId()))
                .persist();
        if (skipTransaction != SkipTransaction.NATIVE) {
            transactionHashList.add(transaction.toTransactionHash());
        }

        return transactionHashList;
    }

    private void persistEthereumTransactionWithEmptyHash(long consensusTimestamp) {
        domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.consensusTimestamp(consensusTimestamp).hash(EMPTY_BYTE_ARRAY))
                .persist();
    }

    private TransactionHash persistTransaction(long consensusTimestamp) {
        return persistTransaction(consensusTimestamp, TransactionType.CRYPTOTRANSFER);
    }

    private TransactionHash persistTransaction(long consensusTimestamp, TransactionType type) {
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).type(type.getProtoId()))
                .persist();
        return transaction.toTransactionHash();
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }

    private enum SkipTransaction {
        ETHEREUM,
        NATIVE,
        NOTHING
    }
}
