// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.migration.ContractResultMigration.MigrationContractLog;
import static org.hiero.mirror.importer.migration.ContractResultMigration.MigrationContractResult;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import jakarta.annotation.Resource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.7")
class ContractResultMigrationTest extends ImporterIntegrationTest {

    private final RowMapper<MigrationContractLog> logsRowMapper = new DataClassRowMapper<>(MigrationContractLog.class);
    private long id = 0;

    @Resource
    private JdbcOperations jdbcOperations;

    @Resource
    private ContractResultMigration contractResultMigration;

    @Test
    void migrateWhenEmpty() throws Exception {
        contractResultMigration.doMigrate();
        assertThat(getContractLogs()).isEmpty();
        assertThat(getContractResults()).isEmpty();
    }

    @Test
    void migrateWhenNoProtobufData() throws Exception {
        MigrationContractResult migrationContractResult1 = contractResult();
        migrationContractResult1.setFunctionResult(null);
        insert(migrationContractResult1);

        MigrationContractResult migrationContractResult2 = contractResult();
        migrationContractResult2.setFunctionResult(new byte[0]);
        insert(migrationContractResult2);

        MigrationContractResult migrationContractResult3 = contractResult();
        migrationContractResult3.setFunctionResult(new byte[] {0, 1, 2});
        insert(migrationContractResult3);

        contractResultMigration.doMigrate();

        assertThat(getContractLogs()).isEmpty();
        assertThat(getContractResults())
                .hasSize(3)
                .describedAs("Fields have not been migrated")
                .extracting(MigrationContractResult::getContractId)
                .allMatch(Objects::isNull);
    }

    @SuppressWarnings("deprecation")
    @Test
    void migrate() throws Exception {
        ContractFunctionResult.Builder functionResult = contractFunctionResult();
        MigrationContractResult contractResult = contractResult(functionResult);
        insert(contractResult);

        contractResultMigration.doMigrate();

        ContractLoginfo loginfo = functionResult.getLogInfo(0);
        assertThat(getContractLogs())
                .hasSize(1)
                .first()
                .returns(loginfo.getBloom().toByteArray(), MigrationContractLog::getBloom)
                .returns(contractResult.getConsensusTimestamp(), MigrationContractLog::getConsensusTimestamp)
                .returns(loginfo.getContractID().getContractNum(), MigrationContractLog::getContractId)
                .returns(0, MigrationContractLog::getIndex)
                .returns(Hex.encodeHexString(loginfo.getTopic(0).toByteArray()), MigrationContractLog::getTopic0)
                .returns(Hex.encodeHexString(loginfo.getTopic(1).toByteArray()), MigrationContractLog::getTopic1)
                .returns(Hex.encodeHexString(loginfo.getTopic(2).toByteArray()), MigrationContractLog::getTopic2)
                .returns(Hex.encodeHexString(loginfo.getTopic(3).toByteArray()), MigrationContractLog::getTopic3);

        assertThat(getContractResults())
                .hasSize(1)
                .first()
                .returns(functionResult.getBloom().toByteArray(), MigrationContractResult::getBloom)
                .returns(contractResult.getConsensusTimestamp(), MigrationContractResult::getConsensusTimestamp)
                .returns(functionResult.getContractCallResult().toByteArray(), MigrationContractResult::getCallResult)
                .returns(functionResult.getContractID().getContractNum(), MigrationContractResult::getContractId)
                .returns(functionResult.getErrorMessage(), MigrationContractResult::getErrorMessage)
                .returns(
                        functionResult.getCreatedContractIDsList().stream()
                                .map(EntityId::of)
                                .map(EntityId::getId)
                                .toArray(Long[]::new),
                        MigrationContractResult::getCreatedContractIds);
    }

    @SuppressWarnings("deprecation")
    @Test
    void migrateWhenEmptyContractIDs() throws Exception {
        ContractFunctionResult.Builder functionResult = contractFunctionResult()
                .clearContractID()
                .clearCreatedContractIDs()
                .addCreatedContractIDs(ContractID.getDefaultInstance());

        MigrationContractResult contractResult = contractResult(functionResult);
        insert(contractResult);

        contractResultMigration.doMigrate();

        assertThat(getContractResults())
                .hasSize(1)
                .first()
                .returns(null, MigrationContractResult::getContractId)
                .returns(new Long[] {null}, MigrationContractResult::getCreatedContractIds);
    }

