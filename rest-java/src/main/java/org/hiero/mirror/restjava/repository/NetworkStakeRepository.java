// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.jooq.domain.tables.NetworkStake.NETWORK_STAKE;

import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

@Named
@RequiredArgsConstructor
public class NetworkStakeRepository {

    private final DSLContext dslContext;

    public Optional<NetworkStake> findLatest() {
        return dslContext
                .select(
                        NETWORK_STAKE.MAX_STAKING_REWARD_RATE_PER_HBAR,
                        NETWORK_STAKE.MAX_STAKE_REWARDED,
                        NETWORK_STAKE.MAX_TOTAL_REWARD,
                        NETWORK_STAKE.NODE_REWARD_FEE_DENOMINATOR,
                        NETWORK_STAKE.NODE_REWARD_FEE_NUMERATOR,
                        NETWORK_STAKE.RESERVED_STAKING_REWARDS,
                        NETWORK_STAKE.REWARD_BALANCE_THRESHOLD,
                        NETWORK_STAKE.STAKE_TOTAL,
                        NETWORK_STAKE.STAKING_PERIOD,
                        NETWORK_STAKE.STAKING_PERIOD_DURATION,
                        NETWORK_STAKE.STAKING_PERIODS_STORED,
                        NETWORK_STAKE.STAKING_REWARD_FEE_DENOMINATOR,
                        NETWORK_STAKE.STAKING_REWARD_FEE_NUMERATOR,
                        NETWORK_STAKE.STAKING_REWARD_RATE,
                        NETWORK_STAKE.STAKING_START_THRESHOLD,
                        NETWORK_STAKE.UNRESERVED_STAKING_REWARD_BALANCE)
                .from(NETWORK_STAKE)
                .where(NETWORK_STAKE.CONSENSUS_TIMESTAMP.eq(
                        DSL.select(DSL.max(NETWORK_STAKE.CONSENSUS_TIMESTAMP)).from(NETWORK_STAKE)))
                .fetchOptionalInto(NetworkStake.class);
    }
}
