// SPDX-License-Identifier: Apache-2.0

import {CryptoAllowanceService} from '../../service';
import {assertSqlQueryEqual} from '../testutils';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import EntityId from '../../entityId';

setupIntegrationTest();

const defaultOwnerFilter = 'owner = $1';
const additionalConditions = [defaultOwnerFilter, 'spender > $2'];
describe('CryptoAllowanceService.getAccountAllowancesQuery tests', () => {
  test('Verify simple query', async () => {
    const {query, params} = CryptoAllowanceService.getAccountAllowancesQuery([defaultOwnerFilter], [2], 'asc', 5);
    const expected = `select *
    from crypto_allowance
    where owner = $1
    order by spender asc limit $2`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 5]);
  });

  test('Verify additional conditions', async () => {
    const {query, params} = CryptoAllowanceService.getAccountAllowancesQuery(additionalConditions, [2, 10], 'asc', 5);
    const expected = `select *
    from crypto_allowance
    where owner = $1 and spender > $2
    order by spender asc limit $3`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 5]);
  });
});

const defaultOwner = EntityId.parseString('2000');
const defaultPayer = EntityId.parseString('3000');
const defaultSpender = EntityId.parseString('4000');
const defaultInputCryptoAllowance = [
  {
    amount: 1000,
    owner: defaultOwner.toString(),
    payer_account_id: defaultPayer.toString(),
    spender: defaultSpender.toString(),
    timestamp_range: '[0,)',
  },
];

const defaultExpectedCryptoAllowance = [
  {
    amount: 1000,
    owner: defaultOwner.getEncodedId(),
    payerAccountId: defaultPayer.getEncodedId(),
    spender: defaultSpender.getEncodedId(),
  },
];

describe('CryptoAllowanceService.getAccountCrytoAllownces tests', () => {
  test('CryptoAllowanceService.getAccountCrytoAllownces - No match', async () => {
    await expect(
      CryptoAllowanceService.getAccountCryptoAllowances([defaultOwnerFilter], [2], 'asc', 5)
    ).resolves.toStrictEqual([]);
  });

  test('CryptoAllowanceService.getAccountCrytoAllownces - Matching entity', async () => {
    await integrationDomainOps.loadCryptoAllowances(defaultInputCryptoAllowance);

    await expect(
      CryptoAllowanceService.getAccountCryptoAllowances([defaultOwnerFilter], [defaultOwner.getEncodedId()], 'asc', 5)
    ).resolves.toMatchObject(defaultExpectedCryptoAllowance);
  });

  const spender4001 = EntityId.parseString('4001');
  const spender4002 = EntityId.parseString('4002');
  const spender4003 = EntityId.parseString('4003');
  const inputCryptoAllowance = [
    {
      amount: 1000,
      owner: defaultOwner.toString(),
      payer_account_id: defaultPayer.toString(),
      spender: defaultSpender.toString(),
      timestamp_range: '[0,)',
    },
    {
      amount: 1000,
      owner: defaultOwner.toString(),
      payer_account_id: defaultPayer.toString(),
      spender: spender4001.toString(),
      timestamp_range: '[0,)',
    },
    {
      amount: 1000,
      owner: defaultOwner.toString(),
      payer_account_id: defaultPayer.toString(),
      spender: spender4002.toString(),
      timestamp_range: '[0,)',
    },
    {
      amount: 1000,
      owner: defaultOwner.toString(),
      payer_account_id: defaultPayer.toString(),
      spender: spender4003.toString(),
      timestamp_range: '[0,)',
    },
  ];

  const expectedCryptoAllowance = [
    {
      amount: 1000,
      owner: defaultOwner.getEncodedId(),
      payerAccountId: defaultPayer.getEncodedId(),
      spender: spender4002.getEncodedId(),
    },
    {
      amount: 1000,
      owner: defaultOwner.getEncodedId(),
      payerAccountId: defaultPayer.getEncodedId(),
      spender: spender4003.getEncodedId(),
    },
  ];

  test('CryptoAllowanceService.getAccountCryptoAllowances - Matching spender gt entity', async () => {
    await integrationDomainOps.loadCryptoAllowances(inputCryptoAllowance);

    await expect(
      CryptoAllowanceService.getAccountCryptoAllowances(
        [defaultOwnerFilter, 'spender > $2'],
        [defaultOwner.getEncodedId(), spender4001.getEncodedId()],
        'asc',
        5
      )
    ).resolves.toMatchObject(expectedCryptoAllowance);
  });

  test('CryptoAllowanceService.getAccountCryptoAllowances - Matching spender entity', async () => {
    await integrationDomainOps.loadCryptoAllowances(inputCryptoAllowance);

    await expect(
      CryptoAllowanceService.getAccountCryptoAllowances(
        [defaultOwnerFilter, 'spender in ($2, $3)'],
        [defaultOwner.getEncodedId(), spender4002.getEncodedId(), spender4003.getEncodedId()],
        'asc',
        5
      )
    ).resolves.toMatchObject(expectedCryptoAllowance);
  });
});
