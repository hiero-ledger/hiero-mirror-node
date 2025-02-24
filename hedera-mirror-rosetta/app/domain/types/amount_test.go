// SPDX-License-Identifier: Apache-2.0

package types

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/stretchr/testify/assert"
)

var (
	hbarAmount        = &HbarAmount{Value: 400}
	hbarRosettaAmount = &types.Amount{Value: "400", Currency: CurrencyHbar}
)

func TestAmountSliceToRosetta(t *testing.T) {
	amountSlice := AmountSlice{
		&HbarAmount{Value: 100},
	}
	expected := []*types.Amount{
		{Value: "100", Currency: CurrencyHbar},
	}
	actual := amountSlice.ToRosetta()
	assert.Equal(t, expected, actual)
}

func TestHbarAmountGetValue(t *testing.T) {
	assert.Equal(t, int64(400), hbarAmount.GetValue())
}

func TestHbarAmountToRosettaAmount(t *testing.T) {
	assert.Equal(t, hbarRosettaAmount, hbarAmount.ToRosetta())
}

func TestNewAmountSuccess(t *testing.T) {
	input := &types.Amount{
		Value:    "5",
		Currency: CurrencyHbar,
	}
	expected := &HbarAmount{Value: 5}
	actual, err := NewAmount(input)
	assert.Nil(t, err)
	assert.Equal(t, expected, actual)
}

func TestNewAmountFailure(t *testing.T) {
	tests := []struct {
		name  string
		input *types.Amount
	}{
		{name: "InvalidAmount", input: &types.Amount{Value: "abc", Currency: CurrencyHbar}},
		{name: "InvalidCurrencySymbol", input: &types.Amount{Value: "1", Currency: &types.Currency{Symbol: "foobar"}}},
		{
			name: "InvalidCurrencySymbolWithSpecifiedDecimals",
			input: &types.Amount{
				Value:    "1",
				Currency: &types.Currency{Decimals: 8, Symbol: "foobar"}},
		},
		{
			name: "NegativeCurrencyDecimals",
			input: &types.Amount{
				Value:    "1",
				Currency: &types.Currency{Decimals: -1, Symbol: CurrencyHbar.Symbol},
			},
		},
		{
			name: "InvalidCurrencyDecimalsForHbar",
			input: &types.Amount{
				Value:    "1",
				Currency: &types.Currency{Decimals: 2, Symbol: CurrencyHbar.Symbol},
			},
		},
		{
			name: "InvalidCurrencyMetadataForHbar",
			input: &types.Amount{
				Value: "1",
				Currency: &types.Currency{
					Symbol: CurrencyHbar.Symbol,
					Metadata: map[string]interface{}{
						"issuer": "group",
					},
				},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := NewAmount(tt.input)
			assert.NotNil(t, err)
			assert.Nil(t, actual)
		})
	}
}
