// SPDX-License-Identifier: Apache-2.0

const FILE_PATH = './input.txt';
const BASE_URL = __ENV.BASE_URL;
const VUS_COUNT = __ENV.DEFAULT_VUS ? parseInt(__ENV.DEFAULT_VUS) : 1;

const DATA_REGEX = /(^\{.*\}$)/gm;

const parsedRequests = new SharedArray('parsed payloads', function () {
  const fileContent = open(FILE_PATH);
  const requests = [];

  let requestIndex = 0;
  let match;
  while ((match = DATA_REGEX.exec(fileContent)) !== null) {
    const rawJsonBody = match[1];

    try {
      requests.push({
        scenarioKey: String(requestIndex),
        payload: rawJsonBody,
      });
      requestIndex++;
    } catch (e) {
      console.error(`Failed to parse JSON for timestamp ${scenarioKey}: ${e.message}`);
    }
  }
  return requests;
});

const params = {
  headers: {
    'Content-Type': 'application/json',
    'Accept-Encoding': 'gzip',
  },
};

class TrafficReplayScenarioBuilder extends utils.BaseMultiScenarioBuilder {
  constructor(parsedRequests) {
    super();
    this._parsedRequests = parsedRequests;
  }

  build() {
    const that = this;

    let combinedOptions;
    for (let i = 0; i < that._parsedRequests.length; i++) {
      const data = that._parsedRequests[i];
      const scenarioName = `${that._name}_${data.scenarioKey}`;
      const options = utils.getOptionsWithScenario(scenarioName, null, {url: that._url, payload: data.payload});
      options.scenarios[scenarioName].duration = __ENV.TRAFFIC_REPLAY_DURATION ? __ENV.TRAFFIC_REPLAY_DURATION : '5s';
      if (!combinedOptions) {
        combinedOptions = options;
      } else {
        combinedOptions.scenarios[scenarioName] = options.scenarios[scenarioName];
      }
    }

    function run() {
      const active = scenario.name;
      const scenarioDef = combinedOptions.scenarios[active];
      const url = (scenarioDef && scenarioDef.tags && scenarioDef.tags.url) || '';
      const payload = (scenarioDef && scenarioDef.tags && scenarioDef.tags.payload) || '';
      const response = that._request(url, payload);
      check(response, {
        [that._checkName]: (r) => that._checkFunc(r),
      });
    }

    return {options: combinedOptions, run};
  }
}

const {options, run} = new TrafficReplayScenarioBuilder(parsedRequests)
  .name('trafficReplay')
  .url(`${__ENV.BASE_URL}/api/v1/contracts/call`)
  .request((url, payload) => http.post(url, payload, params))
  .check('Traffic replay OK', (r) => r.status === 200)
  .build();

export {options, run};
