// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
)

type AccountBalanceSnapshotBuilder struct {
	accountBalances    []domain.AccountBalance
	consensusTimestamp int64
	dbClient           interfaces.DbClient
}

func (b *AccountBalanceSnapshotBuilder) AddAccountBalance(accountId, balance int64) *AccountBalanceSnapshotBuilder {
	b.accountBalances = append(b.accountBalances, domain.AccountBalance{
		AccountId:          domain.MustDecodeEntityId(accountId),
		Balance:            balance,
		ConsensusTimestamp: b.consensusTimestamp,
	})
	return b
}

func (b *AccountBalanceSnapshotBuilder) Persist() {
	db := b.dbClient.GetDb()
	if len(b.accountBalances) != 0 {
		db.Create(b.accountBalances)
	}
}

func NewAccountBalanceSnapshotBuilder(dbClient interfaces.DbClient, consensusTimestamp int64) *AccountBalanceSnapshotBuilder {
	return &AccountBalanceSnapshotBuilder{
		accountBalances:    make([]domain.AccountBalance, 0),
		consensusTimestamp: consensusTimestamp,
		dbClient:           dbClient,
	}
}
