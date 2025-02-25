// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
