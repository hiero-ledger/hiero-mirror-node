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

package services

import (
	"context"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
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

	balances, accountIdString, rErr := a.accountRepo.RetrieveBalanceAtBlock(ctx, accountId, block.ConsensusEndNanos)
	if rErr != nil {
		return nil, rErr
	}

	var metadata map[string]interface{}
	if accountId.HasAlias() && accountIdString != "" {
		metadata = map[string]interface{}{"account_id": accountIdString}
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
