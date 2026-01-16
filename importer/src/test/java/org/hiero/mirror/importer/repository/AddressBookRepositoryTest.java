// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
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
