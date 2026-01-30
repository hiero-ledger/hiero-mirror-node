// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.File;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.ListAssert;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.TestPropertySource;

@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.87.1")
class AddBalanceTimestampMigrationTest extends AbstractStakingMigrationTest {

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
        assertThat(findAllEntities()).isEmpty();
        assertThat(findAllEntityHistories()).isEmpty();
        assertThat(findAllTokenAccounts()).isEmpty();
        assertThat(findAllTokenAccountHistories()).isEmpty();
    }

    @Test
    void migrateNoRecordFile() {
        // given
        final var entity =
                domainBuilder.entity().customize(e -> e.balanceTimestamp(null)).get();
        persistEntities(entity);

        final var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.balanceTimestamp(null))
                .get();
        persistEntityHistories(entityHistory);

        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.balanceTimestamp(null))
                .get();
        persistTokenAccounts(tokenAccount);

        final var tokenAccountHistory = domainBuilder
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
        final var latestRecordFile = domainBuilder.recordFile().get();
        persistRecordFile(latestRecordFile);

        final var entity = domainBuilder.entity().get();
        final var entity2 = domainBuilder.entity().get();
        // entity with null balance will not have balance timestamp set
        final var entity3 = domainBuilder
                .entity()
                .customize(e -> e.balance(null).balanceTimestamp(null))
                .get();
        persistEntities(entity, entity2, entity3);

        final var entityHistory = domainBuilder.entityHistory().get();
        final var entityHistory2 = domainBuilder.entityHistory().get();
        // entity history with null balance will not have balance timestamp set
        final var entityHistory3 = domainBuilder
                .entityHistory()
                .customize(e -> e.balance(null).balanceTimestamp(null))
                .get();
        persistEntityHistories(entityHistory, entityHistory2, entityHistory3);

        final var tokenAccount = domainBuilder.tokenAccount().get();
        final var tokenAccount2 = domainBuilder.tokenAccount().get();
        persistTokenAccounts(tokenAccount, tokenAccount2);

        final var tokenAccountHistory = domainBuilder.tokenAccountHistory().get();
        final var tokenAccountHistory2 = domainBuilder.tokenAccountHistory().get();
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

    private ListAssert<Entity> assertEntities() {
        return assertThat(findAllEntities())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "balance", "balanceTimestamp", "deleted");
    }

    private ListAssert<EntityHistory> assertEntityHistories() {
        return assertThat(findAllEntityHistories())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "balance", "balanceTimestamp", "deleted");
    }

    private ListAssert<TokenAccount> assertTokenAccounts() {
        return assertThat(findAllTokenAccounts())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "accountId", "balance", "balanceTimestamp", "tokenId");
    }

    private ListAssert<TokenAccountHistory> assertTokenAccountHistories() {
        return assertThat(findAllTokenAccountHistories())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "accountId", "balance", "balanceTimestamp", "tokenId");
    }

    private void persistEntities(Entity... entities) {
        for (final var entity : entities) {
            jdbcOperations.update(
                    "insert into entity (id, num, realm, shard, balance, deleted, timestamp_range) values (?, ?, ?, ?, ?, ?, ?::int8range)",
                    entity.getId(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    entity.getBalance(),
                    entity.getDeleted(),
                    PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
        }
    }

    private void persistEntityHistories(EntityHistory... entityHistories) {
        for (final var history : entityHistories) {
            jdbcOperations.update(
                    "insert into entity_history (id, num, realm, shard, balance, deleted, timestamp_range) values (?, ?, ?, ?, ?, ?, ?::int8range)",
                    history.getId(),
                    history.getNum(),
                    history.getRealm(),
                    history.getShard(),
                    history.getBalance(),
                    history.getDeleted(),
                    PostgreSQLGuavaRangeType.INSTANCE.asString(history.getTimestampRange()));
        }
    }

    private void persistTokenAccounts(TokenAccount... tokenAccounts) {
        for (final var tokenAccount : tokenAccounts) {
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
        for (final var tokenAccountHistory : tokenAccountHistories) {
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

    protected List<Entity> findAllEntities() {
        return jdbcOperations.query(
                "select id, balance, balance_timestamp, deleted from entity", (rs, rowNum) -> Entity.builder()
                        .id(rs.getLong("id"))
                        .balance((Long) rs.getObject("balance"))
                        .balanceTimestamp((Long) rs.getObject("balance_timestamp"))
                        .deleted(rs.getBoolean("deleted"))
                        .build());
    }

    private List<EntityHistory> findAllEntityHistories() {
        return jdbcOperations.query(
                "select id, balance, balance_timestamp, deleted from entity_history",
                (rs, rowNum) -> EntityHistory.builder()
                        .id(rs.getLong("id"))
                        .balance((Long) rs.getObject("balance"))
                        .balanceTimestamp((Long) rs.getObject("balance_timestamp"))
                        .deleted(rs.getBoolean("deleted"))
                        .build());
    }

    private List<TokenAccount> findAllTokenAccounts() {
        return jdbcOperations.query(
                "select account_id, balance, balance_timestamp, token_id from token_account", tokenAccountMapper);
    }

    private List<TokenAccountHistory> findAllTokenAccountHistories() {
        return jdbcOperations.query(
                "select account_id, balance, balance_timestamp, token_id from token_account_history",
                tokenAccountHistoryMapper);
    }

    private final RowMapper<TokenAccount> tokenAccountMapper = (rs, rowNum) -> TokenAccount.builder()
            .accountId(rs.getLong("account_id"))
            .balance(rs.getLong("balance"))
            .balanceTimestamp((Long) rs.getObject("balance_timestamp"))
            .tokenId(rs.getLong("token_id"))
            .build();

    private final RowMapper<TokenAccountHistory> tokenAccountHistoryMapper =
            (rs, rowNum) -> TokenAccountHistory.builder()
                    .accountId(rs.getLong("account_id"))
                    .balance(rs.getLong("balance"))
                    .balanceTimestamp((Long) rs.getObject("balance_timestamp"))
                    .tokenId(rs.getLong("token_id"))
                    .build();
}
