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
	entityId = domain.MustDecodeEntityId(100)
	status   = "pending"
)

func exampleTransaction() *Transaction {
	return &Transaction{
		EntityId: &entityId,
		Hash:     "somehash",
		Memo:     []byte("transfer"),
		Operations: OperationSlice{
			{
				AccountId: AccountId{},
				Amount:    &HbarAmount{Value: int64(400)},
				Index:     1,
				Status:    status,
				Type:      "transfer",
			},
		},
	}
}

func expectedTransaction() *types.Transaction {
	return &types.Transaction{
		TransactionIdentifier: &types.TransactionIdentifier{Hash: "somehash"},
		Operations: []*types.Operation{
			{
				OperationIdentifier: &types.OperationIdentifier{Index: 1},
				Type:                "transfer",
				Status:              &status,
				Account:             &types.AccountIdentifier{Address: "0.0.0"},
				Amount:              &types.Amount{Value: "400", Currency: CurrencyHbar},
			},
		},
		Metadata: map[string]interface{}{
			"entity_id": entityId.String(),
			"memo":      "transfer",
		},
	}
}

func TestToRosettaTransaction(t *testing.T) {
	// given
	expected := expectedTransaction()

	// when
	actual := exampleTransaction().ToRosetta()

	// then
	assert.Equal(t, expected, actual)
}

func TestToRosettaTransactionNoEntityId(t *testing.T) {
	// given
	transaction := exampleTransaction()
	transaction.EntityId = nil
	expected := expectedTransaction()
	delete(expected.Metadata, "entity_id")

	// when
	actual := transaction.ToRosetta()

	// then
	assert.Equal(t, expected, actual)
}
func TestToRosettaTransactionNilMemo(t *testing.T) {
	// given
	transaction := exampleTransaction()
	transaction.Memo = nil
	expected := expectedTransaction()
	delete(expected.Metadata, "memo")

	// when
	actual := transaction.ToRosetta()

	// then
	assert.Equal(t, expected, actual)
}

func TestToRosettaTransactionEmptyMemo(t *testing.T) {
	// given
	transaction := exampleTransaction()
	transaction.Memo = []byte{}
	expected := expectedTransaction()
	delete(expected.Metadata, "memo")

	// when
	actual := transaction.ToRosetta()

	// then
	assert.Equal(t, expected, actual)
}
