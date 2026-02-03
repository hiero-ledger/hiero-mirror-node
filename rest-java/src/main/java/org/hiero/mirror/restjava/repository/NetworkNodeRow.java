// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

/**
 * Interface-based projection representing a network node query result row from the database.
 * Spring Data JPA creates a proxy implementation that maps query column aliases to these getter methods.
 */
public interface NetworkNodeRow {
    String getDescription();

    String getMemo();

    Long getNodeId();

    String getNodeAccountId();

    byte[] getNodeCertHash();

    String getPublicKey();

    String getFileId();

    Long getStartConsensusTimestamp();

    Long getEndConsensusTimestamp();

    byte[] getAdminKey();

    Boolean getDeclineReward();

    String getGrpcProxyEndpoint();

    Long getMaxStake();

    Long getMinStake();

    Long getRewardRateStart();

    Long getStake();

    Long getStakeNotRewarded();

    Long getStakeRewarded();

    Long getStakingPeriod();

    String getServiceEndpoints();
}
