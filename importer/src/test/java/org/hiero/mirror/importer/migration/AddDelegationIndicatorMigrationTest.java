// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = AddDelegationIndicatorMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class AddDelegationIndicatorMigrationTest extends ImporterIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final String REVERT_ENTITY_DDL = "alter table entity drop column if exists delegation_indicator";
    private static final String REVERT_ENTITY_HISTORY_DDL =
            "alter table entity_history drop column if exists delegation_indicator";
    private static final String REVERT_ETHEREUM_TX_DDL =
            "alter table ethereum_transaction drop column if exists authorization_list";

    @AfterEach
    void teardown() {
        ownerJdbcTemplate.update(REVERT_ENTITY_DDL);
        ownerJdbcTemplate.update(REVERT_ENTITY_HISTORY_DDL);
        ownerJdbcTemplate.update(REVERT_ETHEREUM_TX_DDL);
    }

    @Test
    void empty() {
        runMigration();

        assertThat(count("entity")).isZero();
        assertThat(count("entity_history")).isZero();
        assertThat(count("ethereum_transaction")).isZero();
    }

    @Test
    void canInsertDataWithNewColumns() throws JsonProcessingException {
        // given
        runMigration();

        // when
        final var delegationIndicator = new byte[] {
            (byte) 0xef,
            0x01,
            0x00,
            0x01,
            0x02,
            0x03,
            0x04,
            0x05,
            0x06,
            0x07,
            0x08,
            0x09,
            0x0a,
            0x0b,
            0x0c,
            0x0d,
            0x0e,
            0x0f,
            0x10,
            0x11,
            0x12,
            0x13,
            0x14,
            0x15,
            0x16,
            0x17,
            0x18
        };
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.delegationIndicator(delegationIndicator))
                .get();
        persistEntity(entity);

        final var entityHistory = domainBuilder
                .entityHistory()
                .customize(eh -> eh.delegationIndicator(delegationIndicator))
                .get();
        persistEntityHistory(entityHistory);

        final var authorization = new Authorization()
                .toBuilder()
                        .address("0x123")
                        .chainId("0x1")
                        .nonce(5L)
                        .r("0x222")
                        .s("0x333")
                        .yParity(1)
                        .build();
        final var ethereumTxn = domainBuilder
                .ethereumTransaction(true)
                .customize(txn -> txn.authorizationList(List.of(authorization)))
                .get();
        persistEthereumTransaction(ethereumTxn);

        // then
        final var actualDelegationIndicator = jdbcOperations.queryForObject(
                "select delegation_indicator from entity where id = ?", byte[].class, entity.getId());
        assertThat(actualDelegationIndicator).isEqualTo(delegationIndicator);

        final var actualDelegationIndicatorHistory = jdbcOperations.queryForObject(
                "select delegation_indicator from entity_history where id = ?", byte[].class, entityHistory.getId());
        assertThat(actualDelegationIndicatorHistory).isEqualTo(delegationIndicator);

        final var actualAuthList = jdbcOperations.queryForObject(
                "select authorization_list::text from ethereum_transaction where consensus_timestamp = ?",
                String.class,
                ethereumTxn.getConsensusTimestamp());
        assertThat(actualAuthList).isNotNull();

        final var actualTree = OBJECT_MAPPER.readTree(actualAuthList);
        final var expectedJson = OBJECT_MAPPER.writeValueAsString(List.of(authorization));
        assertThat(actualTree).isEqualTo(OBJECT_MAPPER.readTree(expectedJson));
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath =
                isV1() ? "v1/V1.117.0__add_delegation_indicator.sql" : "v2/V2.22.0__add_delegation_indicator.sql";
        var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    private long count(String table) {
        return jdbcOperations.queryForObject("select count(*) from " + table, Long.class);
    }

    private void persistEntity(Entity entity) {
        jdbcOperations.update(
                "insert into entity (id, num, realm, shard, created_timestamp, timestamp_range, type, delegation_indicator) "
                        + "values (?, ?, ?, ?, ?, ?::int8range, ?::entity_type, ?)",
                entity.getId(),
                entity.getNum(),
                entity.getRealm(),
                entity.getShard(),
                entity.getCreatedTimestamp(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()),
                entity.getType().name(),
                entity.getDelegationIndicator());
    }

    private void persistEntityHistory(EntityHistory history) {
        jdbcOperations.update(
                "insert into entity_history (id, num, realm, shard, created_timestamp, timestamp_range, type, delegation_indicator) "
                        + "values (?, ?, ?, ?, ?, ?::int8range, ?::entity_type, ?)",
                history.getId(),
                history.getNum(),
                history.getRealm(),
                history.getShard(),
                history.getCreatedTimestamp(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(history.getTimestampRange()),
                history.getType().name(),
                history.getDelegationIndicator());
    }

    @SneakyThrows
    private void persistEthereumTransaction(EthereumTransaction ethTx) {
        jdbcOperations.update(
                """
                insert into ethereum_transaction (
                  call_data, chain_id, consensus_timestamp, data, gas_limit, gas_price, hash,
                  max_fee_per_gas, max_gas_allowance, max_priority_fee_per_gas, nonce, payer_account_id,
                  recovery_id, signature_r, signature_s, signature_v, to_address, type, value, authorization_list)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""",
                ethTx.getCallData(),
                ethTx.getChainId(),
                ethTx.getConsensusTimestamp(),
                ethTx.getData(),
                ethTx.getGasLimit(),
                ethTx.getGasPrice(),
                ethTx.getHash(),
                ethTx.getMaxFeePerGas(),
                ethTx.getMaxGasAllowance() != null ? ethTx.getMaxGasAllowance() : 0L,
                ethTx.getMaxPriorityFeePerGas(),
                ethTx.getNonce(),
                ethTx.getPayerAccountId().getId(),
                ethTx.getRecoveryId(),
                ethTx.getSignatureR(),
                ethTx.getSignatureS(),
                ethTx.getSignatureV(),
                ethTx.getToAddress(),
                ethTx.getType(),
                ethTx.getValue(),
                OBJECT_MAPPER.writeValueAsString(ethTx.getAuthorizationList()));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.21.0" : "1.116.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
