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
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
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
