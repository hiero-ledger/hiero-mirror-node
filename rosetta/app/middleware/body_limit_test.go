// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestBodyLimit(t *testing.T) {
	const maxBytes = 1024
	tests := []struct {
		name     string
		size     int
		tooLarge bool
	}{
		{name: "empty"},
		{name: "at max", size: maxBytes},
		{name: "over max", size: maxBytes + 1, tooLarge: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			var body []byte
			var readErr error
			inner := http.HandlerFunc(func(_ http.ResponseWriter, request *http.Request) {
				body, readErr = io.ReadAll(request.Body)
			})
			request := httptest.NewRequest(http.MethodPost, defaultPath, strings.NewReader(strings.Repeat("a", tt.size)))

			// when
			BodyLimitMiddleware(inner, maxBytes).ServeHTTP(httptest.NewRecorder(), request)

			// then
			if tt.tooLarge {
				var maxBytesErr *http.MaxBytesError
				assert.ErrorAs(t, readErr, &maxBytesErr)
				return
			}

			assert.NoError(t, readErr)
			assert.Len(t, body, tt.size)
		})
	}
}
