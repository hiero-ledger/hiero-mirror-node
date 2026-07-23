// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AddressBookRepositoryTest extends ImporterIntegrationTest {

    private final AddressBookRepository addressBookRepository;

    @Test
    void save() {
        var addressBook = domainBuilder.addressBook().get();
        var addressBookEntry = domainBuilder
                .addressBookEntry()
                .customize(e -> e.consensusTimestamp(addressBook.getStartConsensusTimestamp()))
                .get();
        addressBook.getEntries().add(addressBookEntry);
        addressBookRepository.save(addressBook);
        assertThat(addressBookRepository.findById(addressBook.getStartConsensusTimestamp()))
                .get()
                .isEqualTo(addressBook)
                .extracting(AddressBook::getEntries)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(AtomicReference.class, EntityType.class)
                .isEqualTo(addressBook.getEntries());
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

    @Test
    void findLatest() {
        domainBuilder.addressBook().persist();
        var addressBook2 = domainBuilder.addressBook().persist();
        var addressBook3 = domainBuilder
                .addressBook()
                .customize(a -> a.fileId(systemEntity.addressBookFile101()))
                .persist();

        assertThat(addressBookRepository.findLatest(
                        addressBook3.getStartConsensusTimestamp(),
                        systemEntity.addressBookFile102().getId()))
                .get()
                .isEqualTo(addressBook2);
    }
}
