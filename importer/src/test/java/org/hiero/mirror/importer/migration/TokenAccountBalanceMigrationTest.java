// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;

import com.google.common.collect.Range;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainWrapper;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TokenAccountHistoryRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
@Tag("migration")
class TokenAccountBalanceMigrationTest extends ImporterIntegrationTest {

    private static final String DELETE_TOKEN_BALANCE_SQL =
            "delete from token_balance where consensus_timestamp <= :consensusTimestamp";

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final RecordFileRepository recordFileRepository;

    private final NamedParameterJdbcOperations namedParameterJdbcOperations;
    private final TransactionTemplate transactionTemplate;

    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final ImporterProperties importerProperties;

    private TokenAccountBalanceMigration tokenAccountBalanceMigration;
    private AccountBalanceFile accountBalanceFile1;
    private AccountBalanceFile accountBalanceFile2;
    private AtomicLong timestamp;
    private TokenAccount tokenAccount;
    private TokenAccount tokenAccount2;
    private TokenAccount tokenAccount3;
    private TokenAccount deletedEntityTokenAccount4;
    private TokenAccount disassociatedTokenAccount5;
    private TokenBalance tokenBalance;

    @BeforeEach
    void beforeEach() {
        timestamp = new AtomicLong(domainBuilder.timestamp());

        var accountBalanceFileRepositoryProvider = objectProvider(accountBalanceFileRepository);
        var namedParameterJdbcTemplateProvider = objectProvider(namedParameterJdbcOperations);
        var recordFileRepositoryProvider = objectProvider(recordFileRepository);
        var transactionTemplateProvider = objectProvider(transactionTemplate);
        tokenAccountBalanceMigration = new TokenAccountBalanceMigration(
                accountBalanceFileRepositoryProvider,
                importerProperties,
                namedParameterJdbcTemplateProvider,
                recordFileRepositoryProvider,
                transactionTemplateProvider);
    }

    @Test
    void checksum() {
        assertThat(tokenAccountBalanceMigration.getChecksum()).isEqualTo(3);
    }

