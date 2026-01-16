// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.IterableAssert;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.37.0")
class AddAddressBookServiceEndpointsMigrationTest extends ImporterIntegrationTest {

    private static final RowMapper<MigrationAddressBookServiceEndpoint> ADDRESS_BOOK_SERVICE_ENDPOINT_MAPPER =
            rowMapper(MigrationAddressBookServiceEndpoint.class);
    private static final RowMapper<MigrationAddressBook> ADDRESS_BOOK_MAPPER = rowMapper(MigrationAddressBook.class);
    private static final RowMapper<MigrationAddressBookEntry> ADDRESS_BOOK_ENTRY_MAPPER =
            rowMapper(MigrationAddressBookEntry.class);
    private final String baseAccountId = "0.0.";
    private final String baseIp = "127.0.0.";
    private final int basePort = 443;
    private final int nodeAccountOffset = 3;

    @Value("classpath:db/migration/v1/V1.37.1__add_address_book_service_endpoints.sql")
    private final File sql;

    private int addressBookEntryIdCounter;

    @BeforeEach
    void before() {
        revertToPreV_1_37();
        ownerJdbcTemplate.execute("alter table address_book_entry drop constraint if exists "
                + "address_book_entry_consensus_timestamp_fkey");
        // previous address_book_entry had
        addressBookEntryIdCounter = 1;
    }

    @Test
    void verifyMigrateWhenBothDeprecatedIpAndMigrationAddressBookServiceEndpointsAreSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 3;
        int numEndPoints = nodeIdCount * (endPointPerNode + 1);

        insertAddressBook(systemEntity.addressBookFile101(), consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(true, consensusTimestamp, nodeIdCount, endPointPerNode);

        assertThat(jdbcOperations.queryForObject("select count(*) from address_book_entry", Integer.class))
                .isEqualTo(numEndPoints);

        runMigration();

        assertThat(findAllAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAllAddressBookServiceEndpoints())
                .isNotEmpty()
                .hasSize(numEndPoints)
                .extracting(MigrationAddressBookServiceEndpoint::getPort)
                .containsExactlyInAnyOrder(443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454);
    }

    @Test
    void verifyMigrateWhenOnlyMigrationAddressBookServiceEndpointsAreSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 3;
        int numEndPoints = nodeIdCount * endPointPerNode;

        insertAddressBook(systemEntity.addressBookFile102(), consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(false, consensusTimestamp, nodeIdCount, endPointPerNode);

        assertThat(jdbcOperations.queryForObject("select count(*) from address_book_entry", Integer.class))
                .isEqualTo(numEndPoints);

        runMigration();

        assertThat(findAllAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAllAddressBookServiceEndpoints())
                .isNotEmpty()
                .hasSize(numEndPoints)
                .extracting(MigrationAddressBookServiceEndpoint::getPort)
                .containsExactlyInAnyOrder(443, 444, 445, 446, 447, 448, 449, 450, 451);
    }

    @Test
    void verifyMigrateWhenOnlyDeprecatedIpIsSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 0;

