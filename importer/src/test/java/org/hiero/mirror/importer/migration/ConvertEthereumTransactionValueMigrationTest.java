// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS_BIGINT;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.parser.record.ethereum.Eip1559EthereumTransactionParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.59.2")
class ConvertEthereumTransactionValueMigrationTest extends ImporterIntegrationTest {

    private final ConvertEthereumTransactionValueMigration convertEthereumTransactionValueMigration;

    private final JdbcTemplate jdbcTemplate;

    @Test
    void empty() {
        convertEthereumTransactionValueMigration.doMigrate();
        assertThat(findAllEthereumTransactions()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var transactions = new ArrayList<EthereumTransaction>();
        var expectedTransactions = new ArrayList<EthereumTransaction>();

        // transaction value is null
        transactions.add(ethereumTransaction(null));
        expectedTransactions.add(transactions.get(0));

        var tinybar = BigInteger.valueOf(1);
        var transaction = ethereumTransaction(toWeibar(tinybar));
        transactions.add(transaction);
        expectedTransactions.add(
                transaction.toBuilder().value(tinybar.toByteArray()).build());

        // larger than an unsigned long can hold
        tinybar = new BigInteger("700000000000000000", 16);
        transaction = ethereumTransaction(toWeibar(tinybar));
        transactions.add(transaction);
        expectedTransactions.add(
                transaction.toBuilder().value(tinybar.toByteArray()).build());

        persistEthereumTransactions(transactions);

        // when
        convertEthereumTransactionValueMigration.doMigrate();

        // then
        assertThat(findAllEthereumTransactions()).containsExactlyInAnyOrderElementsOf(expectedTransactions);
    }

    private EthereumTransaction ethereumTransaction(byte[] value) {
        return EthereumTransaction.builder()
                .consensusTimestamp(domainBuilder.timestamp())
                .data(domainBuilder.bytes(32))
                .gasLimit(20_000_000L)
                .hash(domainBuilder.bytes(32))
                .maxGasAllowance(20_000_000L)
                .nonce(domainBuilder.number())
                .payerAccountId(domainBuilder.id())
                .signatureR(domainBuilder.bytes(32))
                .signatureS(domainBuilder.bytes(32))
                .value(value)
                .type(Eip1559EthereumTransactionParser.EIP1559_TYPE_BYTE)
                .build();
    }

    private List<EthereumTransaction> findAllEthereumTransactions() {
        return jdbcTemplate.query("select * from ethereum_transaction", (rs, index) -> EthereumTransaction.builder()
                .consensusTimestamp(rs.getLong("consensus_timestamp"))
                .data(rs.getBytes("data"))
                .gasLimit(rs.getLong("gas_limit"))
                .hash(rs.getBytes("hash"))
                .maxGasAllowance(rs.getLong("max_gas_allowance"))
                .nonce(rs.getLong("nonce"))
                .payerAccountId(rs.getLong("payer_account_id"))
                .signatureR(rs.getBytes("signature_r"))
                .signatureS(rs.getBytes("signature_s"))
                .type(rs.getInt("type"))
                .value(rs.getBytes("value"))
                .build());
    }

    private void persistEthereumTransactions(List<EthereumTransaction> transactions) {
        jdbcTemplate.batchUpdate(
                "insert into ethereum_transaction (consensus_timestamp, data, gas_limit, hash, max_gas_allowance, "
                        + "nonce, payer_account_id, signature_r, signature_s, type, value) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transactions,
                transactions.size(),
                (ps, transaction) -> {
                    ps.setLong(1, transaction.getConsensusTimestamp());
                    ps.setBytes(2, transaction.getData());
                    ps.setLong(3, transaction.getGasLimit());
                    ps.setBytes(4, transaction.getHash());
                    ps.setLong(5, transaction.getMaxGasAllowance());
                    ps.setLong(6, transaction.getNonce());
                    ps.setLong(7, transaction.getPayerAccountId());
                    ps.setBytes(8, transaction.getSignatureR());
                    ps.setBytes(9, transaction.getSignatureS());
                    ps.setShort(10, transaction.getType().shortValue());
                    ps.setBytes(11, transaction.getValue());
                });
    }

    private byte[] toWeibar(BigInteger value) {
        return value.multiply(WEIBARS_TO_TINYBARS_BIGINT).toByteArray();
    }

    @Builder(toBuilder = true)
    @Data
    private static class EthereumTransaction {
        private Long consensusTimestamp;
        private byte[] data;
        private Long gasLimit;
        private byte[] hash;
        private Long maxGasAllowance;
        private Long nonce;
        private Long payerAccountId;
        private byte[] signatureR;
        private byte[] signatureS;
        private Integer type;
        private byte[] value;
    }
}
