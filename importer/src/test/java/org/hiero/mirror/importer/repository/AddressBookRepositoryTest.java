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
