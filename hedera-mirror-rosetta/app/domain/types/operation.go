// SPDX-License-Identifier: Apache-2.0

package types

import "github.com/coinbase/rosetta-sdk-go/types"

// Operation is domain level struct used to represent Operation within Transaction
type Operation struct {
	AccountId AccountId
	Amount    Amount
	Index     int64
	Metadata  map[string]interface{}
	Status    string
	Type      string
}

// ToRosetta returns Rosetta type Operation from the current domain type Operation
func (o Operation) ToRosetta() *types.Operation {
	var amount *types.Amount
	if o.Amount != nil {
		amount = o.Amount.ToRosetta()
	}
	var status *string
	if o.Status != "" {
		status = &o.Status
	}
	return &types.Operation{
		Account:             o.AccountId.ToRosetta(),
		Amount:              amount,
		Metadata:            o.Metadata,
		OperationIdentifier: &types.OperationIdentifier{Index: o.Index},
		Status:              status,
		Type:                o.Type,
	}
}

type OperationSlice []Operation

// ToRosetta returns a slice of Rosetta Operation
func (o OperationSlice) ToRosetta() []*types.Operation {
	rosettaOperations := make([]*types.Operation, 0, len(o))
	for _, operation := range o {
		rosettaOperations = append(rosettaOperations, operation.ToRosetta())
	}
	return rosettaOperations
}
