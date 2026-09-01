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
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
)

// blockAPIService implements the server.BlockAPIServicer interface.
type blockAPIService struct {
	accountRepo interfaces.AccountRepository
	BaseService
	entityCache                   *cache.Cache[int64, types.AccountId]
	maxTransactions               int
	maxTransactionsInBlock        int
	transactionIdentifierPageSize int
}

// NewBlockAPIService creates a new instance of a blockAPIService.
func NewBlockAPIService(
	accountRepo interfaces.AccountRepository,
	baseService BaseService,
	entityCacheConfig config.Cache,
	responseConfig config.Response,
	serverContext context.Context,
) server.BlockAPIServicer {
	entityCache := cache.NewContext(
		serverContext,
		cache.AsLRU[int64, types.AccountId](lru.WithCapacity(entityCacheConfig.MaxSize)),
	)
	return &blockAPIService{
		accountRepo:                   accountRepo,
		BaseService:                   baseService,
		entityCache:                   entityCache,
		maxTransactions:               responseConfig.MaxTransactions,
		maxTransactionsInBlock:        responseConfig.MaxTransactionsInBlock,
		transactionIdentifierPageSize: responseConfig.TransactionIdentifierPageSize,
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

	includedHashes, otherTransactions, err := s.findTransactionIdentifiers(
		ctx,
		block.ConsensusStartNanos,
		block.ConsensusEndNanos,
	)
	if err != nil {
		return nil, err
	}

	if block.Transactions, err = s.FindBetween(
		ctx,
		block.ConsensusStartNanos,
		block.ConsensusEndNanos,
		includedHashes,
	); err != nil {
		return nil, err
	}

	if err = s.updateOperationAccountAlias(ctx, block.Transactions...); err != nil {
		return nil, err
	}

	return &rTypes.BlockResponse{Block: block.ToRosetta(), OtherTransactions: otherTransactions}, nil
}

func (s *blockAPIService) findTransactionIdentifiers(
	ctx context.Context,
	consensusStart int64,
	consensusEnd int64,
) ([]string, []*rTypes.TransactionIdentifier, *rTypes.Error) {
	includedHashes := make([]string, 0, s.maxTransactionsInBlock)
	var otherTransactions []*rTypes.TransactionIdentifier
	seen := make(map[string]struct{})
	cursor := consensusStart - 1

	for {
		identifiers, err := s.FindBetweenTransactionIdentifiers(
			ctx,
			consensusStart,
			consensusEnd,
			cursor,
			s.transactionIdentifierPageSize,
		)
		if err != nil {
			return nil, nil, err
		}

		for _, identifier := range identifiers {
			if _, ok := seen[identifier.Hash]; ok {
				continue
			}

			seen[identifier.Hash] = struct{}{}
			if len(seen) > s.maxTransactions {
				return nil, nil, errors.ErrTransactionLimitExceeded
			}

			if len(includedHashes) < s.maxTransactionsInBlock {
				includedHashes = append(includedHashes, identifier.Hash)
			} else {
				otherTransactions = append(
					otherTransactions,
					&rTypes.TransactionIdentifier{Hash: identifier.Hash},
				)
			}
		}

		// an empty page also ends the scan, so a non-positive configured page size cannot spin forever
		if len(identifiers) == 0 || len(identifiers) < s.transactionIdentifierPageSize {
			return includedHashes, otherTransactions, nil
		}

		cursor = identifiers[len(identifiers)-1].ConsensusTimestamp
	}
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
