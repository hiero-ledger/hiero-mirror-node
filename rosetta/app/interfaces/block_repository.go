// SPDX-License-Identifier: Apache-2.0

package interfaces

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
)

// BlockRepository Interface that all BlockRepository structs must implement
type BlockRepository interface {

	// FindByHash retrieves a block by a given Hash
	FindByHash(ctx context.Context, hash string) (*types.Block, *rTypes.Error)

	// FindByIdentifier retrieves a block by index and hash
	FindByIdentifier(ctx context.Context, index int64, hash string) (*types.Block, *rTypes.Error)

	// FindByIndex retrieves a block by given index
	FindByIndex(ctx context.Context, index int64) (*types.Block, *rTypes.Error)

	// RetrieveGenesis retrieves the genesis block
	RetrieveGenesis(ctx context.Context) (*types.Block, *rTypes.Error)

	// RetrieveLatest retrieves the second-latest block. It's required to hide the latest block so account service can
	// add 0-amount genesis token balance to a block for tokens with first transfer to the account in the next block
	RetrieveLatest(ctx context.Context) (*types.Block, *rTypes.Error)
}
