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

package construction

import (
	"fmt"
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	accountIdA       = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(9500))
	accountIdB       = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(9505))
	sdkAccountIdA, _ = accountIdA.ToSdkAccountId()
	sdkAccountIdB, _ = accountIdB.ToSdkAccountId()

	currencyHbar = types.CurrencyHbar

	defaultSigners   = []types.AccountId{accountIdA}
	defaultTransfers = []transferOperation{
		{accountId: accountIdA, amount: &types.HbarAmount{Value: -15}},
		{accountId: accountIdB, amount: &types.HbarAmount{Value: 15}},
	}
	outOfRangeAccountId = hiero.AccountID{Shard: 2 << 15, Realm: 2 << 16, Account: 2<<32 + 5}
)

type transferOperation struct {
	accountId types.AccountId
	amount    types.Amount
}

func TestCryptoTransferTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(cryptoTransferTransactionConstructorSuite))
}

type cryptoTransferTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *cryptoTransferTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newCryptoTransferTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	h := newCryptoTransferTransactionConstructor()
	assert.Equal(suite.T(), types.HbarAmount{Value: 1_00000000}, h.GetDefaultMaxTransactionFee())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetOperationType() {
	h := newCryptoTransferTransactionConstructor()
	assert.Equal(suite.T(), types.OperationTypeCryptoTransfer, h.GetOperationType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newCryptoTransferTransactionConstructor()
	assert.Equal(suite.T(), "TransferTransaction", h.GetSdkTransactionType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name            string
		transfers       []transferOperation
		expectError     bool
		expectedSigners []types.AccountId
	}{
		{name: "Success", transfers: defaultTransfers, expectedSigners: defaultSigners},
		{name: "AccountNumOutOfRange", transfers: []transferOperation{
			{accountId: types.NewAccountIdFromEntityId(domain.EntityId{EntityNum: -1}), amount: &types.HbarAmount{Value: -15}},
			{accountId: accountIdB, amount: &types.HbarAmount{Value: 15}},
		}, expectError: true},
		{name: "EmptyOperations", expectError: true},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := suite.makeOperations(tt.transfers)
			h := newCryptoTransferTransactionConstructor()

			// when
			tx, signers, err := h.Construct(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
				assertCryptoTransferTransaction(t, operations, tx)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() hiero.TransactionInterface {
		return hiero.NewTransferTransaction().
			AddHbarTransfer(sdkAccountIdA, hiero.HbarFromTinybar(-15)).
			AddHbarTransfer(sdkAccountIdB, hiero.HbarFromTinybar(15)).
			SetTransactionID(hiero.TransactionIDGenerate(sdkAccountIdA))
	}

	expectedTransfers := []string{
		transferStringify(sdkAccountIdA, -15, currencyHbar.Symbol, uint32(currencyHbar.Decimals), 0),
		transferStringify(sdkAccountIdB, 15, currencyHbar.Symbol, uint32(currencyHbar.Decimals), 0),
	}

	var tests = []struct {
		name           string
		getTransaction func() hiero.TransactionInterface
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() hiero.TransactionInterface {
				return hiero.NewTokenCreateTransaction()
			},
			expectError: true,
		},
		{
			name: "OutOfRangeAccountIdHbarTransfer",
			getTransaction: func() hiero.TransactionInterface {
				return hiero.NewTransferTransaction().
					AddHbarTransfer(outOfRangeAccountId, hiero.HbarFromTinybar(-15)).
					AddHbarTransfer(sdkAccountIdB, hiero.HbarFromTinybar(15)).
					SetTransactionID(hiero.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() hiero.TransactionInterface {
				return hiero.NewTransferTransaction()
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			h := newCryptoTransferTransactionConstructor()
			tx := tt.getTransaction()

			// when
			operations, signers, err := h.Parse(defaultContext, tx)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, operations)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, defaultSigners, signers)

				actualTransfers := make([]string, 0, len(operations))
				for _, operation := range operations {
					actualTransfers = append(actualTransfers, operationTransferStringify(operation))
				}
				assert.ElementsMatch(t, expectedTransfers, actualTransfers)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name            string
		transfers       []transferOperation
		operations      types.OperationSlice
		expectError     bool
		expectedSigners []types.AccountId
	}{
		{
			name:            "Success",
			transfers:       defaultTransfers,
			expectedSigners: defaultSigners,
		},
		{
			name:        "ZeroAmount",
			transfers:   []transferOperation{{accountId: accountIdA, amount: &types.HbarAmount{}}},
			expectError: true,
		},

		{
			name: "InvalidHbarSum",
			transfers: []transferOperation{
				{accountId: accountIdA, amount: &types.HbarAmount{Value: -15}},
				{accountId: accountIdB, amount: &types.HbarAmount{Value: 25}},
			},
			expectError: true,
		},
		{
			name: "InvalidOperationType",
			operations: types.OperationSlice{
				{
					AccountId: accountIdA,
					Type:      types.OperationTypeTokenWipe,
					Amount:    &types.HbarAmount{Value: 100},
				},
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := tt.operations
			if len(tt.operations) == 0 {
				operations = suite.makeOperations(tt.transfers)
			}

			h := newCryptoTransferTransactionConstructor()

			// when
			signers, err := h.Preprocess(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) makeOperations(transfers []transferOperation) types.OperationSlice {
	operations := make(types.OperationSlice, 0, len(transfers))
	for _, transfer := range transfers {
		operation := types.Operation{
			AccountId: transfer.accountId,
			Index:     int64(len(operations)),
			Type:      types.OperationTypeCryptoTransfer,
			Amount:    transfer.amount,
		}
		operations = append(operations, operation)
	}
	return operations
}

func assertCryptoTransferTransaction(
	t *testing.T,
	operations types.OperationSlice,
	actual hiero.TransactionInterface,
) {
	assert.IsType(t, &hiero.TransferTransaction{}, actual)
	tx, _ := actual.(*hiero.TransferTransaction)
	assert.False(t, tx.IsFrozen())

	expectedTransfers := make([]string, 0, len(operations))
	for _, operation := range operations {
		expectedTransfers = append(
			expectedTransfers,
			operationTransferStringify(operation),
		)
	}

	actualHbarTransfers := tx.GetHbarTransfers()

	actualTransfers := make([]string, 0)
	for accountId, amount := range actualHbarTransfers {
		actualTransfers = append(
			actualTransfers,
			transferStringify(accountId, amount.AsTinybar(), currencyHbar.Symbol, uint32(currencyHbar.Decimals), 0),
		)
	}

	assert.ElementsMatch(t, expectedTransfers, actualTransfers)
}

func operationTransferStringify(operation types.Operation) string {
	serialNumber := int64(0)
	amount := operation.Amount
	return fmt.Sprintf("%s_%d_%s_%d_%d", operation.AccountId.String(), amount.GetValue(), amount.GetSymbol(),
		amount.GetDecimals(), serialNumber)
}

func transferStringify(
	account hiero.AccountID,
	amount int64,
	symbol string,
	decimals uint32,
	serialNumber int64,
) string {
	return fmt.Sprintf("%s_%d_%s_%d_%d", account, amount, symbol, decimals, serialNumber)
}
