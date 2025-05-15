// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"

	cache "github.com/Code-Hex/go-generics-cache"
	"github.com/Code-Hex/go-generics-cache/policy/lru"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
)

// blockAPIService implements the server.BlockAPIServicer interface.
type blockAPIService struct {
	accountRepo interfaces.AccountRepository
	BaseService
	entityCache            *cache.Cache[int64, types.AccountId]
	maxTransactionsInBlock int
}

// NewBlockAPIService creates a new instance of a blockAPIService.
func NewBlockAPIService(
	accountRepo interfaces.AccountRepository,
	baseService BaseService,
	entityCacheConfig config.Cache,
	maxTransactionsInBlock int,
	serverContext context.Context,
) server.BlockAPIServicer {
	entityCache := cache.NewContext(
		serverContext,
		cache.AsLRU[int64, types.AccountId](lru.WithCapacity(entityCacheConfig.MaxSize)),
	)
	return &blockAPIService{
		accountRepo:            accountRepo,
		BaseService:            baseService,
		entityCache:            entityCache,
		maxTransactionsInBlock: maxTransactionsInBlock,
	}
}

// Block implements the /block endpoint.
func (s *blockAPIService) Block(
	ctx context.Context,
	request *rTypes.BlockRequest,
) (*rTypes.BlockResponse, *rTypes.Error) {
	block, err := s.RetrieveBlock(ctx, request.BlockIdentifier)
	if err != nil {
		return nil, err
	}

	if block.Transactions, err = s.FindBetween(ctx, block.ConsensusStartNanos, block.ConsensusEndNanos); err != nil {
		return nil, err
	}

	var otherTransactions []*rTypes.TransactionIdentifier
	if len(block.Transactions) > s.maxTransactionsInBlock {
		otherTransactions = make([]*rTypes.TransactionIdentifier, 0, len(block.Transactions)-s.maxTransactionsInBlock)
		for _, transaction := range block.Transactions[s.maxTransactionsInBlock:] {
			otherTransactions = append(otherTransactions, &rTypes.TransactionIdentifier{Hash: transaction.Hash})
		}
		block.Transactions = block.Transactions[0:s.maxTransactionsInBlock]
	}

	if err = s.updateOperationAccountAlias(ctx, block.Transactions...); err != nil {
		return nil, err
	}

	return &rTypes.BlockResponse{Block: block.ToRosetta(), OtherTransactions: otherTransactions}, nil
}

// BlockTransaction implements the /block/transaction endpoint.
func (s *blockAPIService) BlockTransaction(
	ctx context.Context,
	request *rTypes.BlockTransactionRequest,
) (*rTypes.BlockTransactionResponse, *rTypes.Error) {
	h := tools.SafeRemoveHexPrefix(request.BlockIdentifier.Hash)
	block, err := s.FindByIdentifier(ctx, request.BlockIdentifier.Index, h)
	if err != nil {
		return nil, err
	}

	transaction, err := s.FindByHashInBlock(
		ctx,
		request.TransactionIdentifier.Hash,
		block.ConsensusStartNanos,
		block.ConsensusEndNanos,
	)
	if err != nil {
		return nil, err
	}

	if err = s.updateOperationAccountAlias(ctx, transaction); err != nil {
		return nil, err
	}

	return &rTypes.BlockTransactionResponse{Transaction: transaction.ToRosetta()}, nil
}

func (s *blockAPIService) updateOperationAccountAlias(
	ctx context.Context,
	transactions ...*types.Transaction,
) *rTypes.Error {
	for _, transaction := range transactions {
		operations := transaction.Operations
		for index := range operations {
			var cached types.AccountId
			var found bool

			accountId := operations[index].AccountId
			if cached, found = s.entityCache.Get(accountId.GetId()); !found {
				result, err := s.accountRepo.GetAccountAlias(ctx, accountId)
				if err != nil {
					return err
				}

				s.entityCache.Set(result.GetId(), result)
				cached = result
			}

			operations[index].AccountId = cached
		}
	}

	return nil
}
