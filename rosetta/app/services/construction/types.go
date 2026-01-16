// SPDX-License-Identifier: Apache-2.0

package construction

import (
	"context"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
)

// BaseTransactionConstructor defines the methods to construct a transaction
type BaseTransactionConstructor interface {
	// Construct constructs a transaction from its operations
	Construct(
		ctx context.Context,
		operations types.OperationSlice,
	) (hiero.TransactionInterface, []types.AccountId, *rTypes.Error)

	// Parse parses a signed or unsigned transaction to get its operations and required signers
	Parse(ctx context.Context, transaction hiero.TransactionInterface) (
		types.OperationSlice,
		[]types.AccountId,
		*rTypes.Error,
	)

	// Preprocess preprocesses the operations to get required signers
	Preprocess(ctx context.Context, operations types.OperationSlice) ([]types.AccountId, *rTypes.Error)
}

type TransactionConstructor interface {
	BaseTransactionConstructor

	// GetDefaultMaxTransactionFee gets the default max transaction fee in hbar
	GetDefaultMaxTransactionFee(operationType string) (types.HbarAmount, *rTypes.Error)
}
