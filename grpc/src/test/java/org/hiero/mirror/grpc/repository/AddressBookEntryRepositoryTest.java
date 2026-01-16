// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class AddressBookEntryRepositoryTest extends GrpcIntegrationTest {

    private final AddressBookEntryRepository addressBookEntryRepository;

    @Test
    void findByConsensusTimestampAndNodeId() {
        long consensusTimestamp = 1L;
        int limit = 2;
        AddressBookEntry addressBookEntry1 = addressBookEntry(consensusTimestamp, 0L);
        AddressBookEntry addressBookEntry2 = addressBookEntry(consensusTimestamp, 1L);
        AddressBookEntry addressBookEntry3 = addressBookEntry(consensusTimestamp, 2L);
        addressBookEntry(consensusTimestamp + 1, 0L);

        assertThat(addressBookEntryRepository.findByConsensusTimestampAndNodeId(consensusTimestamp, 0L, limit))
                .as("First page has a length equal to limit")
                .hasSize(limit)
                .containsExactly(addressBookEntry1, addressBookEntry2);

        assertThat(addressBookEntryRepository.findByConsensusTimestampAndNodeId(consensusTimestamp, limit, limit))
                .as("Second page has less than limit")
                .containsExactly(addressBookEntry3);
    }

    @Test
    @Transactional
    void serviceEndpoints() {
        AddressBookEntry addressBookEntry = domainBuilder.addressBookEntry(3).persist();
        assertThat(addressBookEntryRepository.findById(addressBookEntry.getId()))
                .get()
                .extracting(AddressBookEntry::getServiceEndpoints)
                .asInstanceOf(InstanceOfAssertFactories.COLLECTION)
                .containsExactlyInAnyOrderElementsOf(addressBookEntry.getServiceEndpoints());
    }

    private AddressBookEntry addressBookEntry(long consensusTimestamp, long nodeId) {
        return domainBuilder
                .addressBookEntry()
                .customize(e -> e.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                .persist();
    }
}
