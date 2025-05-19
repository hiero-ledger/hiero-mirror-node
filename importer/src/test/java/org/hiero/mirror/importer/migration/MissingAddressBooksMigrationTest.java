// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ListAssert;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.AddressBookRepository;
import org.hiero.mirror.importer.repository.AddressBookServiceEndpointRepository;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Tag("migration")
class MissingAddressBooksMigrationTest extends ImporterIntegrationTest {

    private static final NodeAddressBook FINAL = addressBook(15, 0);

    private final MissingAddressBooksMigration missingAddressBooksMigration;
    private final AddressBookRepository addressBookRepository;
    private final FileDataRepository fileDataRepository;
    private final AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;

    @SuppressWarnings("deprecation")
    private static NodeAddressBook addressBook(int size, int endPointSize) {
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
                            .setDomainName("")
                            .setIpAddressV4(ByteString.copyFrom(new byte[] {127, 0, 0, (byte) j}))
                            .setPort(443 + j)
                            .build());
                }
            }

            builder.addNodeAddress(nodeAddressBuilder.build());
        }
        return builder.build();
    }

    @Test
    void checksum() {
        assertThat(missingAddressBooksMigration.getChecksum()).isEqualTo(1);
    }

    @Test
    @Transactional
    void verifyAddressBookMigrationWithNewFileDataAfterCurrentAddressBook() {
        // store initial address books
        addressBookRepository.save(addressBook(ab -> ab.fileId(systemEntity.addressBookFile101()), 1, 4));
        addressBookRepository.save(addressBook(ab -> ab.fileId(systemEntity.addressBookFile102()), 2, 4));
        addressBookRepository.save(addressBook(ab -> ab.fileId(systemEntity.addressBookFile101()), 11, 8));
        addressBookRepository.save(addressBook(ab -> ab.fileId(systemEntity.addressBookFile102()), 12, 8));
        assertEquals(4, addressBookRepository.count());

        // un-parsed file_data
        // file 101 update contents to be split over 1 update and 1 append operation
        byte[] addressBook101Bytes = FINAL.toByteArray();
        int index101 = addressBook101Bytes.length / 2;
        byte[] addressBook101Bytes1 = Arrays.copyOfRange(addressBook101Bytes, 0, index101);
        byte[] addressBook101Bytes2 = Arrays.copyOfRange(addressBook101Bytes, index101, addressBook101Bytes.length);
        createAndStoreFileData(addressBook101Bytes1, 101, false, TransactionType.FILEUPDATE);
        createAndStoreFileData(addressBook101Bytes2, 102, false, TransactionType.FILEAPPEND);

        // file 102 update contents to be split over 1 update and 1 append operation
        byte[] addressBook102Bytes = FINAL.toByteArray();
        int index = addressBook102Bytes.length / 2;
        byte[] addressBook102Bytes1 = Arrays.copyOfRange(addressBook102Bytes, 0, index);
        byte[] addressBook102Bytes2 = Arrays.copyOfRange(addressBook102Bytes, index, addressBook102Bytes.length);
        createAndStoreFileData(addressBook102Bytes1, 201, true, TransactionType.FILEUPDATE);
        createAndStoreFileData(addressBook102Bytes2, 202, true, TransactionType.FILEAPPEND);
        assertEquals(4, fileDataRepository.count());

        // migration on startup
        missingAddressBooksMigration.doMigrate();
        assertEquals(6, addressBookRepository.count());
        AddressBook newAddressBook = addressBookRepository
                .findLatest(205, systemEntity.addressBookFile102().getId())
                .get();
        assertThat(newAddressBook.getStartConsensusTimestamp()).isEqualTo(203L);
        assertAddressBook(newAddressBook, FINAL);
    }

    @DisplayName("Verify skipMigration")
    @ParameterizedTest(name = "with baseline {0} and target {1}")
    @CsvSource({"0, true", "1, false"})
    void skipMigrationPreAddressBookService(int serviceEndpointCount, boolean result) {
        for (int j = 1; j <= serviceEndpointCount; ++j) {
            AddressBookServiceEndpoint addressBookServiceEndpoint = new AddressBookServiceEndpoint();
            addressBookServiceEndpoint.setConsensusTimestamp(j);
            addressBookServiceEndpoint.setDomainName("");
            addressBookServiceEndpoint.setIpAddressV4("127.0.0.1");
            addressBookServiceEndpoint.setPort(443);
            addressBookServiceEndpoint.setNodeId(100L);
            addressBookServiceEndpointRepository.save(addressBookServiceEndpoint);
        }
        assertThat(missingAddressBooksMigration.skipMigration(getConfiguration()))
                .isEqualTo(result);
    }

    private AddressBook addressBook(
            Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer, long consensusTimestamp, int nodeCount) {
        long startConsensusTimestamp = consensusTimestamp + 1;
        List<AddressBookEntry> addressBookEntryList = new ArrayList<>();
        for (long i = 0; i < nodeCount; i++) {
            long nodeId = i;
            long nodeAccountId = 3 + nodeId;
            addressBookEntryList.add(addressBookEntry(a -> a.consensusTimestamp(startConsensusTimestamp)
                    .nodeId(nodeId)
                    .memo("0.0." + nodeAccountId)
                    .nodeAccountId(EntityId.of("0.0." + nodeAccountId))));
        }

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .startConsensusTimestamp(startConsensusTimestamp)
                .fileData("address book memo".getBytes())
                .nodeCount(nodeCount)
                .fileId(systemEntity.addressBookFile102())
                .entries(addressBookEntryList);

        if (addressBookCustomizer != null) {
            addressBookCustomizer.accept(builder);
        }

        return builder.build();
    }

    private AddressBookEntry addressBookEntry(
            Consumer<AddressBookEntry.AddressBookEntryBuilder> nodeAddressCustomizer) {
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(Instant.now().getEpochSecond())
                .description("address book entry")
                .publicKey("rsa+public/key")
                .memo("0.0.3")
                .nodeAccountId(EntityId.of("0.0.5"))
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeId(5L)
                .stake(5L);

        if (nodeAddressCustomizer != null) {
            nodeAddressCustomizer.accept(builder);
        }

        return builder.build();
    }

    private FileData createAndStoreFileData(
            byte[] contents, long consensusTimeStamp, boolean is102, TransactionType transactionType) {
        EntityId entityId = is102 ? systemEntity.addressBookFile102() : systemEntity.addressBookFile101();
        FileData fileData = new FileData(consensusTimeStamp, contents, entityId, transactionType.getProtoId());
        return fileDataRepository.save(fileData);
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
            });
        }
    }

    private ClassicConfiguration getConfiguration() {
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.setTargetAsString("latest");
        return configuration;
    }
}
