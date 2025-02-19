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
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
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
