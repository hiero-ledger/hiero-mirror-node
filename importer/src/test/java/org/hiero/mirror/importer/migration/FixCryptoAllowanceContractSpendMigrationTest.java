// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
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
final class FixCryptoAllowanceContractSpendMigrationTest
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
                environment,
                new ImporterProperties(),
                dbProperties,
                entityProps,
                new SystemEntity(CommonProperties.getInstance()),
                objectProvider(ownerJdbcTemplate));
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
    void nothingToBackfillDoesNotLeaveProgressTable() {
        // given an allowance but no HTS contract-spend history to backfill from
        domainBuilder
                .cryptoAllowance()
                .customize(a -> a.amount(1000).amountGranted(1000L))
                .persist();

        // when
        runMigration();

        // then
        waitForCompletion();
        assertThat(tableExists(PROGRESS_TABLE)).isFalse();
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
        waitForCompletion();
        // The progress table is dropped once the backfill runs to completion
        assertThat(tableExists(PROGRESS_TABLE)).isFalse();
        // Only the -100 contract-initiated spend is applied: 1000 - 100 = 900
        assertThat(cryptoAllowanceRepository.findById(allowance.getId()))
                .get()
                .returns(900L, CryptoAllowance::getAmount)
                .returns(1000L, CryptoAllowance::getAmountGranted);
    }

    @Test
    void migrateReversesWronglyDebitedRelayerAllowance() {
        // given
        // owner granted an allowance to a contract that the old bug never debited (still at full granted amount)
        final var contractAllowance = domainBuilder
                .cryptoAllowance()
                .customize(a -> a.amount(1000).amountGranted(1000L))
                .persist();
        final long owner = contractAllowance.getOwner();
        final long contract = contractAllowance.getSpender();

        // owner also granted a genuine allowance to the EOA that relays the contract call; under the old bug the
        // contract-relayed spend was wrongly debited from this allowance, leaving it at 900 instead of 1000
        final var relayerAllowance = domainBuilder
                .cryptoAllowance()
                .customize(a -> a.owner(owner)
                        .amount(900)
                        .amountGranted(1000L)
                        .timestampRange(Range.atLeast(contractAllowance.getTimestampLower())))
                .persist();
        final long relayer = relayerAllowance.getSpender();

        // Contract-relayed approved spend: payer is the relayer EOA, sender_id is the contract
        final long spendTimestamp = contractAllowance.getTimestampLower() + 10;
        persistApprovedTransfer(owner, relayer, -100, spendTimestamp);
        persistContractResult(contract, spendTimestamp);

        // when
        runMigration();

        // then
        waitForCompletion();
        // The contract allowance gets the missed debit applied: 1000 - 100 = 900
        assertThat(cryptoAllowanceRepository.findById(contractAllowance.getId()))
                .get()
                .returns(900L, CryptoAllowance::getAmount)
                .returns(1000L, CryptoAllowance::getAmountGranted);
        // The relayer allowance gets the wrong debit reversed: 900 + 100 = 1000
        assertThat(cryptoAllowanceRepository.findById(relayerAllowance.getId()))
                .get()
                .returns(1000L, CryptoAllowance::getAmount)
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
                .customize(cr ->
                        cr.contractId(0x167).senderId(EntityId.of(senderId)).consensusTimestamp(consensusTimestamp))
                .persist();
    }
}
