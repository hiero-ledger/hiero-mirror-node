// SPDX-License-Identifier: Apache-2.0

import config from './config';

import {
  checkAPIResponseError,
  checkElementsOrder,
  checkRespObjDefined,
  checkRespArrayLength,
  checkMandatoryParams,
  checkResourceFreshness,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} from './utils';

const resource = 'topic';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const topicId = config[resource].topicId;
const jsonRespKey = 'messages';
const mandatoryParams = [
  'consensus_timestamp',
  'topic_id',
  'message',
  'running_hash',
  'running_hash_version',
  'sequence_number',
];

const topicMessagesPath = (id, sequence = undefined) => {
  const path = `/topics/${id}/messages`;
  if (sequence === undefined) {
    return path;
  }

  return `${path}/${sequence}`;
};

const checkSequenceNumberOrder = (messages, option) => {
  const errorMessage = (asc, previous, current) => {
    const order = asc ? 'ascending' : 'descending';
    return `topic message sequence is not in ${order} order / contiguous: previous - ${previous}, current - ${current}`;
  };

  const {asc} = option;
  let previous = 0;
  for (const message of messages) {
    const sequence = message.sequence_number;
    if (previous !== 0) {
      if ((asc && sequence !== previous + 1) || (!asc && sequence >= previous + 1)) {
        return {passed: false, message: errorMessage(asc, previous, sequence)};
      }
    }

    previous = sequence;
  }

  return {passed: true};
};

const checkSingleField = (elements, option) => {
  const {expected, getActual, message} = option;
  const element = Array.isArray(elements) ? elements[0] : elements;
  const actual = getActual(element);
  if (actual !== expected) {
    return {
      passed: false,
      message: message(actual, expected),
    };
  }

  return {passed: true};
};

/**
 * Verifies /topics/:topicId
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicById = async (server) => {
  let url = getUrl(server, `/topics/${topicId}`);
  let topic = await fetchAPIResponse(url);
  const requiredParams = [
    'admin_key',
    'auto_renew_account',
    'auto_renew_period',
    'created_timestamp',
    'deleted',
    'memo',
    'submit_key',
    'timestamp',
    'topic_id',
  ];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'topic is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: requiredParams,
      message: 'topic object is missing some mandatory fields',
    })
    .run(topic);

  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics for ${topicId}`,
  };
};

/**
 * Verifies /topics/:topicId/messages
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessages = async (server) => {
  let url = getUrl(server, topicMessagesPath(topicId), {limit: resourceLimit});
  let messages = await fetchAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `messages.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .withCheckSpec(checkElementsOrder, {asc: true, key: 'consensus_timestamp', name: 'consensus timestamp'})
    .withCheckSpec(checkSequenceNumberOrder, {asc: true});
  let result = checkRunner.run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, topicMessagesPath(topicId), {limit: resourceLimit, order: 'desc'});
  messages = await fetchAPIResponse(url, jsonRespKey);

  result = checkRunner
    .resetCheckSpec(checkElementsOrder, {asc: false, key: 'consensus_timestamp', name: 'consensus timestamp'})
    .resetCheckSpec(checkSequenceNumberOrder, {asc: false})
    .run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics for ${topicId} and performed order check`,
  };
};

/**
 * Verifies /topics/:topicId/messages?sequencenumber
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessagesBySequenceNumberFilter = async (server) => {
  let url = getUrl(server, topicMessagesPath(topicId), {sequencenumber: 1});
  let messages = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `messages.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .withCheckSpec(checkSingleField, {
      expected: 1,
      getActual: (element) => element.sequence_number,
      message: (actual, expected) => `sequence number ${actual} was expected to be ${expected}`,
    })
    .run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, topicMessagesPath(topicId), {sequencenumber: ['gte:1', 'lte:3']});
  messages = await fetchAPIResponse(url, jsonRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 3,
      message: (elements) => `messages.length of ${elements.length} was expected to be 3`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, topicMessagesPath(topicId), {sequencenumber: ['gte:3', 'lt:3']});
  messages = await fetchAPIResponse(url, jsonRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 0,
      message: (elements) => `messages.length of ${elements.length} was expected to be 0`,
    })
    .run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called topics with sequencenumber filter',
  };
};

/**
 * Verifies /topics/:topicId/messages/:sequencenumber
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessagesByTopicIDAndSequenceNumberPath = async (server) => {
  const url = getUrl(server, topicMessagesPath(topicId, 1));
  const message = await fetchAPIResponse(url);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .run(message);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics for ${topicId} and message sequence number 1`,
  };
};

/**
 * Verifies /topics/messages/:consensusTimestamp
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessagesByConsensusTimestamp = async (server) => {
  let url = getUrl(server, topicMessagesPath(topicId), {limit: resourceLimit});
  const messages = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `messages.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  const consensusTimestamp = messages[0].consensus_timestamp;
  url = getUrl(server, `/topics/messages/${consensusTimestamp}`);
  const message = await fetchAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'message is undefined'})
    .withCheckSpec(checkSingleField, {
      expected: consensusTimestamp,
      getActual: (element) => element.consensus_timestamp,
      message: (actual, expected) => `consensus timestamp ${actual} was expected to be ${expected}`,
    })
    .run(message);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics with consensus timestamp`,
  };
};

/**
 * Verifies the freshness of the latest topic message returned by the api
 *
 * @param {String} server API host endpoint
 */
const checkTopicMessageFreshness = async (server) => {
  return checkResourceFreshness(
    server,
    topicMessagesPath(topicId),
    resource,
    (message) => message.consensus_timestamp,
    jsonRespKey
  );
};

/**
 * Run all topic message tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  if (!topicId) {
    return Promise.resolve();
  }

  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getTopicById, 'REST_JAVA'),
    runTest(getTopicMessages),
    runTest(getTopicMessagesBySequenceNumberFilter),
    runTest(getTopicMessagesByTopicIDAndSequenceNumberPath),
    runTest(getTopicMessagesByConsensusTimestamp),
    runTest(checkTopicMessageFreshness),
  ]);
};

export default {
  resource,
  runTests,
};
