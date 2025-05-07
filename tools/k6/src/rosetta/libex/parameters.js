// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {computeProperties, getValidResponse} from '../../lib/parameters.js';
import {currencyHbar} from './constants.js';

const baseUrl = __ENV['BASE_URL'];

const getNetworkIdentifier = (network) => ({
  blockchain: 'Hedera',
  network,
});

const computeBlockFromNetwork = (networkIdentifier) =>
  computeProperties(['DEFAULT_BLOCK_INDEX', 'DEFAULT_BLOCK_HASH'], () => {
    const requestUrl = `${baseUrl}/rosetta/network/status`;
    const requestBody = {network_identifier: networkIdentifier};
    const response = getValidResponse(requestUrl, requestBody, http.post);
    return {
      DEFAULT_BLOCK_INDEX: parseInt(response.current_block_identifier.index),
      DEFAULT_BLOCK_HASH: response.current_block_identifier.hash,
    };
  });

const computeNetworkInfo = () =>
  computeProperties(['DEFAULT_NETWORK'], () => {
    const requestUrl = `${baseUrl}/rosetta/network/list`;
    const response = getValidResponse(requestUrl, {metadata: {}}, http.post);
    const networks = response.network_identifiers;
    if (networks.length === 0) {
      throw new Error('It was not possible to find a network');
    }
    return {DEFAULT_NETWORK: networks[0].network};
  });

const computeTransactionAndAccountFromBlock = (networkIdentifier, blockIdentifier) =>
  computeProperties(['DEFAULT_TRANSACTION_HASH', 'DEFAULT_ACCOUNT_ID'], () => {
    const requestUrl = `${baseUrl}/rosetta/block`;
    const requestBody = {
      network_identifier: networkIdentifier,
      block_identifier: blockIdentifier,
    };
    const response = getValidResponse(requestUrl, requestBody, http.post);
    const transactions = response.block.transactions;
    if (transactions.length === 0) {
      throw new Error(`It was not possible to find a transaction in block: ${JSON.stringify(blockIdentifier)}`);
    }

    let accountId = null;
    for (const transaction of transactions) {
      if (transaction.operations.length !== 0) {
        accountId = transaction.operations[0].account.address;
      }
    }

    if (accountId == null) {
      throw new Error(`It was not possible to find an account in block: ${JSON.stringify(blockIdentifier)}`);
    }

    return {
      DEFAULT_TRANSACTION_HASH: transactions[0].transaction_identifier.hash,
      DEFAULT_ACCOUNT_ID: accountId,
    };
  });

const setupTestParameters = () => {
  const networkIdentifier = getNetworkIdentifier(computeNetworkInfo(baseUrl).DEFAULT_NETWORK);
  const blockInfo = computeBlockFromNetwork(networkIdentifier);
  const blockIdentifier = {
    index: blockInfo.DEFAULT_BLOCK_INDEX,
    hash: blockInfo.DEFAULT_BLOCK_HASH,
  };
  const {DEFAULT_TRANSACTION_HASH, DEFAULT_ACCOUNT_ID} = computeTransactionAndAccountFromBlock(
    networkIdentifier,
    blockIdentifier
  );
  const accountIdentifier = {address: DEFAULT_ACCOUNT_ID};

  return {
    baseUrl,
    accountIdentifier,
    blockIdentifier,
    networkIdentifier,
    operations: [
      {
        operation_identifier: {index: 0},
        type: 'CRYPTOTRANSFER',
        account: accountIdentifier,
        amount: {
          value: '-100',
          currency: currencyHbar,
        },
      },
      {
        operation_identifier: {index: 1},
        type: 'CRYPTOTRANSFER',
        account: accountIdentifier,
        amount: {
          value: '100',
          currency: currencyHbar,
        },
      },
    ],
    publicKey: {
      hex_bytes:
        __ENV['ROSETTA_ACCOUNT_PUBLIC_KEY'] || 'eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba77',
      curve_type: 'edwards25519',
    },
    signatureType: 'ed25519',
    signingTransaction:
      __ENV['ROSETTA_SIGNING_PAYLOAD'] ||
      '967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507',
    signedTransaction:
      __ENV['ROSETTA_SIGNED_TRANSACTION'] ||
      '0x0aaa012aa7010a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f12660a640a20eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba771a40793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108',
    transactionIdentifier: {
      hash: DEFAULT_TRANSACTION_HASH,
    },
    transactionSignature:
      __ENV['ROSETTA_TRANSACTION_SIGNATURE'] ||
      '793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108',
    unsignedTransaction:
      __ENV['ROSETTA_UNSIGNED_TRANSACTION'] ||
      '0a432a410a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f1200',
  };
};

export {setupTestParameters};
