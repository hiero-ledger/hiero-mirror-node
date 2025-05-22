// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.File;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.44.1")
class SupportDeletedTokenDissociateMigrationTest extends ImporterIntegrationTest {

    private static final int TRANSACTION_TYPE_TOKEN_DISSOCIATE = 41;
    private static final EntityId TREASURY = EntityId.of("0.0.200");
    private static final EntityId NEW_TREASURY = EntityId.of("0.0.201");
    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3);

    @Value("classpath:db/migration/v1/V1.45.0__support_deleted_token_dissociate.sql")
    private File migrationSql;

    @Test
    void verify() {
        // given
        // entities
        // - 2 ft classes
        //   - deleted, account1's token dissociate includes token transfer
        //   - still alive
        // - 3 nft classes
        //   - deleted, account1's token dissociate doesn't include token transfer, account2's includes
        //   - deleted, account1's token dissociate doesn't include token transfer, account2's dissociate happened
        //     before token deletion
        //   - still alive
        EntityId account1 = EntityId.of("0.0.210");
        EntityId account2 = EntityId.of("0.0.211");
        EntityId ftId1 = EntityId.of("0.0.500");
        EntityId ftId2 = EntityId.of("0.0.501");
        EntityId nftId1 = EntityId.of("0.0.502");
        EntityId nftId2 = EntityId.of("0.0.503");
        EntityId nftId3 = EntityId.of("0.0.504");

        Token ftClass1 = token(10L, ftId1, FUNGIBLE_COMMON);
        Token ftClass2 = token(15L, ftId2, FUNGIBLE_COMMON);
        Token nftClass1 = token(20L, nftId1, NON_FUNGIBLE_UNIQUE);
        Token nftClass2 = token(25L, nftId2, NON_FUNGIBLE_UNIQUE);
        Token nftClass3 = token(30L, nftId3, NON_FUNGIBLE_UNIQUE);

        MigrationEntity ft1Entity = entity(ftClass1, true, 50L);
        MigrationEntity ft2Entity = entity(ftClass2);
        MigrationEntity nft1Entity = entity(nftClass1, true, 55L);
        MigrationEntity nft2Entity = entity(nftClass2, true, 60L);
        MigrationEntity nft3Entity = entity(nftClass3);

        persistEntities(List.of(ft1Entity, ft2Entity, nft1Entity, nft2Entity, nft3Entity));

        long account1Ft1DissociateTimestamp = 70;
        long account1Nft1DissociateTimestamp = 75;
        long account2Nft1DissociateTimestamp = 80;
        long account1Nft2DissociateTimestamp = 85;
        long account2Nft2DissociateTimestamp = 55; // happened before token deletion
        List<MigrationTokenAccount> tokenAccounts = List.of(
                tokenAccount(account1, true, 12L, 12L, ftId1),
                tokenAccount(account1, false, 12L, account1Ft1DissociateTimestamp, ftId1),
                tokenAccount(account2, true, 15L, 15L, ftId1),
                tokenAccount(account1, true, 20L, 20L, ftId2),
                tokenAccount(account1, true, 23L, 23L, nftId1),
                tokenAccount(account1, false, 23L, account1Nft1DissociateTimestamp, nftId1),
                tokenAccount(account2, true, 25L, 25L, nftId1),
                tokenAccount(account2, false, 25L, account2Nft1DissociateTimestamp, nftId1),
                tokenAccount(account1, true, 27L, 27L, nftId2),
                tokenAccount(account1, false, 27L, account1Nft2DissociateTimestamp, nftId2),
                tokenAccount(account2, true, 29L, 29L, nftId2),
                tokenAccount(account2, false, 29L, account2Nft2DissociateTimestamp, nftId2));
        persistTokenAccounts(tokenAccounts);

        // token dissociate transactions
        List<Transaction> transactions = List.of(
                tokenDissociateTransaction(account1Ft1DissociateTimestamp, account1),
                tokenDissociateTransaction(account1Nft1DissociateTimestamp, account1),
                tokenDissociateTransaction(account2Nft1DissociateTimestamp, account2),
                tokenDissociateTransaction(account1Nft2DissociateTimestamp, account1),
                tokenDissociateTransaction(account2Nft2DissociateTimestamp, account2));
        persistTransactions(transactions);

        // transfers
        persistTokenTransfers(List.of(
                new TokenTransfer(account1Ft1DissociateTimestamp, -10, ftId1, account1),
                new TokenTransfer(account2Nft1DissociateTimestamp, -1, nftId1, account2)));

        // nfts
        // - 2 for <account1, nftId1>, 1 already deleted before dissociate, the other without dissociate transfer
        // - 2 for <account1, nftId2>, 1 already deleted before dissociate, the other without dissociate transfer
        // - 2 for <account2, nftId1>, 1 already deleted before dissociate, the other with dissociate transfer
        // - 1 for <account2, nftId2>, already deleted, account2 dissociated nftId2 before nft class deletion
        // - 1 for <account1, nftId3>
        // - 1 for <account2, nftId3>
        persistNfts(List.of(
                nft(account1, 25L, true, 27L, 1L, nftId1),
                nft(account1, 25L, false, 25L, 2L, nftId1),
                nft(account1, 30L, true, 35L, 1L, nftId2),
                nft(account1, 30L, false, 30L, 2L, nftId2),
                nft(account1, 40L, false, 40L, 1L, nftId3),
                nft(account2, 28L, true, 32L, 3L, nftId1),
                nft(account2, 28L, false, 28L, 4L, nftId1),
                nft(account2, 33L, true, 37L, 3L, nftId2),
                nft(account2, 45L, false, 45L, 2L, nftId3)));

        // nft transfers from nft class treasury update
        persistNftTransfer(nftTransfer(40L, NEW_TREASURY, TREASURY, NftTransfer.WILDCARD_SERIAL_NUMBER, nftId3));

        // expected token changes
        ftClass1.setTotalSupply(ftClass1.getTotalSupply() - 10);
        ftClass1.setTimestampLower(account1Ft1DissociateTimestamp);
        // 1 nft wiped from explicit token transfer of the token dissociate, 1 wiped from a previous token dissociate
        // without explicit token transfer
        nftClass1.setTotalSupply(nftClass1.getTotalSupply() - 2);
        nftClass1.setTimestampLower(account2Nft1DissociateTimestamp);
        nftClass2.setTotalSupply(nftClass2.getTotalSupply() - 1);
        nftClass2.setTimestampLower(account1Nft2DissociateTimestamp);

        // when
        migrate();

        // then
        assertThat(findAllNfts())
                .containsExactlyInAnyOrder(
                        nft(account1, 25L, true, 27L, 1L, nftId1),
                        nft(account1, 25L, true, account1Nft1DissociateTimestamp, 2L, nftId1),
                        nft(account1, 30L, true, 35L, 1L, nftId2),
                        nft(account1, 30L, true, account1Nft2DissociateTimestamp, 2L, nftId2),
                        nft(account1, 40L, false, 40L, 1L, nftId3),
                        nft(account2, 28L, true, 32L, 3L, nftId1),
                        nft(account2, 28L, true, account2Nft1DissociateTimestamp, 4L, nftId1),
                        nft(account2, 33L, true, 37L, 3L, nftId2),
                        nft(account2, 45L, false, 45L, 2L, nftId3));
        // expect new nft transfers from token dissociate of deleted nft class
        // expect nft transfers for nft treasury update removed
        assertThat(findAllNftTransfers())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "payerAccountId", "senderAccountId")
                .containsExactlyInAnyOrder(
                        nftTransfer(account1Nft1DissociateTimestamp, null, account1, 2L, nftId1),
                        nftTransfer(account1Nft2DissociateTimestamp, null, account1, 2L, nftId2),
                        nftTransfer(account2Nft1DissociateTimestamp, null, account2, 4L, nftId1));
        assertThat(findAllTokenAccounts()).containsExactlyInAnyOrderElementsOf(tokenAccounts);
        assertThat(findAllTokens())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                        "tokenId", "pauseStatus", "treasuryAccountId")
                .containsExactlyInAnyOrder(ftClass1, ftClass2, nftClass1, nftClass2, nftClass3);
        // the token transfer for nft should have been removed
        assertThat(findAllTokenTransfers())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "payerAccountId", "senderAccountId")
                .containsExactlyInAnyOrder(new TokenTransfer(account1Ft1DissociateTimestamp, -10, ftId1, account1));
        assertThat(findAllTransactions()).containsExactlyInAnyOrderElementsOf(transactions);
    }

    @SneakyThrows
    private void migrate() {
        ownerJdbcTemplate.execute(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private MigrationEntity entity(Token token) {
        return entity(token, false, token.getCreatedTimestamp());
    }

    private MigrationEntity entity(Token token, boolean deleted, long modifiedTimestamp) {
        long id = token.getTokenId();
        MigrationEntity entity = new MigrationEntity();
        entity.setCreatedTimestamp(token.getCreatedTimestamp());
        entity.setDeleted(deleted);
        entity.setId(id);
        entity.setModifiedTimestamp(modifiedTimestamp);
        entity.setNum(id);
        entity.setType(TOKEN.getId());
        return entity;
    }

    private Collection<MigrationNft> findAllNfts() {
        return jdbcOperations.query("select * from nft", (rs, rowNum) -> {
            var nft = new MigrationNft();
            nft.setAccountId(rs.getLong("account_id"));
            nft.setCreatedTimestamp(rs.getLong("created_timestamp"));
            nft.setDeleted(rs.getBoolean("deleted"));
            nft.setMetadata(rs.getBytes("metadata"));
            nft.setModifiedTimestamp(rs.getLong("modified_timestamp"));
            nft.setSerialNumber(rs.getLong("serial_number"));
            nft.setTokenId(rs.getLong("token_id"));
            return nft;
        });
    }

    public Collection<MigrationTokenAccount> findAllTokenAccounts() {
        return jdbcOperations.query("select * from token_account", (rs, rowNum) -> {
            var tokenAccount = new MigrationTokenAccount();
            tokenAccount.setAccountId(EntityId.of(rs.getLong("account_id")));
            tokenAccount.setAssociated(rs.getBoolean("associated"));
            tokenAccount.setAutomaticAssociation(rs.getBoolean("automatic_association"));
            tokenAccount.setCreatedTimestamp(rs.getLong("created_timestamp"));
            tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
            tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
            tokenAccount.setModifiedTimestamp(rs.getLong("modified_timestamp"));
            tokenAccount.setTokenId(EntityId.of(rs.getLong("token_id")));
            return tokenAccount;
        });
    }

    private List<Token> findAllTokens() {
        return jdbcOperations.query("select * from token", (rs, rowNum) -> {
            Token token = new Token();
            token.setCreatedTimestamp(rs.getLong("created_timestamp"));
            token.setDecimals(rs.getInt("decimals"));
            token.setFreezeDefault(rs.getBoolean("freeze_default"));
            token.setInitialSupply(rs.getLong("initial_supply"));
            token.setName(rs.getString("name"));
            token.setSupplyType(TokenSupplyTypeEnum.valueOf(rs.getString("supply_type")));
            token.setSymbol(rs.getString("symbol"));
            token.setTimestampLower(rs.getLong("modified_timestamp"));
            token.setTokenId(rs.getLong("token_id"));
            token.setTotalSupply(rs.getLong("total_supply"));
            token.setTreasuryAccountId(EntityId.of(rs.getLong("treasury_account_id")));
            token.setType(TokenTypeEnum.valueOf(rs.getString("type")));
            return token;
        });
    }

    private MigrationNft nft(
            EntityId accountId,
            long createdTimestamp,
            boolean deleted,
            long modifiedTimestamp,
            long serialNumber,
            EntityId tokenId) {
        var nft = new MigrationNft();
        nft.setAccountId(accountId != null ? accountId.getId() : null);
        nft.setCreatedTimestamp(createdTimestamp);
        nft.setDeleted(deleted);
        nft.setMetadata(new byte[] {1});
        nft.setModifiedTimestamp(modifiedTimestamp);
        nft.setSerialNumber(serialNumber);
        nft.setTokenId(tokenId.getId());
        return nft;
    }

    private MigrationNftTransfer nftTransfer(
            long consensusTimestamp, EntityId receiver, EntityId sender, long serialNumber, EntityId tokenId) {
        return MigrationNftTransfer.builder()
                .consensusTimestamp(consensusTimestamp)
                .receiverAccountId(receiver)
                .senderAccountId(sender)
                .serialNumber(serialNumber)
                .tokenId(tokenId.getId())
                .build();
    }

    private Token token(long createdTimestamp, EntityId tokenId, TokenTypeEnum tokenType) {
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(0);
        token.setFreezeDefault(false);
        token.setInitialSupply(0L);
        token.setName("foo");
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol("bar");
        token.setTimestampLower(createdTimestamp);
        token.setTokenId(tokenId.getId());
        token.setTotalSupply(1_000_000L);
        token.setTreasuryAccountId(TREASURY);
        token.setType(tokenType);

        String sql = "insert into token (created_timestamp, decimals, freeze_default, initial_supply, "
                + "modified_timestamp, name, supply_type, symbol, token_id, total_supply, treasury_account_id, type) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Object[] arguments = new Object[] {
            token.getCreatedTimestamp(),
            token.getDecimals(),
            token.getFreezeDefault(),
            token.getInitialSupply(),
            token.getTimestampLower(),
            token.getName(),
            token.getSupplyType(),
            token.getSymbol(),
            token.getTokenId(),
            token.getTotalSupply(),
            token.getTreasuryAccountId().getId(),
            token.getType()
        };

        int[] argumentTypes = new int[] {
            Types.BIGINT,
            Types.BIGINT,
            Types.BOOLEAN,
            Types.BIGINT,
            Types.BIGINT,
            Types.VARCHAR,
            Types.OTHER,
            Types.VARCHAR,
            Types.BIGINT,
            Types.BIGINT,
            Types.BIGINT,
            Types.OTHER
        };

        jdbcOperations.update(sql, arguments, argumentTypes);

        return token;
    }

    private MigrationTokenAccount tokenAccount(
            EntityId accountId, boolean associated, long createdTimestamp, long modifiedTimestamp, EntityId tokenId) {
        var tokenAccount = new MigrationTokenAccount();
        tokenAccount.setAccountId(accountId);
        tokenAccount.setAssociated(associated);
        tokenAccount.setAutomaticAssociation(false);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        tokenAccount.setModifiedTimestamp(modifiedTimestamp);
        tokenAccount.setTokenId(tokenId);
        return tokenAccount;
    }

    private Transaction tokenDissociateTransaction(long consensusNs, EntityId payer) {
        Transaction transaction = new Transaction();
        transaction.setConsensusTimestamp(consensusNs);
        transaction.setEntityId(payer);
        transaction.setPayerAccountId(payer);
        transaction.setNodeAccountId(NODE_ACCOUNT_ID);
        transaction.setResult(22);
        transaction.setType(TRANSACTION_TYPE_TOKEN_DISSOCIATE);
        transaction.setValidStartNs(consensusNs - 5);
        return transaction;
    }

    private void persistEntities(List<MigrationEntity> entities) {
        for (MigrationEntity entity : entities) {
            jdbcOperations.update(
                    "insert into entity (created_timestamp, deleted, id, modified_timestamp, num, realm, shard, type)"
                            + " values (?,?,?,?,?,?,?,?)",
                    entity.getCreatedTimestamp(),
                    entity.isDeleted(),
                    entity.getId(),
                    entity.getModifiedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    entity.getType());
        }
    }

    private void persistNfts(List<MigrationNft> nfts) {
        for (var nft : nfts) {
            jdbcOperations.update(
                    "insert into nft (account_id, created_timestamp, deleted, metadata, modified_timestamp, "
                            + "serial_number, token_id)"
                            + " values(?,?,?,?::bytea,?,?,?)",
                    nft.getAccountId(),
                    nft.getCreatedTimestamp(),
                    nft.getDeleted(),
                    "\\x" + Hex.encodeHexString(nft.getMetadata()),
                    nft.getModifiedTimestamp(),
                    nft.getSerialNumber(),
                    nft.getTokenId());
        }
    }

    private void persistTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            jdbcOperations.update(
                    "insert into transaction (charged_tx_fee, consensus_ns, entity_id, initial_balance, "
                            + "max_fee, memo, node_account_id, payer_account_id, result, scheduled, "
                            + "transaction_bytes, transaction_hash, type, valid_duration_seconds, "
                            + "valid_start_ns)"
                            + " values"
                            + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    transaction.getChargedTxFee(),
                    transaction.getConsensusTimestamp(),
                    transaction.getEntityId().getId(),
                    transaction.getInitialBalance(),
                    transaction.getMaxFee(),
                    transaction.getMemo(),
                    transaction.getNodeAccountId().getId(),
                    transaction.getPayerAccountId().getId(),
                    transaction.getResult(),
                    transaction.isScheduled(),
                    transaction.getTransactionBytes(),
                    transaction.getTransactionHash(),
                    transaction.getType(),
                    transaction.getValidDurationSeconds(),
                    transaction.getValidStartNs());
        }
    }

    private void persistTokenAccounts(List<MigrationTokenAccount> tokenAccounts) {
        for (var tokenAccount : tokenAccounts) {
            jdbcOperations.update(
                    "insert into token_account (account_id, associated, automatic_association, "
                            + "created_timestamp, freeze_status, kyc_status, modified_timestamp, token_id)"
                            + " values"
                            + " (?, ?, ?, ?, ?, ?, ?, ?)",
                    tokenAccount.getAccountId().getId(),
                    tokenAccount.getAssociated(),
                    tokenAccount.getAutomaticAssociation(),
                    tokenAccount.getCreatedTimestamp(),
                    0,
                    0,
                    tokenAccount.getModifiedTimestamp(),
                    tokenAccount.getTokenId().getId());
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

    private void persistNftTransfer(MigrationNftTransfer nftTransfer) {
        jdbcOperations.update(
                "insert into nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id, "
                        + "serial_number, token_id)"
                        + " values (?,?,?,?,?)",
                nftTransfer.getConsensusTimestamp(),
                EntityIdConverter.INSTANCE.convertToDatabaseColumn(nftTransfer.getReceiverAccountId()),
                EntityIdConverter.INSTANCE.convertToDatabaseColumn(nftTransfer.getSenderAccountId()),
                nftTransfer.getSerialNumber(),
                nftTransfer.getTokenId());
    }

    private List<Transaction> findAllTransactions() {
        return jdbcOperations.query("select * from transaction", (rs, rowNum) -> {
            Transaction transaction = new Transaction();
            transaction.setConsensusTimestamp(rs.getLong("consensus_ns"));
            transaction.setEntityId(EntityId.of(0, 0, rs.getLong("entity_id")));
            transaction.setMemo(rs.getBytes("transaction_bytes"));
            transaction.setNodeAccountId(EntityId.of(0, 0, rs.getLong("node_account_id")));
            transaction.setPayerAccountId(EntityId.of(0, 0, rs.getLong("payer_account_id")));
            transaction.setResult(rs.getInt("result"));
            transaction.setType(rs.getInt("type"));
            transaction.setValidStartNs(rs.getLong("valid_start_ns"));
            return transaction;
        });
    }

    private List<TokenTransfer> findAllTokenTransfers() {
        return jdbcOperations.query("select * from token_transfer", (rs, rowNum) -> {
            TokenTransfer tokenTransfer = new TokenTransfer();
            tokenTransfer.setId(new TokenTransfer.Id(
                    rs.getLong("consensus_timestamp"),
                    EntityId.of(rs.getLong("token_id")),
                    EntityId.of(rs.getLong("account_id"))));
            tokenTransfer.setAmount(rs.getLong("amount"));
            return tokenTransfer;
        });
    }

    private List<MigrationNftTransfer> findAllNftTransfers() {
        return jdbcOperations.query(
                "select * from nft_transfer", ImporterIntegrationTest.rowMapper(MigrationNftTransfer.class));
    }

    // Use a custom class for entity table since its columns have changed from the current domain object
    @Data
    @NoArgsConstructor
    private static class MigrationEntity {
        private Long createdTimestamp;
        private boolean deleted = false;
        private long id;
        private Long modifiedTimestamp;
        private long num;
        private long realm = 0;
        private long shard = 0;
        private int type;
    }

    // Use a custom class for nft table since its columns have changed from the current domain object
    @Data
    @NoArgsConstructor
    private static class MigrationNft {
        private Long accountId;
        private Long createdTimestamp;
        private Boolean deleted;
        private byte[] metadata;
        private long modifiedTimestamp;
        private long serialNumber;
        private long tokenId;
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Data
    @NoArgsConstructor
    private static class MigrationNftTransfer {
        private long consensusTimestamp;
        private EntityId receiverAccountId;
        private EntityId senderAccountId;
        private long serialNumber;
        private long tokenId;
    }

    // Use a custom class for the token account table since its columns have changed from the current domain object
    @Data
    @NoArgsConstructor
    private static class MigrationTokenAccount {
        private EntityId accountId;
        private Boolean associated;
        private Boolean automaticAssociation;
        private long createdTimestamp;

        @Enumerated(EnumType.ORDINAL)
        private TokenFreezeStatusEnum freezeStatus;

        @Enumerated(EnumType.ORDINAL)
        private TokenKycStatusEnum kycStatus;

        private long modifiedTimestamp;
        private EntityId tokenId;
    }
}
