// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.CryptoAllowanceRepository;
import org.hiero.mirror.importer.repository.TokenAllowanceRepository;
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
@TestPropertySource(properties = "spring.flyway.target=1.84.1")
class FixAllowanceAmountsMigrationTest extends ImporterIntegrationTest {

    private static final EntityId PAYER = EntityId.of("0.0.1001");

    private static final String PRE_MIGRATION_ALLOWANCES = """
                    insert into crypto_allowance (amount, owner, payer_account_id, spender, timestamp_range)
                      values (50, 2, 1001, 5, '[10,)'), (0, 2, 1001, 6, '[11,)');
                    insert into crypto_allowance_history (amount, owner, payer_account_id, spender, timestamp_range)
                      values (60, 2, 1001, 5, '[9, 10)');
                    insert into token_allowance (amount, owner, payer_account_id, spender, timestamp_range, token_id)
                      values (100, 2, 1001, 5, '[4,)', 101), (500, 2, 1001, 6, '[15,)', 102);
                    insert into token_allowance_history(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                      values (10, 2, 1001, 5, '[1,2)', 101), (20, 2, 1001, 5, '[2,4)', 101);
                    """;

    private static final String PRE_MIGRATION_TOKEN_TRANSFERS = """
                    insert into token_transfer (account_id, amount, consensus_timestamp, is_approval, payer_account_id, token_id)
                    values
                      (2, -10, 2, true, 5, 101), -- older than current grant
                      (2, -10, 5, true, 5, 101),
                      (2, -40, 6, true, 5, 101),
                      (2, -50, 7, false, 5, 101), -- not approved
                      (2, -150, 16, true, 6, 102),
                      (2, -200, 17, true, 22, 102); -- some other spender
                    """;

    private static final String REVERT_DDL = """
                    alter table crypto_allowance drop column amount;
                    alter table crypto_allowance rename column amount_granted to amount;
                    alter table crypto_allowance_history drop column amount;
                    alter table crypto_allowance_history rename column amount_granted to amount;
                    alter table token_allowance drop column amount;
                    alter table token_allowance rename column amount_granted to amount;
                    alter table token_allowance_history drop column amount;
                    alter table token_allowance_history rename column amount_granted to amount;
                    """;

    private final CryptoAllowanceRepository cryptoAllowanceRepository;

    @Value("classpath:db/migration/v1/V1.84.2__allowance_amount.sql")
    private final Resource migrationSql;

    private final TokenAllowanceRepository tokenAllowanceRepository;

    @AfterEach
    void teardown() {
        ownerJdbcTemplate.update(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();

        assertThat(cryptoAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
        assertThat(tokenAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(TokenAllowance.class)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, 100, 500
            true, 50, 350
            """)
    void migrate(boolean hasTokenTransfers, long expectedAmount1, long expectedAmount2) {
        jdbcOperations.update(PRE_MIGRATION_ALLOWANCES);
        if (hasTokenTransfers) {
            jdbcOperations.update(PRE_MIGRATION_TOKEN_TRANSFERS);
        }

        runMigration();

        var cryptoAllowance1 = CryptoAllowance.builder()
                .amount(50)
                .amountGranted(50L)
                .owner(2)
                .payerAccountId(PAYER)
                .spender(5)
                .timestampRange(Range.atLeast(10L))
                .build();
        var cryptoAllowance2 = CryptoAllowance.builder()
                .amountGranted(0L)
                .owner(2)
                .payerAccountId(PAYER)
                .spender(6)
                .timestampRange(Range.atLeast(11L))
                .build();
        var cryptoAllowanceHistory = CryptoAllowance.builder()
                .amountGranted(60L)
                .owner(2)
                .payerAccountId(PAYER)
                .spender(5)
                .timestampRange(Range.closedOpen(9L, 10L))
                .build();
        var tokenAllowance1 = TokenAllowance.builder()
                .amount(expectedAmount1)
                .amountGranted(100L)
                .owner(2)
                .payerAccountId(PAYER)
                .spender(5)
                .timestampRange(Range.atLeast(4L))
                .tokenId(101)
                .build();
        var tokenAllowance2 = TokenAllowance.builder()
                .amount(expectedAmount2)
                .amountGranted(500L)
                .owner(2)
                .payerAccountId(PAYER)
                .spender(6L)
                .tokenId(102)
                .timestampRange(Range.atLeast(15L))
                .build();
        var tokenAllowanceHistory1 = tokenAllowance1.toBuilder()
                .amount(0)
                .amountGranted(10L)
                .timestampRange(Range.closedOpen(1L, 2L))
                .build();
        var tokenAllowanceHistory2 = tokenAllowance1.toBuilder()
                .amount(0)
                .amountGranted(20L)
                .timestampRange(Range.closedOpen(2L, 4L))
                .build();

        assertThat(cryptoAllowanceRepository.findAll()).containsExactlyInAnyOrder(cryptoAllowance1, cryptoAllowance2);
        assertThat(findHistory(CryptoAllowance.class)).containsExactly(cryptoAllowanceHistory);

        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrder(tokenAllowance1, tokenAllowance2);
        assertThat(findHistory(TokenAllowance.class))
                .containsExactlyInAnyOrder(tokenAllowanceHistory1, tokenAllowanceHistory2);
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            ownerJdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }
}
