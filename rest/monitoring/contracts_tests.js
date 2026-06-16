// SPDX-License-Identifier: Apache-2.0

import {map, max} from 'lodash-es';
import config from './config';

import {
  checkAPIResponseError,
  checkEntityId,
  checkMandatoryParams,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  testRunner,
} from './utils';

const contractsPath = '/contracts';
const resource = 'contract';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const contractCallEnabled = config[resource].call;
const contractLogsEnabled = config[resource].logs;
const jsonRespKey = 'contracts';
const jsonResultsRespKey = 'results';
const mandatoryParams = [
  'admin_key',
  'auto_renew_account',
  'contract_id',
  'created_timestamp',
  'evm_address',
  'deleted',
  'expiration_timestamp',
  'file_id',
  'memo',
  'obtainer_id',
  'permanent_removal',
  'proxy_account_id',
  'timestamp',
];
const contractResultParams = ['address', 'bloom', 'contract_id', 'from', 'gas_limit', 'hash', 'timestamp', 'to'];
const maxPages = 10;

/**
 * Verify /contracts and /contracts/{contractId} can be retrieved
 * @param {Object} server API host endpoint
 */
const getContractById = async (server) => {
  let {url, contracts, result} = await getContractsList(server);

  if (!result.passed) {
    return {url, ...result};
  }

  const contract = max(map(contracts, (contract) => contract.contract_id));
  url = getUrl(server, `${contractsPath}/${contract}`);
  const singleContract = await fetchAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts is undefined'})
    .withCheckSpec(checkEntityId, {contractId: singleContract, message: 'contract ID was not found'})
    .run(singleContract);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for single contract',
  };
};

/**
 * Verify /contracts/call can be called
 * @param {Object} server API host endpoint
 */
const postContractCall = async (server) => {
  let url = getUrl(server, `${contractsPath}/call`);
  let body = `{
      "block": "latest",
          "data": "0x2e3cff6a0000000000000000000000000000000000000000000000000000000000000064",
          "estimate": false,
          "gas": 15000000,
          "gasPrice": 100000000,
          "to": "0x0000000000000000000000000000000000000168"
    }`;
  const contractCallResponseParams = ['result'];

  const singleContract = await fetchAPIResponse(url, '', undefined, body);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts call is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractCallResponseParams,
      message: 'contract call response object is missing some mandatory fields',
    })
    .run(singleContract);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts call for single contract',
  };
};

/**
 * Verify contract results can be retrieved for a given contractId (/contracts/{contractId}/results)
 * and at a given timestamp (/contracts/{contractId}/results/{timestamp})
 * @param {Object} server API host endpoint
 */
const getContractResults = async (server) => {
  let contractId = config[resource].contractId;
  if (!contractId) {
    let {url, contractsResults, result} = await getContractsResultsList(server);

    if (!result.passed) {
      return {url, ...result};
    }
    // HAPI transactions (e.g. tokenBurn) have bloom='0x' (empty byte array) while EVM-originated
    // transactions (ContractCall/ContractCreate/EthereumTransaction) carry a 256-byte bloom filter
    let evmResult = contractsResults.find((r) => r.bloom !== '0x');
    if (!evmResult) {
      const {url: nextUrl, evmResult: found, error} = await findEvmContractResult(server);

      if (error) {
        logger.info('getContractResults: error found during fetching of contract results, skipping test');
        return {skipped: true};
      }
      if (!found) {
        logger.info('getContractResults: no EVM contract results found after paginating, skipping test');
        return {skipped: true};
      }
      evmResult = found;
    }

    contractId = evmResult.contract_id;
  }

  let url = getUrl(server, `${contractsPath}/${contractId}/results`);
  const contractResults = await fetchAPIResponse(url, jsonResultsRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract results is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contract results object is missing some mandatory fields',
    })
    .run(contractResults);
  if (!result.passed) {
    return {url, ...result};
  }

  const timestamp = max(map(contractResults, (result) => result.timestamp));
  if (timestamp === undefined) {
    logger.info('getContractResults: timestamp is not valid, skipping test');
    return {skipped: true};
  }
  url = getUrl(server, `${contractsPath}/${contractId}/results/${timestamp}`);
  const contractResultsAtTimestamp = await fetchAPIResponse(url);
  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract results at a given timestamp is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contract results object is missing some mandatory fields',
    })
    .run(contractResultsAtTimestamp);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for contract results at a given timestamp',
  };
};

/**
 * Verify contract result logs can be retrieved for list (/contracts/results/logs)
 * and a given contractId (/contracts/{contractId}/results/logs)
 * @param {Object} server API host endpoint
 */
