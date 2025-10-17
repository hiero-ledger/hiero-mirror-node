// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.grpc.service.NetworkServiceImpl.INVALID_FILE_ID;

import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.domain.AddressBookFilter;
import org.hiero.mirror.grpc.exception.EntityNotFoundException;
import org.hiero.mirror.grpc.repository.AddressBookEntryRepository;
import org.hiero.mirror.grpc.repository.NodeStakeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

@RequiredArgsConstructor
class NetworkServiceTest extends GrpcIntegrationTest {

    private static final long CONSENSUS_TIMESTAMP = 1L;
    private static final long NODE_STAKE_CONSENSUS_TIMESTAMP = 10L; // A daily thing

    private final AddressBookEntryRepository addressBookEntryRepository;
    private final AddressBookProperties addressBookProperties;
    private final DomainBuilder domainBuilder;
    private final NetworkService networkService;
    private final NodeStakeRepository nodeStakeRepository;

    private int pageSize;

    @BeforeEach
    void setup() {
        pageSize = addressBookProperties.getPageSize();
    }

    @AfterEach
    void cleanup() {
        addressBookProperties.setPageSize(pageSize);
    }

    @Test
    void invalidFilter() {
        var filter = AddressBookFilter.builder().fileId(null).limit(-1).build();

        assertThatThrownBy(() -> networkService.getNodes(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("limit: must be greater than or equal to 0")
                .hasMessageContaining("fileId: must not be null");
    }

    @Test
    void addressBookNotFound() {
        var fileId = systemEntity.addressBookFile102();
        var filter = AddressBookFilter.builder().fileId(fileId).build();

        assertThatThrownBy(() -> networkService.getNodes(filter))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("%s does not exist".formatted(fileId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.1001", "2.0.101", "0.2.101", "1.0.102", "0.1.102"})
    void invalidFileId(String fileId) {
        var filter = AddressBookFilter.builder().fileId(EntityId.of(fileId)).build();

        assertThatThrownBy(() -> networkService.getNodes(filter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(INVALID_FILE_ID);
    }

    @Test
    void noNodes() {
        AddressBook addressBook = addressBook();
        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();

        StepVerifier.withVirtualTime(() -> networkService.getNodes(filter))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void lessThanPageSize() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry = addressBookEntry();
        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry);
    }

    @Test
    void equalToPageSize() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        AddressBookEntry addressBookEntry2 = addressBookEntry();
        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2);
    }

    @Test
    void moreThanPageSize() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        AddressBookEntry addressBookEntry2 = addressBookEntry();
        AddressBookEntry addressBookEntry3 = addressBookEntry();
        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2, addressBookEntry3);
    }

    @Test
    void limitReached() {
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        addressBookEntry();
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(addressBook.getFileId())
                .limit(1)
                .build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1);
    }

    @Test
    void cached() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        AddressBookEntry addressBookEntry2 = addressBookEntry();
        AddressBookEntry addressBookEntry3 = addressBookEntry();
        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2, addressBookEntry3);

        addressBookEntryRepository.deleteAll();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2, addressBookEntry3);
    }

    @Test
    void overrideStakeToZeroWhenEmptyNodeStakeTable() {
        var addressBook = addressBook();
        var addressBookEntry = addressBookEntry(10L); // Persist stake
        addressBookEntry.setStake(0L); // Now expected

        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();
        assertThat(getNodes(filter)).containsExactly(addressBookEntry);
    }

    @Test
    void overrideStakeFromNodeStakeTable() {
        var nodeStakeTableStake = 100L;

        var addressBook = addressBook();
        var addressBookEntry1 = addressBookEntry(10L);
        var addressBookEntry2 = addressBookEntry();
        var addressBookEntry3 = addressBookEntry(30L);

        nodeStake(addressBookEntry1.getNodeId(), nodeStakeTableStake);
        nodeStake(addressBookEntry2.getNodeId(), nodeStakeTableStake);

        // Set in memory entries expected stake values for assertion
        addressBookEntry1.setStake(nodeStakeTableStake);
        addressBookEntry2.setStake(nodeStakeTableStake);
        // No node_stake row defined for addressBookEntry3 node ID, so stake expected to be overridden as zero.
        addressBookEntry3.setStake(0L);

        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();
        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2, addressBookEntry3);
        assertThat(addressBookEntryRepository.findAll())
                .extracting(AddressBookEntry::getStake)
                .doesNotContain(nodeStakeTableStake);
    }

    @Test
    void cachedNodeStake() {
        var nodeStakeTableStake = 100L;

        var addressBook = addressBook();
        var addressBookEntry = addressBookEntry(10L);

        nodeStake(addressBookEntry.getNodeId(), nodeStakeTableStake);
        addressBookEntry.setStake(nodeStakeTableStake);

        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();
        assertThat(getNodes(filter)).containsExactly(addressBookEntry);

        nodeStakeRepository.deleteAll();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry);
    }

    private List<AddressBookEntry> getNodes(AddressBookFilter filter) {
        return networkService.getNodes(filter).collectList().block(Duration.ofMillis(1000L));
    }

    private AddressBook addressBook() {
        return domainBuilder
                .addressBook()
                .customize(a -> a.startConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .persist();
    }

    private AddressBookEntry addressBookEntry() {
        return domainBuilder
                .addressBookEntry()
                .customize(a -> a.consensusTimestamp(CONSENSUS_TIMESTAMP))
                .persist();
    }

    private AddressBookEntry addressBookEntry(long stake) {
        return domainBuilder
                .addressBookEntry()
                .customize(a -> a.consensusTimestamp(CONSENSUS_TIMESTAMP).stake(stake))
                .persist();
    }

    private void nodeStake(long nodeId, long stake) {
        domainBuilder
                .nodeStake()
                .customize(e -> e.consensusTimestamp(NODE_STAKE_CONSENSUS_TIMESTAMP)
                        .nodeId(nodeId)
                        .stake(stake))
                .persist();
    }

    @Test
    void overrideNodeAccountIdWhenPresent() {
        var addressBook = addressBook();
        var addressBookEntry1 = addressBookEntry();
        var addressBookEntry2 = addressBookEntry();
        var node1AccountId = EntityId.of(0L, 0L, 1000L);
        var node2AccountId = EntityId.of(0L, 0L, 1001L);
        createNode(addressBookEntry1.getNodeId(), node1AccountId);
        createNode(addressBookEntry2.getNodeId(), node2AccountId);
        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();
        var nodes = getNodes(filter);

        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).getNodeAccountId()).isEqualTo(node1AccountId);
        assertThat(nodes.get(1).getNodeAccountId()).isEqualTo(node2AccountId);
    }

    @Test
    void keepExistingNodeAccountIdWhenRepositoryAccountIdIsEmpty() {
        var addressBook = addressBook();
        var entry = addressBookEntry();
        var originalAccountId = entry.getNodeAccountId();

        domainBuilder
                .node()
                .customize(n ->
                        n.nodeId(entry.getNodeId()).accountId(EntityId.EMPTY).deleted(false))
                .persist();

        var filter = AddressBookFilter.builder().fileId(addressBook.getFileId()).build();
        var result = getNodes(filter);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getNodeAccountId()).isEqualTo(originalAccountId);
    }

    // Helper method to add to the test class
    private void createNode(long nodeId, EntityId accountId) {
        domainBuilder
                .node()
                .customize(n -> {
                    n.nodeId(nodeId);
                    if (accountId != null) {
                        n.accountId(accountId);
                    }
                    n.deleted(false);
                })
                .persist();
    }
}
