// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
)

type StakingRewardTransferBuilder struct {
	dbClient              interfaces.DbClient
	stakingRewardTransfer domain.StakingRewardTransfer
}

func (b *StakingRewardTransferBuilder) AccountId(accountId int64) *StakingRewardTransferBuilder {
	b.stakingRewardTransfer.AccountId = domain.MustDecodeEntityId(accountId)
	return b
}

func (b *StakingRewardTransferBuilder) Amount(amount int64) *StakingRewardTransferBuilder {
	b.stakingRewardTransfer.Amount = amount
	return b
}

func (b *StakingRewardTransferBuilder) ConsensusTimestamp(timestamp int64) *StakingRewardTransferBuilder {
	b.stakingRewardTransfer.ConsensusTimestamp = timestamp
	return b
}

func (b *StakingRewardTransferBuilder) PayerAccountId(payerAccountId int64) *StakingRewardTransferBuilder {
	b.stakingRewardTransfer.PayerAccountId = domain.MustDecodeEntityId(payerAccountId)
	return b
}

func (b *StakingRewardTransferBuilder) Persist() {
	b.dbClient.GetDb().Create(&b.stakingRewardTransfer)
}

func NewStakingRewardTransferBuilder(dbClient interfaces.DbClient) *StakingRewardTransferBuilder {
	return &StakingRewardTransferBuilder{
		dbClient:              dbClient,
		stakingRewardTransfer: domain.StakingRewardTransfer{PayerAccountId: defaultPayer},
	}
}
