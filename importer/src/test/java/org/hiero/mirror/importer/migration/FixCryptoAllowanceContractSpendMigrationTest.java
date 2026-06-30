// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.CryptoAllowanceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@RequiredArgsConstructor
@Tag("migration")
@DisableRepeatableSqlMigration
@ContextConfiguration(initializers = FixCryptoAllowanceContractSpendMigrationTest.Initializer.class)
class FixCryptoAllowanceContractSpendMigrationTest extends ImporterIntegrationTest {

    private final CryptoAllowanceRepository cryptoAllowanceRepository;

    @Test
    void empty() {
        runMigration();
        assertThat(cryptoAllowanceRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        final var allowance = domainBuilder
                .cryptoAllowance()
                .customize(a -> a.amount(1000).amountGranted(1000L))
                .persist();
        final long owner = allowance.getOwner();
        final long spender = allowance.getSpender(); // the contract
        final var relayer = domainBuilder.entityId(); // the EOA that submitted the contract call

        // Contract-initiated approved spend, must be subtracted.
        final long contractSpendTimestamp = allowance.getTimestampLower() + 10;
        persistApprovedTransfer(owner, relayer.getId(), -100, contractSpendTimestamp);
        persistContractResult(spender, contractSpendTimestamp);

        // Already attributed to the spender by the importer (payer == spender), must not be subtracted again.
        final long alreadyTrackedTimestamp = allowance.getTimestampLower() + 20;
        persistApprovedTransfer(owner, spender, -50, alreadyTrackedTimestamp);
        persistContractResult(spender, alreadyTrackedTimestamp);

        // A contract-initiated spend before the allowance was granted must not be subtracted.
        final long beforeGrantTimestamp = allowance.getTimestampLower() - 10;
        persistApprovedTransfer(owner, relayer.getId(), -77, beforeGrantTimestamp);
        persistContractResult(spender, beforeGrantTimestamp);

        // A contract-initiated spend against a different owner must not affect this allowance.
        persistApprovedTransfer(domainBuilder.entityId().getId(), relayer.getId(), -33, contractSpendTimestamp + 1);
        persistContractResult(spender, contractSpendTimestamp + 1);

        // when
        runMigration();

        // then
        // Only the -100 contract-initiated spend is applied: 1000 - 100 = 900
        assertThat(cryptoAllowanceRepository.findById(allowance.getId()))
                .get()
                .returns(900L, CryptoAllowance::getAmount)
                .returns(1000L, CryptoAllowance::getAmountGranted);
    }

    private void persistApprovedTransfer(long owner, long payer, long amount, long consensusTimestamp) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.entityId(owner)
                        .payerAccountId(EntityId.of(payer))
                        .amount(amount)
                        .isApproval(true)
                        .consensusTimestamp(consensusTimestamp))
                .persist();
    }

    private void persistContractResult(long senderId, long consensusTimestamp) {
        domainBuilder
                .contractResult()
                .customize(cr -> cr.senderId(EntityId.of(senderId)).consensusTimestamp(consensusTimestamp))
                .persist();
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath = isV1()
                ? "v1/V1.126.0__fix_crypto_allowance_contract_spend.sql"
                : "v2/V2.31.0__fix_crypto_allowance_contract_spend.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.30.1" : "1.125.1";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
