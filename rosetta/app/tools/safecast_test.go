// SPDX-License-Identifier: Apache-2.0

package tools

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCastToInt64(t *testing.T) {
	tests := []struct {
		name     string
		input    uint64
		expected int64
	}{
		{
			name:     "Max",
			input:    9223372036854775807,
			expected: 9223372036854775807,
		},
		{
			name:     "One",
			input:    1,
			expected: 1,
		},
		{
			name:     "Zero",
			input:    0,
			expected: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := CastToInt64(tt.input)
			assert.NoError(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestCastToInt64OutOfRange(t *testing.T) {
	_, err := CastToInt64(9223372036854775808)
	assert.Error(t, err)
}

func TestCastToUint64(t *testing.T) {
	tests := []struct {
		name     string
		input    int64
		expected uint64
	}{
		{
			name:     "Max",
			input:    9223372036854775807,
			expected: 9223372036854775807,
		},
		{
			name:     "One",
			input:    1,
			expected: 1,
		},
		{
			name:     "Zero",
			input:    0,
			expected: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := CastToUint64(tt.input)
			assert.NoError(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestCastToUint64OutOfRange(t *testing.T) {
	_, err := CastToUint64(-1)
	assert.Error(t, err)
}
