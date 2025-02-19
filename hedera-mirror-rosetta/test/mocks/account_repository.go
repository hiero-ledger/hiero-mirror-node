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

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/stretchr/testify/mock"
)

var NilError *rTypes.Error

type MockAccountRepository struct {
	mock.Mock
}

func (m *MockAccountRepository) GetAccountAlias(ctx context.Context, accountId types.AccountId) (
	types.AccountId,
	*rTypes.Error,
) {
	args := m.Called()
	return args.Get(0).(types.AccountId), args.Get(1).(*rTypes.Error)
}

func (m *MockAccountRepository) GetAccountId(ctx context.Context, accountId types.AccountId) (
	types.AccountId,
	*rTypes.Error,
) {
	args := m.Called(ctx, accountId)
	return args.Get(0).(types.AccountId), args.Get(1).(*rTypes.Error)
}

func (m *MockAccountRepository) RetrieveBalanceAtBlock(
	ctx context.Context,
	accountId types.AccountId,
	consensusEnd int64,
) (types.AmountSlice, string, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(types.AmountSlice), args.Get(1).(string), args.Get(2).(*rTypes.Error)
}
