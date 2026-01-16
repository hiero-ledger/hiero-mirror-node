// SPDX-License-Identifier: Apache-2.0

package types

import (
	"reflect"
	"strconv"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
)

type Amount interface {
	GetDecimals() int64
	GetSymbol() string
	GetValue() int64
	ToRosetta() *types.Amount
}

type AmountSlice []Amount

func (a AmountSlice) ToRosetta() []*types.Amount {
	rosettaAmounts := make([]*types.Amount, 0, len(a))
	for _, amount := range a {
		rosettaAmounts = append(rosettaAmounts, amount.ToRosetta())
	}
	return rosettaAmounts
}

type HbarAmount struct {
	Value int64
}

func (h *HbarAmount) GetDecimals() int64 {
	return int64(CurrencyHbar.Decimals)
}

func (h *HbarAmount) GetSymbol() string {
	return CurrencyHbar.Symbol
}

func (h *HbarAmount) GetValue() int64 {
	return h.Value
}

// ToRosetta returns Rosetta type Amount with hbar currency
func (h *HbarAmount) ToRosetta() *types.Amount {
	return &types.Amount{
		Value:    strconv.FormatInt(h.Value, 10),
		Currency: CurrencyHbar,
	}
}

func NewAmount(amount *types.Amount) (Amount, *types.Error) {
	value, err := tools.ToInt64(amount.Value)
	if err != nil {
		return nil, errors.ErrInvalidOperationsAmount
	}

	currency := amount.Currency
	if currency.Decimals < 0 {
		return nil, errors.ErrInvalidCurrency
	}

	if currency.Symbol == CurrencyHbar.Symbol {
		if !reflect.DeepEqual(currency, CurrencyHbar) {
			return nil, errors.ErrInvalidCurrency
		}

		return &HbarAmount{Value: value}, nil
	}

	return nil, errors.ErrInvalidCurrency
}
