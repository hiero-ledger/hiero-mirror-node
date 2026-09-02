// SPDX-License-Identifier: Apache-2.0

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/stretchr/testify/mock"
)

var NilTransaction *types.Transaction

type MockTransactionRepository struct {
	mock.Mock
}

func (m *MockTransactionRepository) FindByHashInBlock(
	ctx context.Context,
	identifier string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Transaction), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionRepository) FindBetween(ctx context.Context, start, end int64) (
	[]*types.Transaction,
	*rTypes.Error,
) {
	args := m.Called()
	return args.Get(0).([]*types.Transaction), args.Get(1).(*rTypes.Error)
}
