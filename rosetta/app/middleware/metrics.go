// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"net/http"

	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/weaveworks/common/middleware"
)

const (
	application = "rosetta"
	metricsPath = "/metrics"
)

var (
	sizeBuckets = []float64{512, 1024, 10 * 1024, 25 * 1024, 50 * 1024}

	requestBytesHistogram = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "hiero_mirror_rosetta_request_bytes",
		Buckets: sizeBuckets,
		Help:    "Size (in bytes) of messages received in the request.",
	}, []string{"method", "route"})

	requestDurationHistogram = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "hiero_mirror_rosetta_request_duration",
		Buckets: []float64{.1, .25, .5, 1, 2.5, 5},
		Help:    "Time (in seconds) spent serving HTTP requests.",
	}, []string{"method", "route", "status_code", "ws"})

	requestInflightGauge = prometheus.NewGaugeVec(prometheus.GaugeOpts{
		Name: "hiero_mirror_rosetta_request_inflight",
		Help: "Current number of inflight HTTP requests.",
	}, []string{"method", "route"})

	responseBytesHistogram = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "hiero_mirror_rosetta_response_bytes",
		Buckets: sizeBuckets,
		Help:    "Size (in bytes) of messages sent in response.",
	}, []string{"method", "route"})
)

func init() {
	register := prometheus.WrapRegistererWith(prometheus.Labels{"application": application}, prometheus.DefaultRegisterer)
	register.MustRegister(requestBytesHistogram)
	register.MustRegister(requestDurationHistogram)
	register.MustRegister(requestInflightGauge)
	register.MustRegister(responseBytesHistogram)
}

// metricsController holds data used to serve metric requests
type metricsController struct {
}

// NewMetricsController constructs a new MetricsController object
func NewMetricsController() server.Router {
	return &metricsController{}
}

// Routes returns the metrics controller routes
func (c *metricsController) Routes() server.Routes {
	return server.Routes{
		{
			"metrics",
			"GET",
			metricsPath,
			promhttp.Handler().ServeHTTP,
		},
	}
}

// MetricsMiddleware instruments HTTP requests with request metrics
func MetricsMiddleware(next http.Handler) http.Handler {
	return middleware.Instrument{
		Duration:         requestDurationHistogram,
		InflightRequests: requestInflightGauge,
		RequestBodySize:  requestBytesHistogram,
		ResponseBodySize: responseBytesHistogram,
		RouteMatcher:     next.(middleware.RouteMatcher),
	}.Wrap(next)
}
