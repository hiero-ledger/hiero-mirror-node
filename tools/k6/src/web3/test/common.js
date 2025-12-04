// SPDX-License-Identifier: Apache-2.0

import {check, sleep} from 'k6';
import {vu, scenario as k6Scenario} from 'k6/execution';
import http from 'k6/http';
import {SharedArray} from 'k6/data';
import {sanitizeScenarioName} from '../../lib/common.js';

import * as utils from '../../lib/common.js';

const defaultVuData = {
  block: '',
  data: '',
  to: '',
  gas: 0,
  from: '',
  value: 0,
  sleep: 0,
};
const resultField = 'result';

function isNonErrorResponse(response) {
  //instead of doing multiple type checks,
  //lets just do the normal path and return false,
  //if an exception happens.
  try {
    if (response.status !== 200) {
      return false;
    }
    const body = JSON.parse(response.body);
    return body.hasOwnProperty(resultField);
  } catch (e) {
    return false;
  }
}

const isValidListResponse = (response, listName, minEntryCount) => {
  if (!isSuccess(response)) {
    return false;
  }

  const body = JSON.parse(response.body);
  const list = body[listName];
  if (!Array.isArray(list)) {
    return false;
  }

  return list.length > minEntryCount;
};

const isSuccess = (response) => response.status >= 200 && response.status < 300;

const jsonPost = (url, payload) =>
  http.post(url, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
  });

const loadVuDataOrDefault = (filepath, key) =>
  new SharedArray(key, () => {
    const data = JSON.parse(open(filepath));
    return key in data ? data[key] : [];
  });

const HISTORICAL_BLOCK_NUMBER = __ENV.HISTORICAL_BLOCK_NUMBER || 'latest';

function extractBlockFromScenarioName(name) {
  if (typeof name !== 'string') {
    return null;
  }
  const idx = name.lastIndexOf('-');
  if (idx === -1 || idx === name.length - 1) {
    return null;
  }
  return name.substring(idx + 1);
}

function toHexBlockNumber(value) {
  const v = (value ?? '').toString().trim();
  if (v === 'latest' || v === 'pending' || v === 'safe' || v === 'finalized') {
    return 'latest';
  } else if (/^0x[0-9a-fA-F]+$/.test(v) || /^\d+$/.test(v)) {
    return v;
  } else {
    return 'earliest';
  }
}

function getMixedBlocks() {
  const historical = toHexBlockNumber(HISTORICAL_BLOCK_NUMBER);
  if (historical === 'latest') {
    return ['latest'];
  } else {
    return ['latest', historical];
  }
}

function ContractCallTestScenarioBuilder() {
  this._args = null;
  this._name = null;
  this._selector = null;
  this._scenario = null;
  this._tags = {};
  this._to = null;
  this._vuData = null;
  this._shouldRevert = false;
  this._blocks = null;
  this._data = null;
  this._estimate = null;
  this._from = null;
  this._gas = 15000000;
  this._value = null;

  this._url = `${__ENV.BASE_URL_PREFIX}/contracts/call`;

  this.build = function () {
    const that = this;

    if (!that._blocks || that._blocks.length === 0) {
      that._blocks = [`latest`];
    }

    // Create separate scenarios per provided block
    let combinedOptions = null;
    for (let i = 0; i < that._blocks.length; i++) {
      const block = that._blocks[i];
      const sanitized = sanitizeScenarioName(String(block));
      const scenarioName = `${that._name}-${sanitized}`;
      const options = utils.getOptionsWithScenario(scenarioName, that._scenario);

      if (!combinedOptions) {
        combinedOptions = options;
      } else {
        combinedOptions.scenarios[scenarioName] = options.scenarios[scenarioName];
      }
    }

    const run = function () {
      const activeBlock = extractBlockFromScenarioName(k6Scenario.name);

      let sleepSecs = 0;
      const payload = {
        to: that._to,
        estimate: that._estimate || false,
        value: that._value,
        from: that._from,
        block: activeBlock,
      };

      if (that._selector && that._args) {
        payload.data = that._selector + that._args;
      } else {
        const {_vuData: vuData} = that;
        const data = vuData
          ? Object.assign({}, defaultVuData, vuData[vu.idInTest % vuData.length])
          : {
              block: activeBlock,
              data: that._data,
              gas: that._gas,
              from: that._from,
              value: that._value,
            };
        sleepSecs = data.sleep;
        delete data.sleep;

        Object.assign(payload, data);
      }

      const response = jsonPost(that._url, JSON.stringify(payload));
      check(response, {
        [`${k6Scenario.name}`]: (r) => (that._shouldRevert ? !isNonErrorResponse(r) : isNonErrorResponse(r)),
      });

      if (sleepSecs > 0) {
        sleep(sleepSecs);
      }
    };

    return {options: combinedOptions, run};
  };

  // Common methods
  this.name = function (name) {
    this._name = name;
    return this;
  };

  this.to = function (to) {
    this._to = to;
    return this;
  };

  this.scenario = function (scenario) {
    this._scenario = scenario;
    return this;
  };

  this.tags = function (tags) {
    this._tags = tags;
    return this;
  };

  // Methods specific to eth_call
  this.selector = function (selector) {
    this._selector = selector;
    return this;
  };

  this.args = function (args) {
    this._args = args.join('');
    return this;
  };

  this.blocks = function (blocks) {
    this._blocks = Array.isArray(blocks) ? blocks : [blocks];
    return this;
  };

  this.data = function (data) {
    this._data = data;
    return this;
  };

  this.gas = function (gas) {
    this._gas = gas;
    return this;
  };

  this.from = function (from) {
    this._from = from;
    return this;
  };

  this.value = function (value) {
    this._value = value;
    return this;
  };

  this.estimate = function (estimate) {
    this._estimate = estimate;
    return this;
  };

  this.vuData = function (vuData) {
    this._vuData = vuData;
    return this;
  };

  this.shouldRevert = function (shouldRevert) {
    this._shouldRevert = shouldRevert;
    return this;
  };

  return this;
}

export {
  isNonErrorResponse,
  isValidListResponse,
  jsonPost,
  loadVuDataOrDefault,
  ContractCallTestScenarioBuilder,
  getMixedBlocks,
};