const getContractResultsLogs = async (server) => {
  let contractId = config[resource].contractId;
  if (!contractId) {
    let {url, contractsResults, result} = await getContractsResultsList(server);

    if (!result.passed) {
      return {url, ...result};
    }

    contractId = max(map(contractsResults, (contract) => contract.contract_id));
  }

  const jsonLogsRespKey = 'logs';
  const contractLogsParams = ['address', 'bloom', 'contract_id', 'index', 'topics'];
  let url = getUrl(server, `${contractsPath}/${contractId}/results/logs`);
  let contractLogs = await fetchAPIResponse(url, jsonLogsRespKey);

  // Verify contracts logs for a particular contractId
  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract results logs is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractLogsParams,
      message: 'contract results logs object is missing some mandatory fields',
    })
    .run(contractLogs);
  if (!result.passed) {
    return {url, ...result};
  }

  // Verify contracts logs list
  url = getUrl(server, `${contractsPath}/results/logs`, {limit: resourceLimit});
  contractLogs = await fetchAPIResponse(url, jsonLogsRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract logs is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (logs, limit) => `logs.length of ${logs.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: contractLogsParams,
      message: 'contract logs object is missing some mandatory fields',
    })
    .run(contractLogs);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for contract results logs',
  };
};

/**
 * Verify contract state can be retrieved
 * @param {Object} server API host endpoint
 */
const getContractState = async (server) => {
  let contractId = config[resource].contractId;
  if (!contractId) {
    let {url, contracts, result} = await getContractsList(server);

    if (!result.passed) {
      return {url, ...result};
    }
    contractId = max(map(contracts, (contract) => contract.contract_id));
  }
  const contractStateParams = ['address', 'contract_id', 'timestamp', 'slot', 'value'];
  const jsonStateRespKey = 'state';

  let url = getUrl(server, `${contractsPath}/${contractId}/state`);
  const contractState = await fetchAPIResponse(url, jsonStateRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts state is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractStateParams,
      message: 'contract state object is missing some mandatory fields',
    })
    .run(contractState);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for contract state',
  };
};

/**
 * Verify contract result (/contracts/results/{transactionIdOrHash})
 * and actions (/contracts/results/{transactionIdOrHash}/actions) can be retrieved for a given transaction
 * @param {Object} server API host endpoint
 */
const getContractResultsByTransaction = async (server) => {
  let {url, contractsResults, result} = await getContractsResultsList(server);

  if (!result.passed) {
    return {url, ...result};
  }

  // Only ContractCall, ContractCreate, and EthereumTransaction results are indexed in
  // contract_transaction_hash and can be looked up by hash. HAPI transactions (e.g. tokenBurn)
  // appear in the /contracts/results list with a hash but return 404 at /contracts/results/{hash}.
  // EVM-originated results have carry a 256-byte bloom filter
  const evmPredicate = (r) => r.result === 'SUCCESS' && r.bloom !== '0x';
  let evmResult = contractsResults.find(evmPredicate);

  if (!evmResult) {
    const {url: nextUrl, evmResult: found, error} = await findEvmContractResult(server, evmPredicate);

    if (error) {
      logger.info('getContractResultsByTransaction: error during fetching of contract results, skipping test');
      return {skipped: true};
    }
    evmResult = found;
  }

  const transactionHash = evmResult?.hash;
  if (transactionHash === undefined) {
    logger.info('getContractResultsByTransaction: transactionHash is undefined, skipping test');
    return {skipped: true};
  }

  const contractResultParams = ['address', 'failed_initcode', 'hash', 'logs'];
  url = getUrl(server, `${contractsPath}/results/${transactionHash}`);
  const contractResults = await fetchAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract results is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contract results object is missing some mandatory fields',
    })
    .run(contractResults);
  if (!result.passed) {
    return {url, ...result};
  }

  const jsonActionsRespKey = 'actions';
  const contractActionParams = [
    'call_depth',
    'call_operation_type',
    'call_type',
    'caller',
    'caller_type',
    'from',
    'gas',
    'gas_used',
    'index',
    'recipient',
    'recipient_type',
    'result_data_type',
    'timestamp',
    'to',
    'value',
  ];
  url = getUrl(server, `${contractsPath}/results/${transactionHash}/actions`);
  const contractActions = await fetchAPIResponse(url, jsonActionsRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract actions is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractActionParams,
      message: 'contract actions object is missing some mandatory fields',
    })
    .run(contractActions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for contract actions',
  };
};

/**
 * Retrieves contract list
 * @param {Object} server API host endpoint
 */
async function getContractsList(server) {
  let url = getUrl(server, contractsPath, {limit: resourceLimit});
  const contracts = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (contracts, limit) => `contracts.length of ${contracts.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'contract object is missing some mandatory fields',
    })
    .run(contracts);
  return {url, contracts, result};
}

/**
 * Retrieves contract results list (:/contracts/results)
 * @param {Object} server API host endpoint
 */
async function getContractsResultsList(server) {
  const contractsResultsPath = '/contracts/results';
  let url = getUrl(server, contractsResultsPath, {limit: resourceLimit});
  const contractsResults = await fetchAPIResponse(url, jsonResultsRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts results list is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (contracts, limit) =>
        `contractsResults.length of ${contractsResults.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contracts results list object is missing some mandatory fields',
    })
    .run(contractsResults);

  return {url, contractsResults, result};
}

/**
 * Paginates through /contracts/results until a result matching the predicate is found
 * or all pages have been exhausted.
 * @param {string} server API host endpoint
 * @param {Function} predicate Predicate to match the desired result
 * @returns {{url: string, evmResult: Object|null, error: Error|null}}
 */
async function findEvmContractResult(server, predicate = (r) => r.bloom !== '0x') {
  let url = getUrl(server, '/contracts/results', {limit: resourceLimit});

  for (let page = 1; page <= maxPages; page++) {
    const response = await fetchAPIResponse(url);
    if (response instanceof Error) {
      return {url, evmResult: null, error: response};
    }

    const results = response[jsonResultsRespKey];
    if (Array.isArray(results)) {
      const evmResult = results.find(predicate);
      if (evmResult) {
        return {url, evmResult, error: null};
      }
    }

    const next = response.links?.next;
    if (!next) {
      break;
    }

    logger.info(`No matching EVM contract result on page ${page}, fetching next page`);
    url = `${server}${next}`;
  }

  return {url, evmResult: null, error: null};
}

/**
 * Run all contract tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getContractById),
    contractCallEnabled ? runTest(postContractCall, 'WEB3') : '',
    runTest(getContractResults),
    contractLogsEnabled ? runTest(getContractResultsLogs) : '',
    runTest(getContractState),
    runTest(getContractResultsByTransaction),
  ]);
};

export default {
  resource,
  runTests,
};
