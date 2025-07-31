// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowanceHistory;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.RecordFileParser;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.CryptoAllowanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
class FixCryptoAllowanceAmountMigrationTest extends AbstractAsyncJavaMigrationTest<FixCryptoAllowanceAmountMigration> {

    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final DBProperties dbProperties;
    private final EntityProperties entityProperties;
    private final @Getter Class<FixCryptoAllowanceAmountMigration> migrationClass =
            FixCryptoAllowanceAmountMigration.class;
    private final RecordFileParser recordFileParser;
    private final RecordItemBuilder recordItemBuilder;

    private @Getter FixCryptoAllowanceAmountMigration migration;

    private static CryptoAllowance convert(CryptoAllowanceHistory history) {
        return CryptoAllowance.builder()
                .amount(history.getAmount())
                .amountGranted(history.getAmountGranted())
                .owner(history.getOwner())
                .payerAccountId(history.getPayerAccountId())
                .spender(history.getSpender())
                .timestampRange(history.getTimestampRange())
                .build();
    }

    @BeforeEach
    void setup() {
        // Create migration object for each test case due to the cached earliestTimestamp
        var mirrorProperties = new ImporterProperties();
        migration = new FixCryptoAllowanceAmountMigration(
                dbProperties, entityProperties, mirrorProperties, new ObjectProvider<JdbcTemplate>() {
                    @Override
                    public JdbcTemplate getObject() {
                        return ownerJdbcTemplate;
                    }
                });
    }

    @Test
    void empty() {
        // given, when
        runMigration();

        // then
        waitForCompletion();
        assertThat(tableExists("crypto_allowance_migration")).isFalse();
        assertThat(cryptoAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // Crypto allowance for (owner, spender) pair 1
        var history = convert(domainBuilder.cryptoAllowanceHistory().persist());
        // The current crypto allowance for pair 1
        var builder = history.toBuilder()
                .amount(2500)
                .amountGranted(2500L)
                .timestampRange(Range.atLeast(history.getTimestampUpper()));
        var current1 = domainBuilder.wrap(builder, builder::build).persist();
        var owner1 = EntityId.of(current1.getOwner());
        var spender1 = EntityId.of(current1.getSpender());
        // A transfer using allowance
        var cryptoTransferBuilder = CryptoTransfer.builder()
                .amount(-5)
                .consensusTimestamp(domainBuilder.timestamp())
                .entityId(current1.getOwner())
                .isApproval(true)
                .payerAccountId(spender1);
        domainBuilder.wrap(cryptoTransferBuilder, cryptoTransferBuilder::build).persist();
        current1.setAmount(current1.getAmount() - 5);
        // A transfer without allowance
        cryptoTransferBuilder
                .amount(-8)
                .consensusTimestamp(domainBuilder.timestamp())
                .payerAccountId(owner1)
                .isApproval(false);
        domainBuilder.wrap(cryptoTransferBuilder, cryptoTransferBuilder::build).persist();
        // Another transfer using allowance
        cryptoTransferBuilder
                .amount(-6)
                .consensusTimestamp(domainBuilder.timestamp())
                .entityId(current1.getOwner())
                .isApproval(true)
                .payerAccountId(spender1);
        domainBuilder.wrap(cryptoTransferBuilder, cryptoTransferBuilder::build).persist();
        current1.setAmount(current1.getAmount() - 6);

        // Crypto allowance for (owner, spender) pair 2. Note the second crypto transfer using allowance is tracked
        // by importer
        long amountTracked = -11;
        var current2 = domainBuilder
                .cryptoAllowance()
                .customize(ca -> ca.amount(3300 + amountTracked).amountGranted(3300L))
                .persist();
        var spender2 = EntityId.of(current2.getSpender());
        // A transfer using allowance, but with a timestamp before current2, i.e., using allowance before current2,
        // the migration should exclude it
        cryptoTransferBuilder
                .amount(-115)
                .consensusTimestamp(current2.getTimestampLower() - 1)
                .entityId(current2.getOwner())
                .isApproval(true)
                .payerAccountId(spender2);
        domainBuilder.wrap(cryptoTransferBuilder, cryptoTransferBuilder::build).persist();
        // A transfer using allowance, not tracked by importer because it's ingested before the feature is rolled out
        cryptoTransferBuilder.amount(-9).consensusTimestamp(domainBuilder.timestamp());
        domainBuilder.wrap(cryptoTransferBuilder, cryptoTransferBuilder::build).persist();
        current2.setAmount(current2.getAmount() - 9);
        // Another transfer using allowance, it's tracked and reflected in crypto_allowance.amount
        cryptoTransferBuilder
                .amount(amountTracked)
                .consensusTimestamp(domainBuilder.timestamp() + FixCryptoAllowanceAmountMigration.INTERVAL);
        long timestamp = domainBuilder
                .wrap(cryptoTransferBuilder, cryptoTransferBuilder::build)
                .persist()
                .getConsensusTimestamp();

        // A revoked crypto allowance
        var revoked = domainBuilder
                .cryptoAllowance()
                .customize(ca -> ca.amount(0).amountGranted(0L))
                .persist();

        // Add two record file rows
        domainBuilder
                .recordFile()
                .customize(rf -> rf.consensusEnd(timestamp - 1000))
                .persist();
        var lastRecordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.consensusEnd(timestamp))
                .persist();

        // A new record file, processed by parser
        long consensusStart = lastRecordFile.getConsensusEnd() + 10;
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.consensusStart(consensusStart)
                        .consensusEnd(consensusStart + 100)
                        .previousHash(lastRecordFile.getHash())
                        .sidecars(Collections.emptyList()))
                .get();
        // Two crypto transfers using allowance
        var owner1AccountId = EntityId.of(current1.getOwner()).toAccountID();
        var spender1AccountId = EntityId.of(current1.getSpender()).toAccountID();
        var receiverAccountId = domainBuilder.entityId().toAccountID();

