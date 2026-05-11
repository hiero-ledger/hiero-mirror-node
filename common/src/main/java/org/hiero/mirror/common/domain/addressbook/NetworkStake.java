// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Table
@NoArgsConstructor
public class NetworkStake implements Persistable<Long> {

    @Id
    private long consensusTimestamp;

    private long epochDay;
    private long maxStakeRewarded;
    private long maxStakingRewardRatePerHbar;
    private long maxTotalReward;
    private long nodeRewardFeeDenominator;
    private long nodeRewardFeeNumerator;
    private long reservedStakingRewards;
    private long rewardBalanceThreshold;
    private long stakeTotal;
    private long stakingPeriod;
    private long stakingPeriodDuration;
    private long stakingPeriodsStored;
    private long stakingRewardFeeDenominator;
    private long stakingRewardFeeNumerator;
    private long stakingRewardRate;
    private long stakingStartThreshold;
    private long unreservedStakingRewardBalance;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }
}
