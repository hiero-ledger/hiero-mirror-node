// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.jspecify.annotations.NullUnmarked;

/**
 * Record-based projection representing a network node query result from the database. Spring Data JPA maps query column
 * aliases directly to record components, avoiding proxy overhead. The SQL query handles all formatting (hex encoding,
 * "0x" prefixes, JSON conversion) to produce ready-to-use values.
 *
 * <p>Parameter order matches the SQL SELECT clause order (alphabetically sorted by alias name).
 *
 * <p>{@code nodeCertHash} is the raw {@code bytea} from the address book; it is decoded tolerantly and "0x"-prefixed in
 * the mapper. Decoding is intentionally kept out of SQL because the bytes are chain-controlled and not guaranteed to be
 * valid UTF-8.
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
        byte[] nodeCertHash,
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
