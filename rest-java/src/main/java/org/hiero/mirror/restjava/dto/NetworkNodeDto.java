// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.jspecify.annotations.Nullable;

/**
 * Interface-based projection representing a network node query result from the database. Spring Data JPA creates a
 * proxy implementation that maps query column aliases to these getter methods. JSON string fields are converted to
 * objects by the service layer using Spring-managed converters.
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
    String getGrpcProxyEndpointJson();

    @Nullable
    Long getMaxStake();

    @Nullable
    String getMemo();

    @Nullable
    Long getMinStake();

    @Nullable
    Long getNodeAccountId();

    @Nullable
    byte[] getNodeCertHash();

    Long getNodeId();

    @Nullable
    String getPublicKey();

    @Nullable
    Long getRewardRateStart();

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
}
