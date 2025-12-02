// SPDX-License-Identifier: Apache-2.0

import {check} from 'k6';
import {scenario as k6Scenario} from 'k6/execution';
import {Gauge} from 'k6/metrics';
import './parameters.js';

const SCENARIO_DURATION_METRIC_NAME = 'scenario_duration';

const options = {
  thresholds: {
    checks: ['rate>=0'], // make it not fail
    http_req_duration: ['p(95)>=0'], // make it not fail
  },
  insecureSkipTLSVerify: true,
  noConnectionReuse: true,
  noVUConnectionReuse: true,
  setupTimeout: __ENV.DEFAULT_SETUP_TIMEOUT,
};

const scenarioCommon = {
  exec: 'run',
  gracefulStop: __ENV.DEFAULT_GRACEFUL_STOP,
};

const scenarioDefaults = Object.assign({}, scenarioCommon, {
  duration: __ENV.DEFAULT_DURATION,
  executor: 'constant-vus',
  vus: __ENV.DEFAULT_VUS,
});

function getMetricNameWithTags(name, ...tags) {
  return tags.length === 0 ? name : `${name}{${tags}}`;
}

const timeRegex = /^[0-9]+s$/;

function validateTime(name, value) {
  if (!timeRegex.test(value)) {
    throw new Error(`Invalid ${name} ${value}`);
  }
}

function getNextStartTime(startTime, duration, gracefulStop) {
  validateTime('startTime', startTime);
  validateTime('duration', duration);
  validateTime('gracefulStop', gracefulStop);

  return `${parseInt(startTime) + parseInt(duration) + parseInt(gracefulStop)}s`;
}

function getOptionsWithScenario(name, scenario, tags = {}) {
  const sourceScenario = scenario ? Object.assign({}, scenario, scenarioCommon) : scenarioDefaults;
  return Object.assign({}, options, {
    scenarios: {
      [name]: Object.assign({}, sourceScenario, {tags}),
    },
  });
}

function getScenarioTimes(scenario) {
  const executor = scenario.executor;
  if (executor === 'constant-vus') {
    return {
      duration: scenario.duration,
      gracefulStop: scenario.gracefulStop,
      startTime: scenario.startTime,
    };
  } else if (executor === 'ramping-vus') {
    validateTime('gracefulRampDown', scenario.gracefulRampDown);
    let duration = parseInt(scenario.gracefulRampDown);
    for (const stage of scenario.stages) {
      const stageDuration = stage.duration;
      validateTime('stage duration', stageDuration);
      duration += parseInt(stageDuration);
    }

    return {
      duration: `${duration}s`,
      gracefulStop: scenario.gracefulStop,
      startTime: scenario.startTime,
    };
  } else {
    throw new Error(`Unsupported k6 executor ${executor}`);
  }
}

const defaultFilters = {
  true: {test: () => false}, // default exclude filter, exclude nothing
  false: {test: () => true}, // default include filter, include everything
};

function getFilter(suite, exclude) {
  const key = `${suite.toUpperCase()}_TEST_${exclude ? 'EXCLUDE' : 'INCLUDE'}`;
  const value = __ENV[key];
  return value ? new RegExp(value, 'i') : defaultFilters[exclude];
}

function filterTests(tests, suite) {
  if (suite === null) {
    return tests;
  }

  const exclude = getFilter(suite, true);
  const include = getFilter(suite, false);
  const filtered = Object.keys(tests).filter((name) => {
    return !exclude.test(name) && (name === 'rampUp' || include.test(name));
  });
  return Object.fromEntries(filtered.map((name) => [name, tests[name]]));
}

