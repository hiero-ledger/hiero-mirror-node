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
	"context"
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
)

const (
	adminKeyStr           = "302a300506032b6570032100d619a3a22d6bd2a9e4b08f3d999df757e5a9ef0364c13b4b3356bc065b34fa01"
	autoRenewPeriod int64 = 3600
	memo                  = "new memo"
)

var (
	adminKey, _ = hiero.PublicKeyFromString(adminKeyStr)
)

var defaultContext = context.Background()

func TestCompareCurrency(t *testing.T) {
	var tests = []struct {
		name      string
		currencyA *rTypes.Currency
		currencyB *rTypes.Currency
		expected  bool
	}{
		{
			name:      "SamePointer",
			currencyA: types.CurrencyHbar,
			currencyB: types.CurrencyHbar,
			expected:  true,
		},
		{
			name:      "SameValue",
			currencyA: &rTypes.Currency{Symbol: "foobar", Decimals: 12},
			currencyB: &rTypes.Currency{Symbol: "foobar", Decimals: 12},
			expected:  true,
		},
		{
			name: "SameValueWithMetadata",
			currencyA: &rTypes.Currency{
				Symbol:   "foobar",
				Decimals: 12,
				Metadata: map[string]interface{}{
					"meta1": 1,
				},
			},
			currencyB: &rTypes.Currency{
				Symbol:   "foobar",
				Decimals: 12,
				Metadata: map[string]interface{}{
					"meta1": 1,
				},
			},
			expected: true,
		},
		{
			name:      "OneIsNil",
			currencyA: &rTypes.Currency{},
			currencyB: nil,
		},
		{
			name:      "DifferentSymbol",
			currencyA: &rTypes.Currency{Symbol: "A"},
			currencyB: &rTypes.Currency{Symbol: "B"},
		},
		{
			name:      "DifferentDecimals",
			currencyA: &rTypes.Currency{Decimals: 1},
			currencyB: &rTypes.Currency{Decimals: 2},
		},
		{
			name: "DifferentMetadata",
			currencyA: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta1": 1,
				}},
			currencyB: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta2": 1,
				}},
		},
		{
			name: "DifferentMetadataValue",
			currencyA: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta1": 1,
				}},
			currencyB: &rTypes.Currency{
				Metadata: map[string]interface{}{
					"meta1": 2,
				}},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, compareCurrency(tt.currencyA, tt.currencyB))
		})
	}
}

func TestIsNonEmptyPublicKey(t *testing.T) {
	var tests = []struct {
		name     string
		key      hiero.Key
		expected bool
	}{
		{
			name:     "Success",
			key:      adminKey,
			expected: true,
		},
		{
			name:     "EmptyPublicKey",
			key:      hiero.PublicKey{},
			expected: false,
		},
		{
			name:     "NotPublicKey",
			key:      hiero.PrivateKey{},
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isNonEmptyPublicKey(tt.key))
		})
	}
}

func TestIsZeroAccountId(t *testing.T) {
	var tests = []struct {
		name      string
		accountId hiero.AccountID
		expected  bool
	}{
		{
			name:      "ZeroAccountId",
			accountId: hiero.AccountID{},
			expected:  true,
		},
		{
			name:      "NonZeroAccountId",
			accountId: hiero.AccountID{Account: 101},
			expected:  false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isZeroAccountId(tt.accountId))
		})
	}
}

func TestParseOperationMetadataWithValidate(t *testing.T) {
	type data struct {
		Name  string `json:"name" validate:"required"`
		Value int    `json:"value" validate:"required"`
	}

	name := "foobar"
	value := 10

	expected := &data{
		Name:  name,
		Value: value,
	}

	var tests = []struct {
		name        string
		metadatas   []map[string]interface{}
		expectError bool
	}{
		{
			name: "Success",
			metadatas: []map[string]interface{}{{
				"name":  name,
				"value": value,
			}},
		},
		{
			name: "SuccessMultiple",
			metadatas: []map[string]interface{}{
				{"name": name},
				{"value": value},
			},
		},
		{
			name: "SuccessMultipleHonorLast",
			metadatas: []map[string]interface{}{
				{
					"name":  "bad",
					"value": 50,
				},
				{
					"name":  name,
					"value": value,
				},
			},
		},
		{
			name: "MissingField",
			metadatas: []map[string]interface{}{
				{"value": value},
			},
			expectError: true,
		},
		{
			name:        "EmptyMetadata",
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			output := &data{}
			err := parseOperationMetadata(validator.New(), output, tt.metadatas...)

			if tt.expectError {
				assert.NotNil(t, err)
			} else {
				assert.Nil(t, err)
				assert.Equal(t, expected, output)
			}

		})
	}
}