        var transactionId1 = TransactionID.newBuilder()
                .setAccountID(spender1AccountId)
                .setTransactionValidStart(TestUtils.toTimestamp(consensusStart - 10))
                .build();
        var transferList1 = TransferList.newBuilder()
                .addAccountAmounts(AccountAmount.newBuilder()
                        .setAmount(-15)
                        .setAccountID(owner1AccountId)
                        .setIsApproval(true))
                .addAccountAmounts(AccountAmount.newBuilder().setAmount(15).setAccountID(receiverAccountId))
                .build();
        var recordItem1 = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clearTransfers().setTransfers(transferList1))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusStart))
                        .setTransactionID(transactionId1)
                        // For sake of brevity, no fee transfers in record
                        .setTransferList(transferList1))
                .build();
        current1.setAmount(current1.getAmount() - 15);

        var owner2AccountId = EntityId.of(current2.getOwner()).toAccountID();
        var spender2AccountId = EntityId.of(current2.getSpender()).toAccountID();
        var transactionId2 = TransactionID.newBuilder()
                .setAccountID(spender2AccountId)
                .setTransactionValidStart(TestUtils.toTimestamp(consensusStart - 5))
                .build();
        var transferList2 = TransferList.newBuilder()
                .addAccountAmounts(AccountAmount.newBuilder()
                        .setAmount(-19)
                        .setAccountID(owner2AccountId)
                        .setIsApproval(true))
                .addAccountAmounts(AccountAmount.newBuilder().setAmount(19).setAccountID(receiverAccountId))
                .build();
        var recordItem2 = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clearTransfers().setTransfers(transferList2))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId2))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusStart + 1))
                        .setTransactionID(transactionId2)
                        // For sake of brevity, no fee transfers in record
                        .setTransferList(transferList2))
                .build();
        current2.setAmount(current2.getAmount() - 19);

        // A crypto approve allowance transaction to revoke current1
        var transactionId3 = TransactionID.newBuilder()
                .setAccountID(owner1AccountId)
                .setTransactionValidStart(TestUtils.toTimestamp(consensusStart - 3))
                .build();
        var recordItem3 = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(b -> b.clear()
                        .addCryptoAllowances(com.hederahashgraph.api.proto.java.CryptoAllowance.newBuilder()
                                .setAmount(0)
                                .setSpender(spender1AccountId)))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId3))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusStart + 2))
                        .setTransactionID(transactionId3))
                .build();

        recordFile.setItems(List.of(recordItem1, recordItem2, recordItem3));

        // when, run the async migration and ingesting the record file concurrently
        runMigration();
        recordFileParser.parse(recordFile);

        // then
        waitForCompletion();
        assertThat(tableExists("crypto_allowance_migration")).isFalse();

        current1.setTimestampUpper(recordItem3.getConsensusTimestamp());
        var current1Revoked = current1.toBuilder()
                .amount(0)
                .amountGranted(0L)
                .payerAccountId(owner1)
                .timestampRange(Range.atLeast(recordItem3.getConsensusTimestamp()))
                .build();
        assertThat(cryptoAllowanceRepository.findAll()).containsExactlyInAnyOrder(current1Revoked, current2, revoked);
        assertThat(findHistory(CryptoAllowance.class)).containsExactlyInAnyOrder(current1, history);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void skipMigration(boolean trackAllowance) {
        // given
        var entityProps = new EntityProperties(new SystemEntity(CommonProperties.getInstance()));
        entityProps.getPersist().setTrackAllowance(trackAllowance);
        var allowanceAmountMigration = new FixCryptoAllowanceAmountMigration(
                dbProperties, entityProps, new ImporterProperties(), new ObjectProvider<JdbcTemplate>() {
                    @Override
                    public JdbcTemplate getObject() {
                        return ownerJdbcTemplate;
                    }
                });
        var configuration = new FluentConfiguration().target(allowanceAmountMigration.getMinimumVersion());

        // when, then
        assertThat(allowanceAmountMigration.skipMigration(configuration)).isEqualTo(!trackAllowance);
    }
}
