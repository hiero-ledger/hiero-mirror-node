// SPDX-License-Identifier: Apache-2.0

import request from 'supertest';

// set env vars before importing config implicitly
process.env['HEDERA_MIRROR_COMMON_SHARD'] = 1;
process.env['HEDERA_MIRROR_COMMON_REALM'] = 2;

const EntityId = (await import('../entityId')).default;
const {JSONParse} = await import('../utils');
const {loadBalances} = (await import('./integrationDomainOps')).default;
const {default: server} = await import('../server');
const {SystemEntity} = await import('../service');

(await import('./integrationUtils')).setupIntegrationTest();

describe('balances', () => {
  test('snapshot', async () => {
    // given
    const balances = [
      {
        timestamp: 1566560001000000000n,
        id: 2,
        balance: 2,
      },
      {
        timestamp: 1566560001000000000n,
        id: 6,
        balance: 60,
      },
      {
        timestamp: 1566560001000000000n,
        id: 7,
        balance: 770,
      },
      {
        timestamp: 1566560003000000000n,
        id: 2,
        balance: 2,
      },
      {
        timestamp: 1566560003000000000n,
        id: 6,
        balance: 666,
      },
      {
        timestamp: 1566560007000000000n,
        id: 2,
        balance: 2,
      },
      {
        timestamp: 1566560007000000000n,
        id: 6,
        balance: 6,
        tokens: [
          {
            token_num: 90000,
            balance: 662,
          },
        ],
      },
    ];
    await loadBalances(balances);
    const expected = {
      timestamp: '1566560003.000000000',
      balances: [
        {
          account: '1.2.7',
          balance: 770,
          tokens: [],
        },
      ],
      links: {
        next: '/api/v1/balances?timestamp=1566560004.000000000&limit=1&account.id=lt:1.2.7',
      },
    };

    // when
    const response = await request(server).get('/api/v1/balances?timestamp=1566560004.000000000&limit=1');

    // then
    expect(response.status).toEqual(200);
    expect(JSONParse(response.text)).toEqual(expected);
  });
});

describe('SystemEntity', () => {
  test('stakingRewardAccount', () => {
    expect(SystemEntity.stakingRewardAccount.toString()).toEqual('1.2.800');
  });

  test('treasuryAccount', () => {
    const expected = EntityId.of(1, 2, 2);
    expect(SystemEntity.treasuryAccount.toString()).toEqual('1.2.2');
  });
});
