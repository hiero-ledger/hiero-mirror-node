// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;
import org.postgresql.jdbc.PgArray;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
@RequiredArgsConstructor()
public class ContractResultMigration extends AbstractJavaMigration {

    static final DataClassRowMapper<MigrationContractResult> resultRowMapper;
    private static final MigrationVersion VERSION = MigrationVersion.fromVersion("1.46.8");

    static {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(PgArray.class, Long[].class, ContractResultMigration::convert);
        resultRowMapper = new DataClassRowMapper<>(MigrationContractResult.class);
        resultRowMapper.setConversionService(defaultConversionService);
    }

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @SneakyThrows
    private static Long[] convert(PgArray pgArray) {
        return (Long[]) pgArray.getArray();
    }

    @Override
    public String getDescription() {
        return "Parses the protobuf function_result field and normalize it into separate database fields";
    }

    @Override
    public MigrationVersion getVersion() {
        return VERSION;
    }

    @Override
    protected void doMigrate() throws IOException {
        AtomicLong count = new AtomicLong(0L);
        Stopwatch stopwatch = Stopwatch.createStarted();

        jdbcTemplateProvider.getObject().setFetchSize(100);
        jdbcTemplateProvider
                .getObject()
                .query(
                        "select consensus_timestamp, function_result from contract_result "
                                + "order by consensus_timestamp asc",
                        rs -> {
                            MigrationContractResult contractResult = resultRowMapper.mapRow(rs, rs.getRow());
                            if (process(contractResult)) {
                                count.incrementAndGet();
                            }
                        });

        log.info("Updated {} contract results in {}", count, stopwatch);
    }

    @SuppressWarnings({"deprecation", "java:S1874"})
    private boolean process(MigrationContractResult contractResult) {
        long consensusTimestamp = contractResult.getConsensusTimestamp();

        try {
            byte[] functionResult = contractResult.getFunctionResult();
            if (functionResult == null || functionResult.length == 0) {
                return false;
            }

            ContractFunctionResult contractFunctionResult = ContractFunctionResult.parseFrom(functionResult);
            Long[] createdContractIds = new Long[contractFunctionResult.getCreatedContractIDsCount()];

            for (int i = 0; i < createdContractIds.length; ++i) {
                createdContractIds[i] = getContractId(contractFunctionResult.getCreatedContractIDs(i));
            }

            contractResult.setBloom(DomainUtils.toBytes(contractFunctionResult.getBloom()));
            contractResult.setCallResult(DomainUtils.toBytes(contractFunctionResult.getContractCallResult()));
            contractResult.setContractId(getContractId(contractFunctionResult.getContractID()));
            contractResult.setCreatedContractIds(createdContractIds);
            contractResult.setErrorMessage(contractFunctionResult.getErrorMessage());
            update(contractResult);

            for (int index = 0; index < contractFunctionResult.getLogInfoCount(); ++index) {
                ContractLoginfo contractLoginfo = contractFunctionResult.getLogInfo(index);

                MigrationContractLog migrationContractLog = new MigrationContractLog();
                migrationContractLog.setBloom(DomainUtils.toBytes(contractLoginfo.getBloom()));
                migrationContractLog.setConsensusTimestamp(consensusTimestamp);
                migrationContractLog.setContractId(getContractId(contractLoginfo.getContractID()));
                migrationContractLog.setData(DomainUtils.toBytes(contractLoginfo.getData()));
                migrationContractLog.setIndex(index);
                migrationContractLog.setTopic0(DomainUtils.bytesToHex(Utility.getTopic(contractLoginfo, 0)));
                migrationContractLog.setTopic1(DomainUtils.bytesToHex(Utility.getTopic(contractLoginfo, 1)));
                migrationContractLog.setTopic2(DomainUtils.bytesToHex(Utility.getTopic(contractLoginfo, 2)));
                migrationContractLog.setTopic3(DomainUtils.bytesToHex(Utility.getTopic(contractLoginfo, 3)));

                insert(migrationContractLog);
            }

            return true;
        } catch (Exception e) {
            log.warn("Unable to parse {} as ContractFunctionResult", consensusTimestamp, e);
        }

        return false;
    }

    @SuppressWarnings("deprecation")
    private Long getContractId(ContractID contractID) {
        EntityId entityId = EntityId.of(contractID);
        return !EntityId.isEmpty(entityId) ? entityId.getId() : null;
    }

    private void update(MigrationContractResult contractResult) {
        jdbcTemplateProvider
                .getObject()
                .update(
                        "update contract_result set bloom = ?, call_result = ?, contract_id = ?, "
                                + "created_contract_ids = ?, error_message = ? where consensus_timestamp = ?",
                        contractResult.getBloom(),
                        contractResult.getCallResult(),
                        contractResult.getContractId(),
                        contractResult.getCreatedContractIds(),
                        contractResult.getErrorMessage(),
                        contractResult.getConsensusTimestamp());
    }

    private void insert(MigrationContractLog contractLog) {
        jdbcTemplateProvider
                .getObject()
                .update(
                        "insert into contract_log (bloom, consensus_timestamp, contract_id, data, index, topic0, "
                                + "topic1, topic2, topic3) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        contractLog.getBloom(),
                        contractLog.getConsensusTimestamp(),
                        contractLog.getContractId(),
                        contractLog.getData(),
                        contractLog.getIndex(),
                        contractLog.getTopic0(),
                        contractLog.getTopic1(),
                        contractLog.getTopic2(),
                        contractLog.getTopic3());
    }

    @Data
    static class MigrationContractResult {
        private byte[] bloom;
        private byte[] callResult;
        private long consensusTimestamp;
        private Long contractId;
        private Long[] createdContractIds;
        private String errorMessage;
        private byte[] functionParameters;
        private byte[] functionResult;
    }

    @Data
    static class MigrationContractLog {
        private byte[] bloom;
        private long consensusTimestamp;
        private long contractId;
        private byte[] data;
        private int index;
        private String topic0;
        private String topic1;
        private String topic2;
        private String topic3;
    }
}
