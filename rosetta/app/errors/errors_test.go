// SPDX-License-Identifier: Apache-2.0

package errors

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/stretchr/testify/assert"
)

func TestAddErrorDetails(t *testing.T) {
	base := &types.Error{
		Code:      0,
		Message:   "foobar1",
		Retriable: false,
	}
	expected := &types.Error{
		Code:      0,
		Message:   "foobar1",
		Retriable: false,
		Details: map[string]interface{}{
			"name": "value",
		},
	}

	actual := AddErrorDetails(base, "name", "value")

	assert.Equal(t, expected, actual)
	assert.Nil(t, base.Details)

	// add another detail
	expected.Details["name2"] = "value2"
	actual = AddErrorDetails(actual, "name2", "value2")

	assert.Equal(t, expected, actual)
}
