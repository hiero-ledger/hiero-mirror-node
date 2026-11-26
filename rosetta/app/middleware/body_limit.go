// SPDX-License-Identifier: Apache-2.0

package middleware

import "net/http"

// BodyLimitMiddleware fails reading a request body larger than maxBytes.
func BodyLimitMiddleware(inner http.Handler, maxBytes int64) http.Handler {
	return http.HandlerFunc(func(responseWriter http.ResponseWriter, request *http.Request) {
		request.Body = http.MaxBytesReader(responseWriter, request.Body, maxBytes)
		inner.ServeHTTP(responseWriter, request)
	})
}
