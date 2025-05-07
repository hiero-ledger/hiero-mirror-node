// SPDX-License-Identifier: Apache-2.0

package types

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
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
