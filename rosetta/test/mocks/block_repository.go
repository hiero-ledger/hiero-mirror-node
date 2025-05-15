// SPDX-License-Identifier: Apache-2.0

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/stretchr/testify/mock"
)

var NilBlock *types.Block

type MockBlockRepository struct {
	mock.Mock
}

func (m *MockBlockRepository) FindByHash(ctx context.Context, hash string) (*types.Block, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}

func (m *MockBlockRepository) FindByIdentifier(ctx context.Context, index int64, hash string) (
	*types.Block,
	*rTypes.Error,
) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}

func (m *MockBlockRepository) FindByIndex(ctx context.Context, index int64) (*types.Block, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}

func (m *MockBlockRepository) RetrieveGenesis(ctx context.Context) (*types.Block, *rTypes.Error) {
	return m.retrieveBlock(m.Called())
}

func (m *MockBlockRepository) RetrieveLatest(ctx context.Context) (*types.Block, *rTypes.Error) {
	return m.retrieveBlock(m.Called())
}

func (m *MockBlockRepository) retrieveBlock(args mock.Arguments) (*types.Block, *rTypes.Error) {
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}
