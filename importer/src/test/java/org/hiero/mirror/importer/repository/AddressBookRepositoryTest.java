// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AddressBookRepositoryTest extends ImporterIntegrationTest {

    private final AddressBookRepository addressBookRepository;

    @Test
    void save() {
        final var addressBook = saveAddressBookWithEntries(systemEntity.addressBookFile102());

        assertAddressBook(addressBookRepository.findById(addressBook.getStartConsensusTimestamp()), addressBook);
    }

    @Test
    void findLatest() {
        domainBuilder.addressBook().persist();
        final var addressBook2 = saveAddressBookWithEntries(systemEntity.addressBookFile102());
        final var addressBook3 = saveAddressBookWithEntries(systemEntity.addressBookFile101());

        assertAddressBook(
                addressBookRepository.findLatest(
                        addressBook3.getStartConsensusTimestamp(),
                        systemEntity.addressBookFile102().getId()),
                addressBook2);
    }

    @Test
    void serviceEndpointsAreScopedPerEntry() {
        // given — one address book with two entries sharing consensus_timestamp but different node ids,
        // each owning a different number of its own service endpoints
        final var addressBook = domainBuilder.addressBook().get();
        final var timestamp = addressBook.getStartConsensusTimestamp();
        final var entry0 = addressBookEntry(timestamp, 0L, 2);
        final var entry1 = addressBookEntry(timestamp, 1L, 3);
        addressBook.getEntries().add(entry0);
        addressBook.getEntries().add(entry1);
        addressBookRepository.save(addressBook);

        // when / then — assert both read paths: findById (default CRUD) and findLatest (the custom @Query the
        // importer actually uses via getCurrent()), since @Query aggregates fire callbacks through a different mapper
        assertScopedEndpoints(
                addressBookRepository.findById(timestamp).orElseThrow().getEntries());
        assertScopedEndpoints(addressBookRepository
                .findLatest(timestamp, addressBook.getFileId().getId())
                .orElseThrow()
                .getEntries());
    }

    @Test
    void serviceEndpointsPersistRealNodeIdNotListIndex() {
        // given — an entry whose node_id (99) is far larger than any position a list index would produce for its
        // three endpoints (0, 1, 2). If serviceEndpoints were a keyed List, Spring Data JDBC would write the list
        // index into the node_id column; as a Set it must persist the endpoint's own node_id.
        final var addressBook = domainBuilder.addressBook().get();
        final var timestamp = addressBook.getStartConsensusTimestamp();
        addressBook.getEntries().add(addressBookEntry(timestamp, 99L, 3));
        addressBookRepository.save(addressBook);

        // then — read the raw column, bypassing the entity mapping, to prove what actually landed in the database
        final var rawNodeIds = jdbcOperations.queryForList(
                "select node_id from address_book_service_endpoint where consensus_timestamp = ? order by port",
                Long.class,
                timestamp);
        assertThat(rawNodeIds).containsExactly(99L, 99L, 99L);
    }

    private AddressBookEntry addressBookEntry(long consensusTimestamp, long nodeId, int endpoints) {
        final var entry = domainBuilder
                .addressBookEntry(endpoints)
                .customize(e -> e.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                .get();
        entry.getServiceEndpoints().forEach(endpoint -> {
            endpoint.setConsensusTimestamp(consensusTimestamp);
            endpoint.setNodeId(nodeId);
        });
        return entry;
    }

    // Entries and their endpoints are excluded from equals, so compare them recursively. Each entry must have exactly
    // its own node's endpoints, although every entry in the book shares the same consensus timestamp.
    private void assertAddressBook(Optional<AddressBook> actual, AddressBook expected) {
        assertThat(actual)
                .get()
                .isEqualTo(expected)
                .extracting(AddressBook::getEntries)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFieldsOfTypes(AtomicReference.class, EntityType.class)
                .isEqualTo(expected.getEntries());
    }

    private void assertScopedEndpoints(Iterable<AddressBookEntry> entries) {
        // each entry must see only its own node's endpoints, not every endpoint in the book
        assertThat(entries)
                .allSatisfy(entry -> assertThat(entry.getServiceEndpoints())
                        .allSatisfy(endpoint -> assertThat(endpoint.getNodeId()).isEqualTo(entry.getNodeId())))
                .filteredOn(entry -> entry.getNodeId() == 0L)
                .singleElement()
                .satisfies(entry -> assertThat(entry.getServiceEndpoints()).hasSize(2));
        assertThat(entries)
                .filteredOn(entry -> entry.getNodeId() == 1L)
                .singleElement()
                .satisfies(entry -> assertThat(entry.getServiceEndpoints()).hasSize(3));
    }

    private AddressBookEntry addressBookEntryWithEndpoints(long consensusTimestamp, long nodeId) {
        final var ipAddressEndpoint = domainBuilder
                .addressBookServiceEndpoint()
                .customize(e -> e.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                .get();
        final var domainNameEndpoint = domainBuilder
                .addressBookServiceEndpoint()
                .customize(e -> e.consensusTimestamp(consensusTimestamp)
                        .domainName("node" + nodeId + ".example.com")
                        .ipAddressV4("")
                        .nodeId(nodeId)
                        .port(443))
                .get();

        return domainBuilder
                .addressBookEntry()
                .customize(e -> e.consensusTimestamp(consensusTimestamp)
                        .nodeId(nodeId)
                        .serviceEndpoints(new HashSet<>(List.of(ipAddressEndpoint, domainNameEndpoint)))
                        .stake(domainBuilder.number()))
                .get();
    }

    private AddressBook saveAddressBookWithEntries(EntityId fileId) {
        final var addressBook =
                domainBuilder.addressBook().customize(a -> a.fileId(fileId)).get();
        final long consensusTimestamp = addressBook.getStartConsensusTimestamp();
        addressBook.getEntries().add(addressBookEntryWithEndpoints(consensusTimestamp, 0L));
        addressBook.getEntries().add(addressBookEntryWithEndpoints(consensusTimestamp, 1L));
        addressBookRepository.save(addressBook);
        return addressBook;
    }
}
