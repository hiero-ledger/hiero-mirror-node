// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	"encoding/hex"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
)

// AccountAPIService implements the server.AccountAPIServicer interface.
type AccountAPIService struct {
	BaseService
	accountRepo interfaces.AccountRepository
	systemShard int64
	systemRealm int64
}

// NewAccountAPIService creates a new instance of a AccountAPIService.
func NewAccountAPIService(
	baseService BaseService,
	accountRepo interfaces.AccountRepository,
	systemShard int64,
	systemRealm int64,
) server.AccountAPIServicer {
	return &AccountAPIService{
		BaseService: baseService,
		accountRepo: accountRepo,
		systemShard: systemShard,
		systemRealm: systemRealm,
	}
}

// AccountBalance implements the /account/balance endpoint.
func (a *AccountAPIService) AccountBalance(
	ctx context.Context,
	request *rTypes.AccountBalanceRequest,
) (*rTypes.AccountBalanceResponse, *rTypes.Error) {
	accountId, err := types.NewAccountIdFromString(request.AccountIdentifier.Address, a.systemShard, a.systemRealm)
	if err != nil {
		return nil, errors.ErrInvalidAccount
	}

	var block *types.Block
	var rErr *rTypes.Error
	if request.BlockIdentifier != nil {
		block, rErr = a.RetrieveBlock(ctx, request.BlockIdentifier)
	} else {
		block, rErr = a.RetrieveLatest(ctx)
	}
	if rErr != nil {
		return nil, rErr
	}

	balances, accountIdString, publicKey, rErr := a.accountRepo.RetrieveBalanceAtBlock(ctx, accountId, block.ConsensusEndNanos)
	if rErr != nil {
		return nil, rErr
	}

	metadata := make(map[string]interface{})
	if accountId.HasAlias() && accountIdString != "" {
		metadata["account_id"] = accountIdString
	}
	if isEd25519PublicKey(publicKey) {
		metadata["public_key"] = tools.SafeAddHexPrefix(hex.EncodeToString(publicKey))
	}

	return &rTypes.AccountBalanceResponse{
		BlockIdentifier: block.GetRosettaBlockIdentifier(),
		Balances:        balances.ToRosetta(),
		Metadata:        metadata,
	}, nil
}

func (a *AccountAPIService) AccountCoins(
	_ context.Context,
	_ *rTypes.AccountCoinsRequest,
) (*rTypes.AccountCoinsResponse, *rTypes.Error) {
	return nil, errors.ErrNotImplemented
}

func (a *AccountAPIService) AllAccountBalances(
	ctx context.Context,
	request *rTypes.AllAccountBalancesRequest,
) (*rTypes.AllAccountBalancesResponse, *rTypes.Error) {
	// There's no subaccounts, so always delegate to AccountBalance
	singleAccountBalanceRequest := rTypes.AccountBalanceRequest{
		NetworkIdentifier: request.NetworkIdentifier,
		AccountIdentifier: request.AccountIdentifier,
		BlockIdentifier:   request.BlockIdentifier,
		Currencies:        request.Currencies,
	}
	response, err := a.AccountBalance(ctx, &singleAccountBalanceRequest)
	if err != nil {
		return nil, err
	}

	return &rTypes.AllAccountBalancesResponse{
		AccountBalances: []*rTypes.AccountBalanceWithSubAccount{
			{
				Balances: response.Balances,
				Metadata: response.Metadata,
			},
		},
		BlockIdentifier: response.BlockIdentifier,
	}, nil
}

func isEd25519PublicKey(publicKey []byte) bool {
	if len(publicKey) == 34 && publicKey[0] == 0x12 && publicKey[1] == 0x20 {
		return true
	}

	return false
}
