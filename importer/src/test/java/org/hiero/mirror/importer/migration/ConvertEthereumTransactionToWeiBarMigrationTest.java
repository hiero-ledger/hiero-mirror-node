// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests for {@link ConvertEthereumTransactionToWeiBarMigration}.
 * This migration re-parses RLP data from the ethereum_transaction table to convert
 * gas and value fields from tinybar (incorrect) to weibar (correct).
 */
@ContextConfiguration(initializers = ConvertEthereumTransactionToWeiBarMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class ConvertEthereumTransactionToWeiBarMigrationTest extends ImporterIntegrationTest {

    private final ConvertEthereumTransactionToWeiBarMigration migration;
    private final JdbcTemplate jdbcTemplate;

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(findAllEthereumTransactions()).isEmpty();
    }

    @Test
    void migrateWithInvalidRlpData() {
        // given - create transactions with valid and invalid RLP data
        var consensusTimestamp1 = domainBuilder.timestamp();
        var payerAccountId = domainBuilder.id();
        persistEthereumTransaction(consensusTimestamp1, payerAccountId, RAW_TX_TYPE_1);

        // Invalid RLP data (random bytes)
        var consensusTimestamp2 = domainBuilder.timestamp();
        persistEthereumTransaction(consensusTimestamp2, payerAccountId, new byte[] {0x01, 0x02, 0x03});

        // Another valid transaction
        var consensusTimestamp3 = domainBuilder.timestamp();
        var londonRawTx = Hex.decode(
                "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66");
        persistEthereumTransaction(consensusTimestamp3, payerAccountId, londonRawTx);

        // when
        migration.doMigrate();

        // then - verify only valid transactions were updated
        var transactions = findAllEthereumTransactions();
        assertThat(transactions).hasSize(3);

        // Verify first transaction was updated
        var tx1 = transactions.get(0);
        assertThat(tx1.getConsensusTimestamp()).isEqualTo(consensusTimestamp1);
        assertThat(tx1.getGasPrice()).isNotEmpty();

        // Verify second transaction (invalid RLP) was NOT updated - fields remain null
        var tx2 = transactions.get(1);
        assertThat(tx2.getConsensusTimestamp()).isEqualTo(consensusTimestamp2);
        assertThat(tx2.getGasPrice()).isNullOrEmpty();
        assertThat(tx2.getValue()).isNullOrEmpty();

        // Verify third transaction was updated
        var tx3 = transactions.get(2);
        assertThat(tx3.getConsensusTimestamp()).isEqualTo(consensusTimestamp3);
        assertThat(tx3.getMaxFeePerGas()).isNotEmpty();
    }

    @Test
    void migrate() {
        // given - create ethereum transactions with RLP data
        // Type 1 (EIP-2930) transaction with gas_price and value
        var consensusTimestamp1 = domainBuilder.timestamp();
        var payerAccountId = domainBuilder.id();
        persistEthereumTransaction(consensusTimestamp1, payerAccountId, RAW_TX_TYPE_1);

        // Type 2 (EIP-1559) transaction with max_fee_per_gas, max_priority_fee_per_gas, and value
        // LONDON_RAW_TX:
        // 02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0...
        var consensusTimestamp2 = domainBuilder.timestamp();
        var londonRawTx = Hex.decode(
                "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66");
        persistEthereumTransaction(consensusTimestamp2, payerAccountId, londonRawTx);

        // when
        migration.doMigrate();

        // then - verify fields were populated from RLP data
        var transactions = findAllEthereumTransactions();
        assertThat(transactions).hasSize(2);

        // Verify type 1 transaction (has gas_price)
        var tx1 = transactions.get(0);
        assertThat(tx1.getConsensusTimestamp()).isEqualTo(consensusTimestamp1);
        assertThat(tx1.getGasPrice()).isNotEmpty(); // Parsed from RLP: 0xa54f4c3c00
        assertThat(tx1.getMaxFeePerGas()).isNullOrEmpty(); // Not in type 1
        assertThat(tx1.getMaxPriorityFeePerGas()).isNullOrEmpty(); // Not in type 1
        assertThat(tx1.getValue()).isNotEmpty(); // Parsed from RLP: 0x02540be400

        // Verify type 2 transaction (has max_fee_per_gas and max_priority_fee_per_gas)
        var tx2 = transactions.get(1);
        assertThat(tx2.getConsensusTimestamp()).isEqualTo(consensusTimestamp2);
        assertThat(tx2.getGasPrice()).isNullOrEmpty(); // Not in type 2
        assertThat(tx2.getMaxFeePerGas()).isNotEmpty(); // Parsed from RLP: 0x2f
        assertThat(tx2.getMaxPriorityFeePerGas()).isNotEmpty(); // Parsed from RLP: 0x2f
        assertThat(tx2.getValue()).isNotEmpty(); // Parsed from RLP: 0x0de0b6b3a7640000
    }

    private void persistEthereumTransaction(long consensusTimestamp, long payerAccountId, byte[] rlpData) {
        // Provide required NOT NULL fields with dummy values
        // The migration will re-parse the RLP data to populate gas_price, max_fee_per_gas, max_priority_fee_per_gas,
        // and value
        jdbcTemplate.update(
                "insert into ethereum_transaction (consensus_timestamp, data, gas_limit, "
                        + "hash, max_gas_allowance, nonce, payer_account_id, signature_r, signature_s, type) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setLong(1, consensusTimestamp);
                    ps.setBytes(2, rlpData);
                    ps.setLong(3, 1000000L); // gas_limit
                    ps.setBytes(4, new byte[32]); // hash
                    ps.setLong(5, 0L); // max_gas_allowance
                    ps.setLong(6, 0L); // nonce
                    ps.setLong(7, payerAccountId);
                    ps.setBytes(8, new byte[32]); // signature_r
                    ps.setBytes(9, new byte[32]); // signature_s
                    ps.setShort(10, (short) rlpData[0]); // type
                });
    }

    private List<EthereumTransaction> findAllEthereumTransactions() {
        return jdbcTemplate.query(
                "select * from ethereum_transaction order by consensus_timestamp",
                (rs, index) -> EthereumTransaction.builder()
                        .consensusTimestamp(rs.getLong("consensus_timestamp"))
                        .data(rs.getBytes("data"))
                        .gasLimit(rs.getLong("gas_limit"))
                        .gasPrice(rs.getBytes("gas_price"))
                        .hash(rs.getBytes("hash"))
                        .maxFeePerGas(rs.getBytes("max_fee_per_gas"))
                        .maxGasAllowance(rs.getLong("max_gas_allowance"))
                        .maxPriorityFeePerGas(rs.getBytes("max_priority_fee_per_gas"))
                        .nonce(rs.getLong("nonce"))
                        .payerAccountId(rs.getLong("payer_account_id"))
                        .signatureR(rs.getBytes("signature_r"))
                        .signatureS(rs.getBytes("signature_s"))
                        .type(rs.getInt("type"))
                        .value(rs.getBytes("value"))
                        .build());
    }

    @Builder(toBuilder = true)
    @Data
    private static class EthereumTransaction {
        private Long consensusTimestamp;
        private byte[] data;
        private Long gasLimit;
        private byte[] gasPrice;
        private byte[] hash;
        private byte[] maxFeePerGas;
        private Long maxGasAllowance;
        private byte[] maxPriorityFeePerGas;
        private Long nonce;
        private Long payerAccountId;
        private byte[] signatureR;
        private byte[] signatureS;
        private Integer type;
        private byte[] value;
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.24.0" : "1.119.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