function getSequentialTestScenarios(tests, suite = null) {
  const filteredTests = filterTests(tests, suite);

  let startTime = '0s';
  let duration = '0s';
  let gracefulStop = '0s';

  const funcs = {};
  const getUrlFuncs = {};
  const requiredParameters = new Set();
  const scenarios = {};
  const thresholds = {};

  const constantVusTests = [];
  const rampingVusTests = [];

  for (const [name, test] of Object.entries(filteredTests)) {
    const executor = Object.values(test.options.scenarios)[0].executor;
    if (executor === 'constant-vus') {
      constantVusTests.push(name);
      constantVusTests[name] = test;
    } else if (executor === 'ramping-vus') {
      rampingVusTests.push(name);
    } else {
      throw new Error(`Unsupported k6 executor ${executor} in test ${name}`);
    }
  }

  constantVusTests.sort();
  rampingVusTests.sort();

  for (const testName of rampingVusTests.concat(constantVusTests)) {
    const testModule = filteredTests[testName];
    const testScenarios = testModule.options.scenarios;
    const testThresholds = testModule.options.thresholds;
    for (const [scenarioName, testScenario] of Object.entries(testScenarios)) {
      const scenario = Object.assign({}, testScenario);
      const func = testModule[scenario.exec];
      funcs[scenarioName] = func;
      if (func && func.requiredParameters) {
        func.requiredParameters.forEach((param) => requiredParameters.add(param));
      }

      if (testModule.getUrl) {
        getUrlFuncs[scenarioName] = testModule.getUrl;
      }

      scenarios[scenarioName] = scenario;

      // update the scenario's startTime, so scenarios run in sequence
      scenario.startTime = getNextStartTime(startTime, duration, gracefulStop);

      const times = getScenarioTimes(scenario);
      duration = times.duration;
      gracefulStop = times.gracefulStop;
      startTime = times.startTime;

      // thresholds
      const tag = `scenario:${scenarioName}`;
      for (const [name, threshold] of Object.entries(testThresholds)) {
        if (name === 'http_req_duration') {
          thresholds[getMetricNameWithTags(name, tag, 'expected_response:true')] = threshold;
        } else {
          thresholds[getMetricNameWithTags(name, tag)] = threshold;
        }
      }
      thresholds[getMetricNameWithTags('http_reqs', tag)] = ['count>0'];
      thresholds[getMetricNameWithTags(SCENARIO_DURATION_METRIC_NAME, tag)] = ['value>0'];
    }
  }

  const testOptions = Object.assign({}, options, {scenarios, thresholds});

  return {
    funcs,
    getUrlFuncs,
    options: testOptions,
    requiredParameters: [...requiredParameters.values()],
    scenarioDurationGauge: new Gauge(SCENARIO_DURATION_METRIC_NAME),
    scenarios,
  };
}

const checksRegex = /^checks{.*scenario:.*}$/;
const httpReqDurationRegex = /^http_req_duration{.*scenario:.*}$/;
const httpReqsRegex = /^http_reqs{.*scenario:.*}$/;
const scenarioDurationRegex = /^scenario_duration{.*scenario:.*}$/;
const scenarioRegex = /scenario:([^,}]+)/;

function getScenario(metricKey) {
  const match = scenarioRegex.exec(metricKey);
  return match[1];
}

function getTestReportFilename() {
  return `${__ENV.TEST_REPORTS_DIR || '.'}/report.md`;
}

function defaultMetrics() {
  return {
    checks: {
      values: {
        rate: 0,
      },
    },
    http_req_duration: {
      values: {
        avg: 0,
      },
    },
    http_reqs: {
      values: {
        count: 0,
      },
    },
    scenario_duration: {
      values: {
        value: 0,
      },
    },
  };
}

function satisfyParameters(available, required) {
  return required.length === 0 || required.every((param) => available.hasOwnProperty(param) && available[param]);
}

function markdownReport(data, includeUrlColumn, funcs, scenarios, getUrlFuncs = {}) {
  const header = `| Scenario ${
    includeUrlColumn ? '| URL' : ''
  } | VUS | Pass% | RPS | Pass RPS | Avg. Req Duration | Skipped? | Comment |
|----------${
    includeUrlColumn ? '|----------' : ''
  }|-----|-------|-----|----------|-------------------|--------|---------|`;

  // collect the metrics
  const {setup_data: availableParams} = data;
  const {metrics} = data;
  const scenarioMetrics = {};

  for (const [key, value] of Object.entries(metrics)) {
    let name;
    if (checksRegex.test(key)) {
      name = 'checks';
    } else if (httpReqDurationRegex.test(key)) {
      name = 'http_req_duration';
    } else if (httpReqsRegex.test(key)) {
      name = 'http_reqs';
    } else if (scenarioDurationRegex.test(key)) {
      name = 'scenario_duration';
    } else {
      continue;
    }

    const scenario = getScenario(key);
    const existingMetrics = scenarioMetrics[scenario] || defaultMetrics();
    scenarioMetrics[scenario] = Object.assign(existingMetrics, {[name]: value});
  }

  const scenarioUrls = {};
  if (includeUrlColumn) {
    for (const [name, scenario] of Object.entries(scenarios)) {
      const getUrl = getUrlFuncs[name];
      scenarioUrls[name] = getUrl ? getUrl(availableParams) : scenario.tags.url;
    }
  }

  // Generate the markdown report
  let markdown = `${header}\n`;
  for (const scenario of Object.keys(scenarioMetrics).sort()) {
    try {
      const scenarioMetric = scenarioMetrics[scenario];
      const passPercentage = (scenarioMetric['checks'].values.rate * 100.0).toFixed(2);
      const httpReqs = scenarioMetric['http_reqs'].values.count;
      const duration = scenarioMetric['scenario_duration'].values.value; // in ms
      const rps = (((httpReqs * 1.0) / duration) * 1000).toFixed(2);
      const passRps = ((rps * passPercentage) / 100.0).toFixed(2);
      const httpReqDuration = scenarioMetric['http_req_duration'].values.avg.toFixed(2);
      const skipped = satisfyParameters(availableParams, funcs[scenario].requiredParameters || []) ? 'No' : 'Yes';

      markdown += `| ${scenario} ${includeUrlColumn ? '| ' + scenarioUrls[scenario] : ''} | ${
        __ENV.DEFAULT_VUS
      } | ${passPercentage} | ${rps}/s | ${passRps}/s | ${httpReqDuration}ms | ${skipped} | |\n`;
    } catch (err) {
      console.error(`Unable to render report for scenario ${scenario}`);
    }
  }

  return markdown;
}

