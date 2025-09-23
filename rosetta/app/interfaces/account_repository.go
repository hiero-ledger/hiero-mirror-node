// SPDX-License-Identifier: Apache-2.0

package interfaces

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
)

// AccountRepository Interface that all AccountRepository structs must implement
type AccountRepository interface {

	// GetAccountAlias returns the alias info of the account if exists. The same accountId is returned if the account
	// doesn't have an alias
	GetAccountAlias(ctx context.Context, accountId types.AccountId) (types.AccountId, *rTypes.Error)

	// GetAccountId returns the `shard.realm.num` format of the account from its alias if exists
	GetAccountId(ctx context.Context, accountId types.AccountId) (types.AccountId, *rTypes.Error)

	// RetrieveBalanceAtBlock returns the hbar balance and token balances of the account at a given block (provided by
	// consensusEnd timestamp).
	// balance = balanceAtLatestBalanceSnapshot + balanceChangeBetweenSnapshotAndBlock
	// if the account is deleted at T1 and T1 <= consensusEnd, the balance is calculated as
	// balance = balanceAtLatestBalanceSnapshotBeforeT1 + balanceChangeBetweenSnapshotAndT1
	RetrieveBalanceAtBlock(ctx context.Context, accountId types.AccountId, consensusEnd int64) (
		types.AmountSlice,
		string,
		[]byte,
		*rTypes.Error,
	)
}
