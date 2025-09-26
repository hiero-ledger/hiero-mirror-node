// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.addressbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.ConsensusMode;
import org.hiero.mirror.importer.config.CacheConfiguration;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.repository.AddressBookEntryRepository;
import org.hiero.mirror.importer.repository.AddressBookRepository;
import org.hiero.mirror.importer.repository.AddressBookServiceEndpointRepository;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

@RequiredArgsConstructor
class AddressBookServiceImplTest extends ImporterIntegrationTest {

    private static final NodeAddressBook UPDATED = addressBook(10, 0);
    private static final NodeAddressBook FINAL = addressBook(15, 0);
    private static final int TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT = 4;
    private static final String BASE_ACCOUNT_ID = "0.0.";
    private static final int BASE_PORT = 50211;
    private static byte[] initialAddressBookBytes;

    private final AddressBookEntryRepository addressBookEntryRepository;
    private final AddressBookRepository addressBookRepository;
    private final AddressBookService addressBookService;
    private final AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;

    @Qualifier(CacheConfiguration.CACHE_ADDRESS_BOOK)
    private final CacheManager cacheManager;

    private final FileDataRepository fileDataRepository;
    private final ImporterProperties importerProperties;

    @TempDir
    private Path dataPath;

    @Value("classpath:addressbook")
    private Path testPath;

