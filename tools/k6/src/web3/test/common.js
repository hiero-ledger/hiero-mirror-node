// SPDX-License-Identifier: Apache-2.0

import {check, sleep} from 'k6';
import {vu, scenario as k6Scenario} from 'k6/execution';
import http from 'k6/http';
import {SharedArray} from 'k6/data';

import * as utils from '../../lib/common.js';

const defaultVuData = {
  blocks: ['latest'],
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

function sanitizeScenarioName(name) {
  // k6 requires [0-9A-Za-z_-]
  return name.replace(/[^0-9A-Za-z_-]/g, '_');
}

const HISTORICAL_BLOCK_NUMBER = __ENV.HISTORICAL_BLOCK_NUMBER || 'earliest';

function toHexBlockNumber(value) {
  const v = (value ?? '').toString().trim();
  if (v === '' || v === 'earliest' || v === 'latest' || v === 'pending' || /^0x[0-9a-fA-F]+$/.test(v)) {
    return v === '' ? 'earliest' : v;
  }
  // If purely decimal digits, convert to 0x-prefixed hex
  if (/^\d+$/.test(v)) {
    const n = parseInt(v, 10);
    if (!Number.isNaN(n)) {
      return '0x' + n.toString(16);
    }
  }

  return 'earliest';
}

function getMixedBlocks() {
  const historical = toHexBlockNumber(HISTORICAL_BLOCK_NUMBER);
  if (historical === 'latest' || historical === '') {
    return ['latest'];
  }
  return ['latest', historical];
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

  this._blocks = ['latest'];
  this._data = null;
  this._estimate = null;
  this._from = null;
  this._gas = 15000000;
  this._value = null;

  this._url = `${__ENV.BASE_URL_PREFIX}/contracts/call`;

  this.build = function () {
    const that = this;

    // Ensure we always operate in multi-block mode: if not provided, default to mixed blocks
    if (!that._blocks || that._blocks.length === 0) {
      that._blocks = getMixedBlocks();
    }

    // Create separate scenarios per provided block
    let combinedOptions = null;
    for (let i = 0; i < that._blocks.length; i++) {
      const block = that._blocks[i];
      const sanitized = sanitizeScenarioName(String(block));
      const scenarioName = `${that._name}-${sanitized}`;
      const tags = Object.assign({}, that._tags, {test: that._name, block});
      const options = utils.getOptionsWithScenario(scenarioName, that._scenario, tags);
      if (!combinedOptions) {
        combinedOptions = options;
      } else {
        combinedOptions.scenarios[scenarioName] = options.scenarios[scenarioName];
      }
    }

    const run = function () {
      const active = k6Scenario.name;
      const scenarioDef = combinedOptions.scenarios[active] || {};
      const scenarioTags = scenarioDef.tags || {};
      const activeBlock = scenarioTags.block || 'latest';

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
        payload.block = activeBlock;
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
