// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.springframework.lang.Nullable;

/**
 * Interface-based projection representing a network node query result row from the database. Spring Data JPA creates a
 * proxy implementation that maps query column aliases to these getter methods.
 */
public interface NetworkNodeRow {
    @Nullable
    String getDescription();

    @Nullable
    String getMemo();

    Long getNodeId();

    @Nullable
    String getNodeAccountId();

    @Nullable
    byte[] getNodeCertHash();

    @Nullable
    String getPublicKey();

    Long getFileId();

    Long getStartConsensusTimestamp();

    @Nullable
    Long getEndConsensusTimestamp();

    @Nullable
    byte[] getAdminKey();

    @Nullable
    Boolean getDeclineReward();

    @Nullable
    String getGrpcProxyEndpoint();

    @Nullable
    Long getMaxStake();

    @Nullable
    Long getMinStake();

    @Nullable
    Long getRewardRateStart();

    @Nullable
    Long getStake();

    @Nullable
    Long getStakeNotRewarded();

    @Nullable
    Long getStakeRewarded();

    @Nullable
    Long getStakingPeriod();

    String getServiceEndpoints();
}
