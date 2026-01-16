// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.addressbook.AddressBookService;
import org.hiero.mirror.importer.repository.AddressBookEntryRepository;
import org.hiero.mirror.importer.repository.AddressBookRepository;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class EntityRecordItemListenerFileTest extends AbstractEntityRecordItemListenerTest {

    private static final FileID FILE_ID = DOMAIN_BUILDER.entityNum(1001).toFileID();
    private static final byte[] FILE_CONTENTS = {'a', 'b', 'c'};
    private static final int TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT = 4;

    private final AddressBookRepository addressBookRepository;
    private final AddressBookEntryRepository addressBookEntryRepository;
    private final FileDataRepository fileDataRepository;
    private final AddressBookService addressBookService;

    @Value("classpath:addressbook/mainnet")
    private final File addressBookLarge;

    @Value("classpath:addressbook/testnet")
    private final File addressBookSmall;

    private FileID addressBookFileId;

    @BeforeEach
    void before() {
        addressBookFileId = systemEntity.addressBookFile102().toFileID();
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void fileCreate() {
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(transactionBody.getFileCreate(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileCreateDoNotPersist() {
        entityProperties.getPersist().setFiles(false);
        entityProperties.getPersist().setSystemFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
    }

    @Test
    void fileCreatePersistSystemPositive() {
        entityProperties.getPersist().setFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileID fileID =
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build();
        TransactionRecord txnRecord = transactionRecord(transactionBody, fileID);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(transactionBody.getFileCreate(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileCreatePersistSystemNegative() {
        entityProperties.getPersist().setFiles(false);
        Transaction transaction = fileCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileID fileID = domainBuilder.entityId().toFileID();
        TransactionRecord txnRecord = transactionRecord(transactionBody, fileID);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccessNoData(fileID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntity(transactionBody.getFileCreate(), txnRecord.getConsensusTimestamp()));
    }

    @ParameterizedTest(name = "with {0} s and expected {1} ns")
    @CsvSource({
        "9223372036854775807, 9223372036854775807",
        "31556889864403199, 9223372036854775807",
        "-9223372036854775808, -9223372036854775808",
        "-1000000000000000000, -9223372036854775808"
    })
    void fileCreateExpirationTimeOverflow(long seconds, long expectedNanosTimestamp) {
        Transaction transaction =
                fileCreateTransaction(Timestamp.newBuilder().setSeconds(seconds).build());
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var dbAccountEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());
        assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpirationTimestamp());
    }

    @Test
    void fileAppend() {
        Transaction transaction = fileAppendTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCount(1, 3, 1),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileData(transactionBody.getFileAppend().getContents(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileAppendToSystemFile() {
        FileID fileID = domainBuilder.entityId().toFileID();
        Transaction transaction = fileAppendTransaction(fileID, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody, fileID);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCount(1, 3, 1),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileData(transactionBody.getFileAppend().getContents(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateAllToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                this::assertRowCountOnTwoFileTransactions,
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateAllToExistingFailedTransaction() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(FILE_ID)),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntity(createTransactionBody.getFileCreate(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    @Transactional
    void fileAppendToAddressBook() throws IOException {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        byte[] addressBook = FileUtils.readFileToByteArray(addressBookLarge);
        byte[] addressBookUpdate = Arrays.copyOf(addressBook, 6144);
        byte[] addressBookAppend = Arrays.copyOfRange(addressBook, 6144, addressBook.length);

        // Initial address book update
        Transaction transactionUpdate = fileUpdateAllTransaction(addressBookFileId, addressBookUpdate);
        TransactionBody transactionBodyUpdate = getTransactionBody(transactionUpdate);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBodyUpdate.getFileUpdate();
        TransactionRecord recordUpdate = transactionRecord(transactionBodyUpdate, addressBookFileId);

        // Address book append
        Transaction transactionAppend = fileAppendTransaction(addressBookFileId, addressBookAppend);
        TransactionBody transactionBodyAppend = getTransactionBody(transactionAppend);
        FileAppendTransactionBody fileAppendTransactionBody = transactionBodyAppend.getFileAppend();
        TransactionRecord recordAppend = transactionRecord(transactionBodyAppend, addressBookFileId);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordUpdate)
                .transaction(transactionUpdate)
                .build());
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordAppend)
                .transaction(transactionAppend)
                .build());

        // verify current address book is updated
        AddressBook newAddressBook = addressBookService.getCurrent();
        assertAll(
                () -> assertThat(newAddressBook.getStartConsensusTimestamp())
                        .isEqualTo(DomainUtils.timeStampInNanos(recordAppend.getConsensusTimestamp()) + 1),
                () -> assertThat(newAddressBook.getEntries())
                        .describedAs("Should overwrite address book with new update")
                        .hasSize(13),
                () -> assertArrayEquals(addressBook, newAddressBook.getFileData()));

        assertAll(
                this::assertRowCountOnAddressBookTransactions,
                () -> assertTransactionAndRecord(transactionBodyUpdate, recordUpdate),
                () -> assertTransactionAndRecord(transactionBodyAppend, recordAppend),
                () -> assertFileData(fileAppendTransactionBody.getContents(), recordAppend.getConsensusTimestamp()),
                () -> assertFileData(fileUpdateTransactionBody.getContents(), recordUpdate.getConsensusTimestamp()),
                () -> assertAddressBookData(addressBook, recordAppend.getConsensusTimestamp()),
                () -> assertEquals(13 + TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(2, addressBookRepository.count()),
                () -> assertEquals(2, fileDataRepository.count()) // update and append
                );
    }

    @SneakyThrows
    @Test
    void fileAppendToAddressBookInSingleRecordFile() {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        byte[] addressBookCreate = Arrays.copyOf(FileUtils.readFileToByteArray(addressBookSmall), 6144);

        // Initial address book create
        Transaction transactionCreate = fileUpdateAllTransaction(addressBookFileId, addressBookCreate);
        TransactionBody transactionBodyCreate = getTransactionBody(transactionCreate);
        TransactionRecord recordCreate = transactionRecord(transactionBodyCreate, addressBookFileId);
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(transactionCreate)
                .build());

        assertAll(
                () -> assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(1, addressBookRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()) // update and append
                );

        byte[] addressBook = FileUtils.readFileToByteArray(addressBookLarge);
        byte[] addressBookUpdate = Arrays.copyOf(addressBook, 6144);
        byte[] addressBookAppend = Arrays.copyOfRange(addressBook, 6144, addressBook.length);

        // Initial address book update
        Transaction transactionUpdate = fileUpdateAllTransaction(addressBookFileId, addressBookUpdate);
        TransactionBody transactionBodyUpdate = getTransactionBody(transactionUpdate);
        TransactionRecord recordUpdate = transactionRecord(transactionBodyUpdate, addressBookFileId);

        // Address book append
        Transaction transactionAppend = fileAppendTransaction(addressBookFileId, addressBookAppend);
        TransactionBody transactionBodyAppend = getTransactionBody(transactionAppend);
        TransactionRecord recordAppend = transactionRecord(transactionBodyAppend, addressBookFileId);

        parseRecordItemsAndCommit(List.of(
                RecordItem.builder()
                        .transactionRecord(recordUpdate)
                        .transaction(transactionUpdate)
                        .build(),
                RecordItem.builder()
                        .transactionRecord(recordAppend)
                        .transaction(transactionAppend)
                        .build()));

        // verify current address book is updated
        AddressBook newAddressBook = addressBookService.getCurrent();
        assertAll(
                () -> assertThat(newAddressBook.getStartConsensusTimestamp())
                        .isEqualTo(DomainUtils.timeStampInNanos(recordAppend.getConsensusTimestamp()) + 1),
                () -> assertThat(newAddressBook.getEntries())
                        .describedAs("Should overwrite address book with new update")
                        .hasSize(13),
                () -> assertArrayEquals(addressBook, newAddressBook.getFileData()),
                () -> assertAddressBookData(addressBook, recordAppend.getConsensusTimestamp()),
                () -> assertEquals(13 + TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(2, addressBookRepository.count()),
                () -> assertEquals(3, fileDataRepository.count()) // update and append
                );
    }

    @Test
    void fileUpdateAllToNew() {
        Transaction transaction = fileUpdateAllTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateContentsToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);

        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateContentsTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                this::assertRowCountOnTwoFileTransactions,
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileData(transactionBody.getFileUpdate().getContents(), txnRecord.getConsensusTimestamp()),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertNotNull(actualFile.getExpirationTimestamp()),
                () -> assertNotNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateContentsToNew() {
        Transaction transaction = fileUpdateContentsTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileData(transactionBody.getFileUpdate().getContents(), txnRecord.getConsensusTimestamp()),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateExpiryToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateExpiryTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                this::assertRowCountOnTwoFileTransactions,
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(
                                transactionBody.getFileUpdate().getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertNotNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateExpiryToNew() {
        Transaction transaction = fileUpdateExpiryTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity actualFile = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                // Additional entity checks
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(
                                transactionBody.getFileUpdate().getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertNull(actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    @Test
    void fileUpdateKeysToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileUpdateKeysTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity dbFileEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                this::assertRowCountOnTwoFileTransactions,
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                // Additional entity checks
                () -> assertFalse(dbFileEntity.getDeleted()),
                () -> assertNotNull(dbFileEntity.getExpirationTimestamp()),
                () -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.getKey()),
                () -> assertNull(dbFileEntity.getAutoRenewPeriod()),
                () -> assertNull(dbFileEntity.getProxyAccountId()));
    }

    @Test
    void fileUpdateKeysToNew() {
        Transaction transaction = fileUpdateKeysTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity dbFileEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccess(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                // Additional entity checks
                () -> assertFalse(dbFileEntity.getDeleted()),
                () -> assertNull(dbFileEntity.getExpirationTimestamp()),
                () -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.getKey()),
                () -> assertNull(dbFileEntity.getAutoRenewPeriod()),
                () -> assertNull(dbFileEntity.getProxyAccountId()));
    }

    @Test
    void fileUpdateAllToNewSystem() {
        FileID fileID =
                FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(10).build();
        Transaction transaction = fileUpdateAllTransaction(fileID, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord txnRecord = transactionRecord(transactionBody, fileID);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateAddressBookPartial() throws IOException {
        byte[] largeAddressBook = FileUtils.readFileToByteArray(addressBookLarge);
        byte[] addressBookUpdate = Arrays.copyOf(largeAddressBook, largeAddressBook.length / 2);
        Transaction transaction = fileUpdateAllTransaction(addressBookFileId, addressBookUpdate);
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord txnRecord = transactionRecord(transactionBody, addressBookFileId);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        addressBookService.getCurrent();
        assertAll(
                () -> assertRowCountOnSuccess(addressBookFileId),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, txnRecord.getConsensusTimestamp()),
                () -> assertEquals(1, addressBookRepository.count()),
                () -> assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()));
    }

    @Test
    @Transactional
    void fileUpdateAddressBookComplete() throws IOException {
        byte[] addressBook = FileUtils.readFileToByteArray(addressBookSmall);
        assertThat(addressBook).hasSizeLessThan(6144);
        Transaction transaction = fileUpdateAllTransaction(addressBookFileId, addressBook);
        TransactionBody transactionBody = getTransactionBody(transaction);
        FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
        TransactionRecord txnRecord = transactionRecord(transactionBody, addressBookFileId);

        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        // verify current address book is changed
        AddressBook currentAddressBook = addressBookService.getCurrent();
        assertAll(
                () -> assertRowCountOnSuccess(addressBookFileId),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(fileUpdateTransactionBody, txnRecord.getConsensusTimestamp()),
                () -> assertAddressBookData(addressBook, txnRecord.getConsensusTimestamp()),
                () -> assertThat(currentAddressBook.getStartConsensusTimestamp())
                        .isEqualTo(DomainUtils.timeStampInNanos(txnRecord.getConsensusTimestamp()) + 1),
                () -> assertThat(currentAddressBook.getEntries()).hasSize(4),
                () -> assertEquals(2, addressBookRepository.count()),
                () -> assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + 4, addressBookEntryRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()));
    }

    @Test
    void fileUpdateFeeSchedule() {
        FileID fileId = FileID.newBuilder().setFileNum(111L).build();
        Transaction transaction = fileUpdateAllTransaction(fileId, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord =
                transactionRecord(transactionBody, ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileId),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileUpdateThrottles() {
        FileID fileId = FileID.newBuilder().setFileNum(123L).build();
        Transaction transaction = fileUpdateAllTransaction(fileId, FILE_CONTENTS);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord =
                transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccess(fileId),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityAndData(transactionBody.getFileUpdate(), txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileDeleteToExisting() {
        // first create the file
        Transaction fileCreateTransaction = fileCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(fileCreateTransaction);
        TransactionRecord recordCreate = transactionRecord(createTransactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(fileCreateTransaction)
                .build());

        // now update
        Transaction transaction = fileDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity dbFileEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(FILE_ID)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(1, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                // Additional entity checks
                () -> assertTrue(dbFileEntity.getDeleted()),
                () -> assertNotNull(dbFileEntity.getKey()),
                () -> assertNotNull(dbFileEntity.getExpirationTimestamp()),
                () -> assertNull(dbFileEntity.getAutoRenewPeriod()),
                () -> assertNull(dbFileEntity.getProxyAccountId()));
    }

    @Test
    void fileDeleteToNew() {
        Transaction fileDeleteTransaction = fileDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(fileDeleteTransaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(fileDeleteTransaction)
                .build());
        Entity fileEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityHasNullFields(txnRecord.getConsensusTimestamp()),
                () -> assertTrue(fileEntity.getDeleted()));
    }

    @Test
    void fileDeleteFailedTransaction() {
        Transaction fileDeleteTransaction = fileDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(fileDeleteTransaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(fileDeleteTransaction)
                .build());

        assertAll(this::assertRowCountOnFailureNoData, () -> assertFailedFileTransaction(transactionBody, txnRecord));
    }

    @Test
    void fileSystemDeleteTransaction() {
        Transaction systemDeleteTransaction = systemDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemDeleteTransaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(systemDeleteTransaction)
                .build());
        Entity fileEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertThat(fileEntity)
                        .isNotNull()
                        .extracting(Entity::getDeleted)
                        .isEqualTo(true));
    }

    @Test
    void fileSystemUnDeleteTransaction() {
        Transaction systemUndeleteTransaction = systemUnDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemUndeleteTransaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(systemUndeleteTransaction)
                .build());

        assertAll(
                () -> assertRowCountOnSuccessNoData(FILE_ID),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFileEntityHasNullFields(txnRecord.getConsensusTimestamp()));
    }

    @Test
    void fileSystemDeleteInvalidTransaction() {
        Transaction systemDeleteTransaction = systemDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemDeleteTransaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(systemDeleteTransaction)
                .build());

        assertFailedFileTransaction(transactionBody, txnRecord);
    }

    @Test
    void fileSystemUnDeleteFailedTransaction() {
        Transaction systemUndeleteTransaction = systemUnDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(systemUndeleteTransaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(systemUndeleteTransaction)
                .build());

        assertFailedFileTransaction(transactionBody, txnRecord);
    }

    private void assertFailedFileTransaction(TransactionBody transactionBody, TransactionRecord txnRecord) {
        org.hiero.mirror.common.domain.transaction.Transaction transaction =
                getDbTransaction(txnRecord.getConsensusTimestamp());
        assertAll(() -> assertTransactionAndRecord(transactionBody, txnRecord), () -> assertThat(
                        transaction.getEntityId())
                .isEqualTo(EntityId.of(txnRecord.getReceipt().getFileID())));
    }

    private void assertFileEntity(FileCreateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(expected.getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertEquals(expected.getMemo(), actualFile.getMemo()),
                () -> assertArrayEquals(expected.getKeys().toByteArray(), actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    private void assertFileEntityAndData(FileCreateTransactionBody expected, Timestamp consensusTimestamp) {
        assertFileEntity(expected, consensusTimestamp);
        assertFileData(expected.getContents(), consensusTimestamp);
    }

    private void assertFileEntity(FileUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertEquals(
                        DomainUtils.timeStampInNanos(expected.getExpirationTime()),
                        actualFile.getExpirationTimestamp()),
                () -> assertFalse(actualFile.getDeleted()),
                () -> assertEquals(expected.getMemo().getValue(), actualFile.getMemo()),
                () -> assertArrayEquals(expected.getKeys().toByteArray(), actualFile.getKey()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    private void assertFileEntityAndData(FileUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        assertFileEntity(expected, consensusTimestamp);
        assertFileData(expected.getContents(), consensusTimestamp);
    }

    private void assertFileData(ByteString expected, Timestamp consensusTimestamp) {
        FileData actualFileData = fileDataRepository
                .findById(DomainUtils.timeStampInNanos(consensusTimestamp))
                .get();
        assertArrayEquals(expected.toByteArray(), actualFileData.getFileData());
    }

    private void assertAddressBookData(byte[] expected, Timestamp consensusTimestamp) {
        // addressBook.getStartConsensusTimestamp = transaction.consensusTimestamp + 1ns
        AddressBook actualAddressBook = addressBookRepository
                .findById(DomainUtils.timeStampInNanos(consensusTimestamp) + 1)
                .get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }

    private void assertFileEntityHasNullFields(Timestamp consensusTimestamp) {
        Entity actualFile = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertNull(actualFile.getKey()),
                () -> assertNull(actualFile.getExpirationTimestamp()),
                () -> assertNull(actualFile.getAutoRenewPeriod()),
                () -> assertNull(actualFile.getProxyAccountId()));
    }

    private void assertRowCountOnSuccess(FileID fileID) {
        assertRowCount(
                1,
                3, // 3 fee transfers
                1,
                EntityId.of(fileID));
    }

    private void assertRowCountOnTwoFileTransactions() {
        assertRowCount(
                2,
                6, // 3 + 3 fee transfers
                2,
                EntityId.of(FILE_ID));
    }

    private void assertRowCountOnSuccessNoData(FileID fileID) {
        assertRowCount(
                1,
                3, // 3 fee transfers
                0,
                EntityId.of(fileID));
    }

    private void assertRowCountOnFailureNoData() {
        assertRowCount(
                1, 3, // 3 fee transfers
                0);
    }

    private void assertRowCountOnAddressBookTransactions() {
        assertRowCount(
                2,
                6, // 3 + 3 fee transfers
                2,
                EntityId.of(addressBookFileId));
    }

    private void assertRowCount(int numTransactions, int numCryptoTransfers, int numFileData, EntityId... entityIds) {
        assertAll(
                () -> assertEquals(numTransactions, transactionRepository.count()),
                () -> assertEntities(entityIds),
                () -> assertEquals(numCryptoTransfers, cryptoTransferRepository.count()),
                () -> assertEquals(numFileData, fileDataRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()));
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, FILE_ID);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum status) {
        return transactionRecord(transactionBody, status, FILE_ID);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, FileID fileId) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS, fileId);
    }

    private TransactionRecord transactionRecord(
            TransactionBody transactionBody, ResponseCodeEnum status, FileID fileId) {
        return buildTransactionRecord(
                recordBuilder -> recordBuilder.getReceiptBuilder().setFileID(fileId),
                transactionBody,
                status.getNumber());
    }

    private Transaction fileCreateTransaction() {
        return fileCreateTransaction(Timestamp.newBuilder()
                .setSeconds(1571487857L)
                .setNanos(181579000)
                .build());
    }

    @SuppressWarnings("deprecation")
    private Transaction fileCreateTransaction(Timestamp expirationTime) {
        return buildTransaction(builder -> builder.getFileCreateBuilder()
                .setContents(ByteString.copyFromUtf8("test1"))
                .setExpirationTime(expirationTime)
                .setMemo("FileCreate memo")
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .getKeysBuilder()
                .addKeys(keyFromString(KEY)));
    }

    private Transaction fileAppendTransaction() {
        return fileAppendTransaction(FILE_ID, FILE_CONTENTS);
    }

    private Transaction fileAppendTransaction(FileID fileToAppendTo, byte[] contents) {
        return buildTransaction(builder -> builder.getFileAppendBuilder()
                .setContents(ByteString.copyFrom(contents))
                .setFileID(fileToAppendTo));
    }

    private Transaction fileUpdateAllTransaction() {
        return fileUpdateAllTransaction(FILE_ID, FILE_CONTENTS);
    }

    private Transaction fileUpdateAllTransaction(FileID fileToUpdate, byte[] contents) {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setContents(ByteString.copyFrom(contents))
                .setFileID(fileToUpdate)
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setMemo(StringValue.of("FileUpdate memo"))
                .getKeysBuilder()
                .addKeys(keyFromString(KEY)));
    }

    private Transaction fileUpdateContentsTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setContents(ByteString.copyFromUtf8("test2"))
                .setFileID(FILE_ID));
    }

    private Transaction fileUpdateExpiryTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setFileID(FILE_ID)
                .setExpirationTime(Utility.instantToTimestamp(Instant.now())));
    }

    private Transaction fileUpdateKeysTransaction() {
        return buildTransaction(builder -> builder.getFileUpdateBuilder()
                .setFileID(FILE_ID)
                .getKeysBuilder()
                .addKeys(keyFromString(KEY)));
    }

    private Transaction fileDeleteTransaction() {
        return buildTransaction(builder -> builder.getFileDeleteBuilder().setFileID(FILE_ID));
    }

    private Transaction systemDeleteTransaction() {
        return buildTransaction(builder -> builder.getSystemDeleteBuilder()
                .setFileID(FILE_ID)
                .setExpirationTime(
                        TimestampSeconds.newBuilder().setSeconds(100000).build()));
    }

    private Transaction systemUnDeleteTransaction() {
        return buildTransaction(builder -> builder.getSystemUndeleteBuilder().setFileID(FILE_ID));
    }
}
