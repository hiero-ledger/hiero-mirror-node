// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.repository.NetworkNodeRepository.NetworkNodeView;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class NetworkNodeRepositoryTest extends GrpcIntegrationTest {

    private final NetworkNodeRepository networkNodeRepository;

    @Test
    void findByConsensusTimestampAndMinNodeId() {
        final long consensusTimestamp = 1L;
        final int limit = 2;
        final var addressBookEntry1 = addressBookEntry(consensusTimestamp, 0L);
        final var addressBookEntry2 = addressBookEntry(consensusTimestamp, 1L);
        final var addressBookEntry3 = addressBookEntry(consensusTimestamp, 2L);
        addressBookEntry(consensusTimestamp + 1, 0L);

        assertThat(networkNodeRepository.findByConsensusTimestampAndMinNodeId(consensusTimestamp, 0L, limit))
                .as("First page has a length equal to limit")
                .extracting(NetworkNodeView::nodeId)
                .containsExactly(addressBookEntry1.getNodeId(), addressBookEntry2.getNodeId());

        assertThat(networkNodeRepository.findByConsensusTimestampAndMinNodeId(consensusTimestamp, limit, limit))
                .as("Second page has less than limit")
                .extracting(NetworkNodeView::nodeId)
                .containsExactly(addressBookEntry3.getNodeId());
    }

    @Test
    void findByConsensusTimestampAndMinNodeIdIsCached() {
        final long consensusTimestamp = 1L;
        final int limit = 10;
        addressBookEntry(consensusTimestamp, 0L);
        final var expected = networkNodeRepository.findByConsensusTimestampAndMinNodeId(consensusTimestamp, 0L, limit);

        // A row added afterwards is not visible since the same page is served from the cache
        addressBookEntry(consensusTimestamp, 1L);

        assertThat(networkNodeRepository.findByConsensusTimestampAndMinNodeId(consensusTimestamp, 0L, limit))
                .hasSize(1)
                .isSameAs(expected);
    }

    private AddressBookEntry addressBookEntry(final long consensusTimestamp, final long nodeId) {
        return domainBuilder
                .addressBookEntry()
                .customize(e -> e.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                .persist();
    }
}
