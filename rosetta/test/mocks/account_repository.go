// SPDX-License-Identifier: Apache-2.0

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
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
) (types.AmountSlice, string, []byte, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(types.AmountSlice), args.Get(1).(string), args.Get(2).([]byte), args.Get(3).(*rTypes.Error)
}
