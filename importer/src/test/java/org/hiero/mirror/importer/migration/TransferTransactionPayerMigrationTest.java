// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.File;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.6")
class TransferTransactionPayerMigrationTest extends ImporterIntegrationTest {

    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3);
    private static final EntityId PAYER_ID = EntityId.of(0, 0, 10001);

    @Value("classpath:db/migration/v1/V1.47.0__add_transfer_payer.sql")
    private final File migrationSql;

    private final EntityRepository entityRepository;
    private final TransactionRepository transactionRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final TokenTransferRepository tokenTransferRepository;

    @BeforeEach
    void before() {
        revertToPreV_1_47();
    }

    @Test
    void verifyEntityTimestampMigrationEmpty() throws Exception {
        // migration
        migrate();

        assertThat(entityRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
        assertThat(cryptoTransferRepository.count()).isZero();
        assertThat(findNftTransfers()).isEmpty();
        assertThat(tokenTransferRepository.count()).isZero();
    }

    @Test
    void verifyEntityTimestampMigration() throws Exception {
        Entity node = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(10L).timestampRange(Range.atLeast(10L)))
                .get();
        Entity treasury = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(20L).timestampRange(Range.atLeast(20L)))
                .get();
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(30L).timestampRange(Range.atLeast(30L)))
                .get();
        Entity receiver = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(40L).timestampRange(Range.atLeast(40L)))
                .get();
        Entity contract = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(50L)
                        .timestampRange(Range.atLeast(50L))
                        .type(EntityType.CONTRACT))
                .get();
        Entity file = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(60L)
                        .timestampRange(Range.atLeast(60L))
                        .type(EntityType.FILE))
                .get();
        Entity topic = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(70L)
                        .timestampRange(Range.atLeast(70L))
                        .type(EntityType.TOPIC))
                .get();
        Entity token = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(80L)
                        .timestampRange(Range.atLeast(80L))
                        .type(TOKEN))
                .get();
        Entity schedule = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(90L)
                        .timestampRange(Range.atLeast(90L))
                        .type(EntityType.SCHEDULE))
                .get();

        // given
        persistEntities(List.of(node, treasury, sender, receiver, contract, file, topic, token, schedule));

        MigrationTransaction transfer1 =
                transaction(schedule.getCreatedTimestamp() + 200L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer2 =
                transaction(schedule.getCreatedTimestamp() + 300L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer3 =
                transaction(schedule.getCreatedTimestamp() + 400L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer4 =
                transaction(schedule.getCreatedTimestamp() + 500L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        MigrationTransaction transfer5 =
                transaction(schedule.getCreatedTimestamp() + 600L, 0, SUCCESS, TransactionType.CRYPTOTRANSFER);
        persistTransactions(List.of(
                transaction(
                        contract.getCreatedTimestamp(),
                        contract.getId(),
                        SUCCESS,
                        TransactionType.CONTRACTCREATEINSTANCE),
                transaction(
                        contract.getCreatedTimestamp() + 1,
                        contract.getId(),
                        INSUFFICIENT_TX_FEE,
                        TransactionType.CONTRACTUPDATEINSTANCE),
                transaction(file.getCreatedTimestamp(), file.getId(), SUCCESS, TransactionType.FILECREATE),
                transaction(file.getCreatedTimestamp() + 1, file.getId(), SUCCESS, TransactionType.FILEDELETE),
                transaction(topic.getCreatedTimestamp(), topic.getId(), SUCCESS, TransactionType.CONSENSUSCREATETOPIC),
                transaction(
                        topic.getCreatedTimestamp() + 1, topic.getId(), SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(
                        topic.getCreatedTimestamp() + 2, topic.getId(), SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(
                        topic.getCreatedTimestamp() + 3, topic.getId(), SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(token.getCreatedTimestamp(), token.getId(), SUCCESS, TransactionType.TOKENCREATION),
                transaction(schedule.getCreatedTimestamp(), schedule.getId(), SUCCESS, TransactionType.SCHEDULECREATE),
                transfer1,
                transfer2,
                transfer3,
                transfer4,
                transfer5));

        EntityId nodeId = node.toEntityId();
        EntityId treasuryId = treasury.toEntityId();
        EntityId senderId = sender.toEntityId();
        EntityId receiverId = receiver.toEntityId();
        EntityId tokenId = token.toEntityId();
        long senderPaymentAmount = -45L;
        long receivedAmount = 30L;
        long nodePaymentAmount = 10L;
        long treasuryPaymentAmount = 5L;

        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(receivedAmount);
        assessedCustomFee1.setCollectorAccountId(receiverId.getId());
        assessedCustomFee1.setConsensusTimestamp(transfer1.getConsensusTimestamp());
        assessedCustomFee1.setEffectivePayerAccountIds(List.of(senderId.getId()));
        assessedCustomFee1.setPayerAccountId(tokenId);
        assessedCustomFee1.setTokenId(tokenId);

        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(receivedAmount);
        assessedCustomFee2.setCollectorAccountId(receiverId.getId());
        assessedCustomFee2.setConsensusTimestamp(transfer5.getConsensusTimestamp());
        assessedCustomFee2.setEffectivePayerAccountIds(List.of(senderId.getId()));
        assessedCustomFee2.setPayerAccountId(tokenId);
        assessedCustomFee2.setTokenId(tokenId);

        persistAssessedCustomFees(List.of(
                // assessed custom fee transfer
                assessedCustomFee1,
                // all transfers
                assessedCustomFee2));

        var t1c = transfer1.getConsensusTimestamp();
        var t2c = transfer2.getConsensusTimestamp();
        var t3c = transfer3.getConsensusTimestamp();
        var t4c = transfer4.getConsensusTimestamp();
        var t5c = transfer5.getConsensusTimestamp();
        var senderCryptoTransferBuilder =
                CryptoTransfer.builder().amount(senderPaymentAmount).entityId(senderId.getId());
        var receivedCryptoTransferBuilder =
                CryptoTransfer.builder().amount(receivedAmount).entityId(receiverId.getId());
        var nodeCryptoTransferBuilder =
                CryptoTransfer.builder().amount(nodePaymentAmount).entityId(nodeId.getId());
        var treasuryCryptoTransferBuilder =
                CryptoTransfer.builder().amount(treasuryPaymentAmount).entityId(treasuryId.getId());
        persistCryptoTransfers(List.of(
                // assessed custom fee transfer
                senderCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t1c).build(),
                // crypto only transfer
                senderCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t2c).build(),
                // nft transfer
                senderCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t3c).build(),
                // token transfer
                senderCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t4c).build(),
                // all transfers
                senderCryptoTransferBuilder.consensusTimestamp(t5c).build(),
                receivedCryptoTransferBuilder.consensusTimestamp(t5c).build(),
                nodeCryptoTransferBuilder.consensusTimestamp(t5c).build(),
                treasuryCryptoTransferBuilder.consensusTimestamp(t5c).build()));

        var nonFeeTransfers = List.of(
                // assessed custom fee only transfer
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer1.getConsensusTimestamp())
                        .amount(senderPaymentAmount)
                        .entityId(senderId)
                        .build(),
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer1.getConsensusTimestamp())
                        .amount(receivedAmount)
                        .entityId(receiverId)
                        .build(),
                // crypto only transfer
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer2.getConsensusTimestamp())
                        .amount(senderPaymentAmount)
                        .entityId(senderId)
                        .build(),
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer2.getConsensusTimestamp())
                        .amount(receivedAmount)
                        .entityId(receiverId)
                        .build(),
                // nft transfer
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer3.getConsensusTimestamp())
                        .amount(senderPaymentAmount)
                        .entityId(senderId)
                        .build(),
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer3.getConsensusTimestamp())
                        .amount(receivedAmount)
                        .entityId(receiverId)
                        .build(),
                // token transfer
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer4.getConsensusTimestamp())
                        .amount(senderPaymentAmount)
                        .entityId(senderId)
                        .build(),
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer4.getConsensusTimestamp())
                        .amount(receivedAmount)
                        .entityId(receiverId)
                        .build(),
                // all transfers
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer5.getConsensusTimestamp())
                        .amount(senderPaymentAmount)
                        .entityId(senderId)
                        .build(),
                NonFeeTransfer.builder()
                        .consensusTimestamp(transfer5.getConsensusTimestamp())
                        .amount(receivedAmount)
                        .entityId(receiverId)
                        .build());

        persistNonFeeTransfers(nonFeeTransfers);

        persistNftTransfers(List.of(
                // nft transfer
                nftTransfer(transfer3.getConsensusTimestamp(), receiverId, senderId, 1L, tokenId),
                // all transfers
                nftTransfer(transfer5.getConsensusTimestamp(), receiverId, senderId, 2L, tokenId)));

        persistTokenTransfers(List.of(
                // token transfer
                new TokenTransfer(transfer4.getConsensusTimestamp(), -receivedAmount, tokenId, senderId),
                new TokenTransfer(transfer4.getConsensusTimestamp(), receivedAmount, tokenId, receiverId),
                // all transfers
                new TokenTransfer(transfer5.getConsensusTimestamp(), -receivedAmount, tokenId, senderId),
                new TokenTransfer(transfer5.getConsensusTimestamp(), receivedAmount, tokenId, receiverId)));

        // when
        migrate();

        List<SharedTransfer> expectedAssessedCustomFeeTransfers = List.of(
                // assessed custom fee transfer
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, senderId));

        List<SharedTransfer> expectedCryptoTransfers = List.of(
                // assessed custom fee transfer
                new SharedTransfer(senderPaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // crypto only transfer
                new SharedTransfer(senderPaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer2.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // nft transfer
                new SharedTransfer(senderPaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // token transfer
                new SharedTransfer(senderPaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, treasuryId, null),
                // all transfers
                new SharedTransfer(senderPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                new SharedTransfer(nodePaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, nodeId, null),
                new SharedTransfer(
                        treasuryPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, treasuryId, null));

        List<SharedTransfer> expectedNftTransfers = List.of(
                // nft transfer
                new SharedTransfer(1L, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, senderId),
                new SharedTransfer(2L, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, senderId));

        List<SharedTransfer> expectedNonFeeTransfers = List.of(
                // assessed custom fee only transfer
                new SharedTransfer(senderPaymentAmount, transfer1.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer1.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // crypto only transfer
                new SharedTransfer(senderPaymentAmount, transfer2.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer2.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // nft transfer
                new SharedTransfer(senderPaymentAmount, transfer3.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer3.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // token transfer
                new SharedTransfer(senderPaymentAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // token transfer
                new SharedTransfer(senderPaymentAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null));

        List<SharedTransfer> expectedTokenTransfers = List.of(
                // token transfer
                new SharedTransfer(-receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer4.getConsensusTimestamp(), PAYER_ID, receiverId, null),
                // all transfer
                new SharedTransfer(-receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, null, senderId),
                new SharedTransfer(receivedAmount, transfer5.getConsensusTimestamp(), PAYER_ID, receiverId, null));

        // then
        assertThat(findAssessedCustomFees()).containsExactlyInAnyOrderElementsOf(expectedAssessedCustomFeeTransfers);

        assertThat(findCryptoTransfers()).containsExactlyInAnyOrderElementsOf(expectedCryptoTransfers);

        assertThat(findNftTransfers()).containsExactlyInAnyOrderElementsOf(expectedNftTransfers);

        assertThat(findNonFeeTransfersAsSharedTransfers()).containsExactlyInAnyOrderElementsOf(expectedNonFeeTransfers);

        assertThat(findTokenTransfers()).containsExactlyInAnyOrderElementsOf(expectedTokenTransfers);
    }

    private MigrationTransaction transaction(
            long consensusNs, long entityNum, ResponseCodeEnum result, TransactionType type) {
        MigrationTransaction transaction = new MigrationTransaction();
        transaction.setConsensusTimestamp(consensusNs);
        transaction.setEntityId(entityNum);
        transaction.setNodeAccountId(NODE_ACCOUNT_ID.getId());
        transaction.setPayerAccountId(PAYER_ID.getId());
        transaction.setResult(result.getNumber());
        transaction.setType(type.getProtoId());
        transaction.setValidStartNs(consensusNs - 10);
        return transaction;
    }

    private void migrate() throws Exception {
        ownerJdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private MigrationNftTransfer nftTransfer(
            long consensusTimestamp, EntityId receiver, EntityId sender, long serialNumber, EntityId tokenId) {
        Long receiverAccountId = EntityId.isEmpty(receiver) ? null : receiver.getId();
        Long senderAccountId = EntityId.isEmpty(sender) ? null : sender.getId();
        return MigrationNftTransfer.builder()
                .consensusTimestamp(consensusTimestamp)
                .receiverAccountId(receiverAccountId)
                .senderAccountId(senderAccountId)
                .serialNumber(serialNumber)
                .tokenId(tokenId.getId())
                .build();
    }

    private void persistAssessedCustomFees(List<AssessedCustomFee> assessedCustomFees) {
        for (AssessedCustomFee assessedCustomFee : assessedCustomFees) {
            jdbcOperations.update(
                    "insert into assessed_custom_fee (amount, collector_account_id, consensus_timestamp, token_id,"
                            + "effective_payer_account_ids)"
                            + " values (?,?,?,?,?::bigint[])",
                    assessedCustomFee.getAmount(),
                    assessedCustomFee.getCollectorAccountId(),
                    assessedCustomFee.getConsensusTimestamp(),
                    assessedCustomFee.getTokenId().getId(),
                    longListSerializer(assessedCustomFee.getEffectivePayerAccountIds()));
        }
    }

    private void persistCryptoTransfers(List<CryptoTransfer> cryptoTransfers) {
        for (CryptoTransfer cryptoTransfer : cryptoTransfers) {
            jdbcOperations.update(
                    "insert into crypto_transfer (amount, consensus_timestamp, entity_id)" + " values (?,?,?)",
                    cryptoTransfer.getAmount(),
                    cryptoTransfer.getConsensusTimestamp(),
                    cryptoTransfer.getEntityId());
        }
    }

    private void persistNftTransfers(List<MigrationNftTransfer> nftTransfers) {
        for (var nftTransfer : nftTransfers) {
            jdbcOperations.update(
                    """
                            insert into nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id,
                            serial_number, token_id)
                            values (?,?,?,?,?)
                            """,
                    nftTransfer.getConsensusTimestamp(),
                    nftTransfer.getReceiverAccountId(),
                    nftTransfer.getSenderAccountId(),
                    nftTransfer.getSerialNumber(),
                    nftTransfer.getTokenId());
        }
    }

    private void persistNonFeeTransfers(List<NonFeeTransfer> nonFeeTransfers) {
        for (NonFeeTransfer nonFeeTransfer : nonFeeTransfers) {
            jdbcOperations.update(
                    "insert into non_fee_transfer (amount, entity_id, consensus_timestamp)" + " values (?,?,?)",
                    nonFeeTransfer.getAmount(),
                    nonFeeTransfer.getEntityId().getId(),
                    nonFeeTransfer.getConsensusTimestamp());
        }
    }

    private void persistTokenTransfers(List<TokenTransfer> tokenTransfers) {
        for (TokenTransfer tokenTransfer : tokenTransfers) {
            var id = tokenTransfer.getId();
            jdbcOperations.update(
                    "insert into token_transfer (amount, account_id, consensus_timestamp, token_id)"
                            + " values (?,?,?,?)",
                    tokenTransfer.getAmount(),
                    id.getAccountId().getId(),
                    id.getConsensusTimestamp(),
                    id.getTokenId().getId());
        }
    }

    private List<SharedTransfer> findAssessedCustomFees() {
        return jdbcOperations.query("select * from assessed_custom_fee", (rs, rowNum) -> {
            List<Long> effectivePayers = Arrays.asList(
                    (Long[]) rs.getArray("effective_payer_account_ids").getArray());
            EntityId sender = ObjectUtils.isNotEmpty(effectivePayers) ? EntityId.of(effectivePayers.get(0)) : null;
            EntityId receiver = EntityId.of(rs.getLong("collector_account_id"));
            return new SharedTransfer(
                    rs.getLong("amount"),
                    rs.getLong("consensus_timestamp"),
                    EntityId.of(rs.getLong("payer_account_id")),
                    receiver,
                    sender);
        });
    }

    private List<SharedTransfer> findCryptoTransfers() {
        return jdbcOperations.query("select * from crypto_transfer", (rs, rowNum) -> {
            Long amount = rs.getLong("amount");
            EntityId sender = amount < 0 ? EntityId.of(rs.getLong("entity_id")) : null;
            EntityId receiver = amount > 0 ? EntityId.of(rs.getLong("entity_id")) : null;
            return new SharedTransfer(
                    amount,
                    rs.getLong("consensus_timestamp"),
                    EntityId.of(rs.getLong("payer_account_id")),
                    receiver,
                    sender);
        });
    }

    private List<SharedTransfer> findNftTransfers() {
        return jdbcOperations.query("select * from nft_transfer", (rs, rowNum) -> {
            return new SharedTransfer(
                    rs.getLong("serial_number"),
                    rs.getLong("consensus_timestamp"),
                    EntityId.of(rs.getLong("payer_account_id")),
                    EntityId.of(rs.getLong("receiver_account_id")),
                    EntityId.of(rs.getLong("sender_account_id")));
        });
    }

    private List<SharedTransfer> findNonFeeTransfersAsSharedTransfers() {
        return jdbcOperations.query("select * from non_fee_transfer", (rs, rowNum) -> {
            Long amount = rs.getLong("amount");
            EntityId sender = amount < 0 ? EntityId.of(rs.getLong("entity_id")) : null;
            EntityId receiver = amount > 0 ? EntityId.of(rs.getLong("entity_id")) : null;
            return new SharedTransfer(
                    amount,
                    rs.getLong("consensus_timestamp"),
                    EntityId.of(rs.getLong("payer_account_id")),
                    receiver,
                    sender);
        });
    }

    private List<SharedTransfer> findTokenTransfers() {
        return jdbcOperations.query("select * from token_transfer", (rs, rowNum) -> {
            Long amount = rs.getLong("amount");
            EntityId sender = amount < 0 ? EntityId.of(rs.getLong("account_id")) : null;
            EntityId receiver = amount > 0 ? EntityId.of(rs.getLong("account_id")) : null;
            return new SharedTransfer(
                    amount,
                    rs.getLong("consensus_timestamp"),
                    EntityId.of(rs.getLong("payer_account_id")),
                    receiver,
                    sender);
        });
    }

    private void persistEntities(List<Entity> entities) {
        for (Entity entity : entities) {
            jdbcOperations.update(
                    "insert into entity (id, created_timestamp, num, realm, shard, type, timestamp_range) values (?,"
                            + "?,?,?,?,?,?::int8range)",
                    entity.getId(),
                    entity.getCreatedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    entity.getType().getId(),
                    PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
        }
    }

    private void persistTransactions(List<MigrationTransaction> transactions) {
        transactions.forEach(t -> {
            jdbcOperations.update(c -> {
                PreparedStatement preparedStatement = c.prepareStatement("insert into transaction "
                        + "(consensus_timestamp, entity_id, node_account_id, payer_account_id, result, type, "
                        + "valid_start_ns) "
                        + "values (?, ?, ?, ?, ?, ?, ?)");
                preparedStatement.setLong(1, t.getConsensusTimestamp());
                preparedStatement.setLong(2, t.getEntityId());
                preparedStatement.setLong(3, t.getNodeAccountId());
                preparedStatement.setLong(4, t.getPayerAccountId());
                preparedStatement.setInt(5, t.getResult());
                preparedStatement.setInt(6, t.getType());
                preparedStatement.setLong(7, t.getValidStartNs());
                return preparedStatement;
            });
        });
    }

    /**
     * Ensure entity tables match expected state before V_1_47.0
     */
    private void revertToPreV_1_47() {
        // drop payer_account_id columns
        ownerJdbcTemplate.execute("""
            alter table if exists assessed_custom_fee drop column if exists payer_account_id;
            alter table if exists crypto_transfer drop column if exists payer_account_id;
            alter table if exists nft_transfer drop column if exists payer_account_id;
            alter table if exists non_fee_transfer drop column if exists payer_account_id;
            alter table if exists token_transfer drop column if exists payer_account_id;
            """);
    }

    private String longListSerializer(List<Long> longs) {
        return longs == null ? "" : "{" + StringUtils.join(longs, ",") + "}";
    }

    @AllArgsConstructor
    @Builder
    @Data
    @NoArgsConstructor
    private static class MigrationNftTransfer {
        long consensusTimestamp;
        Long receiverAccountId;
        Long senderAccountId;
        long serialNumber;
        long tokenId;
    }

    @Data
    private static class MigrationTransaction {
        private Long consensusTimestamp;
        private Long entityId;
        private Long nodeAccountId;
        private Long payerAccountId;
        private Integer result;
        private Integer type;
        private Long validStartNs;
    }

    // custom class with shared attributes for all transfer classes prior to migration
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SharedTransfer {
        private long amount;
        private long consensusTimeStamp;
        private EntityId payerAccountId;
        private EntityId receiver;
        private EntityId sender;
    }

    @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE) // For Builder
    @Builder
    @Data
    @NoArgsConstructor
    public static class NonFeeTransfer {

        private Long amount;

        private Long consensusTimestamp;

        private EntityId entityId;

        private Boolean isApproval;

        private EntityId payerAccountId;
    }
}
