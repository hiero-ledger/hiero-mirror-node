// SPDX-License-Identifier: Apache-2.0

package tools

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAddsPrefixCorrectly(t *testing.T) {
	// given:
	var testData = []struct {
		string string
	}{
		{"addprefix"},
		{""},
		{"123"},
		{"0x"},
		{"0x "},
		{"0x123aasd"},
	}

	var expectedData = []struct {
		result string
	}{
		{"0xaddprefix"},
		{"0x"},
		{"0x123"},
		{"0x"},
		{"0x "},
		{"0x123aasd"},
	}

	for i, tt := range testData {
		// when:
		result := SafeAddHexPrefix(tt.string)

		// then:
		assert.Equal(t, expectedData[i].result, result)
	}
}

func TestRemovesPrefixCorrectly(t *testing.T) {
	// given:
	var testData = []struct {
		string string
	}{
		{"0xaddprefix"},
		{"0x"},
		{"0x123"},
		{"0x "},
		{"0x123aasd"},
		{"0xaasd"},
		{"234123"},
	}

	var expectedData = []struct {
		result string
	}{
		{"addprefix"},
		{""},
		{"123"},
		{" "},
		{"123aasd"},
		{"aasd"},
		{"234123"},
	}

	for i, tt := range testData {
		// when:
		result := SafeRemoveHexPrefix(tt.string)
		// then:
		assert.Equal(t, expectedData[i].result, result)
	}
}
