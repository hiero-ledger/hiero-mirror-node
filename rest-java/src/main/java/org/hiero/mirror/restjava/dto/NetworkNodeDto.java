// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.jspecify.annotations.NullUnmarked;

/**
 * Record-based projection for {@link org.springframework.data.jdbc.repository.query.Query} results. PostgreSQL folds
 * unquoted identifiers to lowercase, so SQL aliases must be quoted (e.g. {@code as "nodeId"}) to match record accessor
 * names. The query formats hex encoding, {@code 0x} prefixes, and JSON in SQL.
 */
@NullUnmarked
public record NetworkNodeDto(
        byte[] adminKey,
        Long[] associatedRegisteredNodes,
        Boolean declineReward,
        String description,
        Long endConsensusTimestamp,
        Long fileId,
        String grpcProxyEndpointJson,
        Long maxStake,
        String memo,
        Long minStake,
        Long nodeAccountId,
        String nodeCertHash,
        Long nodeId,
        String publicKey,
        Long rewardRateStart,
        String serviceEndpointsJson,
        Long stake,
        Long stakeNotRewarded,
        Long stakeRewarded,
        Long stakingPeriod,
        Long startConsensusTimestamp) {

    private static final Long[] EMPTY = {};

    @Override
    public Long[] associatedRegisteredNodes() {
        return associatedRegisteredNodes != null ? associatedRegisteredNodes : EMPTY;
    }
}
