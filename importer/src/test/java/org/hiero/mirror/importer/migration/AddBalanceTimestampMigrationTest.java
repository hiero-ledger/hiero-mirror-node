// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.File;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.IterableAssert;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.repository.EntityHistoryRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.TokenAccountHistoryRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.87.1")
class AddBalanceTimestampMigrationTest extends AbstractStakingMigrationTest {

    private final EntityRepository entityRepository;
    private final EntityHistoryRepository entityHistoryRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;

    private static final String REVERT_DDL = """
        alter table entity drop column balance_timestamp;
        alter table entity_history drop column balance_timestamp;
        alter table token_account drop column balance_timestamp;
        alter table token_account_history drop column balance_timestamp;
        """;

    @Value("classpath:db/migration/v1/V1.87.2__add_balance_timestamps.sql")
    private File migrationSql;

    @AfterEach
    void teardown() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void migrateEmpty() {
        // given
        // when
        runMigration();

        // then
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(entityHistoryRepository.findAll()).isEmpty();
        assertThat(tokenAccountRepository.findAll()).isEmpty();
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateNoRecordFile() {
        // given
        var entity =
                domainBuilder.entity().customize(e -> e.balanceTimestamp(null)).get();
        persistEntities(entity);

        var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.balanceTimestamp(null))
                .get();
        persistEntityHistories(entityHistory);

        var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.balanceTimestamp(null))
                .get();
        persistTokenAccounts(tokenAccount);

        var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.balanceTimestamp(null))
                .get();
        persistTokenAccountHistories(tokenAccountHistory);

        // when
        runMigration();
        entityHistory.setBalanceTimestamp(entityHistory.getTimestampUpper() - 1);
        tokenAccount.setBalanceTimestamp(0L);
        tokenAccountHistory.setBalanceTimestamp(tokenAccountHistory.getTimestampUpper() - 1);

        // then
        assertEntities().containsExactlyInAnyOrderElementsOf(List.of(entity));
        assertEntityHistories().containsExactlyInAnyOrderElementsOf(List.of(entityHistory));
        assertTokenAccounts().containsExactlyInAnyOrderElementsOf(List.of(tokenAccount));
        assertTokenAccountHistories().containsExactlyInAnyOrderElementsOf(List.of(tokenAccountHistory));
    }

    @Test
    void migrate() {
        // given
        persistRecordFile(domainBuilder.recordFile().get());
        var latestRecordFile = domainBuilder.recordFile().get();
        persistRecordFile(latestRecordFile);

        var entity = domainBuilder.entity().get();
        var entity2 = domainBuilder.entity().get();
        // entity with null balance will not have balance timestamp set
        var entity3 = domainBuilder
                .entity()
                .customize(e -> e.balance(null).balanceTimestamp(null))
                .get();
        persistEntities(entity, entity2, entity3);

        var entityHistory = domainBuilder.entityHistory().get();
        var entityHistory2 = domainBuilder.entityHistory().get();
        // entity history with null balance will not have balance timestamp set
        var entityHistory3 = domainBuilder
                .entityHistory()
                .customize(e -> e.balance(null).balanceTimestamp(null))
                .get();
        persistEntityHistories(entityHistory, entityHistory2, entityHistory3);

        var tokenAccount = domainBuilder.tokenAccount().get();
        var tokenAccount2 = domainBuilder.tokenAccount().get();
        persistTokenAccounts(tokenAccount, tokenAccount2);

        var tokenAccountHistory = domainBuilder.tokenAccountHistory().get();
        var tokenAccountHistory2 = domainBuilder.tokenAccountHistory().get();
        persistTokenAccountHistories(tokenAccountHistory, tokenAccountHistory2);

        // when
        runMigration();
        entity.setBalanceTimestamp(latestRecordFile.getConsensusEnd());
        entity2.setBalanceTimestamp(latestRecordFile.getConsensusEnd());
        entityHistory.setBalanceTimestamp(entityHistory.getTimestampUpper() - 1);
        entityHistory2.setBalanceTimestamp(entityHistory2.getTimestampUpper() - 1);
        tokenAccount.setBalanceTimestamp(latestRecordFile.getConsensusEnd());
        tokenAccount2.setBalanceTimestamp(latestRecordFile.getConsensusEnd());
        tokenAccountHistory.setBalanceTimestamp(tokenAccountHistory.getTimestampUpper() - 1);
        tokenAccountHistory2.setBalanceTimestamp(tokenAccountHistory2.getTimestampUpper() - 1);

        // then
        assertEntities().containsExactlyInAnyOrderElementsOf(List.of(entity, entity2, entity3));
        assertEntityHistories()
                .containsExactlyInAnyOrderElementsOf(List.of(entityHistory, entityHistory2, entityHistory3));
        assertTokenAccounts().containsExactlyInAnyOrderElementsOf(List.of(tokenAccount, tokenAccount2));
        assertTokenAccountHistories()
                .containsExactlyInAnyOrderElementsOf(List.of(tokenAccountHistory, tokenAccountHistory2));
    }

    @SneakyThrows
    private void runMigration() {
        ownerJdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private IterableAssert<Entity> assertEntities() {
        return assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "balance", "balanceTimestamp", "deleted");
    }

    private IterableAssert<EntityHistory> assertEntityHistories() {
        return assertThat(entityHistoryRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "balance", "balanceTimestamp", "deleted");
    }

    private IterableAssert<TokenAccount> assertTokenAccounts() {
        return assertThat(tokenAccountRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "accountId", "balance", "balanceTimestamp", "tokenId");
    }

    private IterableAssert<TokenAccountHistory> assertTokenAccountHistories() {
        return assertThat(tokenAccountHistoryRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "accountId", "balance", "balanceTimestamp", "tokenId");
    }

    private void persistEntities(Entity... entities) {
        for (var entity : entities) {
            persistEntity(entity);
        }
    }

    private void persistEntityHistories(EntityHistory... entityHistories) {
        for (var history : entityHistories) {
            persistEntityHistory(history);
        }
    }

    private void persistTokenAccounts(TokenAccount... tokenAccounts) {
        for (var tokenAccount : tokenAccounts) {
            jdbcOperations.update(
                    "insert into token_account (account_id, balance, created_timestamp, timestamp_range, token_id)"
                            + " values"
                            + " (?, ?, ?, ?::int8range, ?)",
                    tokenAccount.getAccountId(),
                    tokenAccount.getBalance(),
                    tokenAccount.getCreatedTimestamp(),
                    PostgreSQLGuavaRangeType.INSTANCE.asString(tokenAccount.getTimestampRange()),
                    tokenAccount.getTokenId());
        }
    }

    private void persistTokenAccountHistories(TokenAccountHistory... tokenAccountHistories) {
        for (var tokenAccountHistory : tokenAccountHistories) {
            jdbcOperations.update(
                    "insert into token_account_history (account_id, balance, created_timestamp, timestamp_range, token_id)"
                            + " values"
                            + " (?, ?, ?, ?::int8range, ?)",
                    tokenAccountHistory.getAccountId(),
                    tokenAccountHistory.getBalance(),
                    tokenAccountHistory.getCreatedTimestamp(),
                    PostgreSQLGuavaRangeType.INSTANCE.asString(tokenAccountHistory.getTimestampRange()),
                    tokenAccountHistory.getTokenId());
        }
    }
}