    @Test
    void migrateWhenEmpty() {
        tokenAccountBalanceMigration.doMigrate();
        assertThat(tokenAccountRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        setup();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateTokenTransfer() {
        // given
        setup();

        // token transfer between the consensusTimestamp of the balance file and the current timestamp
        var balanceUpdatedAfterBalanceFileConsensusTimestamp = 12345L;
        var tokenTransferId = new TokenTransfer.Id(
                accountBalanceFile2.getConsensusTimestamp() + 1,
                tokenBalance.getId().getTokenId(),
                tokenBalance.getId().getAccountId());
        domainBuilder
                .tokenTransfer()
                .customize(c -> c.id(tokenTransferId)
                        .amount(balanceUpdatedAfterBalanceFileConsensusTimestamp)
                        .build())
                .persist();
        var secondBalanceUpdate = 222L;
        var tokenTransferId2 = new TokenTransfer.Id(
                accountBalanceFile2.getConsensusTimestamp() + 2,
                tokenBalance.getId().getTokenId(),
                tokenBalance.getId().getAccountId());
        domainBuilder
                .tokenTransfer()
                .customize(
                        c -> c.id(tokenTransferId2).amount(secondBalanceUpdate).build())
                .persist();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(
                balanceUpdatedAfterBalanceFileConsensusTimestamp + secondBalanceUpdate + tokenAccount.getBalance());
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenNoRecordFile() {
        // given
        setup();
        recordFileRepository.deleteAll();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        deletedEntityTokenAccount4.setBalance(0L);
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                // Without a record file the balance timestamps are not altered by the migration
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("balanceTimestamp")
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    void migrateWhenNoAccountBalance() {
        // given
        setup();
        accountBalanceFileRepository.deleteAll();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        deletedEntityTokenAccount4.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                // Without an account balance file the balance timestamps are not altered by the migration
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("balanceTimestamp")
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    void migrateWhenNoTokenBalance() {
        // given
        setup();
        namedParameterJdbcOperations.update(
                DELETE_TOKEN_BALANCE_SQL, Map.of("consensusTimestamp", accountBalanceFile2.getConsensusTimestamp()));

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        deletedEntityTokenAccount4.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                // Without a token balance the balance timestamps are not altered by the migration
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("balanceTimestamp")
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    void migrateWhenNoTokenTransfer() {
        // given
        setup();
        tokenTransferRepository.prune(timestamp(Duration.ofSeconds(10)));

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(100L);
        tokenAccount2.setBalance(33L);
        deletedEntityTokenAccount4.setBalance(999999L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    void migrateWhenNoTokenAccount() {
        // given
        setup();
        tokenAccountRepository.deleteAll();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        disassociatedTokenAccount5.setBalance(30L);
        assertThat(tokenAccountRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                        "associated", "automaticAssociation", "createdTimestamp", "timestampRange")
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5)
                .allSatisfy(t -> assertThat(t)
                        .returns(true, TokenAccount::getAssociated)
                        .returns(null, TokenAccount::getAutomaticAssociation)
                        .returns(0L, TokenAccount::getCreatedTimestamp)
                        .returns(Range.atLeast(0L), TokenAccount::getTimestampRange));
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    @Transactional
    void onEndWhenNotFirstFile() {
        // given
        setup();
        // when
        tokenAccountBalanceMigration.onEnd(accountBalanceFile2);

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        deletedEntityTokenAccount4.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    @Transactional
    void onEndEarlyReturn() {
        // given
        var transactionManager = new DataSourceTransactionManager(
                Objects.requireNonNull(((NamedParameterJdbcTemplate) namedParameterJdbcOperations)
                        .getJdbcTemplate()
                        .getDataSource()));
        var txnTemplate = new TransactionTemplate(transactionManager);
        setup();
        accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());

        txnTemplate.executeWithoutResult(s -> tokenAccountBalanceMigration.onEnd(accountBalanceFile1));

        long accountBalanceTimestamp3 = timestamp(Duration.ofMinutes(10));
        var accountBalanceFile3 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp3))
                .persist();
        // when
        tokenAccountBalanceMigration.onEnd(accountBalanceFile3);

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        deletedEntityTokenAccount4.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    @Transactional
    void onEndWhenNoRecordFileAfterTimestamp() {
        // given
        initialSetup();
        accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());

        // when
        tokenAccountBalanceMigration.onEnd(accountBalanceFile1);

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        deletedEntityTokenAccount4.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    @Transactional
    void onEnd() {
        // given
        setup();
        accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());

        // when
        tokenAccountBalanceMigration.onEnd(accountBalanceFile1);

        // then
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    private void setup() {
        initialSetup();

        // Second record file
        var secondRecordFile = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofSeconds(5)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();
        tokenAccount.setBalanceTimestamp(secondRecordFile.getConsensusEnd());
        tokenAccount2.setBalanceTimestamp(secondRecordFile.getConsensusEnd());
        tokenAccount3.setBalanceTimestamp(secondRecordFile.getConsensusEnd());
        deletedEntityTokenAccount4.setBalanceTimestamp(secondRecordFile.getConsensusEnd());
        disassociatedTokenAccount5.setBalanceTimestamp(secondRecordFile.getConsensusEnd());
    }

    private void initialSetup() {
        var entity1 = domainBuilder.entity().customize(e -> e.type(ACCOUNT)).persist();
        var accountId1 = EntityId.of(entity1.getId());
        var entity2 = domainBuilder.entity().customize(e -> e.type(ACCOUNT)).persist();
        var accountId2 = EntityId.of(entity2.getId());
        var entity4 = domainBuilder
                .entity()
                .customize(e -> e.type(ACCOUNT).deleted(true))
                .persist();
        var accountId4 = EntityId.of(entity4.getId());
        var entity5 = domainBuilder.entity().customize(e -> e.type(ACCOUNT)).persist();
        var accountId5 = EntityId.of(entity5.getId());

        var tokenId1 = EntityId.of("0.0.1000");
        var tokenId2 = EntityId.of("0.0.1001");

        domainBuilder
                .token()
                .customize(c -> c.supplyType(TokenSupplyTypeEnum.FINITE)
                        .tokenId(tokenId1.getId())
                        .totalSupply(1_000_000_000L)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .build())
                .persist();
        domainBuilder
                .token()
                .customize(c -> c.supplyType(TokenSupplyTypeEnum.FINITE)
                        .tokenId(tokenId2.getId())
                        .totalSupply(1_000_000_000L)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .build())
                .persist();

        // First record file, it's before account balance files
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofMinutes(10)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();

        // First account balance file
        var firstAccountBalanceFileTimestamp = timestamp(Duration.ofMinutes(10));
        accountBalanceFile1 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(firstAccountBalanceFileTimestamp))
                .persist();

        // Old token balance from first balance file
        // This balance is not expected to be migrated as it will not be in the latest balance file.
        var oldTokenBalanceId = new TokenBalance.Id(firstAccountBalanceFileTimestamp, accountId1, tokenId1);
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(oldTokenBalanceId).balance(99999999L))
                .persist();

        // Second account balance file, this file should be used for the token balance migration
        long accountBalanceTimestamp = timestamp(Duration.ofMinutes(10));
        accountBalanceFile2 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp))
                .persist();

        // A synthetic account balance file
        domainBuilder
                .accountBalanceFile()
                .customize(
                        a -> a.consensusTimestamp(accountBalanceTimestamp + 20).synthetic(true))
                .persist();

        var tokenBalanceId = new TokenBalance.Id(accountBalanceTimestamp, accountId1, tokenId1);
        var tokenBalanceId2 = new TokenBalance.Id(accountBalanceTimestamp, accountId1, tokenId2);
        var tokenBalanceId3 = new TokenBalance.Id(accountBalanceTimestamp, accountId2, tokenId1);
        var tokenBalanceId4 = new TokenBalance.Id(accountBalanceTimestamp, accountId4, tokenId1);
        var tokenBalanceId5 = new TokenBalance.Id(accountBalanceTimestamp, accountId5, tokenId1);

        var tokenBalance1Amount = 100L;
        tokenBalance = domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId).balance(tokenBalance1Amount))
                .persist();
        tokenAccount = tokenAccountBuilder()
                .customize(c -> c.accountId(accountId1.getId()).tokenId(tokenId1.getId()))
                .persist();
        tokenAccount2 = tokenAccountBuilder()
                .customize(c -> c.accountId(accountId1.getId()).tokenId(tokenId2.getId()))
                .persist();
        tokenAccount3 = tokenAccountBuilder()
                .customize(c -> c.accountId(accountId2.getId()).tokenId(tokenId1.getId()))
                .persist();
        // A deleted account, balances for this token account should be 0
        deletedEntityTokenAccount4 = tokenAccountBuilder()
                .customize(c -> c.accountId(accountId4.getId()).tokenId(tokenId1.getId()))
                .persist();
        // A disassociated token account, balances for this token account should be 0
        disassociatedTokenAccount5 = tokenAccountBuilder()
                .customize(c -> c.accountId(accountId5.getId())
                        .tokenId(tokenId1.getId())
                        .associated(false))
                .persist();

        var tokenBalance2Amount = 33L;
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId2).balance(tokenBalance2Amount))
                .persist();

