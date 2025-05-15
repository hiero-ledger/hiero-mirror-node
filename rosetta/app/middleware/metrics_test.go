// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestMetrics(t *testing.T) {
	metricsController := NewMetricsController()
	request := httptest.NewRequest("GET", "http://localhost"+metricsPath, nil)
	recorder := httptest.NewRecorder()
	responseWriter := newTracingResponseWriter(recorder)
	metricsController.Routes()[0].HandlerFunc.ServeHTTP(responseWriter, request)

	response := string(responseWriter.data)
	require.Equal(t, http.StatusOK, responseWriter.statusCode)
	require.Contains(t, responseWriter.Header().Get("Content-Type"), "text/plain")
	require.Contains(t, response, "promhttp_metric_handler_requests_total")
}
