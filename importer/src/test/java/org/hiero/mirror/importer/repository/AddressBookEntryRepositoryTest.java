// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AddressBookEntryRepositoryTest extends ImporterIntegrationTest {

    private final AddressBookEntryRepository addressBookEntryRepository;

    @Test
    void save() {
        var addressBookEntry = domainBuilder.addressBookEntry().get();
        addressBookEntryRepository.save(addressBookEntry);
        assertThat(addressBookEntryRepository.findById(addressBookEntry.getId()))
                .get()
                .isEqualTo(addressBookEntry);
    }
}
