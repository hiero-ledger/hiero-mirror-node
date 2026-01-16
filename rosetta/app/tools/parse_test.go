// SPDX-License-Identifier: Apache-2.0

package tools

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestSafeUnquote(t *testing.T) {
	var tests = []struct {
		name     string
		input    string
		expected string
	}{
		{
			name:     "QuotedString",
			input:    "\"this is quoted\"",
			expected: "this is quoted",
		},
		{
			name:     "NotQuotedString",
			input:    "this is not quoted",
			expected: "this is not quoted",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, SafeUnquote(tt.input))
		})
	}
}
