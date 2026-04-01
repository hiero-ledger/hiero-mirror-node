// SPDX-License-Identifier: Apache-2.0

import basicAuth from 'basic-auth';
import {PrometheusExporter, PrometheusSerializer} from '@opentelemetry/exporter-prometheus';
import {MeterProvider} from '@opentelemetry/sdk-metrics';
import tsscmp from 'tsscmp';
import url from 'url';

import config from '../config.js';
import {apiPrefix, requestPathLabel} from '../constants';

// Converts Express :param route patterns to OpenAPI {param} style
const toOpenApiPath = (req, res) => {
  let path = res.locals[requestPathLabel];

  if (!path) {
    if (!req.route) {
      path = req.path;
    } else {
      path = (req.baseUrl ?? '') + req.route?.path;
    }
  }

  path = path.replace(/:([^/]+)/g, '{$1}');

  if (!path.startsWith(apiPrefix)) {
    return apiPrefix + '/' + path;
  }

  return path;
};

const getChunkSize = (chunk) => {
  if (!chunk) {
    return 0;
  }
  if (Buffer.isBuffer(chunk)) {
    return chunk.length;
  }
  if (typeof chunk === 'string') {
    return Buffer.byteLength(chunk);
  }
  return 0;
};

let exporter;

// Aggregate ingress counters (no labels)
let allRequestCounter, allSuccessCounter, allErrorCounter;
let allClientErrorCounter, allServerErrorCounter, inFlightCounter;

// Per-route instruments
let requestTotalCounter, durationHistogram, requestSizeHistogram, responseSizeHistogram;

const initMetrics = () => {
  const {
    durationBuckets = [25, 100, 250, 500],
    requestSizeBuckets = [],
    responseSizeBuckets = [100, 250, 500, 1000],
  } = config.metrics.config;

  exporter = new PrometheusExporter({preventServerStart: true});
  const meterProvider = new MeterProvider({readers: [exporter]});
  const meter = meterProvider.getMeter('mirror-rest');

  // --- Aggregate ingress counters ---
  allRequestCounter = meter.createCounter('api_all_request', {
    description: 'Total number of requests received',
  });
  allSuccessCounter = meter.createCounter('api_all_success', {
    description: 'Total number of successful requests (2xx)',
  });
  allErrorCounter = meter.createCounter('api_all_errors', {
    description: 'Total number of error requests (4xx+5xx)',
  });
  allClientErrorCounter = meter.createCounter('api_all_client_error', {
    description: 'Total number of client error requests (4xx)',
  });
  allServerErrorCounter = meter.createCounter('api_all_server_error', {
    description: 'Total number of server error requests (5xx)',
  });
  // UpDownCounter — name already has _total so no suffix will be added by exporter
  inFlightCounter = meter.createUpDownCounter('api_all_request_in_processing_total', {
    description: 'Number of requests currently being processed',
  });

  // --- Per-route instruments ---
  requestTotalCounter = meter.createCounter('api_request', {
    description: 'Total number of requests per route',
  });
  durationHistogram = meter.createHistogram('api_request_duration_milliseconds', {
    description: 'Request duration in milliseconds',
    unit: 'ms',
    advice: {explicitBucketBoundaries: durationBuckets},
  });
  requestSizeHistogram = meter.createHistogram('api_request_size_bytes', {
    description: 'Request size in bytes',
    unit: 'By',
    advice: {explicitBucketBoundaries: requestSizeBuckets},
  });
  responseSizeHistogram = meter.createHistogram('api_response_size_bytes', {
    description: 'Response size in bytes',
    unit: 'By',
    advice: {explicitBucketBoundaries: responseSizeBuckets},
  });

  // --- Node.js process metrics ---
  let previousCpuUsage = process.cpuUsage();
  let previousHrTime = process.hrtime.bigint();

  meter
    .createObservableGauge('nodejs_process_cpu_usage_percentage', {
      description: 'Process CPU usage percentage',
    })
    .addCallback((result) => {
      const currentCpuUsage = process.cpuUsage();
      const currentHrTime = process.hrtime.bigint();
      const elapsedUs = Number(currentHrTime - previousHrTime) / 1000;
      const cpuUs = currentCpuUsage.user - previousCpuUsage.user + (currentCpuUsage.system - previousCpuUsage.system);
      previousCpuUsage = currentCpuUsage;
      previousHrTime = currentHrTime;
      result.observe(elapsedUs > 0 ? (cpuUs / elapsedUs) * 100 : 0);
    });

  for (const [name, key] of [
    ['nodejs_process_memory_rss_bytes', 'rss'],
    ['nodejs_process_memory_heap_total_bytes', 'heapTotal'],
    ['nodejs_process_memory_heap_used_bytes', 'heapUsed'],
    ['nodejs_process_memory_external_bytes', 'external'],
  ]) {
    meter.createObservableGauge(name, {unit: 'By'}).addCallback((result) => {
      result.observe(process.memoryUsage()[key]);
    });
  }
};

const authenticate = (req) => {
  const {authentication, username, password} = config.metrics.config;
  if (!authentication) {
    return true;
  }
  const credentials = basicAuth(req);
  return credentials && tsscmp(credentials.name, username) && tsscmp(credentials.pass, password);
};

const metricsHandler = () => {
  initMetrics();
  const serializer = new PrometheusSerializer();
  // Maintain backward-compatible path: uriPath="/swagger" → endpoint at /swagger/metrics/
  const metricsPath = `${config.metrics.config.uriPath}/metrics/`;

  return function metricsMiddleware(req, res, next) {
    const {pathname} = url.parse(req.url, false);
    const normalizedPath = pathname.endsWith('/') ? pathname : `${pathname}/`;

    if (normalizedPath === metricsPath) {
      if (!authenticate(req)) {
        res.set('WWW-Authenticate', 'Basic realm="Metrics"');
        return res.status(401).send('Unauthorized');
      }
      return exporter.collect().then(({resourceMetrics}) => {
        res.set('Content-Type', 'text/plain; charset=utf-8');
        res.send(serializer.serialize(resourceMetrics));
      });
    }

    // Instrument all other requests
    const startTime = Date.now();
    inFlightCounter.add(1);

    // Intercept response writes to measure actual body size
    let responseSize = 0;
    const originalWrite = res.write.bind(res);
    const originalEnd = res.end.bind(res);

    res.write = function (chunk, ...args) {
      responseSize += getChunkSize(chunk);
      return originalWrite(chunk, ...args);
    };
    res.end = function (chunk, ...args) {
      responseSize += getChunkSize(chunk);
      return originalEnd(chunk, ...args);
    };

    res.on('finish', () => {
      inFlightCounter.add(-1);

      const duration = Date.now() - startTime;
      const path = toOpenApiPath(req, res);
      const code = String(res.statusCode);
      const method = req.method;
      const labels = {method, path, code};

      // Aggregate counters
      allRequestCounter.add(1);
      if (res.statusCode >= 200 && res.statusCode < 300) {
        allSuccessCounter.add(1);
      } else if (res.statusCode >= 400 && res.statusCode < 500) {
        allClientErrorCounter.add(1);
        allErrorCounter.add(1);
      } else if (res.statusCode >= 500) {
        allServerErrorCounter.add(1);
        allErrorCounter.add(1);
      }

      // Per-route metrics
      requestTotalCounter.add(1, labels);
      durationHistogram.record(duration, labels);
      requestSizeHistogram.record(parseInt(req.headers['content-length'] ?? '0', 10) || 0, labels);
      responseSizeHistogram.record(responseSize, labels);
    });

    next();
  };
};

export {metricsHandler};
