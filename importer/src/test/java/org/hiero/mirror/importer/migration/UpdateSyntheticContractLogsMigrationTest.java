// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.primitives.Bytes;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.shaded.com.google.common.primitives.Longs;

@Tag("migration")
public class UpdateSyntheticContractLogsMigrationTest extends ImporterIntegrationTest {
    @Owner
    @Resource
    private JdbcTemplate ownerJdbcTemplate;

    @Resource
    private ContractLogRepository contractLogRepository;

    @Resource
    private DomainBuilder domainBuilder;

    @Getter
    @Value("#{environment.matchesProfiles('!v2')}")
    private boolean v1;

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
                        .topic1(Bytes.concat(new byte[12], Longs.toByteArray(sender1.getNum())))
                        .topic2(Bytes.concat(new byte[12], Longs.toByteArray(receiver1.getNum()))))
                .persist();
        var contractLogWithSenderEvmReceiverLongZero = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(sender2.getEvmAddress())
                        .topic2(Longs.toByteArray(receiver2.getNum())))
                .persist();

        var nonTransferContractLog = domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.topic1(Longs.toByteArray(sender3.getNum())).topic2(Longs.toByteArray(receiver3.getNum())))
                .persist();

        var contractLogWithEmptySender = domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.topic0(TRANSFER_SIGNATURE).topic1(new byte[0]).topic2(Longs.toByteArray(receiver3.getNum())))
                .persist();
        runMigration();

        var transferContractLogMissingEntity = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(Long.MAX_VALUE))
                        .topic2(Longs.toByteArray(Long.MAX_VALUE)))
                .persist();

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
}