function sanitizeScenarioName(name) {
  // k6 requires [0-9A-Za-z_-]
  return name.replace(/[^0-9A-Za-z_-]/g, '_');
}

class TestScenarioBuilder {
  constructor() {
    this._checks = {};
    this._fallbackChecks = {'fallback is 200': (r) => r.status === 200};
    this._fallbackRequest = null;
    this._name = null;
    this._request = null;
    this._requiredParameters = [];
    this._scenario = null;
    this._shouldSkip = null;
    this._tags = {};

    this.build = this.build.bind(this);
    this.check = this.check.bind(this);
    this.fallbackRequest = this.fallbackRequest.bind(this);
    this.name = this.name.bind(this);
    this.request = this.request.bind(this);
    this.requiredParameters = this.requiredParameters.bind(this);
    this.scenario = this.scenario.bind(this);
    this.tags = this.tags.bind(this);
  }

  build() {
    const that = this;
    const run = function (testParameters) {
      if (that._shouldSkip == null) {
        that._shouldSkip = !satisfyParameters(testParameters, that._requiredParameters);
        if (that._shouldSkip) {
          console.warn(`Skipping test scenario ${that._name}`);
        }
      }

      if (!that._shouldSkip) {
        const response = that._request(testParameters);
        check(response, that._checks);
      } else {
        // fallback
        const response = that._fallbackRequest(testParameters);
        check(response, that._fallbackChecks);
      }
    };
    run.requiredParameters = this._requiredParameters;

    return {
      options: getOptionsWithScenario(that._name, that._scenario, that._tags),
      run,
    };
  }

  check(name, func) {
    this._checks[name] = func;
    return this;
  }

  fallbackRequest(func) {
    this._fallbackRequest = func;
    return this;
  }

  name(name) {
    this._name = name;
    return this;
  }

  request(func) {
    this._request = func;
    return this;
  }

  requiredParameters(...requiredParameters) {
    this._requiredParameters = requiredParameters;
    return this;
  }

  scenario(scenario) {
    this._scenario = scenario;
    return this;
  }

  tags(tags) {
    this._tags = tags;
    return this;
  }
}

class MultiIdScenarioBuilder {
  constructor(ids) {
    this._ids = ids;
    this._name = null;
    this._request = null;
    this._checkName = null;
    this._checkFunc = null;
    this._url = null;
  }

  name(name) {
    this._name = name;
    return this;
  }

  request(func) {
    this._request = func;
    return this;
  }

  url(url) {
    this._url = url;
    return this;
  }

  check(name, func) {
    this._checkName = name;
    this._checkFunc = func;
    return this;
  }

  build() {
    const that = this;

    let combinedOptions;
    for (let i = 0; i < that._ids.length; i++) {
      const id = that._ids[i];
      const sanitized = sanitizeScenarioName(id);
      const scenarioName = `${that._name}-${sanitized}`;
      const url = that._url.replace('{id}', id);
      const options = getOptionsWithScenario(scenarioName, null, {url});
      if (!combinedOptions) {
        combinedOptions = options;
      } else {
        combinedOptions.scenarios[scenarioName] = options.scenarios[scenarioName];
      }
    }

    function run() {
      const active = k6Scenario.name;
      const scenarioDef = combinedOptions.scenarios[active];
      const url = (scenarioDef && scenarioDef.tags && scenarioDef.tags.url) || '';
      const response = that._request(url);
      check(response, {
        [that._checkName]: (r) => that._checkFunc(r),
      });
    }

    return {options: combinedOptions, run};
  }
}

export {
  getOptionsWithScenario,
  getSequentialTestScenarios,
  getTestReportFilename,
  markdownReport,
  TestScenarioBuilder,
  MultiIdScenarioBuilder,
};
