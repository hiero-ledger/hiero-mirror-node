// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.51.1")
class ContractLogsConvertTopicsToBytesMigrationTest extends ImporterIntegrationTest {

    @Value("classpath:db/migration/v1/V1.51.2__contract_logs_convert_topics_to_bytes.sql")
    private final File migrationSql;

    @AfterEach
    void cleanup() {
        revertMigration();
    }

    @Test
    void verifyConvertTopicsToBytesMigrationEmpty() throws Exception {
        migrate();
        assertThat(retrieveContractLogs()).isEmpty();
    }

    @Test
    void verifyConvertTopicsToBytesMigration() throws Exception {

        MigrationContractLog contractLogWithTopics = contractLog(1, 1, 0, "00", "aa", "bb", "cc");
        persistContractLog(Arrays.asList(contractLogWithTopics, contractLog(2, 2, 1, null, null, null, null)));
        // migration
        migrate();

        List<MigrationContractLog> contractLogs = retrieveContractLogs();
        assertAll(
                () -> assertEquals(2, contractLogs.size()),
                () -> assertEquals(1, contractLogs.get(0).consensusTimestamp),
                () -> assertArrayEquals(new byte[] {0}, contractLogs.get(0).getTopic0Bytes()),
                () -> assertArrayEquals(new byte[] {-86}, contractLogs.get(0).getTopic1Bytes()),
                () -> assertArrayEquals(new byte[] {-69}, contractLogs.get(0).getTopic2Bytes()),
                () -> assertArrayEquals(new byte[] {-52}, contractLogs.get(0).getTopic3Bytes()),
                () -> assertEquals(2, contractLogs.get(1).consensusTimestamp),
                () -> assertNull(contractLogs.get(1).getTopic0Bytes()),
                () -> assertNull(contractLogs.get(1).getTopic1Bytes()),
                () -> assertNull(contractLogs.get(1).getTopic2Bytes()),
                () -> assertNull(contractLogs.get(1).getTopic3Bytes()));
    }

    private MigrationContractLog contractLog(
            long consensusTimestamp,
            long contractId,
            int index,
            String topic0,
            String topic1,
            String topic2,
            String topic3) {
        MigrationContractLog migrationContractLog = new MigrationContractLog();
        migrationContractLog.setConsensusTimestamp(consensusTimestamp);
        migrationContractLog.setContractId(contractId);
        migrationContractLog.setIndex(index);
        migrationContractLog.setTopic0(topic0);
        migrationContractLog.setTopic1(topic1);
        migrationContractLog.setTopic2(topic2);
        migrationContractLog.setTopic3(topic3);
        return migrationContractLog;
    }

    private void migrate() throws Exception {
        ownerJdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private List<MigrationContractLog> retrieveContractLogs() {
        return jdbcOperations.query(
                "select consensus_timestamp, topic0 as topic0_bytes, topic1 as topic1_bytes, " + "topic2 as "
                        + "topic2_bytes, topic3 as topic3_bytes from contract_log order by consensus_timestamp asc",
                new BeanPropertyRowMapper<>(MigrationContractLog.class));
    }

    private void persistContractLog(List<MigrationContractLog> contractLogs) {
        for (MigrationContractLog contractLog : contractLogs) {
            jdbcOperations.update(
                    "insert into contract_log (bloom, consensus_timestamp, contract_id, data, "
                            + "index, payer_account_id, topic0, topic1, topic2, topic3) "
                            + " values"
                            + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    contractLog.getBloom(),
                    contractLog.getConsensusTimestamp(),
                    contractLog.getContractId(),
                    contractLog.getData(),
                    contractLog.getIndex(),
                    contractLog.getPayerAccountId(),
                    contractLog.getTopic0(),
                    contractLog.getTopic1(),
                    contractLog.getTopic2(),
                    contractLog.getTopic3());
        }
    }

    private void revertMigration() {
        ownerJdbcTemplate.execute("""
            alter table contract_log alter column topic0 type varchar(64) using encode(topic0, 'hex');
            alter table contract_log alter column topic1 type varchar(64) using encode(topic1, 'hex');
            alter table contract_log alter column topic2 type varchar(64) using encode(topic2, 'hex');
            alter table contract_log alter column topic3 type varchar(64) using encode(topic3, 'hex');
            """);
    }

    @Data
    @NoArgsConstructor
    private static class MigrationContractLog {
        private final byte[] bloom = new byte[] {2, 2};
        private long consensusTimestamp;
        private long contractId;
        private final byte[] data = new byte[] {2, 2};
        private int index = 0;
        private final long payerAccountId = 100;
        private String topic0;
        private String topic1;
        private String topic2;
        private String topic3;
        private byte[] topic0Bytes;
        private byte[] topic1Bytes;
        private byte[] topic2Bytes;
        private byte[] topic3Bytes;
    }
}
