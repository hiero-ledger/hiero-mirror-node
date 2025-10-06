// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.controller;

import com.google.protobuf.ByteString;
import com.hedera.mirror.api.proto.AddressBookQuery;
import com.hedera.mirror.api.proto.Fee.EstimateMode;
import com.hedera.mirror.api.proto.Fee.FeeEstimate;
import com.hedera.mirror.api.proto.Fee.FeeEstimateQuery;
import com.hedera.mirror.api.proto.Fee.FeeEstimateResponse;
import com.hedera.mirror.api.proto.Fee.FeeExtra;
import com.hedera.mirror.api.proto.Fee.NetworkFee;
import com.hedera.mirror.api.proto.ReactorNetworkServiceGrpc;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.grpc.domain.AddressBookFilter;
import org.hiero.mirror.grpc.service.NetworkService;
import org.hiero.mirror.grpc.util.ProtoUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@GrpcService
@CustomLog
@RequiredArgsConstructor
public class NetworkController extends ReactorNetworkServiceGrpc.NetworkServiceImplBase {

    static final FeeEstimateResponse STUB_RESPONSE = stubResponse();

    private final NetworkService networkService;

    private static FeeEstimateResponse stubResponse() {
        final var feeExtra = FeeExtra.newBuilder()
                .setCharged(0)
                .setCount(1)
                .setFeePerUnit(0L)
                .setIncluded(1)
                .setName("Test data")
                .setSubtotal(0L);
        final var feeEstimate = FeeEstimate.newBuilder().setBase(0L).addExtras(feeExtra);
        return FeeEstimateResponse.newBuilder()
                .addNotes("This API is not yet implemented and only returns stubbed test data")
                .setMode(EstimateMode.STATE)
                .setNetwork(NetworkFee.newBuilder().setMultiplier(1).setSubtotal(0L))
                .setNode(feeEstimate)
                .setService(feeEstimate)
                .setTotal(0)
                .build();
    }

    @Override
    public Mono<FeeEstimateResponse> getFeeEstimate(FeeEstimateQuery request) {
        return Mono.just(STUB_RESPONSE);
    }

    @Override
    public Flux<NodeAddress> getNodes(Mono<AddressBookQuery> request) {
        return request.map(this::toFilter)
                .flatMapMany(networkService::getNodes)
                .map(this::toNodeAddress)
                .onErrorMap(ProtoUtil::toStatusRuntimeException);
    }

    private AddressBookFilter toFilter(AddressBookQuery query) {
        var filter = AddressBookFilter.builder().limit(query.getLimit());

        if (query.hasFileId()) {
            filter.fileId(EntityId.of(query.getFileId()));
        }

        return filter.build();
    }

    @SuppressWarnings("deprecation")
    private NodeAddress toNodeAddress(AddressBookEntry addressBookEntry) {
        var nodeAddress = NodeAddress.newBuilder()
                .setNodeAccountId(addressBookEntry.getNodeAccountId().toAccountID())
                .setNodeId(addressBookEntry.getNodeId());

        if (addressBookEntry.getDescription() != null) {
            nodeAddress.setDescription(addressBookEntry.getDescription());
        }

        if (addressBookEntry.getMemo() != null) {
            nodeAddress.setMemo(ByteString.copyFromUtf8(addressBookEntry.getMemo()));
        }

        if (addressBookEntry.getNodeCertHash() != null) {
            nodeAddress.setNodeCertHash(ProtoUtil.toByteString(addressBookEntry.getNodeCertHash()));
        }

        if (addressBookEntry.getPublicKey() != null) {
            nodeAddress.setRSAPubKey(addressBookEntry.getPublicKey());
        }

        if (addressBookEntry.getStake() != null) {
            nodeAddress.setStake(addressBookEntry.getStake());
        }

        for (var s : addressBookEntry.getServiceEndpoints()) {
            var serviceEndpoint = ServiceEndpoint.newBuilder()
                    .setDomainName(s.getDomainName())
                    .setIpAddressV4(toIpAddressV4(s.getIpAddressV4()))
                    .setPort(s.getPort())
                    .build();
            nodeAddress.addServiceEndpoint(serviceEndpoint);
        }

        return nodeAddress.build();
    }

    private ByteString toIpAddressV4(String ipAddress) {
        try {
            if (StringUtils.isBlank(ipAddress)) {
                return ByteString.EMPTY;
            }

            return ProtoUtil.toByteString(InetAddress.getByName(ipAddress).getAddress());
        } catch (UnknownHostException e) {
            // Shouldn't occur since we never pass hostnames to InetAddress.getByName()
            log.warn("Unable to convert IP address to byte array", e.getMessage());
        }

        return ByteString.EMPTY;
    }
}