    @SuppressWarnings("deprecation")
    protected static NodeAddressBook addressBook(int size, int endPointSize) {
        var builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < size; ++i) {
            long nodeId = 3 + i;
            var nodeAccountId = DOMAIN_BUILDER.entityNum(nodeId);
            var nodeAddressBuilder = NodeAddress.newBuilder()
                    .setIpAddress(ByteString.copyFromUtf8("127.0.0." + nodeId))
                    .setPortno((int) nodeId)
                    .setNodeId(nodeId)
                    .setMemo(ByteString.copyFromUtf8(nodeAccountId.toString()))
                    .setNodeAccountId(nodeAccountId.toAccountID())
                    .setNodeCertHash(ByteString.copyFromUtf8("nodeCertHash"))
                    .setRSAPubKey("rsa+public/key");

            // add service endpoints
            if (endPointSize > 0) {
                List<ServiceEndpoint> serviceEndpoints = new ArrayList<>();
                for (int j = 1; j <= size; ++j) {
                    serviceEndpoints.add(ServiceEndpoint.newBuilder()
                            .setIpAddressV4(ByteString.copyFrom(new byte[] {127, 0, 0, (byte) j}))
                            .setPort(443 + j)
                            .setDomainName("")
                            .build());
                }
            }

            builder.addNodeAddress(nodeAddressBuilder.build());
        }
        return builder.build();
    }

    @BeforeAll
    static void setupAll() throws IOException {
        Path addressBookPath =
                ResourceUtils.getFile("classpath:addressbook/testnet").toPath();
        initialAddressBookBytes = Files.readAllBytes(addressBookPath);
    }

    @BeforeEach
    void setup() {
        importerProperties.setInitialAddressBook(null);
    }

    private FileData createFileData(
            byte[] contents, long consensusTimeStamp, boolean is102, TransactionType transactionType) {
        EntityId entityId = is102 ? systemEntity.addressBookFile102() : systemEntity.addressBookFile101();
        return new FileData(consensusTimeStamp, contents, entityId, transactionType.getProtoId());
    }

    private FileData store(byte[] contents, long consensusTimeStamp, boolean is102) {
        FileData fileData = createFileData(contents, consensusTimeStamp, is102, TransactionType.FILEUPDATE);
        return fileDataRepository.save(fileData);
    }

    private void update(byte[] contents, long consensusTimeStamp, boolean is102) {
        FileData fileData = createFileData(contents, consensusTimeStamp, is102, TransactionType.FILEUPDATE);
        addressBookService.update(fileData);
    }

    private void append(byte[] contents, long consensusTimeStamp, boolean is102) {
        FileData fileData = createFileData(contents, consensusTimeStamp, is102, TransactionType.FILEAPPEND);
        addressBookService.update(fileData);
    }

    @Test
    void startupWithOtherNetworkIncorrectInitialAddressBookPath() {
        importerProperties.setInitialAddressBook(dataPath.resolve("test-v1"));
        assertThrows(IllegalStateException.class, addressBookService::getCurrent);
        assertEquals(0, addressBookRepository.count());
    }

    @Test
    void startupWithDefaultNetwork() {
        // init address book and verify initial state
        assertEquals(0, addressBookEntryRepository.count());
        assertEquals(0, addressBookRepository.count());

        AddressBook addressBook = addressBookService.getCurrent();

        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(1L);
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void startupWithOtherNetwork() {
        // copy other addressbook to file system
        FileCopier fileCopier = FileCopier.create(testPath, dataPath)
                .from("")
                .filterFiles("test-v1")
                .to("");
        fileCopier.copy();

        importerProperties.setInitialAddressBook(dataPath.resolve("test-v1"));
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(1L);
        assertEquals(1, addressBookRepository.count());
    }

    @Test
    @Transactional
    void updateCompleteFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        long addressBookConsensusTimeStamp = 5L;
        update(addressBookBytes, addressBookConsensusTimeStamp, true);

        // assert current addressBook is updated
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(addressBookConsensusTimeStamp + 1);
        assertAddressBook(addressBook, UPDATED);

        // assert repositories contain updates
        assertAddressBookData(UPDATED.toByteArray(), addressBookConsensusTimeStamp + 1);
        assertEquals(2, addressBookRepository.count());
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount(),
                addressBookEntryRepository.count());
    }

    @Test
    void updateFileWithoutFileCreateOrUpdate() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        assertDoesNotThrow(() -> append(addressBookPartial, 8L, true));
        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());
    }

    @Test
    void updatePartialFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        update(addressBookPartial, 5L, true);

        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    @Transactional
    void appendCompleteFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 3;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, index * 2);
        byte[] addressBookBytes3 = Arrays.copyOfRange(addressBookBytes, index * 2, addressBookBytes.length);

        update(addressBookBytes1, 2L, true);
        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        append(addressBookBytes2, 3L, true);
        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        long addressBookConsensusTimeStamp = 5L;
        append(addressBookBytes3, addressBookConsensusTimeStamp, true);
        assertAddressBook(addressBookService.getCurrent(), UPDATED);

        assertAddressBookData(addressBookBytes, addressBookConsensusTimeStamp + 1);

        assertEquals(2, addressBookRepository.count());
        assertEquals(
                UPDATED.getNodeAddressCount() + TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT,
                addressBookEntryRepository.count());

        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(6L);
        assertThat(addressBook.getEntries()).hasSize(UPDATED.getNodeAddressCount());
    }

    @Test
    void appendPartialFile() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        update(addressBookBytes1, 2L, true);

        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void ignoreEmptyByteArray() {
        update(new byte[] {}, 2L, true);
        append(new byte[] {}, 3L, true);

        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            234, false
            101, true
            102, true
            """)
    void isAddressBook(long fileNum, boolean expected) {
        assertThat(addressBookService.isAddressBook(domainBuilder.entityNum(fileNum)))
                .isEqualTo(expected);
    }

    @Test
    @Transactional
    void verifyAddressBookUpdateAcrossSessions() {
        // create network book, perform an update and append
        byte[] addressBookBytes = FINAL.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        update(addressBookBytes1, 2L, true);

        // create new address book and submit another append to complete file
        addressBookService.getCurrent();

        long addressBookConsensusTimeStamp = 3L;
        append(addressBookBytes2, addressBookConsensusTimeStamp, true);

        // verify valid address book and repository update
        AddressBook addressBook = addressBookService.getCurrent();
        assertAddressBook(addressBook, FINAL);
        assertAddressBookData(FINAL.toByteArray(), addressBookConsensusTimeStamp + 1);
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(addressBookConsensusTimeStamp + 1);
    }

    @Test
    @Transactional
    void appendCompleteFileAcrossFileIds() {
        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookBytes1 = Arrays.copyOfRange(addressBookBytes, 0, index);
        byte[] addressBookBytes2 = Arrays.copyOfRange(addressBookBytes, index, addressBookBytes.length);

        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);

        // init address book and verify initial state
        assertEquals(0, addressBookEntryRepository.count());
        assertEquals(0, addressBookRepository.count());

        // perform file 102 first update and confirm no change to current address book and nodes addresses
        update(addressBookBytes1, 2L, true); // fileID 102
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
        assertEquals(1, addressBookRepository.count()); // initial

        addressBookService.getCurrent();

        update(addressBook101Bytes1, 3L, false);
        long addressBook101ConsensusTimeStamp = 5L;
        append(addressBook101Bytes2, addressBook101ConsensusTimeStamp, false);

        // verify partial bytes match 101 complete address book update
        assertAddressBookData(FINAL.toByteArray(), addressBook101ConsensusTimeStamp + 1);
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + FINAL.getNodeAddressCount(), addressBookEntryRepository.count());
        assertEquals(2, addressBookRepository.count());

        // verify current address book bytes still match original load and not 101 update and append
        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        // perform file 102 append
        long addressBook102ConsensusTimeStamp = 7L;
        append(addressBookBytes2, addressBook102ConsensusTimeStamp, true);

        // verify address book and node addresses are updated
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(addressBook102ConsensusTimeStamp + 1);
        assertAddressBook(addressBookService.getCurrent(), UPDATED);

        // 15 (101 update) + 12 (102 update)
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount() + FINAL.getNodeAddressCount(),
                addressBookEntryRepository.count());
        assertAddressBookData(UPDATED.toByteArray(), addressBook102ConsensusTimeStamp + 1);
        assertEquals(3, addressBookRepository.count());
    }

    @Test
    void verify102AddressBookEndPointsAreSet() {
        long initialTimestamp = 5L;
        byte[] addressBookBytes = UPDATED.toByteArray();
        update(addressBookBytes, initialTimestamp, true);

        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(addressBookBytes, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile102(), AddressBook::getFileId)
                .returns(UPDATED.getNodeAddressCount(), AddressBook::getNodeCount)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, UPDATED));

        long newTimestamp = 10L;
        byte[] newAddressBookBytes = FINAL.toByteArray();
        update(newAddressBookBytes, newTimestamp, true);

        assertThat(addressBookRepository.findById(newTimestamp + 1))
                .get()
                .returns(newAddressBookBytes, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile102(), AddressBook::getFileId)
                .returns(newTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, FINAL));

        assertEquals(3, addressBookRepository.count()); // bootstrap, UPDATED and FINAL
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount() + FINAL.getNodeAddressCount(),
                addressBookEntryRepository.count());

        // verify end consensus timestamp was set for previous address book
        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(systemEntity.addressBookFile102(), AddressBook::getFileId)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(newTimestamp, AddressBook::getEndConsensusTimestamp);
    }

    @Test
    void verify101AddressBookEndPointsAreSet() {
        long initialTimestamp = 5L;
        byte[] addressBookBytes = UPDATED.toByteArray();
        update(addressBookBytes, initialTimestamp, false);

        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(addressBookBytes, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile101(), AddressBook::getFileId)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, UPDATED));

        long newTimestamp = 10L;
        byte[] newAddressBookBytes = FINAL.toByteArray();
        update(newAddressBookBytes, newTimestamp, false);

        assertThat(addressBookRepository.findById(newTimestamp + 1))
                .get()
                .returns(newAddressBookBytes, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile101(), AddressBook::getFileId)
                .returns(newTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, FINAL));

        assertEquals(3, addressBookRepository.count()); // bootstrap, UPDATED and FINAL
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + UPDATED.getNodeAddressCount() + FINAL.getNodeAddressCount(),
                addressBookEntryRepository.count());

        // verify end consensus timestamp was set for previous address book
        assertThat(addressBookRepository.findById(initialTimestamp + 1))
                .get()
                .returns(systemEntity.addressBookFile101(), AddressBook::getFileId)
                .returns(initialTimestamp + 1, AddressBook::getStartConsensusTimestamp)
                .returns(newTimestamp, AddressBook::getEndConsensusTimestamp);
    }

    @Test
    void invalidInitialRealm() {
        commonProperties.setRealm(1000L);
        assertThatThrownBy(() -> addressBookService.getCurrent())
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessageContaining("Unable to load starting address book");
    }

    @Test
    void invalidInitialShard() {
        commonProperties.setShard(1000L);
        assertThatThrownBy(() -> addressBookService.getCurrent())
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessageContaining("Unable to load starting address book");
    }

    @Test
    @Transactional
    void invalidRealm() {
        var addressBook = addressBookService.getCurrent();
        assertThat(addressBook).isNotNull();

        var badAddressBook = UPDATED.toBuilder();
        badAddressBook
                .getNodeAddressBuilderList()
                .forEach(n -> n.setNodeAccountId(n.getNodeAccountIdBuilder().setRealmNum(1000L)));
        update(badAddressBook.build().toByteArray(), 2L, true);

        assertThat(addressBookService.getCurrent()).isEqualTo(addressBook);
        assertThat(addressBookRepository.count()).isOne();
    }

    @Test
    void verify101DoesntUpdate102() {
        long timestamp102 = 2L;
        byte[] addressBookBytes102 = UPDATED.toByteArray();
        update(addressBookBytes102, timestamp102, true);

        assertThat(addressBookRepository.findById(timestamp102 + 1))
                .get()
                .returns(addressBookBytes102, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile102(), AddressBook::getFileId)
                .returns(timestamp102 + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, UPDATED));

        long timestamp101 = 10L;
        byte[] addressBookBytes101 = FINAL.toByteArray();
        update(addressBookBytes101, timestamp101, false);

        assertThat(addressBookRepository.findById(timestamp101 + 1))
                .get()
                .returns(addressBookBytes101, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile101(), AddressBook::getFileId)
                .returns(timestamp101 + 1, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp)
                .satisfies(a -> assertAddressBook(a, FINAL));
    }

    @SuppressWarnings("deprecation")
    @Test
    @Transactional
    void verifyAddressBookWithMissingFields() {
        // Given
        var nodeAccountId = domainBuilder.entityId();
        var ipAddress = "127.0.0.1";
        var nodeAddressBook = NodeAddressBook.newBuilder()
                .addNodeAddress(NodeAddress.newBuilder()
                        .setIpAddress(ByteString.copyFromUtf8(ipAddress))
                        .setNodeAccountId(nodeAccountId.toAccountID()))
                .build();
        byte[] addressBookBytes = nodeAddressBook.toByteArray();
        long consensusTimeStamp = 5L;

        // When
        update(addressBookBytes, consensusTimeStamp - 1, true);

        // Then
        assertAddressBookData(addressBookBytes, consensusTimeStamp);
        softly.assertThat(addressBookService.getCurrent())
                .isNotNull()
                .extracting(AddressBook::getEntries, InstanceOfAssertFactories.list(AddressBookEntry.class))
                .hasSize(nodeAddressBook.getNodeAddressCount())
                .first()
                .returns("", AddressBookEntry::getDescription)
                .returns(null, AddressBookEntry::getMemo)
                .returns(nodeAccountId, AddressBookEntry::getNodeAccountId)
                .returns(null, AddressBookEntry::getNodeCertHash)
                .returns(0L, AddressBookEntry::getNodeId)
                .returns("", AddressBookEntry::getPublicKey)
                .returns(consensusTimeStamp, AddressBookEntry::getConsensusTimestamp)
                .returns(0L, AddressBookEntry::getStake)
                .extracting(
                        AddressBookEntry::getServiceEndpoints,
                        InstanceOfAssertFactories.set(AddressBookServiceEndpoint.class))
                .hasSize(1)
                .first()
                .returns(0L, AddressBookServiceEndpoint::getNodeId)
                .returns("", AddressBookServiceEndpoint::getDomainName)
                .returns(ipAddress, AddressBookServiceEndpoint::getIpAddressV4)
                .returns(consensusTimeStamp, AddressBookServiceEndpoint::getConsensusTimestamp)
                .returns(0, AddressBookServiceEndpoint::getPort);
    }

    @SuppressWarnings("deprecation")
    @Test
    @Transactional
    void verifyAddressBookWithMissingNodeAccount() {
        // Given
        var nodeAddressBook = NodeAddressBook.newBuilder()
                .addNodeAddress(NodeAddress.newBuilder()
                        .setIpAddress(ByteString.copyFromUtf8("127.0.0.1"))
                        .setMemo(ByteString.copyFromUtf8("memo")))
                .build();
        byte[] addressBookBytes = nodeAddressBook.toByteArray();
        long consensusTimeStamp = 5L;
        var current = addressBookService.getCurrent();

        // When
        update(addressBookBytes, consensusTimeStamp - 1, true);

        // Then
        assertThat(addressBookService.getCurrent()).isEqualTo(current);
    }

    @Test
    void verifyGetCurrentCallPopulatesInitialAddressBook() {
        assertEquals(0, addressBookRepository.count());

        assertThat(addressBookService.getCurrent())
                .returns(initialAddressBookBytes, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile102(), AddressBook::getFileId)
                .returns(1L, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp);
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void verifyUpdateCallPopulatesInitialAddressBook() {
        byte[] addressBookBytes = UPDATED.toByteArray();
        int index = addressBookBytes.length / 2;
        byte[] addressBookPartial = Arrays.copyOfRange(addressBookBytes, 0, index);

        assertEquals(0, addressBookRepository.count());
        update(addressBookPartial, 2L, false);

        assertThat(addressBookService.getCurrent())
                .returns(initialAddressBookBytes, AddressBook::getFileData)
                .returns(systemEntity.addressBookFile102(), AddressBook::getFileId)
                .returns(1L, AddressBook::getStartConsensusTimestamp)
                .returns(null, AddressBook::getEndConsensusTimestamp);
        assertEquals(1, addressBookRepository.count());
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT, addressBookEntryRepository.count());
    }

    @Test
    void verifyAddressBookMigrationInitiatedByDownloader() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        byte[] addressBookBytes3 = FINAL.toByteArray();
        store(addressBookBytes3, 4L, false);

        byte[] addressBookBytes4 = FINAL.toByteArray();
        store(addressBookBytes4, 5L, true);

        // migration
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(6L);
        assertAddressBook(addressBook, FINAL);

        assertEquals(4, fileDataRepository.count());
        assertEquals(5, addressBookRepository.count()); // initial plus 4 files
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT
                        + (UPDATED.getNodeAddressCount() * 2L)
                        + (FINAL.getNodeAddressCount() * 2L),
                addressBookEntryRepository.count());
    }

    @Test
    void verifyAddressBookMigrationInitiatedByParser() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        byte[] addressBookBytes3 = FINAL.toByteArray();
        store(addressBookBytes3, 4L, false);

        byte[] addressBookBytes4 = FINAL.toByteArray();
        store(addressBookBytes4, 5L, true);

        // migration
        int addressBook5NodeCount = 20;
        byte[] addressBookBytes5 = addressBook(addressBook5NodeCount, 0).toByteArray();
        addressBookService.update(createFileData(addressBookBytes5, 6L, true, TransactionType.FILEUPDATE));

        assertEquals(5, fileDataRepository.count());
        assertEquals(6, addressBookRepository.count()); // initial plus 5 files
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT
                        + (UPDATED.getNodeAddressCount() * 2L)
                        + (FINAL.getNodeAddressCount() * 2L)
                        + addressBook5NodeCount,
                addressBookEntryRepository.count());
    }

    @Test
    void verifyAddressBookWithServiceEndpointsOnly() throws UnknownHostException {
        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 4;

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            nodeAddressList.add(getNodeAddress(
                    i,
                    BASE_ACCOUNT_ID + i,
                    null,
                    List.of(
                            String.format("127.0.%d.1", i),
                            String.format("127.0.%d.2", i),
                            String.format("127.0.%d.3", i),
                            String.format("127.0.%d.4", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder =
                NodeAddressBook.newBuilder().addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, false);

        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries, addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode, addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyAddressBookWithDeprecatedIpOnly() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 1;

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            nodeAddressList.add(getNodeAddress(i, BASE_ACCOUNT_ID + i, String.format("127.0.%d.0", i), List.of()));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder =
                NodeAddressBook.newBuilder().addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, false);

        assertArrayEquals(
                initialAddressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries, addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode, addressBookServiceEndpointRepository.count());
    }

    @SneakyThrows
    @Test
    void verifyAddressBookWithDeprecatedIpAndServiceEndpoints() {
        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 5; // deprecated ip + service endpoints

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            nodeAddressList.add(getNodeAddress(
                    i,
                    BASE_ACCOUNT_ID + i,
                    String.format("127.0.%d.0", i),
                    List.of(
                            String.format("127.0.%d.1", i),
                            String.format("127.0.%d.2", i),
                            String.format("127.0.%d.3", i),
                            String.format("127.0.%d.4", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder =
                NodeAddressBook.newBuilder().addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries, addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode, addressBookServiceEndpointRepository.count());
    }

    @SneakyThrows
    @Test
    void verifyAddressBookWitDomainName() {
        var domainName = "localhost";
        var nodeAddressBook = NodeAddressBook.newBuilder()
                .addNodeAddress(NodeAddress.newBuilder()
                        .addServiceEndpoint(ServiceEndpoint.newBuilder()
                                .setDomainName(domainName)
                                .setPort(BASE_PORT))
                        .setNodeId(0L)
                        .setNodeAccountId(domainBuilder.entityId().toAccountID()))
                .build();

        var path = dataPath.resolve("addressbook.bin");
        var bytes = nodeAddressBook.toByteArray();
        Files.write(path, bytes);
        importerProperties.setInitialAddressBook(path);

        assertThat(addressBookService.migrate())
                .extracting(AddressBook::getEntries, InstanceOfAssertFactories.list(AddressBookEntry.class))
                .hasSize(1)
                .first()
                .extracting(
                        AddressBookEntry::getServiceEndpoints,
                        InstanceOfAssertFactories.set(AddressBookServiceEndpoint.class))
                .hasSize(1)
                .first()
                .returns("", AddressBookServiceEndpoint::getIpAddressV4)
                .returns(BASE_PORT, AddressBookServiceEndpoint::getPort)
                .returns(domainName, AddressBookServiceEndpoint::getDomainName);
        assertThat(addressBookRepository.count()).isOne();
    }

    @Test
    void verifyDuplicateNodeAddressPerNodeIdAreCollapsed() throws UnknownHostException {
        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 6; // deprecated ip, service endpoints, deprecated ip + service endpoints

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            // deprecated ip
            nodeAddressList.add(getNodeAddress(i, BASE_ACCOUNT_ID + i, String.format("127.0.%d.0", i), List.of()));

            // subset of only service endpoints
            nodeAddressList.add(getNodeAddress(
                    i,
                    BASE_ACCOUNT_ID + i,
                    null,
                    List.of(String.format("127.0.%d.1", i), String.format("127.0.%d.2", i))));

            // another deprecated ip and more service endpoints
            nodeAddressList.add(getNodeAddress(
                    i,
                    BASE_ACCOUNT_ID + i,
                    String.format("128.0.%d.0", i),
                    List.of(String.format("127.0.%d.3", i), String.format("127.0.%d.4", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder =
                NodeAddressBook.newBuilder().addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries, addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode, addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyEmptyDeprecatedMemo() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 1; // service endpoint

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            // empty deprecated ip
            nodeAddressList.add(getNodeAddress(i, "", null, List.of(String.format("127.0.%d.0", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder =
                NodeAddressBook.newBuilder().addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries, addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode, addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyDuplicateEndpointsPerNodeAddressAreCollapsed() throws UnknownHostException {

        List<NodeAddress> nodeAddressList = new ArrayList<>();
        int nodeAccountStart = 3;
        int addressBookEntries = 5;
        int numEndpointsPerNode = 3; // 127.0.<nodeId>.0, 127.0.<nodeId>.1, 127.0.<nodeId>.2

        for (int i = nodeAccountStart; i < addressBookEntries + nodeAccountStart; i++) {
            // deprecated ip
            nodeAddressList.add(getNodeAddress(i, BASE_ACCOUNT_ID + i, String.format("127.0.%d.0", i), List.of()));

            // subset of only service endpoints
            nodeAddressList.add(getNodeAddress(i, BASE_ACCOUNT_ID + i, null, List.of(String.format("127.0.%d.0", i))));

            // another deprecated ip and more service endpoints
            nodeAddressList.add(getNodeAddress(
                    i,
                    BASE_ACCOUNT_ID + i,
                    String.format("127.0.%d.0", i),
                    List.of(String.format("127.0.%d.1", i), String.format("127.0.%d.2", i))));
        }

        NodeAddressBook.Builder nodeAddressBookBuilder =
                NodeAddressBook.newBuilder().addAllNodeAddress(nodeAddressList);

        byte[] addressBookBytes = nodeAddressBookBuilder.build().toByteArray();
        update(addressBookBytes, 2L, true);

        assertArrayEquals(addressBookBytes, addressBookService.getCurrent().getFileData());

        assertEquals(2, addressBookRepository.count()); // bootstrap and new address book with service endpoints
        assertEquals(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT + addressBookEntries, addressBookEntryRepository.count());
        assertEquals(addressBookEntries * numEndpointsPerNode, addressBookServiceEndpointRepository.count());
    }

    @Test
    void verifyAddressBookMigrationWithNewFileDataAfterCurrentAddressBook() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        // initial migration
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(4L);
        assertAddressBook(addressBook, UPDATED);

        // valid file data added but no address book produced
        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);
        fileDataRepository.save(createFileData(addressBook101Bytes1, 4L, false, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook101Bytes2, 5L, false, TransactionType.FILEAPPEND));

        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBook102Bytes = FINAL.toByteArray();
        int index = addressBook102Bytes.length / 2;
        byte[] addressBook102Bytes1 = Arrays.copyOfRange(addressBook102Bytes, 0, index);
        byte[] addressBook102Bytes2 = Arrays.copyOfRange(addressBook102Bytes, index, addressBook102Bytes.length);
        fileDataRepository.save(createFileData(addressBook102Bytes1, 6L, true, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook102Bytes2, 7L, true, TransactionType.FILEAPPEND));

        // migration on startup
        AddressBook newAddressBook = addressBookService.migrate();
        assertThat(newAddressBook.getStartConsensusTimestamp()).isEqualTo(8L);
        assertAddressBook(newAddressBook, FINAL);

        assertEquals(6, fileDataRepository.count());
        assertEquals(5, addressBookRepository.count()); // initial plus 4 files
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT
                        + (UPDATED.getNodeAddressCount() * 2L)
                        + (FINAL.getNodeAddressCount() * 2L),
                addressBookEntryRepository.count());
    }

    @Test
    @Transactional
    void verifyUpdateWithNewFileDataAfterCurrentAddressBook() {
        byte[] addressBookBytes1 = UPDATED.toByteArray();
        store(addressBookBytes1, 2L, false);

        byte[] addressBookBytes2 = UPDATED.toByteArray();
        store(addressBookBytes2, 3L, true);

        // initial migration
        AddressBook addressBook = addressBookService.getCurrent();
        assertThat(addressBook.getStartConsensusTimestamp()).isEqualTo(4L);
        assertAddressBook(addressBook, UPDATED);

        // valid file data added but no address book produced
        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);
        fileDataRepository.save(createFileData(addressBook101Bytes1, 4L, false, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook101Bytes2, 5L, false, TransactionType.FILEAPPEND));

        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBook102Bytes = FINAL.toByteArray();
        int index = addressBook102Bytes.length / 2;
        byte[] addressBook102Bytes1 = Arrays.copyOfRange(addressBook102Bytes, 0, index);
        byte[] addressBook102Bytes2 = Arrays.copyOfRange(addressBook102Bytes, index, addressBook102Bytes.length);
        fileDataRepository.save(createFileData(addressBook102Bytes1, 6L, true, TransactionType.FILEUPDATE));
        fileDataRepository.save(createFileData(addressBook102Bytes2, 7L, true, TransactionType.FILEAPPEND));

        // migration on update, missing address books are created
        addressBookService.update(createFileData(addressBook101Bytes1, 10L, false, TransactionType.FILEUPDATE));
        AddressBook newAddressBook = addressBookService.getCurrent(); // latest missing address book is current
        assertThat(newAddressBook.getStartConsensusTimestamp()).isEqualTo(8L);
        assertAddressBook(newAddressBook, FINAL);

        assertEquals(7, fileDataRepository.count());
        assertEquals(5, addressBookRepository.count()); // initial plus 4 files
        assertEquals(
                TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT
                        + (UPDATED.getNodeAddressCount() * 2L)
                        + (FINAL.getNodeAddressCount() * 2L),
                addressBookEntryRepository.count());
    }

    @Test
    void getNodesEmptyNodeStake() {
        assertThat(addressBookService.getNodes())
                .hasSize(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT)
                .allMatch(c -> c.getStake() == 1L)
                .allMatch(c -> c.getTotalStake() == 4L)
                .allMatch(c -> c.getNodeAccountId().getNum() - 3 == c.getNodeId())
                .allSatisfy(c -> assertThat(c.getPublicKey()).isNotNull())
                .extracting(ConsensusNode::getNodeId)
                .containsExactly(0L, 1L, 2L, 3L);
    }

    @CsvSource(
            textBlock =
                    """
                            EQUAL, 10000, 1, 4
                            EQUAL, 0, 1, 4
                            STAKE, 10000, 10000, 40000
                            STAKE, 0, 1, 4
                            STAKE_IN_ADDRESS_BOOK, 10000, 10000, 40000
                            STAKE_IN_ADDRESS_BOOK, 0, 1, 4
                            """)
    @ParameterizedTest
    void getNodes(ConsensusMode mode, long stake, long expectedNodeStake, long expectedTotalStake) {
        long timestamp = domainBuilder.timestamp();
        var nodeId = new AtomicInteger(0);
        for (int i = 0; i < TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT; i++) {
            // node stake
            domainBuilder
                    .nodeStake()
                    .customize(n -> n.consensusTimestamp(timestamp)
                            .nodeId(nodeId.getAndIncrement())
                            .stake(stake))
                    .persist();
        }
        importerProperties.setConsensusMode(mode);

        assertThat(addressBookService.getNodes())
                .hasSize(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT)
                .allSatisfy(c -> assertThat(c).returns(expectedTotalStake, ConsensusNode::getTotalStake))
                .allSatisfy(c -> assertThat(c.getPublicKey()).isNotNull())
                .allMatch(c -> c.getStake() == expectedNodeStake)
                .extracting(ConsensusNode::getNodeId)
                .containsExactly(0L, 1L, 2L, 3L);
    }

    @CsvSource(
            textBlock =
                    """
                            EQUAL, 1, 6
                            STAKE, 10000, 60000
                            STAKE_IN_ADDRESS_BOOK, 10000, 40000
                            """)
    @ParameterizedTest
    void getNodesWithNodeStakeCountMoreThanAddressBook(
            ConsensusMode mode, long expectedNodeStake, long expectedTotalStake) {
        long timestamp = domainBuilder.timestamp();
        var nodeId = new AtomicInteger(0);
        final int nodeCount = 6; // regardless of mode, always have 4 nodes in address book and 6 nodes in nodeStakes.
        for (int i = 0; i < nodeCount; i++) {
            // node stake
            domainBuilder
                    .nodeStake()
                    .customize(n -> n.consensusTimestamp(timestamp)
                            .nodeId(nodeId.getAndIncrement())
                            .stake(10000L))
                    .persist();
        }
        importerProperties.setConsensusMode(mode);

        assertThat(addressBookService.getNodes())
                .hasSize(TEST_INITIAL_ADDRESS_BOOK_NODE_COUNT)
                .allMatch(c -> c.getStake() == expectedNodeStake)
                .allMatch(c -> c.getTotalStake() == expectedTotalStake)
                .allMatch(c -> c.getNodeAccountId().getNum() - 3 == c.getNodeId())
                .allSatisfy(c -> assertThat(c.getPublicKey()).isNotNull())
                .extracting(ConsensusNode::getNodeId)
                .containsExactly(0L, 1L, 2L, 3L);
    }

    @Test
    void refresh() {
        long timestamp = domainBuilder.timestamp();
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(timestamp).nodeId(0))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(timestamp).nodeId(1))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(timestamp).nodeId(2))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(timestamp).nodeId(3))
                .persist();

        // Verify cache is empty to start
        assertNull(cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY));

        // Verify getCurrent() adds an entry to the cache
        var nodes = addressBookService.getNodes();
        var nodesCache = cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY).get();
        assertThat(nodes)
                .isNotNull()
                .isEqualTo(nodesCache)
                .allMatch(node -> node.getStake() > 1)
                .allMatch(node -> node.getTotalStake() > 1)
                .allMatch(node -> node.getNodeAccountId() != null);

        addressBookService.refresh();
        assertNull(cacheManager.getCache(CACHE_NAME).get(SimpleKey.EMPTY));
    }

    private ServiceEndpoint getServiceEndpoint(String ip, Integer port) throws UnknownHostException {
        return ServiceEndpoint.newBuilder()
                .setIpAddressV4(ByteString.copyFrom(InetAddress.getByName(ip).getAddress()))
                .setPort(port)
                .setDomainName("")
                .build();
    }

    @SuppressWarnings("deprecation")
    private NodeAddress getNodeAddress(
            int accountNum, String deprecatedMemo, String deprecatedIp, List<String> serviceEndpoints)
            throws UnknownHostException {
        NodeAddress.Builder nodeAddressBuilder = NodeAddress.newBuilder()
                .setDescription("NodeAddressWithServiceEndpoint")
                .setNodeAccountId(domainBuilder.entityNum(accountNum).toAccountID())
                .setNodeCertHash(ByteString.copyFromUtf8(accountNum + "NodeCertHash"))
                .setNodeId(accountNum - AddressBookServiceImpl.INITIAL_NODE_ID_ACCOUNT_ID_OFFSET)
                .setRSAPubKey(accountNum + "RSAPubKey")
                .setStake(500);

        if (StringUtils.isNotBlank(deprecatedIp)) {
            nodeAddressBuilder
                    .setIpAddress(ByteString.copyFromUtf8(deprecatedIp))
                    .setPortno(BASE_PORT);
        }

        if (StringUtils.isNotBlank(deprecatedMemo)) {
            nodeAddressBuilder.setMemo(ByteString.copyFromUtf8(deprecatedMemo));
        }

        for (String endpoint : serviceEndpoints) {
            nodeAddressBuilder.addServiceEndpoint(getServiceEndpoint(endpoint, BASE_PORT));
        }

        return nodeAddressBuilder.build();
    }

    private void assertAddressBookData(byte[] expected, long consensusTimestamp) {
        AddressBook actualAddressBook =
                addressBookRepository.findById(consensusTimestamp).get();
        assertArrayEquals(expected, actualAddressBook.getFileData());
    }

    @SuppressWarnings("deprecation")
    private void assertAddressBook(AddressBook actual, NodeAddressBook expected) {
        ListAssert<AddressBookEntry> listAssert =
                assertThat(actual.getEntries()).hasSize(expected.getNodeAddressCount());

        for (NodeAddress nodeAddress : expected.getNodeAddressList()) {
            listAssert.anySatisfy(abe -> {
                assertThat(abe.getMemo()).isEqualTo(nodeAddress.getMemo().toStringUtf8());
                assertThat(abe.getNodeAccountId()).isEqualTo(EntityId.of(nodeAddress.getNodeAccountId()));
                assertThat(abe.getNodeCertHash())
                        .isEqualTo(nodeAddress.getNodeCertHash().toByteArray());
                assertThat(abe.getPublicKey()).isEqualTo(nodeAddress.getRSAPubKey());
                assertThat(abe.getNodeId()).isEqualTo(nodeAddress.getNodeId());

                assertAddressBookEndPoints(abe.getServiceEndpoints(), nodeAddress.getServiceEndpointList());
            });
        }
    }

    private void assertAddressBookEndPoints(Set<AddressBookServiceEndpoint> actual, List<ServiceEndpoint> expected) {
        if (expected.isEmpty()) {
            return;
        }

        var listAssert = assertThat(actual).hasSize(expected.size());

        for (ServiceEndpoint serviceEndpoint : expected) {
            listAssert.anySatisfy(abe -> {
                AtomicReference<String> ip = new AtomicReference<>("");
                assertDoesNotThrow(() -> ip.set(InetAddress.getByAddress(
                                serviceEndpoint.getIpAddressV4().toByteArray())
                        .getHostAddress()));

                assertThat(abe.getPort()).isEqualTo(serviceEndpoint.getPort());
                assertThat(abe.getIpAddressV4()).isEqualTo(ip.get());
            });
        }
    }
}
