// SPDX-License-Identifier: Apache-2.0

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/mock"
)

var (
	NilHederaTransaction *hiero.TransferTransaction
	NilOperations        types.OperationSlice
	NilSigners           []types.AccountId
)

type MockTransactionConstructor struct {
	mock.Mock
}

func (m *MockTransactionConstructor) Construct(
	ctx context.Context,
	operations types.OperationSlice,
) (hiero.TransactionInterface, []types.AccountId, *rTypes.Error) {
	args := m.Called(ctx, operations)
	return args.Get(0).(hiero.TransactionInterface), args.Get(1).([]types.AccountId),
		args.Get(2).(*rTypes.Error)
}

func (m *MockTransactionConstructor) Parse(ctx context.Context, transaction hiero.TransactionInterface) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	args := m.Called(ctx, transaction)
	return args.Get(0).(types.OperationSlice), args.Get(1).([]types.AccountId), args.Get(2).(*rTypes.Error)
}

func (m *MockTransactionConstructor) Preprocess(ctx context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	args := m.Called(ctx, operations)
	return args.Get(0).([]types.AccountId), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionConstructor) GetDefaultMaxTransactionFee(operationType string) (
	types.HbarAmount,
	*rTypes.Error,
) {
	args := m.Called(operationType)
	return args.Get(0).(types.HbarAmount), args.Get(1).(*rTypes.Error)
}