        var tokenBalance3Amount = 4444L;
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId3).balance(tokenBalance3Amount))
                .persist();

        // Token balance to deleted account, this should not be migrated
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId4).balance(999999L))
                .persist();

        // Token balance to disassociated token account, this should not be migrated
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId5).balance(10L))
                .persist();

        // token transfer after the first account balance file
        var tokenTransfer2Amount = 1L;
        var tokenTransferId2 = new TokenTransfer.Id(timestamp(Duration.ofSeconds(1)), tokenId2, accountId1);
        domainBuilder
                .tokenTransfer()
                .customize(
                        c -> c.id(tokenTransferId2).amount(tokenTransfer2Amount).build())
                .persist();

        // Token transfer to deleted account, should not be added to token account balance
        var tokenTransferId4 = new TokenTransfer.Id(timestamp(Duration.ofSeconds(1)), tokenId1, accountId4);
        domainBuilder
                .tokenTransfer()
                .customize(c -> c.id(tokenTransferId4).amount(9876L).build())
                .persist();

        // Token transfer to disassociated token account, should not be added to token account balance
        var tokenTransferId5 = new TokenTransfer.Id(timestamp(Duration.ofSeconds(1)), tokenId1, accountId5);
        domainBuilder
                .tokenTransfer()
                .customize(c -> c.id(tokenTransferId5).amount(20L).build())
                .persist();

        tokenAccount.setBalance(tokenBalance1Amount);
        tokenAccount2.setBalance(tokenBalance2Amount + tokenTransfer2Amount);
        tokenAccount3.setBalance(tokenBalance3Amount);
        deletedEntityTokenAccount4.setBalance(1009875L);
        disassociatedTokenAccount5.setBalance(0L);
    }

    private long timestamp(Duration delta) {
        return timestamp.addAndGet(delta.toNanos());
    }

    private DomainWrapper<TokenAccount, TokenAccount.TokenAccountBuilder<?, ?>> tokenAccountBuilder() {
        return domainBuilder.tokenAccount().customize(ta -> ta.balance(0));
    }
}