func TestParseOperationMetadataWithoutValidate(t *testing.T) {
	type data struct {
		Name  string `json:"name"`
		Value int    `json:"value"`
	}

	// given
	name := "foobar"
	expected := &data{
		Name:  name,
		Value: 0,
	}

	metadata := map[string]interface{}{
		"name": name,
	}

	// when
	output := &data{}
	err := parseOperationMetadata(nil, output, metadata)

	// then
	assert.Nil(t, err)
	assert.Equal(t, expected, output)
}

func TestValidateOperationsWithType(t *testing.T) {
	var tests = []struct {
		name            string
		operations      types.OperationSlice
		size            int
		operationType   string
		expectNilAmount bool
		expectError     bool
	}{
		{
			name:          "SuccessSingleOperation",
			operations:    types.OperationSlice{getOperation(0, types.OperationTypeCryptoTransfer)},
			size:          1,
			operationType: types.OperationTypeCryptoTransfer,
		},
		{
			name: "SuccessMultipleOperations",
			operations: types.OperationSlice{
				getOperation(0, types.OperationTypeCryptoTransfer),
				getOperation(1, types.OperationTypeCryptoTransfer),
			},
			size:          0,
			operationType: types.OperationTypeCryptoTransfer,
		},
		{
			name:            "SuccessExpectNilAmount",
			operations:      types.OperationSlice{{AccountId: accountIdA, Type: types.OperationTypeCryptoTransfer}},
			size:            0,
			operationType:   types.OperationTypeCryptoTransfer,
			expectNilAmount: true,
		},
		{
			name: "NonNilAmount",
			operations: types.OperationSlice{
				{
					AccountId: accountIdA,
					Amount:    &types.HbarAmount{Value: 1},
					Type:      types.OperationTypeCryptoTransfer,
				},
			},
			size:            0,
			operationType:   types.OperationTypeCryptoTransfer,
			expectNilAmount: true,
			expectError:     true,
		},
		{
			name:          "EmptyOperations",
			operationType: types.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name: "OperationsSizeMismatch",
			operations: types.OperationSlice{
				getOperation(0, types.OperationTypeCryptoTransfer),
				getOperation(1, types.OperationTypeCryptoTransfer),
			},
			size:          1,
			operationType: types.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name:          "OperationTypeMismatch",
			operations:    types.OperationSlice{getOperation(0, types.OperationTypeCryptoTransfer)},
			size:          1,
			operationType: types.OperationTypeTokenCreate,
			expectError:   true,
		},
		{
			name: "MultipleOperationTypes",
			operations: types.OperationSlice{
				getOperation(0, types.OperationTypeCryptoTransfer),
				getOperation(0, types.OperationTypeTokenCreate),
			},
			size:          0,
			operationType: types.OperationTypeCryptoTransfer,
			expectError:   true,
		},
		{
			name:          "OperationMissingAmount",
			operations:    types.OperationSlice{{AccountId: accountIdA, Type: types.OperationTypeCryptoTransfer}},
			size:          1,
			operationType: types.OperationTypeCryptoTransfer,
			expectError:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := validateOperations(tt.operations, tt.size, tt.operationType, tt.expectNilAmount)

			if tt.expectError {
				assert.NotNil(t, err)
			} else {
				assert.Nil(t, err)
			}
		})
	}
}

func getOperation(index int64, operationType string) types.Operation {
	return types.Operation{
		AccountId: accountIdA,
		Amount:    &types.HbarAmount{Value: 20},
		Index:     index,
		Type:      operationType,
	}
}
