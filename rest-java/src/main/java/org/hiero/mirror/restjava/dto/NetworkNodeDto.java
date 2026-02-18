// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.jspecify.annotations.Nullable;

/**
 * Interface-based projection representing a network node query result from the database. Spring Data JPA creates a
 * proxy implementation that maps query column aliases to these getter methods.
 */
public interface NetworkNodeDto {
    @Nullable
    byte[] getAdminKey();

    @Nullable
    Boolean getDeclineReward();

    @Nullable
    String getDescription();

    @Nullable
    Long getEndConsensusTimestamp();

    Long getFileId();

    @Nullable
    default ServiceEndpoint getGrpcProxyEndpoint() {
        var json = getGrpcProxyEndpointJson();
        if (json == null || json.isEmpty()) return null;
        try {
            return Parser.MAPPER.readValue(json, ServiceEndpoint.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse grpc proxy endpoint", e);
        }
    }

    @Nullable
    String getGrpcProxyEndpointJson();

    @Nullable
    Long getMaxStake();

    @Nullable
    String getMemo();

    @Nullable
    Long getMinStake();

    @Nullable
    String getNodeAccountId();

    @Nullable
    byte[] getNodeCertHash();

    Long getNodeId();

    @Nullable
    String getPublicKey();

    @Nullable
    Long getRewardRateStart();

    default List<ServiceEndpoint> getServiceEndpoints() {
        try {
            return Parser.MAPPER.readValue(getServiceEndpointsJson(), Parser.SERVICE_ENDPOINT_LIST_TYPE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse service endpoints", e);
        }
    }

    String getServiceEndpointsJson();

    @Nullable
    Long getStake();

    @Nullable
    Long getStakeNotRewarded();

    @Nullable
    Long getStakeRewarded();

    @Nullable
    Long getStakingPeriod();

    Long getStartConsensusTimestamp();

    class Parser {
        static final ObjectMapper MAPPER = new ObjectMapper();
        static final TypeReference<List<ServiceEndpoint>> SERVICE_ENDPOINT_LIST_TYPE = new TypeReference<>() {};

        private Parser() {}
    }
}
