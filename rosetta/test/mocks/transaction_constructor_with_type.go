// SPDX-License-Identifier: Apache-2.0

package mocks

import (
	"context"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/stretchr/testify/mock"
)

type MockTransactionConstructorWithType struct {
	mock.Mock
}

func (m *MockTransactionConstructorWithType) Construct(
	ctx context.Context,
	operations types.OperationSlice,
) (hiero.TransactionInterface, []types.AccountId, *rTypes.Error) {
	args := m.Called(ctx, operations)
	return args.Get(0).(hiero.TransactionInterface), args.Get(1).([]types.AccountId),
		args.Get(2).(*rTypes.Error)
}

func (m *MockTransactionConstructorWithType) Parse(ctx context.Context, transaction hiero.TransactionInterface) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	args := m.Called(ctx, transaction)
	return args.Get(0).(types.OperationSlice), args.Get(1).([]types.AccountId), args.Get(2).(*rTypes.Error)
}

func (m *MockTransactionConstructorWithType) Preprocess(ctx context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	args := m.Called(ctx, operations)
	return args.Get(0).([]types.AccountId), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionConstructorWithType) GetDefaultMaxTransactionFee() types.HbarAmount {
	args := m.Called()
	return args.Get(0).(types.HbarAmount)
}

func (m *MockTransactionConstructorWithType) GetOperationType() string {
	return types.OperationTypeCryptoTransfer
}

func (m *MockTransactionConstructorWithType) GetSdkTransactionType() string {
	return "TransferTransaction"
}
