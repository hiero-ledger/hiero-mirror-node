// SPDX-License-Identifier: Apache-2.0

package tools

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestGetsCorrectStringValuesFromMap(t *testing.T) {
	// given:
	inputData := map[int32]string{
		1: "abc",
		2: "asd",
		3: "aaaa",
		4: "1",
	}
	expected := []string{
		"abc",
		"asd",
		"aaaa",
		"1",
	}

	// when:
	result := GetStringValuesFromInt32StringMap(inputData)

	// then:
	assert.ElementsMatch(t, expected, result)
}
