/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package middleware

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hellofresh/health-go/v4"

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
