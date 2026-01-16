// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.IterableAssert;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.ErrataType;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@SuppressWarnings("java:S5786")
@RequiredArgsConstructor
@Tag("migration")
public class ErrataMigrationTest extends ImporterIntegrationTest {

    public static final long BAD_TIMESTAMP1 = 1568415600193620000L;
    private static final long BAD_TIMESTAMP2 = 1568528100472477002L;
    private static final long BAD_TIMESTAMP_FIXED_OFFSET = 1658421000626004000L;
    private static final long RECEIVER_PAYER_TIMESTAMP = 1570118944399195000L;

    private static final AccountBalanceFile EXPECTED_ACCOUNT_BALANCE_FILE1 = AccountBalanceFile.builder()
            .consensusTimestamp(BAD_TIMESTAMP1)
            .timeOffset(-1)
            .build();
    private static final AccountBalanceFile EXPECTED_ACCOUNT_BALANCE_FILE2 = AccountBalanceFile.builder()
            .consensusTimestamp(BAD_TIMESTAMP2)
            .timeOffset(-1)
            .build();
    private static final AccountBalanceFile EXPECTED_ACCOUNT_BALANCE_FILE_FIXED_OFFSET = AccountBalanceFile.builder()
            .consensusTimestamp(BAD_TIMESTAMP_FIXED_OFFSET)
            .timeOffset(53)
            .build();
    private static final List<AccountBalanceFile> EXPECTED_ACCOUNT_BALANCE_FILES = List.of(
            EXPECTED_ACCOUNT_BALANCE_FILE1, EXPECTED_ACCOUNT_BALANCE_FILE2, EXPECTED_ACCOUNT_BALANCE_FILE_FIXED_OFFSET);

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final ContractResultRepository contractResultRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityProperties entityProperties;
    private final EntityRepository entityRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final ErrataMigration errataMigration;
    private final ImporterProperties importerProperties;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void setup() {
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.MAINNET);
    }

    @AfterEach
    void teardown() {
        importerProperties.setEndDate(Utility.MAX_INSTANT_LONG);
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        importerProperties.setStartDate(Instant.EPOCH);
    }

    @Test
    void checksum() {
        assertThat(errataMigration.getChecksum()).isEqualTo(6);
    }

    @Test
    void migrateNotMainnet() throws Exception {
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        domainBuilder.accountBalanceFile().persist();
        domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(BAD_TIMESTAMP1))
                .persist();
        spuriousTransfer(1L, 10, TransactionType.CRYPTOTRANSFER, false, false);

        errataMigration.doMigrate();

        assertBalanceOffsets(Collections.emptyList());
        assertErrataTransfers(ErrataType.INSERT, 0);
        assertErrataTransfers(ErrataType.DELETE, 0);
        assertErrataTransactions(ErrataType.INSERT, 0);
        assertErrataTransactions(ErrataType.DELETE, 0);
        assertThat(contractResultRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
        assertThat(findHistory(Entity.class)).isEmpty();

        assertThat(entityProperties.getPersist().isEntityHistory()).isTrue();
        assertThat(entityProperties.getPersist().isTrackBalance()).isTrue();
    }

    @Test
    void migrateOutsideDateRange() throws Exception {
        Instant now = Instant.now();
        importerProperties.setStartDate(now);
        importerProperties.setEndDate(now.plusSeconds(1L));

        errataMigration.doMigrate();

        assertErrataTransfers(ErrataType.INSERT, 0);
        assertErrataTransactions(ErrataType.INSERT, 0);
        assertThat(contractResultRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
        assertThat(findHistory(Entity.class)).isEmpty();

        assertThat(entityProperties.getPersist().isEntityHistory()).isTrue();
        assertThat(entityProperties.getPersist().isTrackBalance()).isTrue();
    }

    @Test
    void migrateMainnet() throws Exception {
        domainBuilder.accountBalanceFile().persist();
        domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(BAD_TIMESTAMP1))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(BAD_TIMESTAMP2))
                .persist();
        domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(BAD_TIMESTAMP_FIXED_OFFSET))
                .persist();
        spuriousTransfer(RECEIVER_PAYER_TIMESTAMP, 10, TransactionType.CRYPTOTRANSFER, true, false); // Expected
        spuriousTransfer(1L, 15, TransactionType.CRYPTOTRANSFER, false, false); // Expected
        spuriousTransfer(2L, 15, TransactionType.CRYPTOTRANSFER, false, true); // Expected
        spuriousTransfer(3L, 22, TransactionType.CRYPTOTRANSFER, false, false); // Wrong result
        spuriousTransfer(4L, 10, TransactionType.CRYPTODELETE, true, false); // Wrong type
        spuriousTransfer(1577836799000000000L, 10, TransactionType.CRYPTOTRANSFER, false, false); // Outside period

        errataMigration.doMigrate();

        assertBalanceOffsets(EXPECTED_ACCOUNT_BALANCE_FILES);
        assertThat(contractResultRepository.count()).isEqualTo(1L);
        assertThat(entityRepository.count()).isZero();
        assertThat(findHistory(Entity.class)).isEmpty();
        assertThat(tokenTransferRepository.count()).isEqualTo(24L);
        assertErrataTransactions(ErrataType.INSERT, 113);
        assertErrataTransactions(ErrataType.DELETE, 0);
        assertErrataTransfers(ErrataType.INSERT, 566);
        assertErrataTransfers(ErrataType.DELETE, 6)
                .extracting(CryptoTransfer::getConsensusTimestamp)
                .containsOnly(1L, 2L, RECEIVER_PAYER_TIMESTAMP);

        assertThat(entityProperties.getPersist().isEntityHistory()).isTrue();
        assertThat(entityProperties.getPersist().isTrackBalance()).isTrue();
    }

    @Test
    void migrateWithExistingData() throws Exception {
        var now = DomainUtils.convertToNanosMax(Instant.now());
        domainBuilder.recordFile().customize(r -> r.consensusStart(now)).persist();
        migrateMainnet();
    }

    @Test
    void migrateIdempotency() throws Exception {
        migrateMainnet();
        errataMigration.doMigrate();
        assertBalanceOffsets(EXPECTED_ACCOUNT_BALANCE_FILES);
        assertErrataTransactions(ErrataType.INSERT, 113);
        assertErrataTransactions(ErrataType.DELETE, 0);
        assertErrataTransfers(ErrataType.INSERT, 566);
        assertErrataTransfers(ErrataType.DELETE, 6);
        assertThat(contractResultRepository.count()).isEqualTo(1L);
        assertThat(entityRepository.count()).isZero();
        assertThat(findHistory(Entity.class)).isEmpty();
        assertThat(tokenTransferRepository.count()).isEqualTo(24L);

        assertThat(entityProperties.getPersist().isEntityHistory()).isTrue();
        assertThat(entityProperties.getPersist().isTrackBalance()).isTrue();
    }

    @Test
    void migrateMissedTokenTransfers() throws Exception {
        long existingTimestamp = DomainUtils.convertToNanosMax(Instant.parse("2023-02-10T02:16:18.649144003Z"));
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(existingTimestamp))
                .persist();
        errataMigration.doMigrate();
        assertThat(tokenTransferRepository.count()).isEqualTo(24L);

        assertThat(entityProperties.getPersist().isEntityHistory()).isTrue();
        assertThat(entityProperties.getPersist().isTrackBalance()).isTrue();
    }

    @Test
    void onEndWithoutOffset() {
        AccountBalanceFile accountBalanceFile =
                domainBuilder.accountBalanceFile().get();
        accountBalanceFile.setConsensusTimestamp(1L);
        errataMigration.onEnd(accountBalanceFile);
        assertThat(accountBalanceFile.getTimeOffset()).isZero();
    }

    @Test
    void onEndWithOffset() {
        AccountBalanceFile accountBalanceFile =
                domainBuilder.accountBalanceFile().get();
        accountBalanceFile.setConsensusTimestamp(BAD_TIMESTAMP1);
        errataMigration.onEnd(accountBalanceFile);
        assertThat(accountBalanceFile.getTimeOffset()).isEqualTo(-1);

        accountBalanceFile = domainBuilder.accountBalanceFile().get();
        accountBalanceFile.setConsensusTimestamp(BAD_TIMESTAMP_FIXED_OFFSET);
        errataMigration.onEnd(accountBalanceFile);
        assertThat(accountBalanceFile.getTimeOffset()).isEqualTo(53);
    }

    @Test
    void onEndNotMainnet() {
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
        accountBalanceFile.setConsensusTimestamp(BAD_TIMESTAMP1);
        errataMigration.onEnd(accountBalanceFile);
        assertThat(accountBalanceFile.getTimeOffset()).isZero();
    }

    private void assertBalanceOffsets(List<AccountBalanceFile> expected) {
        assertThat(accountBalanceFileRepository.findAll())
                .filteredOn(a -> a.getTimeOffset() != 0)
                .usingRecursiveFieldByFieldElementComparatorOnFields("consensusTimestamp", "timeOffset")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private IterableAssert<CryptoTransfer> assertErrataTransfers(ErrataType errata, int expected) {
        return assertThat(cryptoTransferRepository.findAll())
                .filteredOn(c -> c.getErrata() == errata)
                .hasSize(expected);
    }

    private void assertErrataTransactions(ErrataType errata, int expected) {
        assertThat(transactionRepository.findAll())
                .filteredOn(c -> c.getErrata() == errata)
                .hasSize(expected);
    }

    private void spuriousTransfer(
            long consensusTimestamp, int result, TransactionType type, boolean receiverIsPayer, boolean senderIsPayer) {
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .result(result)
                        .type(type.getProtoId())
                        .itemizedTransfer(null))
                .persist();
        long amount = 100000L;
        long payer = transaction.getPayerAccountId().getId() + 1000L;
        long sender = senderIsPayer ? payer : domainBuilder.id() + 1000L;
        long receiver = receiverIsPayer ? payer : domainBuilder.id() + 1000L;

        insertCryptoTransfer(transaction, sender, -amount);
        insertCryptoTransfer(transaction, receiver, amount);
        insertCryptoTransfer(transaction, 3L, 1L);
        insertCryptoTransfer(transaction, 98L, 2L);
        insertCryptoTransfer(transaction, 98L, 4L);
        insertCryptoTransfer(transaction, payer, -3L);
        insertCryptoTransfer(transaction, payer, -4L);
    }

    private void insertCryptoTransfer(Transaction transaction, long entityId, long amount) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(amount)
                        .consensusTimestamp(transaction.getConsensusTimestamp())
                        .entityId(entityId)
                        .payerAccountId(transaction.getPayerAccountId()))
                .persist();
    }
}
