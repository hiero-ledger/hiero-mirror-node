// SPDX-License-Identifier: Apache-2.0

import {setupIntegrationTest} from './integrationUtils';
import integrationDomainOps from './integrationDomainOps';
import * as utils from '../utils';
import request from 'supertest';
import server from '../server';
import * as constants from '../constants';

setupIntegrationTest();

describe('Accounts deduplicate timestamp tests', () => {
  const nanoSecondsPerSecond = 10n ** 9n;
  const tenDaysInNs = constants.ONE_DAY_IN_NS * 10n;
  const currentNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;
  const beginningOfCurrentMonth = utils.getFirstDayOfMonth(currentNs);
  const beginningOfPreviousMonth = utils.getFirstDayOfMonth(beginningOfCurrentMonth - 1n);

  const balanceTimestamp1 = beginningOfPreviousMonth + tenDaysInNs;
  const timestampRange1 = balanceTimestamp1 + 7n;
  const timestampRange2 = timestampRange1 - nanoSecondsPerSecond;
  const createdTimestamp1 = balanceTimestamp1 - nanoSecondsPerSecond;
  const consensusTimestamp1 = createdTimestamp1 - 1n;
  const consensusTimestamp2 = balanceTimestamp1 + nanoSecondsPerSecond;

  beforeEach(async () => {
    await integrationDomainOps.loadAccounts([
      {
        num: 3,
      },
      {
        num: 7,
      },
      {
        balance: 80,
        balance_timestamp: balanceTimestamp1,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${timestampRange1},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        balance: 30,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${timestampRange2 - 1n}, ${timestampRange2})`,
        staked_node_id: 2,
        staked_account_id: 2,
      },
      {
        num: 9,
      },
      {
        num: 98,
      },
    ]);
    await integrationDomainOps.loadBalances([
      {
        timestamp: balanceTimestamp1,
        id: 2,
        balance: 2,
      },
      {
        timestamp: balanceTimestamp1,
        id: 7,
        balance: 80,
        tokens: [
          {
            token_num: 99998,
            balance: 7,
          },
          {
            token_num: 99999,
            balance: 77,
          },
        ],
      },
      {
        timestamp: balanceTimestamp1,
        id: 8,
        balance: 80,
        tokens: [
          {
            token_num: 99998,
            balance: 98,
          },
          {
            token_num: 99999,
            balance: 88,
          },
        ],
      },
      {
        timestamp: beginningOfPreviousMonth,
        id: 2,
        balance: 2,
      },
      {
        timestamp: beginningOfPreviousMonth,
        id: 9,
        balance: 999,
      },
    ]);

    await integrationDomainOps.loadTokenAccounts([
      {
        token_id: '0.0.99998',
        account_id: '0.0.7',
        balance: 7,
        created_timestamp: createdTimestamp1,
      },
      {
        token_id: '0.0.99999',
        account_id: '0.0.7',
        balance: 77,
        created_timestamp: '2200',
      },
      {
        token_id: '0.0.99998',
        account_id: '0.0.8',
        balance: 8,
        created_timestamp: createdTimestamp1,
      },
      {
        token_id: '0.0.99999',
        account_id: '0.0.8',
        balance: 88,
        created_timestamp: createdTimestamp1,
      },
    ]);

    await integrationDomainOps.loadTransactions([
      {
        payerAccountId: '0.0.9',
        nodeAccountId: '0.0.3',
        consensus_timestamp: beginningOfPreviousMonth,
        name: 'TOKENCREATION',
        type: '29',
        entity_id: '0.0.90000',
      },
      {
        payerAccountId: '0.0.9',
        nodeAccountId: '0.0.3',
        consensus_timestamp: consensusTimestamp1,
        name: 'CRYPTODELETE',
        type: '12',
        entity_id: '0.0.7',
      },
      {
        charged_tx_fee: 0,
        payerAccountId: '0.0.9',
        nodeAccountId: '0.0.3',
        consensus_timestamp: consensusTimestamp2,
        name: 'CRYPTOUPDATEACCOUNT',
        type: '15',
        entity_id: '0.0.8',
      },
    ]);

    await integrationDomainOps.loadCryptoTransfers([
      {
        consensus_timestamp: createdTimestamp1,
        payerAccountId: '0.0.8',
        nodeAccountId: '0.0.3',
        treasuryAccountId: '0.0.98',
        token_transfer_list: [
          {
            token_id: '0.0.90000',
            account: '0.0.8',
            amount: -1200,
            is_approval: true,
          },
          {
            token_id: '0.0.90000',
            account: '0.0.9',
            amount: 1200,
            is_approval: true,
          },
        ],
      },
      {
        consensus_timestamp: balanceTimestamp1,
        payerAccountId: '0.0.8',
        nodeAccountId: '0.0.3',
        treasuryAccountId: '0.0.98',
        token_transfer_list: [
          {
            token_id: '0.0.90000',
            account: '0.0.8',
            amount: -200,
            is_approval: true,
          },
          {
            token_id: '0.0.90000',
            account: '0.0.1679',
            amount: 200,
            is_approval: true,
          },
        ],
      },
    ]);
  });

  const testSpecs = [
    {
      name: 'Account with timestamp gt and ne',
      urls: [
        `/api/v1/accounts/0.0.8?timestamp=gt:${utils.nsToSecNs(consensusTimestamp1)}`,
        `/api/v1/accounts/0.0.KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ?timestamp=gt:${utils.nsToSecNs(
          consensusTimestamp1
        )}`,
        `/api/v1/accounts/0.0.8?timestamp=ne:${utils.nsToSecNs(consensusTimestamp1)}`,
      ],
      expected: {
        transactions: [
          {
            bytes: 'Ynl0ZXM=',
            charged_tx_fee: 7,
            consensus_timestamp: `${utils.nsToSecNs(balanceTimestamp1)}`,
            entity_id: null,
            max_fee: '33',
            memo_base64: null,
            name: 'CRYPTOTRANSFER',
            nft_transfers: [],
            node: '0.0.3',
            nonce: 0,
            parent_consensus_timestamp: null,
            result: 'SUCCESS',
            scheduled: false,
            staking_reward_transfers: [],
            token_transfers: [
              {
                account: '0.0.8',
                amount: -200,
                token_id: '0.0.90000',
                is_approval: true,
              },
              {
                account: '0.0.1679',
                amount: 200,
                token_id: '0.0.90000',
                is_approval: true,
              },
            ],
            transaction_hash: 'AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8w',
            transaction_id: `0.0.8-${utils.nsToSecNsWithHyphen((balanceTimestamp1 - 1n).toString())}`,
            transfers: [
              {
                account: '0.0.3',
                amount: 2,
                is_approval: false,
              },
              {
                account: '0.0.8',
                amount: -3,
                is_approval: false,
              },
              {
                account: '0.0.98',
                amount: 1,
                is_approval: false,
              },
            ],
            valid_duration_seconds: '11',
            valid_start_timestamp: `${utils.nsToSecNs(balanceTimestamp1 - 1n)}`,
          },
          {
            bytes: 'Ynl0ZXM=',
            charged_tx_fee: 7,
            consensus_timestamp: `${utils.nsToSecNs(createdTimestamp1)}`,
            entity_id: null,
            max_fee: '33',
            memo_base64: null,
            name: 'CRYPTOTRANSFER',
            nft_transfers: [],
            node: '0.0.3',
            nonce: 0,
            parent_consensus_timestamp: null,
            result: 'SUCCESS',
            scheduled: false,
            staking_reward_transfers: [],
            transaction_hash: 'AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8w',
            transaction_id: `0.0.8-${utils.nsToSecNsWithHyphen(consensusTimestamp1.toString())}`,
            transfers: [
              {
                account: '0.0.3',
                amount: 2,
                is_approval: false,
              },
              {
                account: '0.0.8',
                amount: -3,
                is_approval: false,
              },
              {
                account: '0.0.98',
                amount: 1,
                is_approval: false,
              },
            ],
            token_transfers: [
              {
                account: '0.0.8',
                amount: -1200,
                token_id: '0.0.90000',
                is_approval: true,
              },
              {
                account: '0.0.9',
                amount: 1200,
                token_id: '0.0.90000',
                is_approval: true,
              },
            ],
            valid_duration_seconds: '11',
            valid_start_timestamp: `${utils.nsToSecNs(consensusTimestamp1)}`,
          },
        ],
        balance: {
          timestamp: `${utils.nsToSecNs(balanceTimestamp1)}`,
          balance: 80,
          tokens: [
            {
              token_id: '0.0.99998',
              balance: 98,
            },
            {
              token_id: '0.0.99999',
              balance: 88,
            },
          ],
        },
        account: '0.0.8',
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        created_timestamp: null,
        decline_reward: false,
        deleted: false,
        ethereum_nonce: null,
        evm_address: '0x0000000000000000000000000000000000000008',
        expiry_timestamp: null,
        auto_renew_period: null,
        key: null,
        max_automatic_token_associations: 0,
        memo: 'entity memo',
        pending_reward: 0,
        receiver_sig_required: false,
        staked_account_id: '0.0.1',
        staked_node_id: 1,
        stake_period_start: null,
        links: {
          next: null,
        },
      },
    },
  ];

  testSpecs.forEach((spec) => {
    spec.urls.forEach((url) => {
      test(spec.name, async () => {
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        expect(response.body).toEqual(spec.expected);
      });
    });
  });
});
