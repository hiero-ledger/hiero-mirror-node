/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
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
