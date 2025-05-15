// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/hellofresh/health-go/v4"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/stretchr/testify/require"
)

func TestLiveness(t *testing.T) {
	healthController, err := NewHealthController(&config.Config{})
	require.NoError(t, err)

	req := httptest.NewRequest("GET", "http://localhost"+livenessPath, nil)
	recorder := httptest.NewRecorder()
	tracingResponseWriter := newTracingResponseWriter(recorder)
	tracingResponseWriter.statusCode = http.StatusBadGateway
	healthController.Routes()[0].HandlerFunc.ServeHTTP(tracingResponseWriter, req)

	var check health.Check
	err = json.Unmarshal(tracingResponseWriter.data, &check)
	require.NoError(t, err)
	require.Equal(t, http.StatusOK, tracingResponseWriter.statusCode)
	require.Equal(t, "application/json", tracingResponseWriter.Header().Get("Content-Type"))
	require.Equal(t, health.StatusOK, check.Status)
}

func TestReadiness(t *testing.T) {
	for _, tc := range []struct {
		status health.Status
	}{{
		status: health.StatusUnavailable,
	}} {
		healthController, err := NewHealthController(&config.Config{Port: 80})
		require.NoError(t, err)

		req := httptest.NewRequest("GET", "http://localhost"+readinessPath, nil)
		recorder := httptest.NewRecorder()
		tracingResponseWriter := newTracingResponseWriter(recorder)
		tracingResponseWriter.statusCode = http.StatusBadGateway
		healthController.Routes()[1].HandlerFunc.ServeHTTP(tracingResponseWriter, req)

		httpStatus := http.StatusOK
		if tc.status == health.StatusUnavailable {
			httpStatus = http.StatusServiceUnavailable
		}

		var check health.Check
		err = json.Unmarshal(tracingResponseWriter.data, &check)
		require.NoError(t, err)
		require.Equal(t, "application/json", tracingResponseWriter.Header().Get("Content-Type"))
		require.Equal(t, tc.status, check.Status)
		require.Equal(t, httpStatus, tracingResponseWriter.statusCode)
	}
}
