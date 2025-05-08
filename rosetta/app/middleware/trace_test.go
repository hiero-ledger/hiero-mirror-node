// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"bytes"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	log "github.com/sirupsen/logrus"
	"github.com/stretchr/testify/require"
)

const (
	clientIp    = "10.0.0.100"
	defaultIp   = "192.0.2.1"
	defaultPath = "/network/list"
	levelDebug  = "level=debug"
	levelInfo   = "level=info"
)

func TestTrace(t *testing.T) {
	for _, tc := range []struct {
		headers  map[string]string
		path     string
		messages []string
	}{{
		headers:  map[string]string{"": ""},
		path:     defaultPath,
		messages: []string{levelInfo, "GET " + defaultPath + " (200)", defaultIp},
	}, {
		headers:  map[string]string{"": ""},
		path:     livenessPath,
		messages: []string{levelDebug, livenessPath, defaultIp},
	}, {
		headers:  map[string]string{"": ""},
		path:     readinessPath,
		messages: []string{levelDebug, readinessPath, defaultIp},
	}, {
		headers:  map[string]string{"": ""},
		path:     metricsPath,
		messages: []string{levelDebug, metricsPath, defaultIp},
	}, {
		headers:  map[string]string{"": ""},
		path:     metricsPath + "s",
		messages: []string{levelInfo, "GET /metricss (200)", defaultIp},
	}, {
		headers:  map[string]string{xRealIpHeader: clientIp},
		path:     defaultPath,
		messages: []string{clientIp},
	}, {
		headers:  map[string]string{xForwardedForHeader: clientIp},
		path:     defaultPath,
		messages: []string{clientIp},
	}} {
		buf := bytes.NewBuffer(nil)
		level := log.GetLevel()
		log.SetOutput(buf)
		log.SetLevel(log.DebugLevel)

		handler := func(w http.ResponseWriter, r *http.Request) {
			io.WriteString(w, `{"key": "value"`)
		}
		loggingHandler := TracingMiddleware(http.HandlerFunc(handler))

		req := httptest.NewRequest("GET", "http://localhost"+tc.path, nil)
		for k, v := range tc.headers {
			req.Header.Set(k, v)
		}
		recorder := httptest.NewRecorder()
		loggingHandler.ServeHTTP(recorder, req)
		message := strings.ReplaceAll(string(buf.Bytes()), "\n", "")

		for _, content := range tc.messages {
			require.True(t, strings.Contains(message, content),
				"Message did not contain '%s': %s", content, message)
		}

		log.SetOutput(os.Stdout)
		log.SetLevel(level)
	}
}
