// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FixNftTreasuryChangeLogsMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class FixNftTreasuryChangeLogsMigrationTest extends ImporterIntegrationTest {

    private static final byte[] WILDCARD_SERIAL_TOPIC3 =
            Bytes.fromHexString("ffffffffffffffff").toArray();

    private final ContractLogRepository contractLogRepository;

    @Test
    void empty() {
        runMigration();
        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void deletesNftTreasuryChangeLog() {
        // given
        var nftTokenId = persistToken(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.contractId(nftTokenId).topic0(TRANSFER_SIGNATURE).topic3(WILDCARD_SERIAL_TOPIC3))
                .persist();

        // when
        runMigration();

        // then
        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void keepsUnrelatedLogs() {
        // given
        var nftTokenId = persistToken(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        var fungibleTokenId = persistToken(TokenTypeEnum.FUNGIBLE_COMMON);

        var normalNftTransfer = domainBuilder
                .contractLog()
                .customize(cl -> cl.contractId(nftTokenId)
                        .topic0(TRANSFER_SIGNATURE)
                        .topic3(Bytes.fromHexString("01").toArray()))
                .persist();
        var nonTransferWildcard = domainBuilder
                .contractLog()
                .customize(cl -> cl.contractId(nftTokenId)
                        .topic0(domainBuilder.bytes(32))
                        .topic3(WILDCARD_SERIAL_TOPIC3))
                .persist();
        var fungibleWildcardTransfer = domainBuilder
                .contractLog()
                .customize(cl -> cl.contractId(fungibleTokenId)
                        .topic0(TRANSFER_SIGNATURE)
                        .topic3(WILDCARD_SERIAL_TOPIC3))
                .persist();
        var unknownContractWildcardTransfer = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE).topic3(WILDCARD_SERIAL_TOPIC3))
                .persist();
        domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.contractId(nftTokenId).topic0(TRANSFER_SIGNATURE).topic3(WILDCARD_SERIAL_TOPIC3))
                .persist();

        // when
        runMigration();

        // then
        assertThat(contractLogRepository.findAll())
                .containsExactlyInAnyOrder(
                        normalNftTransfer,
                        nonTransferWildcard,
                        fungibleWildcardTransfer,
                        unknownContractWildcardTransfer);
    }

    private EntityId persistToken(TokenTypeEnum type) {
        return EntityId.of(
                domainBuilder.token().customize(t -> t.type(type)).persist().getTokenId());
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath = isV1()
                ? "v1/V1.127.0__fix_nft_treasury_change_logs.sql"
                : "v2/V2.32.0__fix_nft_treasury_change_logs.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            final var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.31.0" : "1.126.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
