// SPDX-License-Identifier: Apache-2.0

import {assertSqlQueryEqual} from '../testutils';
import {TokenService} from '../../service';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import integrationContainerOps from '../integrationContainerOps';
import {CachedToken} from '../../model';
import EntityId from '../../entityId';

setupIntegrationTest();

describe('getQuery', () => {
  const defaultQuery = {
    conditions: [],
    inConditions: [],
    order: 'asc',
    ownerAccountId: 98,
    limit: 25,
  };
  const tokenFields =
    'ta.automatic_association,ta.balance,ta.created_timestamp,ta.freeze_status,ta.kyc_status,ta.token_id ';
  const specs = [
    {
      name: 'default',
      query: defaultQuery,
      expected: {
        sqlQuery:
          'select ' +
          tokenFields +
          'from token_account ta ' +
          ' where ta.account_id = $1 and ta.associated = true order by ta.token_id asc limit $2',
        params: [98, 25],
      },
    },
    {
      name: 'order desc',
      query: {...defaultQuery, order: 'desc'},
      expected: {
        sqlQuery:
          'select ' +
          tokenFields +
          'from token_account ta ' +
          ' where ta.account_id = $1 and ta.associated = true order by ta.token_id desc limit $2',
        params: [98, 25],
      },
    }, // Going onwards fix it
    {
      name: 'token_id eq',
      query: {...defaultQuery, inConditions: [{key: 'token_id', operator: '=', value: 2}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenFields +
          `from token_account ta ` +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id in (2)
          order by ta.token_id asc
          limit $2`,
        params: [98, 25],
      },
    },
    {
      name: 'token_id gt',
      query: {...defaultQuery, conditions: [{key: 'token_id', operator: '>', value: 10}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenFields +
          `from token_account ta ` +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id > $3
          order by ta.token_id asc
          limit $2`,
        params: [98, 25, 10],
      },
    },
    {
      name: 'token_id lt',
      query: {...defaultQuery, conditions: [{key: 'token_id', operator: '<', value: 5}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenFields +
          `from token_account ta ` +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id < $3
          order by ta.token_id asc
          limit $2`,
        params: [98, 25, 5],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      const actual = TokenService.getTokenRelationshipsQuery(spec.query);
      assertSqlQueryEqual(actual.sqlQuery, spec.expected.sqlQuery);
      expect(actual.params).toEqual(spec.expected.params);
    });
  });
});

describe('getCachedTokens', () => {
  const tokenId300 = EntityId.parseString('300');
  const tokenId400 = EntityId.parseString('400');
  const tokenId500 = EntityId.parseString('500');

  beforeEach(async () => {
    const tokens = [
      {
        decimals: 3,
        token_id: tokenId300.toString(),
      },
      {
        decimals: 4,
        freeze_key: [1, 1],
        freeze_status: 2, // UNFROZEN
        kyc_key: [1, 1],
        kyc_status: 2, // REVOKED
        token_id: tokenId400.toString(),
      },
      {
        decimals: 5,
        freeze_default: true,
        freeze_key: [1, 1],
        freeze_status: 1, // FROZEN
        token_id: tokenId500.toString(),
      },
    ];
    await integrationDomainOps.loadTokens(tokens);
  });

  test('no match', async () => {
    expect(await TokenService.getCachedTokens(new Set([100]))).toBeEmpty();
  });

  test('single', async () => {
    const expected = new Map([
      [
        tokenId300.getEncodedId(),
        new CachedToken({
          decimals: 3,
          freeze_status: 0, // not applicable
          kyc_status: 0, // not applicable
          token_id: tokenId300.getEncodedId(),
        }),
      ],
    ]);
    await expect(TokenService.getCachedTokens(new Set([tokenId300.getEncodedId()]))).resolves.toStrictEqual(expected);

    // cache hit
    await integrationContainerOps.cleanUp();
    await expect(TokenService.getCachedTokens(new Set([tokenId300.getEncodedId()]))).resolves.toStrictEqual(expected);
  });

  test('multiple', async () => {
    const expected = new Map([
      [
        tokenId300.getEncodedId(),
        new CachedToken({
          decimals: 3,
          freeze_status: 0, // NOT_APPLICABLE
          kyc_status: 0, // NOT_APPLICABLE
          token_id: 300,
        }),
      ],
      [
        tokenId400.getEncodedId(),
        new CachedToken({
          decimals: 4,
          freeze_status: 2, // UNFROZEN
          kyc_status: 2, // REVOKED
          token_id: 400,
        }),
      ],
      [
        tokenId500.getEncodedId(),
        new CachedToken({
          decimals: 5,
          freeze_status: 1, // FROZEN
          kyc_status: 0, // NOT_APPLICABLE
          token_id: 500,
        }),
      ],
    ]);

    await expect(
      TokenService.getCachedTokens(
        new Set([tokenId300.getEncodedId(), tokenId400.getEncodedId(), tokenId500.getEncodedId()])
      )
    ).resolves.toStrictEqual(expected);
  });
});

describe('putTokenCache', () => {
  test('put then get', async () => {
    const token = {
      decimals: 2,
      freeze_status: 0,
      kyc_status: 0,
      token_id: 200,
    };
    TokenService.putTokenCache(token);
    const expected = new Map([[200, new CachedToken(token)]]);
    await expect(TokenService.getCachedTokens(new Set([200]))).resolves.toStrictEqual(expected);

    // put again, note some fields have different value, to validate the service returns the previous copy
    TokenService.putTokenCache({...token, decimals: 3});
    await expect(TokenService.getCachedTokens(new Set([200]))).resolves.toStrictEqual(expected);
  });
});
