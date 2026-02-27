// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.jspecify.annotations.NullUnmarked;

/**
 * Record-based projection representing a network node query result from the database. Spring Data JPA maps query column
 * aliases directly to record components, avoiding proxy overhead. The SQL query handles all formatting (hex encoding,
 * "0x" prefixes, JSON conversion) to produce ready-to-use values.
 *
 * <p>Parameter order matches the SQL SELECT clause order (alphabetically sorted by alias name).
 */
@NullUnmarked
public record NetworkNodeDto(
        byte[] adminKey,
        Boolean declineReward,
        String description,
        Long endConsensusTimestamp,
        Long fileId,
        String grpcProxyEndpointJson,
        Long maxStake,
        String memo,
        Long minStake,
        Long nodeAccountId,
        byte[] nodeCertHash,
        Long nodeId,
        String publicKey,
        Long rewardRateStart,
        String serviceEndpointsJson,
        Long stake,
        Long stakeNotRewarded,
        Long stakeRewarded,
        Long stakingPeriod,
        Long startConsensusTimestamp) {}