        insertAddressBook(systemEntity.addressBookFile102(), consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(true, consensusTimestamp, nodeIdCount, endPointPerNode);

        assertThat(jdbcOperations.queryForObject("select count(*) from address_book_entry", Integer.class))
                .isEqualTo(nodeIdCount);

        runMigration();

        assertThat(findAllAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAllAddressBookServiceEndpoints())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookServiceEndpoint::getPort)
                .containsExactlyInAnyOrder(443, 444, 445);
    }

    @Test
    void verifyMigrateWhenNeitherDeprecatedIpNorMigrationAddressBookServiceEndpointsAreSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 0;

        insertAddressBook(systemEntity.addressBookFile102(), consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(false, consensusTimestamp, nodeIdCount, endPointPerNode);

        assertThat(jdbcOperations.queryForObject("select count(*) from address_book_entry", Integer.class))
                .isEqualTo(nodeIdCount);

        runMigration();

        assertThat(findAllAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAllAddressBookServiceEndpoints()).isEmpty();
    }

    @Test
    void verifyAddressBookEntryDuplicatesRemoved() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;

        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeId(0L)
                .publicKey("rsa+public/key");

        List<Long> nodeIds = List.of(0L, 1L, 2L);
        List<Integer> ports = List.of(80, 443, 5600);
        int numEndPoints = nodeIds.size() * ports.size();

        // populate address_book and address_book_entry
        insertAddressBook(systemEntity.addressBookFile102(), consensusTimestamp, nodeIdCount);
        nodeIds.forEach(nodeId -> {
            ports.forEach(port -> {
                insertAddressBookEntry(
                        builder.memo(baseAccountId + (nodeId + nodeAccountOffset))
                                .build(),
                        baseIp + nodeId, // duplicate ip per nodeId
                        port);
            });
        });

        assertThat(jdbcOperations.queryForObject("select count(*) from address_book_entry", Integer.class))
                .isEqualTo(numEndPoints);

        runMigration();

        assertThat(findAllAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIds.size())
                .extracting(MigrationAddressBookEntry::getNodeId)
                .containsExactlyInAnyOrderElementsOf(nodeIds);

        IterableAssert<MigrationAddressBookServiceEndpoint> listAssert =
                assertThat(findAllAddressBookServiceEndpoints()).isNotEmpty().hasSize(numEndPoints);

        List<Integer> allPorts = new ArrayList<>();
        allPorts.addAll(ports);
        allPorts.addAll(ports);
        allPorts.addAll(ports);
        listAssert
                .extracting(MigrationAddressBookServiceEndpoint::getPort)
                .containsExactlyInAnyOrderElementsOf(allPorts);

        // verify address_book counts are updated
        assertThat(findById(consensusTimestamp))
                .get()
                .returns(systemEntity.addressBookFile102(), MigrationAddressBook::getFileId)
                .returns(nodeIds.size(), MigrationAddressBook::getNodeCount)
                .returns(null, MigrationAddressBook::getEndConsensusTimestamp);
    }

    @Test
    void verifyInitialAddressBookNullEntriesUpdated() throws IOException {
        long consensusTimestamp = 1;
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeId(0L)
                .publicKey("rsa+public/key");

        List<Long> nodeIds = List.of(0L, 1L, 2L, 3L);
        insertAddressBook(systemEntity.addressBookFile102(), consensusTimestamp, nodeIds.size());
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(0) + nodeAccountOffset))
                        .build(),
                "",
                0);
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(1) + nodeAccountOffset))
                        .build(),
                "",
                0);
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(2) + nodeAccountOffset))
                        .build(),
                "",
                0);
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(3) + nodeAccountOffset))
                        .build(),
                "",
                0);

        runMigration();

        IterableAssert<MigrationAddressBookEntry> listAssert =
                assertThat(findAllAddressBookEntries()).isNotEmpty().hasSize(nodeIds.size());

        listAssert.extracting(MigrationAddressBookEntry::getNodeId).containsExactlyInAnyOrderElementsOf(nodeIds);
        listAssert
                .extracting(MigrationAddressBookEntry::getNodeAccountId)
                .containsExactlyInAnyOrder(
                        EntityId.of(baseAccountId + (nodeIds.get(0) + nodeAccountOffset)),
                        EntityId.of(baseAccountId + (nodeIds.get(1) + nodeAccountOffset)),
                        EntityId.of(baseAccountId + (nodeIds.get(2) + nodeAccountOffset)),
                        EntityId.of(baseAccountId + (nodeIds.get(3) + nodeAccountOffset)));

        assertThat(findAllAddressBookServiceEndpoints()).isEmpty();
    }

    @Test
    void verifyAddressBookWithValidIpAndInvalidPortMigration() throws IOException {
        long consensusTimestamp = 1;
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .nodeCertHash("nodeCertHash".getBytes())
                .publicKey("rsa+public/key");

        List<Long> nodeIds = List.of(0L, 1L, 2L, 3L);
        insertAddressBook(systemEntity.addressBookFile102(), consensusTimestamp, nodeIds.size());
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(0) + nodeAccountOffset))
                        .consensusTimestamp(consensusTimestamp)
                        .nodeId(nodeIds.get(0))
                        .build(),
                "127.0.0.1",
                null);
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(1) + nodeAccountOffset))
                        .consensusTimestamp(consensusTimestamp)
                        .nodeId(nodeIds.get(1))
                        .build(),
                "127.0.0.2",
                0);
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(2) + nodeAccountOffset))
                        .consensusTimestamp(consensusTimestamp)
                        .nodeId(nodeIds.get(2))
                        .build(),
                "127.0.0.3",
                null);
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(3) + nodeAccountOffset))
                        .consensusTimestamp(consensusTimestamp)
                        .nodeId(nodeIds.get(3))
                        .build(),
                "127.0.0.4",
                50211);

        runMigration();

        IterableAssert<MigrationAddressBookEntry> listAssert =
                assertThat(findAllAddressBookEntries()).isNotEmpty().hasSize(nodeIds.size());

        listAssert.extracting(MigrationAddressBookEntry::getNodeId).containsExactlyInAnyOrderElementsOf(nodeIds);
        listAssert
                .extracting(MigrationAddressBookEntry::getNodeAccountId)
                .containsExactlyInAnyOrder(
                        EntityId.of(baseAccountId + (nodeIds.get(0) + nodeAccountOffset)),
                        EntityId.of(baseAccountId + (nodeIds.get(1) + nodeAccountOffset)),
                        EntityId.of(baseAccountId + (nodeIds.get(2) + nodeAccountOffset)),
                        EntityId.of(baseAccountId + (nodeIds.get(3) + nodeAccountOffset)));

        IterableAssert<MigrationAddressBookServiceEndpoint> serviceListAssert =
                assertThat(findAllAddressBookServiceEndpoints()).isNotEmpty().hasSize(nodeIds.size());

        serviceListAssert
                .extracting(MigrationAddressBookServiceEndpoint::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L, 3L);

        serviceListAssert
                .extracting(MigrationAddressBookServiceEndpoint::getPort)
                .containsExactlyInAnyOrder(-1, 0, -1, 50211);
    }

    private List<AddressBookEntry> getAndSaveAddressBookEntries(
            boolean deprecatedIp, long consensusTimestamp, int nodeCount, int endPointCount) {
        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        int numEndpointsPerNode = deprecatedIp ? endPointCount + 1 : endPointCount;
        for (int id = 0; id < nodeCount; id++) {
            addressBookEntries.addAll(getAndSaveAddressBookEntry(
                    deprecatedIp, (long) numEndpointsPerNode * id, consensusTimestamp, id, endPointCount));
        }

        return addressBookEntries;
    }

    private List<AddressBookEntry> getAndSaveAddressBookEntry(
            boolean deprecatedIp, long id, long consensusTimestamp, long nodeId, int endPointCount) {
        long accountIdNum = nodeAccountOffset + nodeId;
        String accountId = baseAccountId + accountIdNum;
        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .nodeId(nodeId)
                .memo(accountId)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeAccountId(EntityId.of(accountId))
                .publicKey("rsa+public/key");

        AtomicLong idCount = new AtomicLong(id);
        AddressBookEntry addressBookEntry = builder.build();
        if (deprecatedIp) {
            insertAddressBookEntry(addressBookEntry, baseIp + idCount.get(), basePort + (int) idCount.get());
            idCount.getAndIncrement();
            addressBookEntries.add(addressBookEntry);
        }

        for (int i = 1; i <= endPointCount; i++) {
            insertAddressBookEntry(addressBookEntry, baseIp + idCount.get(), basePort + (int) idCount.get());
            idCount.getAndIncrement();
            addressBookEntries.add(addressBookEntry);
        }

        // handle no endpoints case
        if (!deprecatedIp && endPointCount == 0) {
            insertAddressBookEntry(addressBookEntry, "", 0);
            addressBookEntries.add(addressBookEntry);
        }

        return addressBookEntries;
    }

    /**
     * Insert address book object using only columns supported before V_1_37.1
     *
     * @param fileEntityId
     * @param startConsensusTimestamp
     * @param nodeCount
     */
    private void insertAddressBook(EntityId fileEntityId, long startConsensusTimestamp, int nodeCount) {
        AddressBook addressBook = new AddressBook();
        addressBook.setFileData(new byte[] {});
        addressBook.setFileId(fileEntityId);
        addressBook.setStartConsensusTimestamp(startConsensusTimestamp);
        addressBook.setNodeCount(nodeCount);

        jdbcOperations.update(
                "insert into address_book (start_consensus_timestamp, end_consensus_timestamp, file_id, "
                        + "node_count, file_data) values"
                        + " (?, ?, ?, ?, ?)",
                addressBook.getStartConsensusTimestamp(),
                addressBook.getEndConsensusTimestamp(),
                addressBook.getFileId().getId(),
                addressBook.getNodeCount(),
                addressBook.getFileData());
    }

    /**
     * Insert address book entry object using only columns supported before V_1_37.1
     *
     * @param addressBookEntry address book entry ref
     * @param ip               service endpoint ip
     * @param port             service endpoint  port
     */
    private void insertAddressBookEntry(AddressBookEntry addressBookEntry, String ip, Integer port) {
        Long nodeAccountId = EntityId.isEmpty(addressBookEntry.getNodeAccountId())
                ? null
                : addressBookEntry.getNodeAccountId().getId();
        jdbcOperations.update(
                "insert into address_book_entry (id, consensus_timestamp, ip, memo, node_account_id, "
                        + "node_cert_hash, node_id, port, public_key) values"
                        + " (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                addressBookEntryIdCounter++,
                addressBookEntry.getConsensusTimestamp(),
                ip,
                addressBookEntry.getMemo(),
                nodeAccountId,
                addressBookEntry.getNodeCertHash(),
                addressBookEntry.getNodeId(),
                port,
                addressBookEntry.getPublicKey());
    }

    private Iterable<MigrationAddressBookServiceEndpoint> findAllAddressBookServiceEndpoints() {
        return jdbcOperations.query(
                "select * from address_book_service_endpoint", ADDRESS_BOOK_SERVICE_ENDPOINT_MAPPER);
    }

    private Iterable<MigrationAddressBookEntry> findAllAddressBookEntries() {
        return jdbcOperations.query("select * from address_book_entry", ADDRESS_BOOK_ENTRY_MAPPER);
    }

    private Optional<MigrationAddressBook> findById(long id) {
        return Optional.of(jdbcOperations.queryForObject(
                "select * from address_book where start_consensus_timestamp = ?", ADDRESS_BOOK_MAPPER, id));
    }

    private void runMigration() throws IOException {
        log.info("Run migration: {}", sql.getName());
        ownerJdbcTemplate.update(FileUtils.readFileToString(sql, "UTF-8"));
    }

    /**
     * Ensure entity tables match expected state before V_1_37.1
     */
    private void revertToPreV_1_37() {
        // remove address_book_service_endpoint table if present
        ownerJdbcTemplate.execute("drop table if exists address_book_service_endpoint cascade;");

        // drop describe and stake columns. Also drop primary key
        ownerJdbcTemplate.execute("""
                        alter table if exists address_book_entry
                            drop column if exists description,
                            drop column if exists stake,
                            drop constraint if exists address_book_entry_pkey;
                        """);

        // restore id, ip and port columns. Also restore primary key
        ownerJdbcTemplate.execute("""
                        alter table if exists address_book_entry
                            add column if not exists id integer,
                            add column if not exists ip varchar(128) null,
                            add column if not exists port integer null,
                            alter column node_account_id drop not null,
                            alter column node_id drop not null,
                            add primary key (id);
                        """);
    }

    // Use a custom class for address_book_service_endpoint table since its columns have changed from the current domain
    // object
    @Data
    @Builder
    @AllArgsConstructor
    private static class MigrationAddressBookServiceEndpoint {
        private long consensusTimestamp;
        private String ipAddressV4;
        private long nodeId;
        private Integer port;
    }

    @Data
    @NoArgsConstructor
    private static class MigrationAddressBookEntry {
        private long consensusTimestamp;
        private long nodeId;
        private String memo;
        private EntityId nodeAccountId;
        private byte[] nodeCertHash;
        private String publicKey;
        private String description;
        private Long stake;
    }

    @Data
    @NoArgsConstructor
    private static class MigrationAddressBook {
        private Long startConsensusTimestamp;
        private Long endConsensusTimestamp;
        private byte[] fileData;
        private EntityId fileId;
        private Integer nodeCount;
    }
}
