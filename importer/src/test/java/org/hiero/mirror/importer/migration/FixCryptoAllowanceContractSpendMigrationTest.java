// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.CryptoAllowanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class FixCryptoAllowanceContractSpendMigrationTest
        extends AbstractAsyncJavaMigrationTest<FixCryptoAllowanceContractSpendMigration> {

    private static final String PROGRESS_TABLE = "crypto_allowance_contract_spend_progress";

    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final DBProperties dbProperties;
    private final EntityProperties entityProperties;
    private final Environment environment;

    private @Getter FixCryptoAllowanceContractSpendMigration migration;

    @BeforeEach
    void setup() {
        ownerJdbcTemplate.execute("drop table if exists " + PROGRESS_TABLE);
        migration = createMigration(entityProperties);
    }

    private FixCryptoAllowanceContractSpendMigration createMigration(EntityProperties entityProps) {
        return new FixCryptoAllowanceContractSpendMigration(
                environment, new ImporterProperties(), dbProperties, entityProps, objectProvider(ownerJdbcTemplate));
    }

    @Test
    void empty() {
        // given, when
        runMigration();

        // then
        waitForCompletion();
        assertThat(tableExists(PROGRESS_TABLE)).isFalse();
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

        // The record file establishes the processing frontier the async migration walks back from.
        domainBuilder
                .recordFile()
                .customize(rf -> rf.consensusEnd(allowance.getTimestampLower() + 1000))
                .persist();

        // when
        runMigration();

        // then
        waitForCompletion();
        // Only the -100 contract-initiated spend is applied: 1000 - 100 = 900
        assertThat(cryptoAllowanceRepository.findById(allowance.getId()))
                .get()
                .returns(900L, CryptoAllowance::getAmount)
                .returns(1000L, CryptoAllowance::getAmountGranted);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void skipMigration(boolean trackAllowance) {
        // given
        var entityProps = new EntityProperties(new SystemEntity(CommonProperties.getInstance()));
        entityProps.getPersist().setTrackAllowance(trackAllowance);
        var contractSpendMigration = createMigration(entityProps);
        var configuration = new FluentConfiguration().target(contractSpendMigration.getMinimumVersion());

        // when, then
        assertThat(contractSpendMigration.skipMigration(configuration)).isEqualTo(!trackAllowance);
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
}
