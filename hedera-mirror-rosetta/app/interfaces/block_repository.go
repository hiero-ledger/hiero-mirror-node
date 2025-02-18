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

package interfaces

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
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
