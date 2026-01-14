// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.persistence.Id;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisableRepeatableSqlMigration
@DisablePartitionMaintenance
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.93.2")
class UpdateTokenFreezeKycStatusMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL = """
                    alter table if exists token
                      drop column freeze_status,
                      drop column kyc_status;
                    alter table if exists token_history
                      drop column freeze_status,
                      drop column kyc_status;
                    alter table if exists token_account
                      alter column automatic_association set default false,
                      alter column automatic_association set not null,
                      alter column balance_timestamp set not null,
                      alter column created_timestamp set not null,
                      alter column freeze_status set default 0,
                      alter column freeze_status set not null,
                      alter column kyc_status set default 0,
                      alter column kyc_status set not null;
                    alter table if exists token_account_history
                      alter column automatic_association set default false,
                      alter column automatic_association set not null,
                      alter column balance_timestamp set not null,
                      alter column created_timestamp set not null,
                      alter column freeze_status set default 0,
                      alter column freeze_status set not null,
                      alter column kyc_status set default 0,
                      alter column kyc_status set not null;
                    """;

    private final DomainBuilder domainBuilder;

    @Value("classpath:db/migration/v1/V1.94.0__update_token_freeze_kyc_status.sql")
    private final Resource migrationSql;

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(postFindAll()).isEmpty();
        assertThat(findHistory("token", PostMigrationToken.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var tokens = new ArrayList<MigrationToken>();
        var expectedHistory = new ArrayList<PostMigrationToken>();

        // freeze default is false, and have both freeze key and kyc key
        var token = getMigrationToken().build();
        tokens.add(token);
        expectedHistory.add(token.toPostMigrationToken()
                .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                .kycStatus(TokenKycStatusEnum.REVOKED)
                .build());

        // freeze default is true, and have both freeze key and kyc key
        token = getMigrationToken().freezeDefault(true).build();
        tokens.add(token);
        expectedHistory.add(token.toPostMigrationToken()
                .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                .kycStatus(TokenKycStatusEnum.REVOKED)
                .build());

        // no freeze key / kyc key
        token = getMigrationToken().freezeKey(null).kycKey(null).build();
        tokens.add(token);
        expectedHistory.add(token.toPostMigrationToken()
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .build());

        persistMigrationTokens(tokens, false);

        // add current tokens, and the expected current tokens
        tokens.forEach(
                t -> t.timestampRange = Range.atLeast(t.getTimestampRange().upperEndpoint()));
        var expectedCurrent = expectedHistory.stream()
                .map(t -> t.toBuilder()
                        .timestampRange(Range.atLeast(t.getTimestampRange().upperEndpoint()))
                        .build())
                .toList();

        persistMigrationTokens(tokens, true);

        // when
        runMigration();

        // then
        assertThat(postFindAll()).containsExactlyInAnyOrderElementsOf(expectedCurrent);
        assertThat(findHistory("token", PostMigrationToken.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    @SuppressWarnings("java:S1854") // timestamp is needed in order to avoid repetition
    private MigrationToken.MigrationTokenBuilder getMigrationToken() {
        long timestamp = domainBuilder.timestamp();
        return MigrationToken.builder()
                .createdTimestamp(timestamp)
                .freezeKey(domainBuilder.bytes(4))
                .initialSupply(3L)
                .kycKey(domainBuilder.bytes(4))
                .name(domainBuilder.text(4))
                .symbol(domainBuilder.text(4))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 1))
                .tokenId(domainBuilder.id())
                .treasuryAccountId(domainBuilder.id());
    }

    private void persistMigrationTokens(Collection<MigrationToken> migrationTokens, boolean current) {
        var sql = String.format("""
                        insert into %s (created_timestamp, decimals, freeze_default, freeze_key, initial_supply, kyc_key,
                          max_supply, name, symbol, timestamp_range, token_id, treasury_account_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::int8range, ?, ?)
                        """, current ? "token" : "token_history");
        jdbcOperations.batchUpdate(sql, migrationTokens, migrationTokens.size(), (ps, token) -> {
            ps.setLong(1, token.getCreatedTimestamp());
            ps.setInt(2, token.getDecimals());
            ps.setBoolean(3, token.isFreezeDefault());
            ps.setBytes(4, token.getFreezeKey());
            ps.setLong(5, token.getInitialSupply());
            ps.setBytes(6, token.getKycKey());
            ps.setLong(7, token.getMaxSupply());
            ps.setString(8, token.getName());
            ps.setString(9, token.getSymbol());
            ps.setString(10, PostgreSQLGuavaRangeType.INSTANCE.asString(token.getTimestampRange()));
            ps.setLong(11, token.getTokenId());
            ps.setLong(12, token.getTreasuryAccountId());
        });
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            ownerJdbcTemplate.execute(script);
        }
    }

    private List<PostMigrationToken> postFindAll() {
        return jdbcOperations.query("SELECT * FROM token", (rs, index) -> PostMigrationToken.builder()
                .createdTimestamp(rs.getLong("created_timestamp"))
                .decimals(rs.getInt("decimals"))
                .freezeDefault(rs.getBoolean("freeze_default"))
                .freezeKey(rs.getBytes("freeze_key"))
                .freezeStatus(TokenFreezeStatusEnum.values()[rs.getInt("freeze_status")])
                .initialSupply(rs.getLong("initial_supply"))
                .kycKey(rs.getBytes("kyc_key"))
                .kycStatus(TokenKycStatusEnum.values()[rs.getInt("kyc_status")])
                .maxSupply(rs.getLong("max_supply"))
                .name(rs.getString("name"))
                .pauseStatus(TokenPauseStatusEnum.valueOf(rs.getString("pause_status")))
                .supplyType(TokenSupplyTypeEnum.valueOf(rs.getString("supply_type")))
                .symbol(rs.getString("symbol"))
                .timestampRange(PostgreSQLGuavaRangeType.longRange(rs.getString("timestamp_range")))
                .tokenId(rs.getLong("token_id"))
                .totalSupply(rs.getLong("total_supply"))
                .treasuryAccountId(EntityId.of(rs.getLong("treasury_account_id")))
                .type(TokenTypeEnum.valueOf(rs.getString("type")))
                .build());
    }

    @Builder(toBuilder = true)
    @Data
    private static class MigrationToken {
        private long createdTimestamp;
        private int decimals;
        private boolean freezeDefault;
        private byte[] freezeKey;
        private long initialSupply;
        private byte[] kycKey;

        @Builder.Default
        private long maxSupply = 1000L;

        private String name;
        private String symbol;
        private Range<Long> timestampRange;
        private long tokenId;
        private long treasuryAccountId;

        public PostMigrationToken.PostMigrationTokenBuilder toPostMigrationToken() {
            return PostMigrationToken.builder()
                    .createdTimestamp(createdTimestamp)
                    .decimals(decimals)
                    .freezeDefault(freezeDefault)
                    .freezeKey(freezeKey)
                    .initialSupply(initialSupply)
                    .kycKey(kycKey)
                    .maxSupply(maxSupply)
                    .name(name)
                    .pauseStatus(TokenPauseStatusEnum.NOT_APPLICABLE)
                    .supplyType(TokenSupplyTypeEnum.INFINITE)
                    .symbol(symbol)
                    .timestampRange(timestampRange)
                    .tokenId(tokenId)
                    .totalSupply(0L)
                    .treasuryAccountId(EntityId.of(treasuryAccountId))
                    .type(TokenTypeEnum.FUNGIBLE_COMMON);
        }
    }

    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    private static class PostMigrationToken {
        private Long createdTimestamp;
        private Integer decimals;
        private byte[] feeScheduleKey;
        private Boolean freezeDefault;
        private byte[] freezeKey;
        private TokenFreezeStatusEnum freezeStatus;
        private Long initialSupply;
        private byte[] kycKey;
        private TokenKycStatusEnum kycStatus;
        private long maxSupply;
        private String name;
        private byte[] pauseKey;
        private TokenPauseStatusEnum pauseStatus;
        private byte[] supplyKey;
        private TokenSupplyTypeEnum supplyType;
        private String symbol;
        private Range<Long> timestampRange;

        @Id
        private Long tokenId;

        private Long totalSupply;
        private EntityId treasuryAccountId;
        private TokenTypeEnum type;
        private byte[] wipeKey;
    }
}
