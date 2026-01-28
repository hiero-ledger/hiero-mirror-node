// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.primitives.Longs;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.DomainBuilder;
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

@Tag("migration")
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@ContextConfiguration(initializers = UpdateSyntheticContractLogsMigrationTest.Initializer.class)
final class UpdateSyntheticContractLogsMigrationTest extends ImporterIntegrationTest {

    @Resource
    private ContractLogRepository contractLogRepository;

    @Resource
    private DomainBuilder domainBuilder;

    @Test
    void emptyDatabase() {
        runMigration();
        assertEquals(0, contractLogRepository.count());
    }

    @Test
    void migrate() {
        var sender1 = domainBuilder.entity().persist();
        var sender2 = domainBuilder.entity().persist();
        var sender3 = domainBuilder.entity().persist();
        var receiver1 = domainBuilder.entity().persist();
        var receiver2 = domainBuilder.entity().persist();
        var receiver3 = domainBuilder.entity().persist();

        var contractLogWithLongZero = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(sender1.getNum()))
                        .topic2(trim(Longs.toByteArray(receiver1.getNum()))))
                .persist();
        var contractLogWithSenderEvmReceiverLongZero = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(trim(sender2.getEvmAddress()))
                        .topic2(Longs.toByteArray(receiver2.getNum())))
                .persist();

        var nonTransferContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic1(trim(Longs.toByteArray(sender3.getNum())))
                        .topic2(trim(Longs.toByteArray(receiver3.getNum()))))
                .persist();

        var contractLogWithEmptySender = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(trim(new byte[0]))
                        .topic2(Longs.toByteArray(receiver3.getNum())))
                .persist();

        var transferContractLogMissingEntity = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(trim(Longs.toByteArray(Long.MAX_VALUE)))
                        .topic2(trim(Longs.toByteArray(Long.MAX_VALUE))))
                .persist();

        runMigration();

        contractLogWithLongZero.setTopic1(sender1.getEvmAddress());
        contractLogWithLongZero.setTopic2(receiver1.getEvmAddress());

        contractLogWithSenderEvmReceiverLongZero.setTopic2(receiver2.getEvmAddress());

        contractLogWithEmptySender.setTopic2(receiver3.getEvmAddress());

        assertThat(contractLogRepository.findAll())
                .containsExactlyInAnyOrder(
                        contractLogWithLongZero,
                        contractLogWithSenderEvmReceiverLongZero,
                        nonTransferContractLog,
                        contractLogWithEmptySender,
                        transferContractLogMissingEntity);
    }

    @SneakyThrows
    private void runMigration() {
        String migrationFilepath = isV1()
                ? "v1/V1.110.0__fix_transfer_synthetic_contract_logs.sql"
                : "v2/V2.15.0__fix_transfer_synthetic_contract_logs.sql";
        var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.14.0" : "1.109.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
