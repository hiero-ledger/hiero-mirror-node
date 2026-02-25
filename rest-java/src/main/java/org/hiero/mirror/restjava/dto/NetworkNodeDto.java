// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.jspecify.annotations.Nullable;

/**
 * Record-based projection representing a network node query result from the database. Spring Data JPA maps query column
 * aliases directly to record components, avoiding proxy overhead. JSON string fields are converted to objects by the
 * service layer using Spring-managed converters.
 *
 * <p>Parameter order matches the SQL SELECT clause order (alphabetically sorted by alias name).
 */
public record NetworkNodeDto(
        byte @Nullable [] adminKey,
        @Nullable Boolean declineReward,
        @Nullable String description,
        @Nullable Long endConsensusTimestamp,
        @Nullable Long fileId,
        @Nullable String grpcProxyEndpointJson,
        @Nullable Long maxStake,
        @Nullable String memo,
        @Nullable Long minStake,
        @Nullable Long nodeAccountId,
        byte @Nullable [] nodeCertHash,
        @Nullable Long nodeId,
        @Nullable String publicKey,
        @Nullable Long rewardRateStart,
        @Nullable String serviceEndpointsJson,
        @Nullable Long stake,
        @Nullable Long stakeNotRewarded,
        @Nullable Long stakeRewarded,
        @Nullable Long stakingPeriod,
        @Nullable Long startConsensusTimestamp) {}
