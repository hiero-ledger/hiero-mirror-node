// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"fmt"
	"net"
	"net/http"
	"time"

	log "github.com/sirupsen/logrus"
)

const (
	xForwardedForHeader = "X-Forwarded-For"
	xRealIpHeader       = "X-Real-IP"
)

var internalPaths = map[string]bool{livenessPath: true, metricsPath: true, readinessPath: true}

// tracingResponseWriter wraps a regular ResponseWriter in order to store the HTTP status code
type tracingResponseWriter struct {
	http.ResponseWriter
	data       []byte
	statusCode int
}

func newTracingResponseWriter(w http.ResponseWriter) *tracingResponseWriter {
	return &tracingResponseWriter{w, []byte{}, http.StatusOK}
}

func (w *tracingResponseWriter) WriteHeader(code int) {
	w.statusCode = code
	w.ResponseWriter.WriteHeader(code)
}

func (w *tracingResponseWriter) Write(data []byte) (n int, err error) {
	w.data = data
	return w.ResponseWriter.Write(data)
}

// TracingMiddleware traces requests to the log
func TracingMiddleware(inner http.Handler) http.Handler {
	return http.HandlerFunc(func(responseWriter http.ResponseWriter, request *http.Request) {
		start := time.Now()
		clientIpAddress := getClientIpAddress(request)
		path := request.URL.RequestURI()
		tracingResponseWriter := newTracingResponseWriter(responseWriter)

		inner.ServeHTTP(tracingResponseWriter, request)

		message := fmt.Sprintf("%s %s %s (%d) in %s",
			clientIpAddress, request.Method, path, tracingResponseWriter.statusCode, time.Since(start))

		if internalPaths[path] {
			log.Debug(message)
		} else {
			log.Info(message)
		}
	})
}

func getClientIpAddress(r *http.Request) string {
	ipAddress := r.Header.Get(xRealIpHeader)

	if len(ipAddress) == 0 {
		ipAddress = r.Header.Get(xForwardedForHeader)
	}

	if len(ipAddress) == 0 {
		ipAddress, _, _ = net.SplitHostPort(r.RemoteAddr)
	}

	return ipAddress
}
