// SPDX-License-Identifier: Apache-2.0

package types

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
)

// Transaction is domain level struct used to represent Transaction conceptual mapping in Hedera
type Transaction struct {
	EntityId   *domain.EntityId
	Hash       string
	Memo       []byte
	Operations OperationSlice
}

// ToRosetta returns Rosetta type Transaction from the current domain type Transaction
func (t *Transaction) ToRosetta() *types.Transaction {
	operations := t.Operations.ToRosetta()
	metadata := make(map[string]interface{})

	if t.EntityId != nil {
		metadata["entity_id"] = t.EntityId.String()
	}

	if len(t.Memo) != 0 {
		metadata[MetadataKeyMemo] = string(t.Memo)
	}

	return &types.Transaction{
		TransactionIdentifier: &types.TransactionIdentifier{Hash: t.Hash},
		Operations:            operations,
		Metadata:              metadata,
	}
}
