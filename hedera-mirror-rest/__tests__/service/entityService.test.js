// SPDX-License-Identifier: Apache-2.0

import {EntityService} from '../../service';
import AccountAlias from '../../accountAlias';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';

setupIntegrationTest();

const defaultEntityAlias = new AccountAlias('1', '2', 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ');
const defaultInputEntity = [
  {
    alias: defaultEntityAlias.base32Alias,
    evm_address: 'ac384c53f03855fa1b3616052f8ba32c6c2a2fec',
    id: 281483566645248,
    num: 0,
    shard: 1,
    realm: 2,
  },
];
const defaultInputContract = [
  {
    evm_address: 'cef2a2c6c23ab8f2506163b1af55830f35c483ca',
    id: 4295062919,
    num: 95623,
    shard: 0,
    realm: 1,
  },
];

const defaultExpectedEntity = {id: 281483566645248};
const defaultExpectedContractId = {id: 4295062919};

describe('EntityService.getAccountFromAlias tests', () => {
  test('EntityService.getAccountFromAlias - No match', async () => {
    await expect(EntityService.getAccountFromAlias({alias: '1'})).resolves.toBeNull();
  });

  test('EntityService.getAccountFromAlias - Matching entity', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getAccountFromAlias(defaultEntityAlias)).resolves.toMatchObject(defaultExpectedEntity);
  });

  test('EntityService.getAccountFromAlias - Duplicate alias', async () => {
    const inputEntities = [
      {
        alias: defaultEntityAlias.base32Alias,
        id: 3,
        num: 3,
        shard: 1,
        realm: 2,
      },
      {
        alias: defaultEntityAlias.base32Alias,
        id: 4,
        num: 4,
        shard: 1,
        realm: 2,
      },
    ];
    await integrationDomainOps.loadEntities(inputEntities);

    await expect(() => EntityService.getAccountFromAlias(defaultEntityAlias)).rejects.toThrowErrorMatchingSnapshot();
  });
});

describe('EntityService.getAccountIdFromAlias tests', () => {
  test('EntityService.getAccountIdFromAlias - No match - result required', async () => {
    await expect(() => EntityService.getAccountIdFromAlias(defaultEntityAlias)).rejects.toThrowErrorMatchingSnapshot();
  });

  test('EntityService.getAccountIdFromAlias - No match - result not required', async () => {
    await expect(EntityService.getAccountIdFromAlias(defaultEntityAlias, false)).resolves.toBe(null);
  });

  test('EntityService.getAccountFromAlias - Matching id', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getAccountIdFromAlias(defaultEntityAlias)).resolves.toBe(defaultExpectedEntity.id);
  });
});

describe('EntityService.getEntityIdFromEvmAddress tests', () => {
  const defaultEvmAddress = defaultInputEntity[0].evm_address;

  test('EntityService.getEntityIdFromEvmAddress - Matching evm address', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getEntityIdFromEvmAddress(defaultEvmAddress)).resolves.toBe(defaultExpectedEntity.id);
  });

  test('EntityService.getEntityIdFromEvmAddress - No match - result required', async () => {
    await expect(() =>
      EntityService.getEntityIdFromEvmAddress(defaultEvmAddress)
    ).rejects.toThrowErrorMatchingSnapshot();
  });

  test('EntityService.getEntityIdFromEvmAddress - No match - result not required', async () => {
    await expect(EntityService.getEntityIdFromEvmAddress(defaultEvmAddress, false)).resolves.toBe(null);
  });

  test('EntityService.getEntityIdFromEvmAddress - Multiple matches', async () => {
    const inputEntities = [
      defaultInputEntity[0],
      {
        ...defaultInputEntity[0],
        id: defaultInputEntity[0].id + 1,
        num: defaultInputEntity[0].num + 1,
      },
    ];
    await integrationDomainOps.loadEntities(inputEntities);

    await expect(() =>
      EntityService.getEntityIdFromEvmAddress(defaultEvmAddress)
    ).rejects.toThrowErrorMatchingSnapshot();
  });
});

describe('EntityService.isValidAccount tests', () => {
  test('EntityService.isValidAccount - No match', async () => {
    await expect(EntityService.isValidAccount(defaultInputEntity[0].id)).resolves.toBe(false);
  });

  test('EntityService.getAccountFromAlias - Matching', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.isValidAccount(defaultInputEntity[0].id)).resolves.toBe(true);
  });
});

describe('EntityService.getEncodedId tests', () => {
  test('EntityService.getEncodedId - No match', async () => {
    await expect(EntityService.getEncodedId(defaultInputEntity[0].id)).resolves.toBe(defaultExpectedEntity.id);
  });

  test('EntityService.getEncodedId - Matching id', async () => {
    await expect(EntityService.getEncodedId(defaultInputEntity[0].id)).resolves.toBe(defaultExpectedEntity.id);
    await expect(EntityService.getEncodedId(defaultInputContract[0].id)).resolves.toBe(defaultExpectedContractId.id);
  });

  test('EntityService.getEncodedId - Matching alias', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getEncodedId(defaultInputEntity[0].alias)).resolves.toBe(defaultExpectedEntity.id);
  });

  test('EntityService.getEncodedId - Matching evm address', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);
    await integrationDomainOps.loadContracts(defaultInputContract);

    const accountEvmAddress = defaultInputEntity[0].evm_address;
    await expect(EntityService.getEncodedId(accountEvmAddress)).resolves.toBe(defaultExpectedEntity.id);
    await expect(EntityService.getEncodedId(`0x${accountEvmAddress}`)).resolves.toBe(defaultExpectedEntity.id);

    const contractEvmAddress = defaultInputContract[0].evm_address;
    await expect(EntityService.getEncodedId(contractEvmAddress)).resolves.toBe(defaultExpectedContractId.id);
    await expect(EntityService.getEncodedId(`0x${contractEvmAddress}`)).resolves.toBe(defaultExpectedContractId.id);
  });

  test('EntityService.getEncodedId - Invalid alias', async () => {
    await expect(EntityService.getEncodedId('deadbeef=')).rejects.toThrowErrorMatchingSnapshot();
  });
});
