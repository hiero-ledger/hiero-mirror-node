// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;

import com.google.common.primitives.Longs;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;

@Tag("migration")
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@ContextConfiguration(initializers = UpdateSyntheticContractLogsMigrationTest.Initializer.class)
final class UpdateSyntheticContractLogsMigrationTest extends ImporterIntegrationTest {

    @Resource
    private DomainBuilder domainBuilder;

    @Test
    void emptyDatabase() {
        runMigration();
        assertThat(count("contract_log")).isZero();
    }

    @Test
    void migrate() {
        final var sender1 = domainBuilder.entity().get();
        persistEntity(sender1);

        final var sender2 = domainBuilder.entity().get();
        persistEntity(sender2);

        final var sender3 = domainBuilder.entity().get();
        persistEntity(sender3);

        final var receiver1 = domainBuilder.entity().get();
        persistEntity(receiver1);

        final var receiver2 = domainBuilder.entity().get();
        persistEntity(receiver2);

        final var receiver3 = domainBuilder.entity().get();
        persistEntity(receiver3);

        final var contractLogWithLongZero = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(sender1.getNum()))
                        .topic2(trim(Longs.toByteArray(receiver1.getNum()))))
                .get();
        persistContractLog(contractLogWithLongZero);

        final var contractLogWithSenderEvmReceiverLongZero = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(trim(sender2.getEvmAddress()))
                        .topic2(Longs.toByteArray(receiver2.getNum())))
                .get();
        persistContractLog(contractLogWithSenderEvmReceiverLongZero);

        final var nonTransferContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic1(trim(Longs.toByteArray(sender3.getNum())))
                        .topic2(trim(Longs.toByteArray(receiver3.getNum()))))
                .get();
        persistContractLog(nonTransferContractLog);

        final var contractLogWithEmptySender = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(trim(new byte[0]))
                        .topic2(Longs.toByteArray(receiver3.getNum())))
                .get();
        persistContractLog(contractLogWithEmptySender);

        final var transferContractLogMissingEntity = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(trim(Longs.toByteArray(Long.MAX_VALUE)))
                        .topic2(trim(Longs.toByteArray(Long.MAX_VALUE))))
                .get();
        persistContractLog(transferContractLogMissingEntity);

        runMigration();

        contractLogWithLongZero.setTopic1(sender1.getEvmAddress());
        contractLogWithLongZero.setTopic2(receiver1.getEvmAddress());

        contractLogWithSenderEvmReceiverLongZero.setTopic2(receiver2.getEvmAddress());

        contractLogWithEmptySender.setTopic2(receiver3.getEvmAddress());

        assertThat(findAllContractLogs())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("synthetic")
                .containsExactlyInAnyOrder(
                        contractLogWithLongZero,
                        contractLogWithSenderEvmReceiverLongZero,
                        nonTransferContractLog,
                        contractLogWithEmptySender,
                        transferContractLogMissingEntity);
    }

    private long count(String table) {
        return jdbcOperations.queryForObject("select count(*) from " + table, Long.class);
    }

    private List<ContractLog> findAllContractLogs() {
        return jdbcOperations.query("select * from contract_log", contractLogRowMapper);
    }

    private void persistEntity(Entity entity) {
        jdbcOperations.update(
                "insert into entity (id, num, realm, shard, created_timestamp, timestamp_range, type, evm_address, alias) "
                        + "values (?, ?, ?, ?, ?, ?::int8range, ?::entity_type, ?, ?)",
                entity.getId(),
                entity.getNum(),
                entity.getRealm(),
                entity.getShard(),
                entity.getCreatedTimestamp(),
                io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType.INSTANCE.asString(
                        entity.getTimestampRange()),
                entity.getType().name(),
                entity.getEvmAddress(),
                entity.getAlias());
    }

    private void persistContractLog(ContractLog contractLog) {
        jdbcOperations.update(
                "insert into contract_log (bloom, consensus_timestamp, contract_id, data, index, "
                        + "payer_account_id, root_contract_id, topic0, topic1, topic2, topic3, "
                        + "transaction_hash, transaction_index) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                contractLog.getBloom(),
                contractLog.getConsensusTimestamp(),
                contractLog.getContractId().getId(),
                contractLog.getData(),
                contractLog.getIndex(),
                contractLog.getPayerAccountId() != null
                        ? contractLog.getPayerAccountId().getId()
                        : null,
                contractLog.getRootContractId() != null
                        ? contractLog.getRootContractId().getId()
                        : null,
                contractLog.getTopic0(),
                contractLog.getTopic1(),
                contractLog.getTopic2(),
                contractLog.getTopic3(),
                contractLog.getTransactionHash(),
                contractLog.getTransactionIndex());
    }

    private final RowMapper<ContractLog> contractLogRowMapper = (rs, rowNum) -> {
        var contractLog = new ContractLog();
        contractLog.setBloom(rs.getBytes("bloom"));
        contractLog.setConsensusTimestamp(rs.getLong("consensus_timestamp"));
        contractLog.setContractId(EntityId.of(rs.getLong("contract_id")));
        contractLog.setData(rs.getBytes("data"));
        contractLog.setIndex(rs.getInt("index"));
        long payerAccountId = rs.getLong("payer_account_id");
        contractLog.setPayerAccountId(rs.wasNull() ? null : EntityId.of(payerAccountId));
        long rootContractId = rs.getLong("root_contract_id");
        contractLog.setRootContractId(rs.wasNull() ? null : EntityId.of(rootContractId));
        contractLog.setTopic0(rs.getBytes("topic0"));
        contractLog.setTopic1(rs.getBytes("topic1"));
        contractLog.setTopic2(rs.getBytes("topic2"));
        contractLog.setTopic3(rs.getBytes("topic3"));
        contractLog.setTransactionHash(rs.getBytes("transaction_hash"));
        contractLog.setTransactionIndex(rs.getInt("transaction_index"));
        return contractLog;
    };

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath = isV1()
                ? "v1/V1.110.0__fix_transfer_synthetic_contract_logs.sql"
                : "v2/V2.15.0__fix_transfer_synthetic_contract_logs.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            final var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.14.0" : "1.109.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
