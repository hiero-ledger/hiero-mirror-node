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

package types

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/stretchr/testify/assert"
)

var (
	statusUnknown = "unknown"
)

func customizeAmount(amount Amount) func(*Operation) {
	return func(o *Operation) {
		o.Amount = amount
	}
}

func customizeRosettaAmount(amount *types.Amount) func(*types.Operation) {
	return func(o *types.Operation) {
		o.Amount = amount
	}
}

func customizeIndex(index int64) func(*Operation) {
	return func(o *Operation) {
		o.Index = index
	}
}

func customizeRosettaIndex(index int64) func(*types.Operation) {
	return func(o *types.Operation) {
		o.OperationIdentifier.Index = index
	}
}

func customizeStatus(status string) func(*Operation) {
	return func(o *Operation) {
		o.Status = status
	}
}

func customizeRosettaStatus(status *string) func(*types.Operation) {
	return func(o *types.Operation) {
		o.Status = status
	}
}

func exampleOperation(customizers ...func(*Operation)) *Operation {
	operation := &Operation{
		AccountId: AccountId{accountId: domain.MustDecodeEntityId(1)},
		Index:     1,
		Status:    "pending",
		Type:      "transfer",
	}

	for _, customize := range customizers {
		customize(operation)
	}

	return operation
}

func expectedOperation(customizers ...func(operation *types.Operation)) *types.Operation {
	status := "pending"
	operation := &types.Operation{
		Account:             &types.AccountIdentifier{Address: "0.0.1"},
		OperationIdentifier: &types.OperationIdentifier{Index: 1},
		Status:              &status,
		Type:                "transfer",
	}

	for _, customize := range customizers {
		customize(operation)
	}

	return operation
}

func TestOperationToRosetta(t *testing.T) {
	var tests = []struct {
		name     string
		input    *Operation
		expected *types.Operation
	}{
		{
			name:     "HbarAmount",
			input:    exampleOperation(customizeAmount(hbarAmount)),
			expected: expectedOperation(customizeRosettaAmount(hbarRosettaAmount)),
		},
		{
			name:     "NilAmount",
			input:    exampleOperation(customizeAmount(nil)),
			expected: expectedOperation(customizeRosettaAmount(nil)),
		},
		{
			name:     "NoStatus",
			input:    exampleOperation(customizeStatus("")),
			expected: expectedOperation(customizeRosettaStatus(nil)),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// when:
			rosettaOperation := tt.input.ToRosetta()

			// then:
			assert.Equal(t, tt.expected, rosettaOperation)
		})
	}
}

func TestOperationSliceToRosetta(t *testing.T) {
	operationSlice := OperationSlice{
		*exampleOperation(customizeAmount(hbarAmount)),
		*exampleOperation(customizeIndex(2), customizeStatus("")),
		*exampleOperation(customizeIndex(3), customizeStatus("unknown"), customizeAmount(nil)),
	}
	expected := []*types.Operation{
		expectedOperation(customizeRosettaAmount(hbarRosettaAmount)),
		expectedOperation(customizeRosettaIndex(2), customizeRosettaStatus(nil)),
		expectedOperation(customizeRosettaIndex(3), customizeRosettaStatus(&statusUnknown),
			customizeRosettaAmount(nil)),
	}
	assert.ElementsMatch(t, expected, operationSlice.ToRosetta())
}
