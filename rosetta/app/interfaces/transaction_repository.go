// SPDX-License-Identifier: Apache-2.0

package interfaces

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
)

// TransactionRepository Interface that all TransactionRepository structs must implement
type TransactionRepository interface {

	// FindBetween retrieves all Transaction between the provided start and end timestamp inclusively
	FindBetween(ctx context.Context, start, end int64) ([]*types.Transaction, *rTypes.Error)

	// FindByHashInBlock retrieves a transaction by its hash in the block identified by [consensusStart, consensusEnd]
	FindByHashInBlock(ctx context.Context, hash string, consensusStart, consensusEnd int64) (
		*types.Transaction,
		*rTypes.Error,
	)
}
