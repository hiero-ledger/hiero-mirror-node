// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = BackfillEthereumContractInitcodeMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class BackfillEthereumContractInitcodeMigrationTest extends ImporterIntegrationTest {

    private static final int CONTRACT_CREATE = TransactionType.CONTRACTCREATEINSTANCE.getProtoId();
    private static final int ETHEREUM_TRANSACTION = TransactionType.ETHEREUMTRANSACTION.getProtoId();

    @Test
    void empty() {
        runMigration();
        assertThat(countContracts()).isZero();
    }

    @Test
    void backfillsNullInitcodeFromCallData() {
        final var callData = domainBuilder.bytes(32);
        final var contractId = persistEthereumCreatedContract(null, callData, ETHEREUM_TRANSACTION);

        runMigration();

        assertThat(findInitcode(contractId)).isEqualTo(callData);
    }

    @Test
    void backfillsEmptyInitcodeFromCallData() {
        final var callData = domainBuilder.bytes(16);
        final var contractId = persistEthereumCreatedContract(new byte[0], callData, ETHEREUM_TRANSACTION);

        runMigration();

        assertThat(findInitcode(contractId)).isEqualTo(callData);
    }

    @Test
    void leavesExistingInitcodeUnchanged() {
        final var existing = domainBuilder.bytes(8);
        final var contractId = persistEthereumCreatedContract(existing, domainBuilder.bytes(32), ETHEREUM_TRANSACTION);

        runMigration();

        assertThat(findInitcode(contractId)).isEqualTo(existing);
    }

    @Test
    void leavesNonEthereumParentUnchanged() {
        final var contractId = persistEthereumCreatedContract(
                null, domainBuilder.bytes(32), TransactionType.CONTRACTCALL.getProtoId());

        runMigration();

        assertThat(findInitcode(contractId)).isNull();
    }

    @Test
    void leavesEmptyCallDataUnchanged() {
        final var contractId = persistEthereumCreatedContract(null, new byte[0], ETHEREUM_TRANSACTION);

        runMigration();

        assertThat(findInitcode(contractId)).isNull();
    }

    @Test
    void isIdempotent() {
        final var callData = domainBuilder.bytes(24);
        final var contractId = persistEthereumCreatedContract(null, callData, ETHEREUM_TRANSACTION);

        runMigration();
        final var afterFirstRun = findInitcode(contractId);
        runMigration();

        assertThat(afterFirstRun).isEqualTo(callData);
        assertThat(findInitcode(contractId)).isEqualTo(afterFirstRun);
    }

    private long persistEthereumCreatedContract(byte[] initcode, byte[] callData, int parentType) {
        final long createdTimestamp = domainBuilder.timestamp();
        final long parentTimestamp = domainBuilder.timestamp();
        final var entity =
                domainBuilder.entity(domainBuilder.entityId(), createdTimestamp).persist();
        final var contractId = entity.getId();

        domainBuilder
                .contract()
                .customize(c -> c.fileId(null).id(contractId).initcode(initcode))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(parentTimestamp)
                        .entityId(EntityId.of(contractId))
                        .parentConsensusTimestamp(null)
                        .type(parentType))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(createdTimestamp)
                        .entityId(EntityId.of(contractId))
                        .parentConsensusTimestamp(parentTimestamp)
                        .type(CONTRACT_CREATE))
                .persist();
        if (parentType == ETHEREUM_TRANSACTION) {
            domainBuilder
                    .ethereumTransaction(true)
                    .customize(e -> e.callData(callData).callDataId(null).consensusTimestamp(parentTimestamp))
                    .persist();
        }
        return contractId;
    }

    private long countContracts() {
        return jdbcOperations.queryForObject("select count(*) from contract", Long.class);
    }

    private byte[] findInitcode(long contractId) {
        return jdbcOperations.queryForObject("select initcode from contract where id = ?", byte[].class, contractId);
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath = isV1()
                ? "v1/V1.129.0__backfill_ethereum_contract_initcode.sql"
                : "v2/V2.34.0__backfill_ethereum_contract_initcode.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.update(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            final var environment = configurableApplicationContext.getEnvironment();
            final var version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.33.0" : "1.128.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
