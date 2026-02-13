// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.mirror.api.proto.AddressBookQuery;
import com.hedera.mirror.api.proto.NetworkServiceGrpc;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.util.ProtoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.client.ImportGrpcClients;

@ImportGrpcClients(types = NetworkServiceGrpc.NetworkServiceBlockingStub.class)
@RequiredArgsConstructor
final class NetworkControllerTest extends GrpcIntegrationTest {

    private static final long CONSENSUS_TIMESTAMP = 1L;

    private final NetworkServiceGrpc.NetworkServiceBlockingStub blockingService;

    @Test
    void getNodesMissingFileId() {
        final var query = AddressBookQuery.newBuilder().build();

        assertThatThrownBy(() -> {
                    final var iterator = blockingService.getNodes(query);
                    iterator.hasNext();
                })
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getNodesInvalidFileId() {
        final var query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder().setFileNum(-1).build())
                .build();

        assertThatThrownBy(() -> {
                    final var iterator = blockingService.getNodes(query);
                    iterator.hasNext();
                })
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Invalid entity ID")
                .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getNodesInvalidLimit() {
        final var query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder().build())
                .setLimit(-1)
                .build();

        assertThatThrownBy(() -> {
                    final var iterator = blockingService.getNodes(query);
                    iterator.hasNext();
                })
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("limit: must be greater than or equal to 0")
                .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getNodesNotFound() {
        final var query = AddressBookQuery.newBuilder()
                .setFileId(systemEntity.addressBookFile102().toFileID())
                .build();

        assertThatThrownBy(() -> {
                    final var iterator = blockingService.getNodes(query);
                    iterator.hasNext();
                })
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("does not exist")
                .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void getNodesNoLimit() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntry();
        final var addressBookEntry2 = addressBookEntry();
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .build();

        final var responseIterator = blockingService.getNodes(query);
        List<NodeAddress> nodes = new ArrayList<>();
        responseIterator.forEachRemaining(nodes::add);

        assertThat(nodes)
                .hasSize(2)
                .satisfies(n -> assertEntry(addressBookEntry1, n.get(0)))
                .satisfies(n -> assertEntry(addressBookEntry2, n.get(1)));
    }

    @Test
    void getNodesNoLimitServiceEndpointWithDomainName() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntryCustomized("www.example-node.com", "", 5000);
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .build();

        final var responseIterator = blockingService.getNodes(query);
        List<NodeAddress> nodes = new ArrayList<>();
        responseIterator.forEachRemaining(nodes::add);

        assertThat(nodes).hasSize(1).satisfies(n -> assertEntry(addressBookEntry1, n.get(0)));
    }

    @Test
    void getNodesTestWithEmptyDomainNameAndIpAddress() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntryCustomized("", "", 0);
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .build();

        final var responseIterator = blockingService.getNodes(query);
        List<NodeAddress> nodes = new ArrayList<>();
        responseIterator.forEachRemaining(nodes::add);

        assertThat(nodes).hasSize(1).satisfies(n -> assertEntry(addressBookEntry1, n.get(0)));
    }

    @Test
    void getNodesLimitReached() {
        final var addressBook = addressBook();
        final var addressBookEntry1 = addressBookEntry();
        addressBookEntry();
        final var query = AddressBookQuery.newBuilder()
                .setFileId(addressBook.getFileId().toFileID())
                .setLimit(1)
                .build();

        final var responseIterator = blockingService.getNodes(query);
        List<NodeAddress> nodes = new ArrayList<>();
        responseIterator.forEachRemaining(nodes::add);

        assertThat(nodes).hasSize(1).satisfies(n -> assertEntry(addressBookEntry1, n.get(0)));
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

        final var responseIterator = blockingService.getNodes(query);
        List<NodeAddress> nodes = new ArrayList<>();
        responseIterator.forEachRemaining(nodes::add);

        assertThat(nodes).hasSize(1).first().satisfies(n -> assertThat(n)
                .isNotNull()
                .returns("", NodeAddress::getDescription)
                .returns(ByteString.EMPTY, NodeAddress::getMemo)
                .returns(addressBookEntry.getNodeAccountId(), t -> EntityId.of(n.getNodeAccountId()))
                .returns(ByteString.EMPTY, NodeAddress::getNodeCertHash)
                .returns(addressBookEntry.getNodeId(), NodeAddress::getNodeId)
                .returns("", NodeAddress::getRSAPubKey)
                .returns(0L, NodeAddress::getStake));
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
}
