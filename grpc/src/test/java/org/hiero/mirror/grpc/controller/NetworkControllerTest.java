// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.api.proto.AddressBookQuery;
import com.hedera.mirror.api.proto.ReactorNetworkServiceGrpc;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.HashSet;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.util.ProtoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

final class NetworkControllerTest extends GrpcIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);
    private static final long CONSENSUS_TIMESTAMP = 1L;

    @Autowired
    private DomainBuilder domainBuilder;

    @Autowired
    private SystemEntity systemEntity;

    @Autowired
    private ReactorNetworkServiceGrpc.ReactorNetworkServiceStub reactiveService;

    @Test
    void getNodesMissingFileId() {
        final var query = AddressBookQuery.newBuilder().build();
        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.INVALID_ARGUMENT, "fileId: must not be null"))
                .verify(WAIT);
    }

    @Test
    void getNodesInvalidFileId() {
        final var query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder().setFileNum(-1).build())
                .build();
        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.INVALID_ARGUMENT, "Invalid entity ID"))
                .verify(WAIT);
    }

    @Test
    void getNodesInvalidLimit() {
        final var query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder().build())
                .setLimit(-1)
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(
                        t, Status.Code.INVALID_ARGUMENT, "limit: must be greater " + "than or equal to 0"))
                .verify(WAIT);
    }

    @Test
    void getNodesNotFound() {
        final var query = AddressBookQuery.newBuilder()
                .setFileId(systemEntity.addressBookFile102().toFileID())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.NOT_FOUND, "does not exist"))
                .verify(WAIT);
    }

    @Test
    void getNodesNoLimit() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntry();
        final var addressBookEntry2 = addressBookEntry();
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertEntry(addressBookEntry1, n))
                .consumeNextWith(n -> assertEntry(addressBookEntry2, n))
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void getNodesNoLimitServiceEndpointWithDomainName() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntryCustomized("www.example-node.com", "", 5000);
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertEntry(addressBookEntry1, n))
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void getNodesTestWithEmptyDomainNameAndIpAddress() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntryCustomized("", "", 0);
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertEntry(addressBookEntry1, n))
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void getNodesLmitReached() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntry();
        addressBookEntry();
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .setLimit(1)
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertEntry(addressBookEntry1, n))
                .expectComplete()
                .verify(WAIT);
    }

    @SuppressWarnings("deprecation")
    @Test
    void getNodesNullFields() {
        final var addressBook = addressBook();
        final var addressBookEntry = domainBuilder
                .addressBookEntry()
                .customize(a -> a.consensusTimestamp(CONSENSUS_TIMESTAMP)
                        .description(null)
                        .memo(null)
                        .nodeCertHash(null)
                        .publicKey(null)
                        .stake(null))
                .persist();
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertThat(n)
                        .isNotNull()
                        .returns("", NodeAddress::getDescription)
                        .returns(ByteString.EMPTY, NodeAddress::getMemo)
                        .returns(addressBookEntry.getNodeAccountId(), t -> EntityId.of(n.getNodeAccountId()))
                        .returns(ByteString.EMPTY, NodeAddress::getNodeCertHash)
                        .returns(addressBookEntry.getNodeId(), NodeAddress::getNodeId)
                        .returns("", NodeAddress::getRSAPubKey)
                        .returns(0L, NodeAddress::getStake))
                .expectComplete()
                .verify(WAIT);
    }

    private AddressBook addressBook() {
        return domainBuilder
                .addressBook()
                .customize(a -> a.startConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .persist();
    }

    private AddressBookEntry addressBookEntry() {
        return domainBuilder
                .addressBookEntry(1)
                .customize(a -> a.consensusTimestamp(CONSENSUS_TIMESTAMP))
                .persist();
    }

    private AddressBookEntry addressBookEntryCustomized(String domainName, String ipAddress, int port) {
        final var serviceEndpoints = new HashSet<AddressBookServiceEndpoint>();
        final var endpoint = domainBuilder
                .addressBookServiceEndpoint()
                .customize(a -> a.domainName(domainName).ipAddressV4(ipAddress).port(port))
                .get();
        serviceEndpoints.add(endpoint);
        return domainBuilder
                .addressBookEntry(1)
                .customize(a -> a.serviceEndpoints(serviceEndpoints).consensusTimestamp(CONSENSUS_TIMESTAMP))
                .persist();
    }

    @SuppressWarnings("deprecation")
    private void assertEntry(AddressBookEntry addressBookEntry, NodeAddress nodeAddress) {
        assertThat(nodeAddress)
                .isNotNull()
                .returns(addressBookEntry.getDescription(), NodeAddress::getDescription)
                .returns(ByteString.copyFromUtf8(addressBookEntry.getMemo()), NodeAddress::getMemo)
                .returns(addressBookEntry.getNodeAccountId(), n -> EntityId.of(n.getNodeAccountId()))
                .returns(ProtoUtil.toByteString(addressBookEntry.getNodeCertHash()), NodeAddress::getNodeCertHash)
                .returns(addressBookEntry.getNodeId(), NodeAddress::getNodeId)
                .returns(addressBookEntry.getPublicKey(), NodeAddress::getRSAPubKey)
                .returns(addressBookEntry.getStake(), NodeAddress::getStake);

        var serviceEndpoint = addressBookEntry.getServiceEndpoints().iterator().next();
        ByteString ipAddress = ByteString.EMPTY;
        try {
            if (StringUtils.isNotBlank(serviceEndpoint.getIpAddressV4())) {
                ipAddress = ProtoUtil.toByteString(
                        InetAddress.getByName(serviceEndpoint.getIpAddressV4()).getAddress());
            }
        } catch (Exception e) {
            // Ignore
        }
        assertThat(nodeAddress.getServiceEndpointList())
                .hasSize(1)
                .first()
                .returns(ipAddress, ServiceEndpoint::getIpAddressV4)
                .returns(serviceEndpoint.getPort(), ServiceEndpoint::getPort)
                .returns(serviceEndpoint.getDomainName(), ServiceEndpoint::getDomainName)
                .extracting(ServiceEndpoint::getIpAddressV4)
                .isNotEqualTo(
                        ByteString.copyFrom(InetAddress.getLoopbackAddress().getAddress()));
    }

    private void assertException(Throwable t, Status.Code status, String message) {
        assertThat(t).isNotNull().isInstanceOf(StatusRuntimeException.class).hasMessageContaining(message);

        final var statusRuntimeException = (StatusRuntimeException) t;
        assertThat(statusRuntimeException.getStatus().getCode()).isEqualTo(status);
    }
}
