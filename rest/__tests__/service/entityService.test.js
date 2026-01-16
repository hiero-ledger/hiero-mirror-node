// SPDX-License-Identifier: Apache-2.0

import EntityId from '../../entityId';
import {EntityService} from '../../service';
import AccountAlias from '../../accountAlias';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import {getMirrorConfig} from '../../config.js';

setupIntegrationTest();

const {
  common: {realm: systemRealm, shard: systemShard},
} = getMirrorConfig();

const defaultEntityAlias = new AccountAlias(
  systemShard,
  systemRealm,
  'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ'
);
const defaultInputEntityId = EntityId.parseString('95622');
const defaultInputEntity = [
  {
    alias: defaultEntityAlias.base32Alias,
    evm_address: 'ac384c53f03855fa1b3616052f8ba32c6c2a2fec',
    id: defaultInputEntityId.getEncodedId(),
    ...defaultInputEntityId,
  },
];
const defaultInputContractId = EntityId.parseString('95623');
const defaultInputContract = [
  {
    evm_address: 'cef2a2c6c23ab8f2506163b1af55830f35c483ca',
    id: defaultInputContractId.getEncodedId(),
    num: defaultInputContractId.num,
    shard: systemShard,
    realm: systemRealm,
  },
];

const defaultExpectedEntity = {id: EntityId.parseString('95622').getEncodedId()};
const defaultExpectedContractId = {id: EntityId.parseString('95623').getEncodedId()};

describe('EntityService.getAccountFromAlias tests', () => {
  test('EntityService.getAccountFromAlias - No match', async () => {
    await expect(EntityService.getAccountFromAlias({alias: '1'})).resolves.toBeNull();
  });

  test('EntityService.getAccountFromAlias - Matching entity', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getAccountFromAlias(defaultEntityAlias)).resolves.toMatchObject(defaultExpectedEntity);
  });

  test('EntityService.getAccountFromAlias - Duplicate alias', async () => {
    const entityId1 = EntityId.parseString('3');
    const entityId2 = EntityId.parseString('4');

    const inputEntities = [
      {
        alias: defaultEntityAlias.base32Alias,
        id: entityId1.getEncodedId(),
        ...entityId1,
      },
      {
        alias: defaultEntityAlias.base32Alias,
        id: entityId2.getEncodedId(),
        ...entityId2,
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
  const entity = defaultInputEntity[0];
  const defaultEvmAddress = EntityId.parse(`${entity.shard}.${entity.realm}.${entity.evm_address}`);

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
        id: BigInt(defaultInputEntity[0].id) + 1n,
        num: BigInt(defaultInputEntity[0].num) + 1n,
      },
    ];
    await integrationDomainOps.loadEntities(inputEntities);

    await expect(() =>
      EntityService.getEntityIdFromEvmAddress(defaultEvmAddress)
    ).rejects.toThrowErrorMatchingSnapshot();
  });

  test('EntityService.getEntityIdFromEvmAddress - Non-zero realm', async () => {
    const entity = {
      evm_address: '81eaa748d5252be68c1185588beca495459fdba4',
      id: 18014948265296876n,
      num: 1004,
      shard: 1,
      realm: 2,
    };
    await integrationDomainOps.loadEntities([entity]);

    await expect(EntityService.getEntityIdFromEvmAddress(EntityId.parse(entity.evm_address))).resolves.toBe(
      18014948265296876n
    );
    await expect(EntityService.getEntityIdFromEvmAddress(EntityId.parse(`0x${entity.evm_address}`))).resolves.toBe(
      18014948265296876n
    );
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
    await expect(EntityService.getEncodedId(defaultInputEntity[0].num + '')).resolves.toBe(defaultExpectedEntity.id);
  });

  test('EntityService.getEncodedId - Matching id', async () => {
    await expect(EntityService.getEncodedId(defaultInputEntity[0].num + '')).resolves.toBe(defaultExpectedEntity.id);
    await expect(EntityService.getEncodedId(defaultInputContract[0].num + '')).resolves.toBe(
      defaultExpectedContractId.id
    );
  });

  test('EntityService.getEncodedId - Matching alias', async () => {
    const entityId = EntityId.parseString('1001');
    const entity = {
      alias: 'AAAQEAYEAUDAOCAJCAIREEYUCULBOGAZ',
      id: entityId.getEncodedId(),
      ...entityId,
    };
    await integrationDomainOps.loadEntities([entity]);

    await expect(EntityService.getEncodedId(entity.alias)).resolves.toBe(entity.id);
    await expect(EntityService.getEncodedId(`${entity.realm}.${entity.alias}`)).resolves.toBe(entity.id);
    await expect(EntityService.getEncodedId(`${entity.shard}.${entity.realm}.${entity.alias}`)).resolves.toBe(
      entity.id
    );
  });

  test('EntityService.getEncodedId - Matching evm address', async () => {
    const entityId = EntityId.parseString('1002');
    const entity = {
      evm_address: '71eaa748d5252be68c1185588beca495459fdba4',
      id: entityId.getEncodedId(),
      ...entityId,
    };
    await integrationDomainOps.loadEntities([entity]);

    await expect(EntityService.getEncodedId(entity.evm_address)).resolves.toBe(entity.id);
    await expect(EntityService.getEncodedId(`0x${entity.evm_address}`)).resolves.toBe(entity.id);
    await expect(EntityService.getEncodedId(`${entity.realm}.${entity.evm_address}`)).resolves.toBe(entity.id);
    await expect(EntityService.getEncodedId(`${entity.shard}.${entity.realm}.${entity.evm_address}`)).resolves.toBe(
      entity.id
    );
  });

  test('EntityService.getEncodedId - Non-default shard/realm', async () => {
    const entityId = EntityId.parseString('1003');
    const entity = {
      alias: 'AEBAGBAFAYDQQCIQCEJBGFAVCYLRQGJA',
      evm_address: '81eaa748d5252be68c1185588beca495459fdba4',
      id: entityId.getEncodedId(),
      ...entityId,
    };
    await integrationDomainOps.loadEntities([entity]);

    await expect(EntityService.getEncodedId(entity.alias)).resolves.toBe(entity.id);
    await expect(
      EntityService.getEncodedId(`${entity.realm + 1}.${entity.alias}`)
    ).rejects.toThrowErrorMatchingSnapshot();
    await expect(
      EntityService.getEncodedId(`${entity.shard + 1}.${entity.realm + 1}.${entity.alias}`)
    ).rejects.toThrowErrorMatchingSnapshot();
    await expect(EntityService.getEncodedId(entity.evm_address)).resolves.toBe(entity.id);
    await expect(EntityService.getEncodedId(`0x${entity.evm_address}`)).resolves.toBe(entity.id);
    await expect(
      EntityService.getEncodedId(`${entity.realm + 1}.${entity.evm_address}`)
    ).rejects.toThrowErrorMatchingSnapshot();
    await expect(
      EntityService.getEncodedId(`${entity.shard + 1}.${entity.realm + 1}.${entity.evm_address}`)
    ).rejects.toThrowErrorMatchingSnapshot();
  });

  test('EntityService.getEncodedId - Invalid alias', async () => {
    await expect(EntityService.getEncodedId('deadbeef=')).rejects.toThrowErrorMatchingSnapshot();
  });
});
