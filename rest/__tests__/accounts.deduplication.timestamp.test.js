// SPDX-License-Identifier: Apache-2.0

import {setupIntegrationTest} from './integrationUtils';
import integrationDomainOps from './integrationDomainOps';
import * as utils from '../utils';
import request from 'supertest';
import server from '../server';
import * as constants from '../constants';
import EntityId from '../entityId';

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

  const entityId1 = EntityId.parseString('1');
  const entityId2 = EntityId.systemEntity.treasuryAccount;
  const entityId3 = EntityId.parseString('3');
  const entityId7 = EntityId.parseString('7');
  const entityId8 = EntityId.parseString('8');
  const entityId9 = EntityId.parseString('9');
  const entityId98 = EntityId.systemEntity.networkAdminFeeAccount;
  const entityId1679 = EntityId.parseString('1679');
  const entityId90000 = EntityId.parseString('90000');
  const entityId99998 = EntityId.parseString('99998');
  const entityId99999 = EntityId.parseString('99999');

  beforeEach(async () => {
    await integrationDomainOps.loadAccounts([
      {
        num: entityId3.num,
      },
      {
        num: entityId7.num,
      },
      {
        balance: 80,
        balance_timestamp: balanceTimestamp1,
        num: entityId8.num,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${timestampRange1},)`,
        staked_node_id: entityId1.num,
        staked_account_id: entityId1.num,
      },
      {
        balance: 30,
        num: entityId8.num,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${timestampRange2 - 1n}, ${timestampRange2})`,
        staked_node_id: entityId2.num,
        staked_account_id: entityId2.num,
      },
      {
        num: entityId9.num,
      },
      {
        num: entityId98.num,
      },
    ]);
    await integrationDomainOps.loadBalances([
      {
        timestamp: balanceTimestamp1,
        id: entityId2.num,
        balance: 2,
      },
      {
        timestamp: balanceTimestamp1,
        id: entityId7.num,
        balance: 80,
        tokens: [
          {
            token_num: entityId99998.num,
            balance: 7,
          },
          {
            token_num: entityId99999.num,
            balance: 77,
          },
        ],
      },
      {
        timestamp: balanceTimestamp1,
        id: entityId8.num,
        balance: 80,
        tokens: [
          {
            token_num: entityId99998.num,
            balance: 98,
          },
          {
            token_num: entityId99999.num,
            balance: 88,
          },
        ],
      },
      {
        timestamp: beginningOfPreviousMonth,
        id: entityId2.num,
        balance: 2,
      },
      {
        timestamp: beginningOfPreviousMonth,
        id: entityId9.num,
        balance: 999,
      },
    ]);

    await integrationDomainOps.loadTokenAccounts([
      {
        token_id: entityId99998.num,
        account_id: entityId7.num,
        balance: 7,
        created_timestamp: createdTimestamp1,
      },
      {
        token_id: entityId99999.num,
        account_id: entityId7.num,
        balance: 77,
        created_timestamp: '2200',
      },
      {
        token_id: entityId99998.num,
        account_id: entityId8.num,
        balance: 8,
        created_timestamp: createdTimestamp1,
      },
      {
        token_id: entityId99999.num,
        account_id: entityId8.num,
        balance: 88,
        created_timestamp: createdTimestamp1,
      },
    ]);

    await integrationDomainOps.loadTransactions([
      {
        payerAccountId: entityId9.num,
        nodeAccountId: entityId3.num,
        consensus_timestamp: beginningOfPreviousMonth,
        name: 'TOKENCREATION',
        type: '29',
        entity_id: entityId90000.num,
        high_volume: false,
      },
      {
        payerAccountId: entityId9.num,
        nodeAccountId: entityId3.num,
        consensus_timestamp: consensusTimestamp1,
        name: 'CRYPTODELETE',
        type: '12',
        entity_id: entityId7.num,
        high_volume: false,
      },
      {
        charged_tx_fee: 0,
        payerAccountId: entityId9.num,
        nodeAccountId: entityId3.num,
        consensus_timestamp: consensusTimestamp2,
        name: 'CRYPTOUPDATEACCOUNT',
        type: '15',
        entity_id: entityId8.num,
        high_volume: false,
      },
    ]);

    await integrationDomainOps.loadCryptoTransfers([
      {
        consensus_timestamp: createdTimestamp1,
        payerAccountId: entityId8.num,
        nodeAccountId: entityId3.num,
        treasuryAccountId: entityId98.num,
        token_transfer_list: [
          {
            token_id: entityId90000.num,
            account: entityId8.num,
            amount: -1200,
            is_approval: true,
          },
          {
            token_id: entityId90000.num,
            account: entityId9.num,
            amount: 1200,
            is_approval: true,
          },
        ],
      },
      {
        consensus_timestamp: balanceTimestamp1,
        payerAccountId: entityId8.num,
        nodeAccountId: entityId3.num,
        treasuryAccountId: entityId98.num,
        token_transfer_list: [
          {
            token_id: entityId90000.num,
            account: entityId8.num,
            amount: -200,
            is_approval: true,
          },
          {
            token_id: entityId90000.num,
            account: entityId1679.num,
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
        `/api/v1/accounts/${entityId8.toString()}?timestamp=gt:${utils.nsToSecNs(consensusTimestamp1)}`,
        `/api/v1/accounts/${entityId8.shard}.${
          entityId8.realm
        }.KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ?timestamp=gt:${utils.nsToSecNs(consensusTimestamp1)}`,
        `/api/v1/accounts/${entityId8.toString()}?timestamp=ne:${utils.nsToSecNs(consensusTimestamp1)}`,
      ],
      expected: {
        transactions: [
          {
            batch_key: null,
            bytes: 'Ynl0ZXM=',
            charged_tx_fee: 7,
            consensus_timestamp: `${utils.nsToSecNs(balanceTimestamp1)}`,
            entity_id: null,
            high_volume: false,
            max_custom_fees: [],
            max_fee: '33',
            memo_base64: null,
            name: 'CRYPTOTRANSFER',
            nft_transfers: [],
            node: entityId3.toString(),
            nonce: 0,
            parent_consensus_timestamp: null,
            result: 'SUCCESS',
            scheduled: false,
            staking_reward_transfers: [],
            token_transfers: [
              {
                account: entityId8.toString(),
                amount: -200,
                token_id: entityId90000.toString(),
                is_approval: true,
              },
              {
                account: entityId1679.toString(),
                amount: 200,
                token_id: entityId90000.toString(),
                is_approval: true,
              },
            ],
            transaction_hash: 'AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8w',
            transaction_id: `${entityId8.toString()}-${utils.nsToSecNsWithHyphen((balanceTimestamp1 - 1n).toString())}`,
            transfers: [
              {
                account: entityId3.toString(),
                amount: 2,
                is_approval: false,
              },
              {
                account: entityId8.toString(),
                amount: -3,
                is_approval: false,
              },
              {
                account: entityId98.toString(),
                amount: 1,
                is_approval: false,
              },
            ],
            valid_duration_seconds: '11',
            valid_start_timestamp: `${utils.nsToSecNs(balanceTimestamp1 - 1n)}`,
          },
          {
            batch_key: null,
            bytes: 'Ynl0ZXM=',
            charged_tx_fee: 7,
            consensus_timestamp: `${utils.nsToSecNs(createdTimestamp1)}`,
            entity_id: null,
            high_volume: false,
            max_custom_fees: [],
            max_fee: '33',
            memo_base64: null,
            name: 'CRYPTOTRANSFER',
            nft_transfers: [],
            node: entityId3.toString(),
            nonce: 0,
            parent_consensus_timestamp: null,
            result: 'SUCCESS',
            scheduled: false,
            staking_reward_transfers: [],
            transaction_hash: 'AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8w',
            transaction_id: `${entityId8.toString()}-${utils.nsToSecNsWithHyphen(consensusTimestamp1.toString())}`,
            transfers: [
              {
                account: entityId3.toString(),
                amount: 2,
                is_approval: false,
              },
              {
                account: entityId8.toString(),
                amount: -3,
                is_approval: false,
              },
              {
                account: entityId98.toString(),
                amount: 1,
                is_approval: false,
              },
            ],
            token_transfers: [
              {
                account: entityId8.toString(),
                amount: -1200,
                token_id: entityId90000.toString(),
                is_approval: true,
              },
              {
                account: entityId9.toString(),
                amount: 1200,
                token_id: entityId90000.toString(),
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
              token_id: entityId99998.toString(),
              balance: 98,
            },
            {
              token_id: entityId99999.toString(),
              balance: 88,
            },
          ],
        },
        account: entityId8.toString(),
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        created_timestamp: null,
        decline_reward: false,
        delegation_address: '0x',
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
        staked_account_id: entityId1.toString(),
        staked_node_id: entityId1.num,
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
