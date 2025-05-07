// SPDX-License-Identifier: Apache-2.0

package scenario

import (
	"fmt"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/stretchr/testify/assert"
)

// asserter is used to be able to retrieve the error reported by the called assertion
type asserter struct {
	err error
}

// Errorf is used by the called assertion to report an error
func (a *asserter) Errorf(format string, args ...interface{}) {
	a.err = fmt.Errorf(format, args...)
}

type assertTransactionFunc func(t *asserter, transaction *types.Transaction)
type compareOp int
type includeFilterFunc func(operation *types.Operation) bool

const (
	eq compareOp = iota
	gt
	gte
	lt
	lte
)

var comparisonFuncs = map[compareOp]assert.ComparisonAssertionFunc{
	eq:  assert.Equal,
	gt:  assert.Greater,
	gte: assert.GreaterOrEqual,
	lt:  assert.Less,
	lte: assert.LessOrEqual,
}

func assertTransactionAll(transaction *types.Transaction, funcs ...assertTransactionFunc) error {
	var t asserter
	for _, assertFunc := range funcs {
		assertFunc(&t, transaction)
		if t.err != nil {
			return t.err
		}
	}

	return t.err
}

func assertTransactionMemo(memo string) assertTransactionFunc {
	return func(t *asserter, transaction *types.Transaction) {
		if len(memo) == 0 {
			assert.NotContains(t, transaction.Metadata, "memo")
		} else {
			assert.IsType(t, memo, transaction.Metadata["memo"])
			actual, _ := transaction.Metadata["memo"].(string)
			assert.Equal(t, memo, actual)
		}
	}
}

func assertTransactionOpTypesContains(expectedOpTypes ...string) assertTransactionFunc {
	return func(t *asserter, transaction *types.Transaction) {
		var actualOpTypes []string
		for _, operation := range transaction.Operations {
			actualOpTypes = append(actualOpTypes, operation.Type)
		}

		assert.Subset(t, actualOpTypes, expectedOpTypes)
	}
}

func assertTransactionOpSuccess(t *asserter, transaction *types.Transaction) {
	message := "operation %d status ('%s') isn't success"
	for index, operation := range transaction.Operations {
		status := *operation.Status
		if !assert.Equalf(t, status, operationStatusSuccess, message, index, status) {
			return
		}
	}
}

func assertTransactionOpCount(expected int, op compareOp) assertTransactionFunc {
	return func(t *asserter, transaction *types.Transaction) {
		comparisonFuncs[op](t, len(transaction.Operations), expected)
	}
}

func assertTransactionMetadataAndType(expected string, expectedType interface{}) assertTransactionFunc {
	return func(t *asserter, transaction *types.Transaction) {
		metadata := transaction.Metadata
		if !assert.Contains(t, metadata, expected) {
			return
		}
		assert.IsType(t, expectedType, metadata[expected])
	}
}

func assertTransactionMetadata(key string, value interface{}) assertTransactionFunc {
	return func(t *asserter, transaction *types.Transaction) {
		metadata := transaction.Metadata
		if !assert.Contains(t, metadata, key) {
			return
		}
		assert.Equal(t, metadata[key], value)
	}
}

type accountAmount struct {
	Account *types.AccountIdentifier
	Amount  *types.Amount
}

func assertTransactionIncludesTransfers(expected []accountAmount) assertTransactionFunc {
	return func(t *asserter, transaction *types.Transaction) {
		actual := make([]accountAmount, 0, len(transaction.Operations))
		for _, operation := range transaction.Operations {
			actual = append(actual, accountAmount{Account: operation.Account, Amount: operation.Amount})
		}

		assert.Subset(t, actual, expected)
	}
}

func assertTransactionOnlyIncludesTransfers(
	expected []accountAmount,
	includeFilter includeFilterFunc,
	ignoreAmountMetadata bool,
) assertTransactionFunc {
	return func(t *asserter, transaction *types.Transaction) {
		actual := make([]accountAmount, 0, len(transaction.Operations))
		for _, operation := range transaction.Operations {
			if includeFilter(operation) {
				amount := operation.Amount
				if ignoreAmountMetadata {
					amount = removeMetadata(amount)
				}
				actual = append(actual, accountAmount{Account: operation.Account, Amount: amount})
			}
		}

		assert.ElementsMatch(t, actual, expected)
	}
}

func removeMetadata(amount *types.Amount) *types.Amount {
	clone := *amount
	clone.Metadata = nil
	return &clone
}
