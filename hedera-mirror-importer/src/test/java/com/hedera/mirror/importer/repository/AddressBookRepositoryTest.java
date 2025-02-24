// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.importer.addressbook.AddressBookServiceImpl.FILE_101;
import static com.hedera.mirror.importer.addressbook.AddressBookServiceImpl.FILE_102;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
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
        var addressBook3 =
                domainBuilder.addressBook().customize(a -> a.fileId(FILE_101)).persist();

        assertThat(addressBookRepository.findLatest(addressBook3.getStartConsensusTimestamp(), FILE_102.getId()))
                .get()
                .isEqualTo(addressBook2);
    }
}
