// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ReactorNetworkServiceGrpc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;

@TestConfiguration
public class GrpcTestConfiguration {

    private static final String IN_PROCESS_SERVER_NAME = "test-server";

    @Bean
    ReactorNetworkServiceGrpc.ReactorNetworkServiceStub networkReactiveStub(GrpcChannelFactory channels) {
        return ReactorNetworkServiceGrpc.newReactorStub(channels.createChannel("in-process:" + IN_PROCESS_SERVER_NAME));
    }

    @Bean
    ConsensusServiceGrpc.ConsensusServiceBlockingStub consensusBlockingStub(GrpcChannelFactory channels) {
        return ConsensusServiceGrpc.newBlockingStub(channels.createChannel("in-process:" + IN_PROCESS_SERVER_NAME));
    }

    @Bean
    ReactorConsensusServiceGrpc.ReactorConsensusServiceStub consensusReactiveStub(GrpcChannelFactory channels) {
        return ReactorConsensusServiceGrpc.newReactorStub(
                channels.createChannel("in-process:" + IN_PROCESS_SERVER_NAME));
    }
}
