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

func (m *MockTransactionRepository) FindBetween(ctx context.Context, start, end int64, hashes []string) (
	[]*types.Transaction,
	*rTypes.Error,
) {
	args := m.Called()
	return args.Get(0).([]*types.Transaction), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionRepository) FindBetweenTransactionIdentifiers(
	ctx context.Context,
	start int64,
	end int64,
	cursor int64,
	limit int,
) ([]types.TransactionIdentifier, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).([]types.TransactionIdentifier), args.Get(1).(*rTypes.Error)
}
