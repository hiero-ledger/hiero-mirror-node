// SPDX-License-Identifier: Apache-2.0

package domain

const tableNameStakingRewardTransfer = "staking_reward_transfer"

type StakingRewardTransfer struct {
	AccountId          EntityId `json:"account_id"`
	Amount             int64
	ConsensusTimestamp int64    `json:"consensus_timestamp"`
	PayerAccountId     EntityId `json:"payer_account_id"`
}

func (StakingRewardTransfer) TableName() string {
	return tableNameStakingRewardTransfer
}
