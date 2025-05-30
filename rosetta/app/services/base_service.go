// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
)

// BaseService - Struct implementing common functionalities used by more than 1 service
type BaseService struct {
	blockRepo       interfaces.BlockRepository
	transactionRepo interfaces.TransactionRepository
}

// NewOfflineBaseService - Service containing common functions that are shared between other services, for offline mode
func NewOfflineBaseService() BaseService {
	return BaseService{}
}

// NewOnlineBaseService - Service containing common functions that are shared between other services, for online mode
func NewOnlineBaseService(
	blockRepo interfaces.BlockRepository,
	transactionRepo interfaces.TransactionRepository,
) BaseService {
	return BaseService{
		blockRepo:       blockRepo,
		transactionRepo: transactionRepo,
	}
}

func (b *BaseService) IsOnline() bool {
	return b.blockRepo != nil
}

func (b *BaseService) FindByHashInBlock(
	ctx context.Context,
	identifier string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	if !b.IsOnline() {
		return nil, errors.ErrInternalServerError
	}

	return b.transactionRepo.FindByHashInBlock(ctx, identifier, consensusStart, consensusEnd)
}

func (b *BaseService) FindBetween(ctx context.Context, start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	if !b.IsOnline() {
		return nil, errors.ErrInternalServerError
	}

	return b.transactionRepo.FindBetween(ctx, start, end)
}

func (b *BaseService) FindByIdentifier(ctx context.Context, index int64, hash string) (*types.Block, *rTypes.Error) {
	if !b.IsOnline() {
		return nil, errors.ErrInternalServerError
	}

	return b.blockRepo.FindByIdentifier(ctx, index, hash)
}

// RetrieveBlock - Retrieves Block by a given PartialBlockIdentifier
func (b *BaseService) RetrieveBlock(ctx context.Context, bIdentifier *rTypes.PartialBlockIdentifier) (
	*types.Block,
	*rTypes.Error,
) {
	if !b.IsOnline() {
		return nil, errors.ErrInternalServerError
	}

	if bIdentifier.Hash != nil && bIdentifier.Index != nil {
		h := tools.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return b.blockRepo.FindByIdentifier(ctx, *bIdentifier.Index, h)
	} else if bIdentifier.Hash == nil && bIdentifier.Index != nil {
		return b.blockRepo.FindByIndex(ctx, *bIdentifier.Index)
	} else if bIdentifier.Index == nil && bIdentifier.Hash != nil {
		h := tools.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return b.blockRepo.FindByHash(ctx, h)
	} else {
		return b.blockRepo.RetrieveLatest(ctx)
	}
}

func (b *BaseService) RetrieveGenesis(ctx context.Context) (*types.Block, *rTypes.Error) {
	if !b.IsOnline() {
		return nil, errors.ErrInternalServerError
	}

	return b.blockRepo.RetrieveGenesis(ctx)
}

func (b *BaseService) RetrieveLatest(ctx context.Context) (*types.Block, *rTypes.Error) {
	if !b.IsOnline() {
		return nil, errors.ErrInternalServerError
	}

	return b.blockRepo.RetrieveLatest(ctx)
}