    @Test
    void migrateWhenEmptyTopic() throws Exception {
        ContractFunctionResult.Builder functionResult = contractFunctionResult();
        functionResult.setLogInfo(
                0, functionResult.getLogInfoBuilder(0).clearTopic().addTopic(ByteString.copyFrom(new byte[0])));
        MigrationContractResult contractResult = contractResult(functionResult);
        insert(contractResult);

        contractResultMigration.doMigrate();

        assertThat(getContractLogs())
                .hasSize(1)
                .first()
                .returns("", MigrationContractLog::getTopic0)
                .returns(null, MigrationContractLog::getTopic1)
                .returns(null, MigrationContractLog::getTopic2)
                .returns(null, MigrationContractLog::getTopic3);
    }

    private MigrationContractResult contractResult() {
        return contractResult(contractFunctionResult());
    }

    private MigrationContractResult contractResult(ContractFunctionResult.Builder functionResult) {
        MigrationContractResult migrationContractResult = new MigrationContractResult();
        migrationContractResult.setConsensusTimestamp(++id);
        migrationContractResult.setFunctionParameters(new byte[] {6, 7, 8});
        migrationContractResult.setFunctionResult(functionResult.build().toByteArray());
        return migrationContractResult;
    }

    @SuppressWarnings("deprecation")
    private ContractFunctionResult.Builder contractFunctionResult() {
        long contractNum = ++id;
        ContractID contractID =
                ContractID.newBuilder().setContractNum(contractNum).build();

        return ContractFunctionResult.newBuilder()
                .setBloom(ByteString.copyFrom(new byte[] {0, 1}))
                .setContractCallResult(ByteString.copyFrom(new byte[] {2, 3}))
                .setContractID(contractID)
                .addCreatedContractIDs(
                        ContractID.newBuilder().setContractNum(contractNum + 1).build())
                .setErrorMessage("")
                .setGasUsed(100L)
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setBloom(ByteString.copyFrom(new byte[] {4, 5}))
                        .setContractID(contractID)
                        .setData(ByteString.copyFrom(new byte[] {6, 7}))
                        .addTopic(ByteString.copyFrom(new byte[] {0}))
                        .addTopic(ByteString.copyFrom(new byte[] {1}))
                        .addTopic(ByteString.copyFrom(new byte[] {2}))
                        .addTopic(ByteString.copyFrom(new byte[] {3}))
                        .build());
    }

    private void insert(MigrationContractResult cr) {
        jdbcOperations.update(c -> {
            PreparedStatement preparedStatement = c.prepareStatement("insert into contract_result "
                    + "(bloom, call_result, consensus_timestamp, contract_id, created_contract_ids, error_message, "
                    + "function_parameters, function_result, gas_limit, gas_used) "
                    + "values (?, ?, ?, ?, ?, ?, ?, ?, 100, 100)");
            preparedStatement.setBytes(1, cr.getBloom());
            preparedStatement.setBytes(2, cr.getCallResult());
            preparedStatement.setLong(3, cr.getConsensusTimestamp());
            preparedStatement.setObject(4, cr.getContractId());
            preparedStatement.setArray(5, c.createArrayOf("bigint", cr.getCreatedContractIds()));
            preparedStatement.setObject(6, cr.getErrorMessage());
            preparedStatement.setBytes(7, cr.getFunctionParameters());
            preparedStatement.setBytes(8, cr.getFunctionResult());
            return preparedStatement;
        });
    }

    private List<MigrationContractResult> getContractResults() {
        return jdbcOperations.query("select * from contract_result", ContractResultMigration.resultRowMapper);
    }

    private List<MigrationContractLog> getContractLogs() {
        return jdbcOperations.query("select * from contract_log", logsRowMapper);
    }
}
