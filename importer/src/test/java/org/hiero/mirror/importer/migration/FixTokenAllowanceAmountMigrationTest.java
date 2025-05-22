// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.migration.GasConsumedMigrationTest.createMigrationContractResult;
import static org.hiero.mirror.importer.migration.GasConsumedMigrationTest.persistMigrationContractResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenTransfer.Id;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.TokenAllowanceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.87.0")
class FixTokenAllowanceAmountMigrationTest extends ImporterIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:db/migration/v1/V1.87.1__fix_token_allowance_amount.sql")
    private final Resource sql;

    private final TransactionTemplate transactionTemplate;

    private final TokenAllowanceRepository tokenAllowanceRepository;

    @Test
    void empty() {
        runMigration();
        assertThat(tokenAllowanceRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var owner1 = EntityId.of(domainBuilder.id());
        var spender1 = EntityId.of(domainBuilder.id());
        var spender2 = EntityId.of(domainBuilder.id());
        var token1 = EntityId.of(domainBuilder.id());
        var token2 = EntityId.of(domainBuilder.id());

        // A token transfer which uses history token allowance
        long transferTimestamp1 = domainBuilder.timestamp();
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-100)
                        .id(new Id(transferTimestamp1, token1, owner1))
                        .isApproval(true)
                        .payerAccountId(owner1))
                .persist();

        var migrationContractResult1 = createMigrationContractResult(
                transferTimestamp1, spender1, domainBuilder.entityId().getId(), null, domainBuilder);
        persistMigrationContractResult(migrationContractResult1, jdbcTemplate);

        // Token1 allowances granted by owner1, note the first token allowance's amount didn't change while the
        // self grant has a negative balance both due to the bug
        var tokenAllowance1 = domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.amount(4000)
                        .amountGranted(4000L)
                        .owner(owner1.getId())
                        .spender(spender1.getId())
                        .tokenId(token1.getId()))
                .persist();
        // Note tokenAllowance2's remaining amount is negative since the erc20 token transfer was incorrectly applied
        var tokenAllowance2 = domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.amount(-1000)
                        .amount(2000L)
                        .owner(owner1.getId())
                        .spender(owner1.getId())
                        .tokenId(token1.getId()))
                .persist();
        long transferTimestamp2 = domainBuilder.timestamp();
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-3000L)
                        .id(new Id(transferTimestamp2, token1, owner1))
                        .isApproval(true)
                        .payerAccountId(owner1))
                .persist();

        var migrationContractResult2 = createMigrationContractResult(
                transferTimestamp2, spender1, domainBuilder.entityId().getId(), null, domainBuilder);
        persistMigrationContractResult(migrationContractResult2, jdbcTemplate);

        // Token2 allowance granted by owner1 to spender2
        var tokenAllowance3 = domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.amount(4000)
                        .amountGranted(5000L)
                        .owner(owner1.getId())
                        .spender(spender2.getId())
                        .tokenId(token2.getId()))
                .persist();
        long transferTimestamp3 = domainBuilder.timestamp();
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-4100L)
                        .id(new Id(transferTimestamp3, token2, owner1))
                        .isApproval(true)
                        .payerAccountId(owner1))
                .persist();

        var migrationContractResult3 = createMigrationContractResult(
                transferTimestamp3, spender2, domainBuilder.entityId().getId(), null, domainBuilder);
        persistMigrationContractResult(migrationContractResult3, jdbcTemplate);
        // A normal token transfer using token allowance, note due to the inaccuracy in how we mark a transfer as
        // is_approval, the aggregated spent amount is 1000 + 4100 > granted amount 5000, the migration should have a
        // safeguard to set remaining amount to 0
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-1000)
                        .id(new Id(domainBuilder.timestamp(), token2, owner1))
                        .isApproval(true)
                        .payerAccountId(spender2))
                .persist();

        // Token1 allowance granted by owner1 to spender2, it shouldn't be touched by the migration at all
        var tokenAllowance4 = domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.amount(2500)
                        .amountGranted(3000L)
                        .owner(owner1.getId())
                        .spender(spender2.getId())
                        .tokenId(token1.getId()))
                .persist();
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-500)
                        .id(new Id(domainBuilder.timestamp(), token1, owner1))
                        .isApproval(true)
                        .payerAccountId(spender2))
                .persist();

        // when
        runMigration();

        // then
        tokenAllowance1.setAmount(1000L);
        tokenAllowance2.setAmount(tokenAllowance2.getAmountGranted());
        tokenAllowance3.setAmount(0);
        assertThat(tokenAllowanceRepository.findAll())
                .containsExactlyInAnyOrder(tokenAllowance1, tokenAllowance2, tokenAllowance3, tokenAllowance4);
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = sql.getInputStream()) {
            transactionTemplate.executeWithoutResult(s -> {
                try {
                    jdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
