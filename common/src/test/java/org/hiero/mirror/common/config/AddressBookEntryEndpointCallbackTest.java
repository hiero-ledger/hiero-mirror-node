// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.junit.jupiter.api.Test;

class AddressBookEntryEndpointCallbackTest {

    private static final long TIMESTAMP = 100L;
    private final AddressBookEntryEndpointCallback callback = new AddressBookEntryEndpointCallback();

    @Test
    void dropsCrossNodeEndpoints() {
        // simulate the @MappedCollection over-distribution: every entry is loaded with all endpoints in the book
        final var allEndpoints =
                List.of(endpoint(0L, 0), endpoint(0L, 1), endpoint(1L, 0), endpoint(1L, 1), endpoint(1L, 2));
        final var entry0 = entry(0L, allEndpoints);
        final var entry1 = entry(1L, allEndpoints);
        final var addressBook = AddressBook.builder()
                .startConsensusTimestamp(TIMESTAMP)
                .entries(new java.util.HashSet<>(List.of(entry0, entry1)))
                .build();

        final var result = callback.onAfterConvert(addressBook);

        assertThat(result.getEntries()).allSatisfy(entry -> assertThat(entry.getServiceEndpoints())
                .allSatisfy(endpoint -> assertThat(endpoint.getNodeId()).isEqualTo(entry.getNodeId())));
        assertThat(entry0.getServiceEndpoints()).hasSize(2);
        assertThat(entry1.getServiceEndpoints()).hasSize(3);
    }

    @Test
    void handlesEntryWithoutEndpoints() {
        final var entry = entry(0L, List.of());
        final var addressBook = AddressBook.builder()
                .startConsensusTimestamp(TIMESTAMP)
                .entries(new java.util.HashSet<>(List.of(entry)))
                .build();

        assertThat(callback.onAfterConvert(addressBook).getEntries())
                .singleElement()
                .satisfies(e -> assertThat(e.getServiceEndpoints()).isEmpty());
    }

    private AddressBookEntry entry(long nodeId, List<AddressBookServiceEndpoint> endpoints) {
        return AddressBookEntry.builder()
                .consensusTimestamp(TIMESTAMP)
                .nodeId(nodeId)
                .serviceEndpoints(new LinkedHashSet<>(endpoints))
                .build();
    }

    private AddressBookServiceEndpoint endpoint(long nodeId, int index) {
        return AddressBookServiceEndpoint.builder()
                .id(new AddressBookServiceEndpoint.Id(TIMESTAMP, "127.0.0." + nodeId, nodeId, 50211 + index, null))
                .build();
    }
}
