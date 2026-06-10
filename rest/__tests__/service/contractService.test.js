// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import _ from 'lodash';

import {ContractService} from '../../service';
import {assertSqlQueryEqual} from '../testutils';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import {TransactionResult, TransactionType} from '../../model';
import {orderFilterValues} from '../../constants';
import EntityId from '../../entityId';
import config from '../../config';

setupIntegrationTest();

const entityId0 = EntityId.parseString('0');
const entityId1 = EntityId.parseString('1');
const entityId2 = EntityId.parseString('2');
const entityId3 = EntityId.parseString('3');
const entityId4 = EntityId.parseString('4');
const entityId5 = EntityId.parseString('5');
const entityId8 = EntityId.parseString('8');
const entityId9 = EntityId.parseString('9');
const entityId10 = EntityId.parseString('10');
const entityId11 = EntityId.parseString('11');
const entityId21 = EntityId.parseString('21');
const entityId22 = EntityId.parseString('22');
const entityId123 = EntityId.parseString('123');
const entityId124 = EntityId.parseString('124');
const entityId500 = EntityId.parseString('500');
const entityId600 = EntityId.parseString('600');
const entityId2000 = EntityId.parseString('2000');
const entityId9000 = EntityId.parseString('9000');
const entityId9999 = EntityId.parseString('9999');
const entityId111278 = EntityId.parseString('111278');
const entityId111168 = EntityId.parseString('111168');
const entityId111169 = EntityId.parseString('111169');
const entityId111276 = EntityId.parseString('111276');
const entityId111481 = EntityId.parseString('111481');
const entityId111482 = EntityId.parseString('111482');
const entityId222001 = EntityId.parseString('222001');
const entityId222002 = EntityId.parseString('222002');
const entityId222003 = EntityId.parseString('222003');

describe('ContractService.getContractResultsByIdAndFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      ['cr.contract_id = $1'],
      [2],
      'asc',
      5
    );
    const expected = `
        select
            cr.amount,
            cr.bloom,
            cr.call_result,
            cr.consensus_timestamp,
            cr.contract_id,
            cr.created_contract_ids,
            cr.error_message,
            cr.failed_initcode,
            cr.function_parameters,
            case when cr.sender_id is null then cr.function_result else '' end as function_result,
            cr.gas_consumed,
            cr.gas_limit,
            cr.gas_used,
            cr.payer_account_id,
            cr.sender_id,
            cr.transaction_hash,
            cr.transaction_index,
            cr.transaction_nonce,
            cr.transaction_result,
            coalesce(e.evm_address,'') as evm_address
        from contract_result cr
        left join entity e on e.id = cr.contract_id
        where cr.contract_id = $1
        order by cr.consensus_timestamp asc
        limit $2`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 5]);
  });

  test('Verify additional conditions', async () => {
    const additionalConditions = ['cr.contract_id = $1', 'cr.consensus_timestamp > $2', 'cr.payer_account_id = $3'];
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      additionalConditions,
      [2, 10, 20],
      'asc',
      5
    );
    const expected = `
    select
        cr.amount,
        cr.bloom,
        cr.call_result,
        cr.consensus_timestamp,
        cr.contract_id,
        cr.created_contract_ids,
        cr.error_message,
        cr.failed_initcode,
        cr.function_parameters,
        case when cr.sender_id is null then cr.function_result else '' end as function_result,
        cr.gas_consumed,
        cr.gas_limit,
        cr.gas_used,
        cr.payer_account_id,
        cr.sender_id,
        cr.transaction_hash,
        cr.transaction_index,
        cr.transaction_nonce,
        cr.transaction_result,
        coalesce(e.evm_address,'') as evm_address
    from contract_result cr
    left join entity e on e.id = cr.contract_id
    where cr.contract_id = $1 and cr.consensus_timestamp > $2 and cr.payer_account_id = $3
    order by cr.consensus_timestamp asc
    limit $4`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 20, 5]);
  });

  test('Verify transaction.nonce condition', async () => {
    const additionalConditions = ['cr.contract_id = $1', 'cr.transaction_nonce = $2'];
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      additionalConditions,
      [2, 10],
      'asc',
      5
    );
    const expected = `
    select
        cr.amount,
        cr.bloom,
        cr.call_result,
        cr.consensus_timestamp,
        cr.contract_id,
        cr.created_contract_ids,
        cr.error_message,
        cr.failed_initcode,
        cr.function_parameters,
        case when cr.sender_id is null then cr.function_result else '' end as function_result,
        cr.gas_consumed,
        cr.gas_limit,
        cr.gas_used,
        cr.payer_account_id,
        cr.sender_id,
        cr.transaction_hash,
        cr.transaction_index,
        cr.transaction_nonce,
        cr.transaction_result,
        coalesce(e.evm_address,'') as evm_address
    from contract_result cr
    left join entity e on e.id = cr.contract_id
    where cr.contract_id = $1 and cr.transaction_nonce = $2
    order by cr.consensus_timestamp asc
    limit $3
    `;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 5]);
  });

  test('Verify transaction.index condition', async () => {
    const additionalConditions = ['cr.contract_id = $1', 'cr.transaction_index = $2'];
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      additionalConditions,
      [2, 10],
      'asc',
      5
    );
    const expected = `
    select
        cr.amount,
        cr.bloom,
        cr.call_result,
        cr.consensus_timestamp,
        cr.contract_id,
        cr.created_contract_ids,
        cr.error_message,
        cr.failed_initcode,
        cr.function_parameters,
        case when cr.sender_id is null then cr.function_result else '' end as function_result,
        cr.gas_consumed,
        cr.gas_limit,
        cr.gas_used,
        cr.payer_account_id,
        cr.sender_id,
        cr.transaction_hash,
        cr.transaction_index,
        cr.transaction_nonce,
        cr.transaction_result,
        coalesce(e.evm_address,'') as evm_address
    from contract_result cr
    left join entity e on e.id = cr.contract_id
    where cr.contract_id = $1 and cr.transaction_index = $2
    order by cr.consensus_timestamp asc
    limit $3
    `;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 5]);
  });
});

describe('ContractService.getSyntheticContractResultsQuery tests', () => {
  test('No prior results, no extra conditions', () => {
    const [query, params] = ContractService.getSyntheticContractResultsQuery(
      [],
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      10
    );
    const expected = `
      select
        null::bigint as amount, null::bytea as bloom, null::bytea as call_result,
        synth_raw.consensus_timestamp, synth_raw.contract_id,
        null::bigint[] as created_contract_ids, null::text as error_message,
        null::bytea as failed_initcode, '\\x'::bytea as function_parameters,
        null::bytea as function_result, null::bigint as gas_consumed, 0::bigint as gas_limit,
        null::bigint as gas_used, synth_raw.payer_account_id, null::bigint as sender_id,
        synth_raw.transaction_hash, synth_raw.transaction_index, 0::integer as transaction_nonce,
        22::smallint as transaction_result,
        coalesce(e.evm_address, '') as evm_address
      from (
        select distinct on (cl.consensus_timestamp)
          cl.consensus_timestamp,
          coalesce(cl.root_contract_id, cl.contract_id) as contract_id,
          cl.transaction_hash, cl.transaction_index, cl.payer_account_id
        from contract_log cl
        where cl.synthetic is true
        order by cl.consensus_timestamp desc, cl.index desc
        limit $1
      ) synth_raw
      left join entity e on e.id = synth_raw.contract_id
    `;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([10]);
  });

  test('No prior results, with timestamp conditions', () => {
    const [query, params] = ContractService.getSyntheticContractResultsQuery(
      [],
      ['cr.transaction_nonce = 0', 'cr.consensus_timestamp >= $1', 'cr.consensus_timestamp <= $2'],
      [1000, 2000],
      'desc',
      10
    );
    const expected = `
      select
        null::bigint as amount,
        null::bytea as bloom,
        null::bytea as call_result,
        synth_raw.consensus_timestamp,
        synth_raw.contract_id,
        null::bigint[] as created_contract_ids,
        null::text as error_message,
        null::bytea as failed_initcode,
        '\\x'::bytea as function_parameters,
        null::bytea as function_result, null::bigint as gas_consumed, 0::bigint as gas_limit,
        null::bigint as gas_used, synth_raw.payer_account_id, null::bigint as sender_id,
        synth_raw.transaction_hash, synth_raw.transaction_index, 0::integer as transaction_nonce,
        22::smallint as transaction_result,
        coalesce(e.evm_address, '') as evm_address
      from (
        select distinct on (cl.consensus_timestamp)
          cl.consensus_timestamp,
          coalesce(cl.root_contract_id, cl.contract_id) as contract_id,
          cl.transaction_hash, cl.transaction_index, cl.payer_account_id
        from contract_log cl
        where cl.consensus_timestamp >= $1 and cl.consensus_timestamp <= $2 and cl.synthetic is true
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      ) synth_raw
      left join entity e on e.id = synth_raw.contract_id
    `;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([1000, 2000, 10]);
  });

  test('Full page of prior results adds DESC timestamp bound', () => {
    const [query, params] = ContractService.getSyntheticContractResultsQuery(
      [{consensus_timestamp: '5'}],
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      1
    );
    const expected = `
      select
        null::bigint as amount, null::bytea as bloom, null::bytea as call_result,
        synth_raw.consensus_timestamp, synth_raw.contract_id,
        null::bigint[] as created_contract_ids, null::text as error_message,
        null::bytea as failed_initcode, '\\x'::bytea as function_parameters,
        null::bytea as function_result, null::bigint as gas_consumed, 0::bigint as gas_limit,
        null::bigint as gas_used, synth_raw.payer_account_id, null::bigint as sender_id,
        synth_raw.transaction_hash, synth_raw.transaction_index, 0::integer as transaction_nonce,
        22::smallint as transaction_result,
        coalesce(e.evm_address, '') as evm_address
      from (
        select distinct on (cl.consensus_timestamp)
          cl.consensus_timestamp,
          coalesce(cl.root_contract_id, cl.contract_id) as contract_id,
          cl.transaction_hash, cl.transaction_index, cl.payer_account_id
        from contract_log cl
        where cl.consensus_timestamp >= $1 and cl.synthetic is true and cl.consensus_timestamp != all($2)
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      ) synth_raw
      left join entity e on e.id = synth_raw.contract_id
    `;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual(['5', ['5'], 1]);
  });

  test('Transaction index condition is mapped to cl.transaction_index', () => {
    const [query, params] = ContractService.getSyntheticContractResultsQuery(
      [],
      ['cr.transaction_nonce = 0', 'cr.transaction_index = $1'],
      [3],
      'asc',
      10
    );
    expect(query).toContain('cl.transaction_index = $1');
    expect(query).not.toContain('cr.transaction_index');
    expect(params).toEqual([3, 10]);
  });
});

describe('ContractService.getContractResultsByIdAndFilters - synthetic inclusion tests', () => {
  test('Synthetic log with no contract_result appears in results', async () => {
    await integrationDomainOps.addSyntheticContractLog({
      consensus_timestamp: 1,
      contract_id: entityId2.num,
      transaction_hash: Buffer.alloc(32, 0xcc),
      transaction_index: 2,
      payer_account_id: entityId500.num,
    });

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      25,
      true
    );

    expect(response).toHaveLength(1);
    expect(response[0]).toMatchObject({
      contractId: entityId2.getEncodedId(),
      consensusTimestamp: 1,
      gasLimit: 0,
      transactionNonce: 0,
      payerAccountId: entityId500.getEncodedId(),
    });
  });

  test('Real contract_result and synthetic log both appear ordered by timestamp', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: entityId2.num,
        consensus_timestamp: 10,
        transaction_nonce: 0,
      },
    ]);
    await integrationDomainOps.addSyntheticContractLog({
      consensus_timestamp: 20,
      contract_id: entityId3.num,
      transaction_hash: Buffer.alloc(32, 0xcc),
      transaction_index: 2,
    });

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'asc',
      25,
      true
    );

    expect(response).toHaveLength(2);
    expect(response[0]).toMatchObject({consensusTimestamp: 10, gasLimit: 1000}); // real
    expect(response[1]).toMatchObject({consensusTimestamp: 20, gasLimit: 0}); // synthetic
  });

  test('Synthetic log with higher timestamp than real result appears first in desc order', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: entityId2.num,
        consensus_timestamp: 10,
        transaction_nonce: 0,
      },
    ]);
    await integrationDomainOps.addSyntheticContractLog({
      consensus_timestamp: 20,
      contract_id: entityId3.num,
      transaction_hash: Buffer.alloc(32, 0xcc),
      transaction_index: 2,
    });

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      25,
      true
    );

    expect(response).toHaveLength(2);
    expect(response[0]).toMatchObject({consensusTimestamp: 20, gasLimit: 0}); // synthetic first (higher ts)
    expect(response[1]).toMatchObject({consensusTimestamp: 10, gasLimit: 1000}); // real second
  });

  test('Old-style synthetic log (synthetic null) does not appear in results', async () => {
    // Historical rows (pre-2026-04-16) have synthetic=null; synthetic=true filter excludes them.
    // A backfill is planned as a follow-up to include these historical rows.
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 1,
        contract_id: entityId2.num,
        transaction_hash: Buffer.alloc(32, 0xdd),
        transaction_index: 1,
        payer_account_id: entityId500.num,
      },
    ]);

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      25,
      true
    );

    expect(response).toHaveLength(0);
  });

  test('Explicitly non-synthetic log (synthetic=false) without contract_result does not appear', async () => {
    // Simulates an orphaned EVM log: synthetic=false is an EVM log, not a HAPI transfer.
    // The synthetic=true filter excludes it even though NOT EXISTS would pass.
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 1,
        contract_id: entityId2.num,
        synthetic: false,
      },
    ]);

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      25,
      true
    );

    expect(response).toHaveLength(0);
  });

  test('Log with matching contract_result does not appear as synthetic', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: entityId2.num,
        consensus_timestamp: 5,
        transaction_nonce: 0,
      },
    ]);
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 5,
        contract_id: entityId2.num,
        transaction_hash: Buffer.alloc(32, 0xee),
      },
    ]);

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      25,
      true
    );

    // Only the real contract_result should appear, no synthetic duplicate
    expect(response).toHaveLength(1);
    expect(response[0]).toMatchObject({gasLimit: 1000}); // real contract_result has gasLimit from fixture
  });

  test('Synthetic log with root_contract_id resolves to root contract', async () => {
    await integrationDomainOps.addSyntheticContractLog({
      // Production data: synthetic HAPI logs always have root_contract_id = contract_id.
      // The coalesce(root_contract_id, contract_id) in the SELECT handles the null case identically.
      consensus_timestamp: 10,
      contract_id: entityId2.num,
      root_contract_id: entityId2.num,
      transaction_hash: Buffer.alloc(32, 0xff),
      transaction_index: 0,
      payer_account_id: entityId500.num,
    });

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      25,
      true
    );

    expect(response).toHaveLength(1);
    expect(response[0].contractId).toEqual(entityId2.getEncodedId());
  });

  test('Synthetic log with matching contract_result is not duplicated', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: entityId2.num,
        consensus_timestamp: 1,
        transaction_nonce: 0,
      },
    ]);
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 1,
        contract_id: entityId2.num,
        synthetic: true,
      },
    ]);

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0'],
      [],
      'desc',
      25,
      true
    );

    expect(response).toHaveLength(1);
    expect(response[0]).toMatchObject({gasLimit: 1000}); // real contract_result wins, no duplicate
  });

  test('Synthetic log respects timestamp bounds', async () => {
    await integrationDomainOps.addSyntheticContractLog({
      consensus_timestamp: 10,
      contract_id: entityId2.num,
      transaction_hash: Buffer.alloc(32, 0xaa),
    });
    await integrationDomainOps.addSyntheticContractLog({
      consensus_timestamp: 30,
      contract_id: entityId2.num,
      transaction_hash: Buffer.alloc(32, 0xbb),
    });

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.transaction_nonce = 0', 'cr.consensus_timestamp >= $1', 'cr.consensus_timestamp <= $2'],
      [20, 40],
      'asc',
      25,
      true
    );

    expect(response).toHaveLength(1);
    expect(response[0]).toMatchObject({consensusTimestamp: 30, gasLimit: 0});
  });
});

const contractLogContractIdWhereClause = `cl.contract_id = $1`;
describe('ContractService.getContractLogsQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [],
      inner: [],
      upper: [],
      conditions: [contractLogContractIdWhereClause],
      order: 'desc',
      params: [2],
      limit: 5,
    });
    assertSqlQueryEqual(
      query,
      `with entity as (select evm_address, id from entity)
      select cl.bloom, cl.contract_id, cl.consensus_timestamp, cl.data, cl.index, cl.root_contract_id,
             cl.topic0, cl.topic1, cl.topic2, cl.topic3, cl.transaction_hash, cl.transaction_index,
             evm_address
      from contract_log cl
      left join entity e on id = contract_id
      where cl.contract_id = $1
      order by cl.consensus_timestamp desc, cl.index desc
      limit $2`
    );
    expect(params).toEqual([2, 5]);
  });

  test('Verify additional conditions', async () => {
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [],
      inner: [],
      upper: [],
      conditions: [
        `cl.contract_id  = $1`,
        `cl.topic0 in ($2)`,
        `cl.topic1 in ($3)`,
        `cl.topic2 in ($4)`,
        `cl.topic3 in ($5)`,
      ],
      order: 'desc',
      params: [
        1002,
        Buffer.from('11', 'hex'),
        Buffer.from('12', 'hex'),
        Buffer.from('13', 'hex'),
        Buffer.from('14', 'hex'),
      ],
      limit: 5,
    });
    assertSqlQueryEqual(
      query,
      `with entity as (select evm_address, id from entity)
      select cl.bloom, cl.contract_id, cl.consensus_timestamp, cl.data, cl.index, cl.root_contract_id,
             cl.topic0, cl.topic1, cl.topic2, cl.topic3, cl.transaction_hash, cl.transaction_index,evm_address
      from contract_log cl
      left join entity e on id = contract_id
      where cl.contract_id = $1 and cl.topic0 in ($2) and cl.topic1 in ($3) and cl.topic2 in ($4) and cl.topic3 in ($5)
      order by cl.consensus_timestamp desc, cl.index desc
      limit $6`
    );
    expect(params).toEqual([
      1002,
      Buffer.from('11', 'hex'),
      Buffer.from('12', 'hex'),
      Buffer.from('13', 'hex'),
      Buffer.from('14', 'hex'),
      5,
    ]);
  });
  test('Verify [lower, inner] filters', async () => {
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [
        {key: 'index', operator: ' >= ', value: '1'},
        {key: 'timestamp', operator: ' = ', value: '1001'},
      ],
      inner: [{key: 'timestamp', operator: ' > ', value: '1001'}],
      upper: [],
      conditions: [`cl.contract_id  = $1`, `cl.topic0 in ($2)`],
      order: 'desc',
      params: [1002, Buffer.from('11', 'hex')],
      limit: 5,
    });
    assertSqlQueryEqual(
      query,
      `(
        with entity as (select evm_address, id from entity)
        select cl.bloom,cl.contract_id,cl.consensus_timestamp,cl.data,cl.index,cl.root_contract_id,cl.topic0,
          cl.topic1,cl.topic2,cl.topic3,cl.transaction_hash,cl.transaction_index,evm_address
        from contract_log cl
        left join entity e on id = contract_id
        where cl.contract_id = $1 and cl.topic0 in ($2) and cl.index >= $4 and cl.consensus_timestamp = $5
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      ) union (
        with entity as (select evm_address, id from entity)
        select cl.bloom,cl.contract_id,cl.consensus_timestamp,cl.data,cl.index,cl.root_contract_id,cl.topic0,
          cl.topic1,cl.topic2,cl.topic3,cl.transaction_hash,cl.transaction_index,evm_address
        from contract_log cl
        left join entity e on id = contract_id
        where cl.contract_id = $1 and cl.topic0 in ($2) and cl.consensus_timestamp > $6
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      )
      order by consensus_timestamp desc, index desc
      limit $3`
    );
    expect(params).toEqual([1002, Buffer.from('11', 'hex'), 5, '1', '1001', '1001']);
  });
  test('Verify [lower, inner, upper] filters', async () => {
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [
        {key: 'index', operator: ' >= ', value: '1'},
        {key: 'timestamp', operator: ' = ', value: '1001'},
      ],
      inner: [
        {key: 'timestamp', operator: ' > ', value: '1001'},
        {key: 'timestamp', operator: ' < ', value: '1005'},
      ],
      upper: [
        {key: 'index', operator: ' <= ', value: '5'},
        {key: 'timestamp', operator: ' = ', value: '1005'},
      ],
      order: 'desc',
      conditions: [`cl.contract_id  = $1`, `cl.topic0 in ($2)`],
      params: [1002, Buffer.from('11', 'hex')],
      limit: 5,
    });
    assertSqlQueryEqual(
      query,
      `(
        with entity as (select evm_address, id from entity)
        select
          cl.bloom,
          cl.contract_id,
          cl.consensus_timestamp,
          cl.data,
          cl.index,
          cl.root_contract_id,
          cl.topic0,
          cl.topic1,
          cl.topic2,
          cl.topic3,
          cl.transaction_hash,
          cl.transaction_index,
          evm_address
        from
          contract_log cl
          left join entity e on id = contract_id
        where  cl.contract_id = $1
          and cl.topic0 in ($2)
          and cl.index >= $4
          and cl.consensus_timestamp = $5
        order by
          cl.consensus_timestamp desc,
          cl.index desc
        limit $3
      ) union (
        with entity as (select evm_address, id from entity)
        select
          cl.bloom,
          cl.contract_id,
          cl.consensus_timestamp,
          cl.data,
          cl.index,
          cl.root_contract_id,
          cl.topic0,
          cl.topic1,
          cl.topic2,
          cl.topic3,
          cl.transaction_hash,
          cl.transaction_index,
          evm_address
        from
          contract_log cl
          left join entity e on id = contract_id
        where
          cl.contract_id = $1
          and cl.topic0 in ($2)
          and cl.consensus_timestamp > $6
          and cl.consensus_timestamp < $7
        order by
          cl.consensus_timestamp desc,
          cl.index desc
        limit $3
      ) union (
        with entity as (select evm_address, id from entity)
        select
          cl.bloom,
          cl.contract_id,
          cl.consensus_timestamp,
          cl.data,
          cl.index,
          cl.root_contract_id,
          cl.topic0,
          cl.topic1,
          cl.topic2,
          cl.topic3,
          cl.transaction_hash,
          cl.transaction_index,
          evm_address
        from
          contract_log cl
          left join entity e on id = contract_id
        where
          cl.contract_id = $1
          and cl.topic0 in ($2)
          and cl.index <= $8
          and cl.consensus_timestamp = $9
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      )
      order by consensus_timestamp desc, index desc
      limit $3`
    );
    expect(params).toEqual([1002, Buffer.from('11', 'hex'), 5, '1', '1001', '1001', '1005', '5', '1005']);
  });
});

describe('ContractService.getContractResultsByIdAndFilters tests', () => {
  test('No match', async () => {
    const response = await ContractService.getContractResultsByIdAndFilters();
    expect(response).toEqual([]);
  });

  test('Row match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: entityId2.num,
        consensus_timestamp: 1,
        function_parameters: '0x0D',
        amount: 10,
        transaction_nonce: 1,
      },
    ]);

    const expectedContractResult = [
      {
        amount: 10,
        contractId: entityId2.getEncodedId(),
        consensusTimestamp: 1,
        gasLimit: 1000,
        gasUsed: null,
        transactionNonce: 1,
      },
    ];

    const response = await ContractService.getContractResultsByIdAndFilters();
    expect(response).toMatchObject(expectedContractResult);
  });

  test('Id match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: 1,
        consensus_timestamp: 1,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: 123,
        transaction_nonce: 2,
      },
      {
        contract_id: entityId2.num,
        consensus_timestamp: 2,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: 123,
        transaction_nonce: 3,
      },
    ]);

    const expectedContractResult = [
      {
        amount: 10,
        contractId: entityId2.getEncodedId(),
        consensusTimestamp: 2,
        gasLimit: 1000,
        gasUsed: null,
        payerAccountId: entityId123.getEncodedId(),
        transactionNonce: 3,
      },
    ];

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['contract_id = $1'],
      [entityId2.getEncodedId()],
      'asc',
      1
    );
    expect(response).toMatchObject(expectedContractResult);
  });

  test('All params match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: entityId2.num,
        consensus_timestamp: 1,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: entityId123.num,
        transaction_nonce: 4,
      },
      {
        contract_id: entityId2.num,
        consensus_timestamp: 2,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: entityId123.num,
        transaction_nonce: 5,
      },
      {
        contract_id: entityId3.num,
        consensus_timestamp: 3,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: entityId124.num,
        transaction_nonce: 6,
      },
      {
        contract_id: entityId3.num,
        consensus_timestamp: 4,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: entityId124.num,
        transaction_nonce: 7,
      },
      {
        contract_id: entityId3.num,
        consensus_timestamp: 5,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: entityId124.num,
        transaction_nonce: 8,
      },
    ]);

    const expectedContractResult = [
      {
        contractId: entityId3.getEncodedId(),
        consensusTimestamp: 3,
        payerAccountId: entityId124.getEncodedId(),
        transactionNonce: 6,
      },
      {
        contractId: entityId3.getEncodedId(),
        consensusTimestamp: 4,
        payerAccountId: entityId124.getEncodedId(),
        transactionNonce: 7,
      },
    ];

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.contract_id = $1', 'cr.consensus_timestamp > $2', 'cr.payer_account_id = $3'],
      [entityId3.getEncodedId(), 2, entityId124.getEncodedId()],
      'asc',
      2
    );

    expect(response).toMatchObject(expectedContractResult);
  });
});

describe('ContractService.getContractLogsByTimestamps tests', () => {
  const timestamps = [1, 2];
  const input = [
    {
      consensus_timestamp: 1,
      contract_id: entityId1.num,
      data: '0x0012',
      index: 0,
      root_contract_id: entityId1.num,
      topic0: '0x000a',
    },
    {
      consensus_timestamp: 1,
      contract_id: entityId2.num,
      data: '0x0013',
      index: 1,
      root_contract_id: entityId1.num,
      topic0: '0x000b',
    },
    {
      consensus_timestamp: 2,
      contract_id: entityId1.num,
      data: '0x0014',
      index: 0,
      root_contract_id: entityId1.num,
      topic0: '0x000c',
    },
    {
      consensus_timestamp: 2,
      contract_id: entityId3.num,
      data: '0x0015',
      index: 1,
      root_contract_id: entityId1.num,
      topic0: '0x000d',
    },
  ];
  const expected = [
    {
      consensusTimestamp: 1,
      contractId: entityId1.getEncodedId(),
      index: 0,
      rootContractId: entityId1.getEncodedId(),
    },
    {
      consensusTimestamp: 1,
      contractId: entityId2.getEncodedId(),
      index: 1,
      rootContractId: entityId1.getEncodedId(),
    },
    {
      consensusTimestamp: 2,
      contractId: entityId1.getEncodedId(),
      index: 0,
      rootContractId: entityId1.getEncodedId(),
    },
    {
      consensusTimestamp: 2,
      contractId: entityId3.getEncodedId(),
      index: 1,
      rootContractId: entityId1.getEncodedId(),
    },
  ];

  const pickContractLogFields = (contractLogs) => {
    return contractLogs.map((cr) => _.pick(cr, ['consensusTimestamp', 'contractId', 'index', 'rootContractId']));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadContractLogs(input);
  });

  test('No match', async () => {
    await expect(ContractService.getContractLogsByTimestamps('3')).resolves.toHaveLength(0);
  });
  test('Match both timestamps', async () => {
    const results = pickContractLogFields(
      await ContractService.getContractLogsByTimestamps([timestamps[0], timestamps[1]])
    );
    expect(results).toIncludeSameMembers(expected);
  });
  test('Match one timestamp with additional umatched timestamp', async () => {
    const results = pickContractLogFields(await ContractService.getContractLogsByTimestamps([timestamps[0], '3']));
    expect(results).toIncludeSameMembers(expected.slice(0, 2));
  });
  test('Match one timestamp', async () => {
    const results = pickContractLogFields(await ContractService.getContractLogsByTimestamps([timestamps[1]]));
    expect(results).toIncludeSameMembers(expected.slice(2));
  });
});

describe('ContractService.getContractResultsByTimestamps tests', () => {
  const input = [
    {
      contract_id: entityId2.num,
      consensus_timestamp: 2,
      function_parameters: '0x0D',
      amount: 10,
      payer_account_id: entityId5.num,
      transaction_nonce: 9,
    },
    {
      contract_id: entityId3.num,
      consensus_timestamp: 6,
      function_parameters: '0x0D',
      amount: 15,
      payer_account_id: entityId5.num,
      transaction_nonce: 10,
    },
  ];
  const expected = [
    {
      amount: 10,
      callResult: null,
      consensusTimestamp: 2,
      contractId: entityId2.getEncodedId(),
      createdContractIds: [],
      errorMessage: '',
      gasLimit: 1000,
      gasUsed: null,
      payerAccountId: entityId5.getEncodedId(),
      transactionNonce: 9,
    },
    {
      amount: 15,
      callResult: null,
      consensusTimestamp: 6,
      contractId: entityId3.getEncodedId(),
      createdContractIds: [],
      errorMessage: '',
      gasLimit: 1000,
      gasUsed: null,
      payerAccountId: entityId5.getEncodedId(),
      transactionNonce: 10,
    },
  ];

  const pickContractResultFields = (contractResults) => {
    return contractResults.map((cr) =>
      _.pick(cr, [
        'amount',
        'callResult',
        'consensusTimestamp',
        'contractId',
        'createdContractIds',
        'errorMessage',
        'gasLimit',
        'gasUsed',
        'payerAccountId',
        'transactionNonce',
      ])
    );
  };

  beforeEach(async () => {
    await integrationDomainOps.loadContractResults(input);
  });

  test('No match', async () => {
    const contractResults = await ContractService.getContractResultsByTimestamps('1');
    expect(contractResults).toHaveLength(0);
  });

  test('Sing row match single timestamp', async () => {
    const contractResults = await ContractService.getContractResultsByTimestamps(expected[0].consensusTimestamp);
    expect(pickContractResultFields(contractResults)).toIncludeSameMembers(expected.slice(0, 1));
  });

  test('Sing row match multiple timestamps', async () => {
    const contractResults = await ContractService.getContractResultsByTimestamps([
      expected[0].consensusTimestamp,
      '100',
    ]);
    expect(pickContractResultFields(contractResults)).toIncludeSameMembers(expected.slice(0, 1));
  });

  test('Multiple rows match multiple timestamps', async () => {
    const contractResults = await ContractService.getContractResultsByTimestamps(
      expected.map((e) => e.consensusTimestamp)
    );
    expect(pickContractResultFields(contractResults)).toIncludeSameMembers(expected);
  });
});

describe('ContractService.getContractLogs tests', () => {
  const defaultQuery = {
    lower: [],
    inner: [],
    upper: [],
    conditions: [],
    order: 'desc',
    params: [],
    limit: 100,
  };

  test('No match', async () => {
    const response = await ContractService.getContractLogs(defaultQuery);
    expect(response).toEqual([]);
  });

  test('Row match', async () => {
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 1,
        contract_id: entityId2.num,
        index: 0,
      },
    ]);

    const expectedContractLog = [
      {
        consensusTimestamp: 1,
        contractId: entityId2.getEncodedId(),
      },
    ];

    const response = await ContractService.getContractLogs({...defaultQuery, params: []});
    expect(response).toMatchObject(expectedContractLog);
  });

  test('Id match', async () => {
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 1,
        contract_id: entityId2.num,
        index: 0,
        root_contract_id: entityId8.num,
      },
      {
        consensus_timestamp: 1,
        contract_id: entityId3.num,
        index: 1,
        root_contract_id: entityId8.num,
      },
      {
        consensus_timestamp: 2,
        contract_id: entityId3.num,
        index: 0,
        root_contract_id: 9,
      },
      {
        consensus_timestamp: 2,
        contract_id: entityId3.num,
        index: 1,
        root_contract_id: entityId9.num,
      },
      {
        consensus_timestamp: 3,
        contract_id: entityId4.num,
        index: 0,
        root_contract_id: entityId8.num,
      },
    ]);

    const expectedContractLog = [
      {
        consensusTimestamp: 2,
        contractId: entityId3.getEncodedId(),
        index: 1,
      },
      {
        consensusTimestamp: 2,
        contractId: entityId3.getEncodedId(),
        index: 0,
      },
      {
        consensusTimestamp: 1,
        contractId: entityId3.getEncodedId(),
        index: 1,
      },
    ];

    const response = await ContractService.getContractLogs({
      ...defaultQuery,
      conditions: [contractLogContractIdWhereClause],
      params: [entityId3.getEncodedId()],
    });
    expect(response).toMatchObject(expectedContractLog);
  });

  test('All params match', async () => {
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 20,
        contract_id: entityId2.num,
        index: 0,
        root_contract_id: entityId10.num,
        topic0: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea',
        topic1: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb',
        topic2: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec',
        topic3: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed',
      },
      {
        consensus_timestamp: 20,
        contract_id: entityId3.num,
        index: 1,
        root_contract_id: entityId10.num,
        topic0: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea',
        topic1: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb',
        topic2: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec',
        topic3: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed',
      },
      {
        consensus_timestamp: 2,
        contract_id: entityId3.num,
        index: 0,
        root_contract_id: entityId10.num,
      },
    ]);

    const expectedContractLog = [
      {
        consensusTimestamp: 20,
        contractId: entityId3.getEncodedId(),
      },
    ];
    const response = await ContractService.getContractLogs({
      ...defaultQuery,
      lower: [
        {key: 'index', operator: ' = ', value: 1},
        {key: 'timestamp', operator: ' = ', value: 20},
      ],
      conditions: [
        contractLogContractIdWhereClause,
        'cl.topic0 in ($2)',
        'cl.topic1 in ($3)',
        'cl.topic2 in ($4)',
        'cl.topic3 in ($5)',
      ],
      order: 'asc',
      params: [
        entityId3.getEncodedId(),
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea', 'hex'),
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb', 'hex'),
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec', 'hex'),
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed', 'hex'),
      ],
      limit: 25,
    });
    expect(response).toMatchObject(expectedContractLog);
  });
});

describe('ContractService.getContractStateChangesByTimestamps tests', () => {
  beforeEach(async () => {
    await integrationDomainOps.loadContractStateChanges([
      {
        consensus_timestamp: 1,
        contract_id: entityId3.num,
        slot: '01',
        value_read: '0101',
        value_written: 'a1a1',
      },
      {
        consensus_timestamp: 1,
        contract_id: entityId4.num,
        migration: true,
        slot: '02',
        value_read: '0202',
        value_written: 'a2a2',
      },
      {
        consensus_timestamp: 2,
        contract_id: entityId5.num,
        slot: '0a',
        value_read: '0303',
        value_written: 'a3a3',
      },
      {
        consensus_timestamp: 2,
        contract_id: entityId3.num,
        migration: true,
        slot: '0b',
        value_read: '0404',
        value_written: 'a4a4',
      },
      {
        consensus_timestamp: 3,
        contract_id: entityId4.num,
        slot: '0c',
        value_read: '0505',
        value_written: 'a5a5',
      },
      {
        consensus_timestamp: 4,
        contract_id: entityId5.num,
        slot: '0d',
        value_read: '0606',
        value_written: 'a6a6',
      },
    ]);
  });

  test('No match', async () => {
    const response = await ContractService.getContractStateChangesByTimestamps('10');
    expect(response).toBeEmpty();
  });

  test('Row match', async () => {
    const expected = [
      {
        consensusTimestamp: 1,
        contractId: entityId3.getEncodedId(),
        slot: Buffer.from([0x1]),
        valueRead: Buffer.from([0x1, 0x1]),
        valueWritten: Buffer.from([0xa1, 0xa1]),
      },
    ];
    const response = await ContractService.getContractStateChangesByTimestamps('1');
    expect(response).toMatchObject(expected);
  });

  test('Row match with contractId', async () => {
    const expected = [
      {
        consensusTimestamp: 1,
        contractId: entityId3.getEncodedId(),
        slot: Buffer.from([0x1]),
        valueRead: Buffer.from([0x1, 0x1]),
        valueWritten: Buffer.from([0xa1, 0xa1]),
      },
      {
        consensusTimestamp: 1,
        contractId: entityId4.getEncodedId(),
        slot: Buffer.from([0x2]),
        valueRead: Buffer.from([0x2, 0x2]),
        valueWritten: Buffer.from([0xa2, 0xa2]),
      },
    ];
    const response = await ContractService.getContractStateChangesByTimestamps('1', entityId4.getEncodedId());
    expect(response).toMatchObject(expected);
  });
});

describe('ContractService.getContractIdByEvmAddress tests', () => {
  test('No match', async () => {
    const evmAddressFilter = {shard: 0, realm: 0, create2_evm_address: 'deadbeaf'};
    expect(await ContractService.getContractIdByEvmAddress(evmAddressFilter)).toBeNull();
  });

  test('Multiple rows match', async () => {
    const evmAddress = 'a25db2e7595e094b1074122a048030f90b76d526';
    await integrationDomainOps.loadContracts([
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570802912691212000,
        deleted: false,
        expiration_timestamp: null,
        id: entityId111169.num,
        key: "E'\\0x326C0A221220F7ECD392568A9ECE84097C4B3C04C74AE52653D54398E132747B498B287245610A221220FA34ADAC826D3F878CCA5E4B074C7060DAE76259611543D6A876FF4E4B8B5C3A0A2212201ADBD17C33C48D59D0961356D5C0B19B391760A6504C3FC78D3094266FA290D2'",
        memo: '',
        public_key: null,
        proxy_account_id: null,
        realm: 0,
        shard: 0,
        timestamp_range: '[1570802912691212000,)',
        file_id: entityId111168.num,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: evmAddress,
      },
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570803115160698000,
        deleted: false,
        expiration_timestamp: null,
        id: entityId111278.num,
        key: "E'\\0x326C0A2212203053E42F8D8978BC5999080C4A625BBB1BF96CBCA6BAD6A4796808A6812564490A221220CC16FF9223B2E8F8151E8EFB054203CEF5EE9AF6171D2649D46734ECDD7F5A280A22122097C6975280B82DC1969ABA4E7DDE4F478E872E04CD6E0FE3849EAFB5D86315F1'",
        memo: '',
        public_key: null,
        proxy_account_id: null,
        timestamp_range: '[1570803115160698000,)',
        file_id: entityId111276.num,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: null,
      },
      {
        auto_renew_period: 7885000,
        created_timestamp: 1570803584382787001,
        deleted: false,
        expiration_timestamp: 1581285572000000000,
        id: entityId111482.num,
        key: "E'\\0x326C0A221220A13DDC50A38C7ED4A7F64CFD05E364746B8DABC3DAE8B2AFBE9A94FF2105AB1F0A2212202CECF7F1A3EADBBD678EC9D62EED162893A2069D456A4E5061E86F96C95F4FFF0A221220C6C448A8B628C11C55F773A3366D8B75E8188EEF46A50A2CCDDDA6B3B4EF55E3'",
        memo: '',
        public_key: null,
        proxy_account_id: null,
        timestamp_range: '[1570803587949346001,)',
        file_id: entityId111481.num,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: evmAddress,
      },
    ]);

    const evmAddressFilter = {create2_evm_address: evmAddress};
    await expect(() => ContractService.getContractIdByEvmAddress(evmAddressFilter)).rejects.toThrow(
      new Error(`More than one contract or account with the evm address 0x${evmAddress} have been found.`)
    );
  });

  test('One row match', async () => {
    const evmAddress = Buffer.from('1aaafd867fac5d9c228d1dbeb7f218a29c94b', 'hex');
    await integrationDomainOps.loadContracts([
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570802912691212000,
        deleted: false,
        expiration_timestamp: null,
        id: entityId111169.num,
        key: "E'\\0x326C0A221220F7ECD392568A9ECE84097C4B3C04C74AE52653D54398E132747B498B287245610A221220FA34ADAC826D3F878CCA5E4B074C7060DAE76259611543D6A876FF4E4B8B5C3A0A2212201ADBD17C33C48D59D0961356D5C0B19B391760A6504C3FC78D3094266FA290D2'",
        memo: '',
        public_key: null,
        proxy_account_id: null,
        timestamp_range: '[1570802912691212000,)',
        file_id: 111168,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: evmAddress,
      },
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570803115160698000,
        deleted: false,
        expiration_timestamp: null,
        id: entityId111278.num,
        key: "E'\\0x326C0A2212203053E42F8D8978BC5999080C4A625BBB1BF96CBCA6BAD6A4796808A6812564490A221220CC16FF9223B2E8F8151E8EFB054203CEF5EE9AF6171D2649D46734ECDD7F5A280A22122097C6975280B82DC1969ABA4E7DDE4F478E872E04CD6E0FE3849EAFB5D86315F1'",
        memo: '',
        public_key: null,
        proxy_account_id: null,
        timestamp_range: '[1570803115160698000,)',
        file_id: entityId111276.num,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: null,
      },
      {
        auto_renew_period: 7885000,
        created_timestamp: 1570803584382787001,
        deleted: false,
        expiration_timestamp: 1581285572000000000,
        id: entityId111482.num,
        key: "E'\\0x326C0A221220A13DDC50A38C7ED4A7F64CFD05E364746B8DABC3DAE8B2AFBE9A94FF2105AB1F0A2212202CECF7F1A3EADBBD678EC9D62EED162893A2069D456A4E5061E86F96C95F4FFF0A221220C6C448A8B628C11C55F773A3366D8B75E8188EEF46A50A2CCDDDA6B3B4EF55E3'",
        memo: '',
        public_key: null,
        proxy_account_id: null,
        timestamp_range: '[1570803587949346001,)',
        file_id: entityId111481.num,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: null,
      },
    ]);

    const contractId = await ContractService.getContractIdByEvmAddress({
      create2_evm_address: evmAddress,
    });
    expect(contractId.toString()).toEqual(`${entityId111169.getEncodedId()}`);
  });

  describe('ACCOUNT entities (contractIdByEvmAddressQuery includes ACCOUNT type)', () => {
    test('One ACCOUNT row match', async () => {
      const evmAddress = '11aabbcc00000000000000000000000000feedface';
      await integrationDomainOps.addAccount({
        num: entityId222001.num,
        evm_address: evmAddress,
      });

      const contractId = await ContractService.getContractIdByEvmAddress({
        create2_evm_address: evmAddress,
      });
      expect(contractId.toString()).toEqual(`${entityId222001.getEncodedId()}`);
    });

    test('Multiple ACCOUNT rows match same evm_address', async () => {
      const evmAddress = '22bbccdd00000000000000000000000000deadface';
      await integrationDomainOps.addAccount({
        num: entityId222001.num,
        evm_address: evmAddress,
      });
      await integrationDomainOps.addAccount({
        num: entityId222002.num,
        evm_address: evmAddress,
      });

      await expect(() => ContractService.getContractIdByEvmAddress({create2_evm_address: evmAddress})).rejects.toThrow(
        new Error(`More than one contract or account with the evm address 0x${evmAddress} have been found.`)
      );
    });

    test('Deleted ACCOUNT is not matched', async () => {
      const evmAddress = '33ccddee00000000000000000000000000beefface';
      await integrationDomainOps.addAccount({
        num: entityId222003.num,
        evm_address: evmAddress,
        deleted: true,
      });

      expect(await ContractService.getContractIdByEvmAddress({create2_evm_address: evmAddress})).toBeNull();
    });

    test('Two ACCOUNT rows same evm_address but one deleted returns active id', async () => {
      const evmAddress = '44ddeeff00000000000000000000000000cafef00d';
      await integrationDomainOps.addAccount({
        num: entityId222001.num,
        evm_address: evmAddress,
        deleted: true,
      });
      await integrationDomainOps.addAccount({
        num: entityId222002.num,
        evm_address: evmAddress,
        deleted: false,
      });

      const contractId = await ContractService.getContractIdByEvmAddress({
        create2_evm_address: evmAddress,
      });
      expect(contractId.toString()).toEqual(`${entityId222002.getEncodedId()}`);
    });
  });
});

const assertComputeContractIdResolvesWithoutEntityEvmLookup = async (contractIdParam, expectedEncodedId) => {
  const spy = jest.spyOn(ContractService, 'getContractIdByEvmAddress');
  await expect(ContractService.computeContractIdFromString(contractIdParam)).resolves.toEqual(expectedEncodedId);
  expect(spy).not.toHaveBeenCalled();
  spy.mockRestore();
};

describe('ContractService.computeContractIdFromString', () => {
  describe('resolves without getContractIdByEvmAddress (no entity table lookup for id)', () => {
    test('shard.realm.num', async () => {
      await assertComputeContractIdResolvesWithoutEntityEvmLookup(
        '0.0.500',
        EntityId.parseString('0.0.500', {paramName: 'contractId'}).getEncodedId()
      );
    });

    test('realm.num (default shard from config)', async () => {
      await assertComputeContractIdResolvesWithoutEntityEvmLookup(
        '500',
        EntityId.parseString('500', {paramName: 'contractId'}).getEncodedId()
      );
    });

    test('long-zero 20-byte address (12 zero bytes + entity num), stripped form as in path param', async () => {
      await assertComputeContractIdResolvesWithoutEntityEvmLookup(
        '000000000000000000000000000000000000000f',
        EntityId.parseString('15', {paramName: 'contractId'}).getEncodedId()
      );
    });

    test('long-zero address with 0x prefix (parseString strips prefix)', async () => {
      await assertComputeContractIdResolvesWithoutEntityEvmLookup(
        '0x000000000000000000000000000000000000000f',
        EntityId.parseString('15', {paramName: 'contractId'}).getEncodedId()
      );
    });
  });

  describe('opaque / create2-style EVM address uses getContractIdByEvmAddress', () => {
    test('bare 40-hex address', async () => {
      const evm = '71eaa748d5252be68c1185588beca495459fdba4';
      const spy = jest.spyOn(ContractService, 'getContractIdByEvmAddress').mockResolvedValue(99999n);
      await expect(ContractService.computeContractIdFromString(evm)).resolves.toBe(99999n);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          create2_evm_address: evm,
        })
      );
      spy.mockRestore();
    });

    test('shard.realm.opaque form (0.0.<40 hex>)', async () => {
      const evm = '71eaa748d5252be68c1185588beca495459fdba4';
      const param = `0.0.${evm}`;
      const spy = jest.spyOn(ContractService, 'getContractIdByEvmAddress').mockResolvedValue(88888n);
      await expect(ContractService.computeContractIdFromString(param)).resolves.toBe(88888n);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          create2_evm_address: evm,
        })
      );
      spy.mockRestore();
    });

    test('returns null when entity lookup finds no row', async () => {
      const evm = 'deadbeefdeadbeefdeadbeefdeadbeefdeadbeef';
      const spy = jest.spyOn(ContractService, 'getContractIdByEvmAddress').mockResolvedValue(null);
      await expect(ContractService.computeContractIdFromString(evm)).resolves.toBeNull();
      expect(spy).toHaveBeenCalled();
      spy.mockRestore();
    });
  });
});

describe('ContractService.getContractActionsByConsensusTimestamp tests', () => {
  test('No match', async () => {
    const res = await ContractService.getContractActionsByConsensusTimestamp(
      '1676540001234390005',
      2000,
      [],
      orderFilterValues.ASC,
      100
    );
    expect(res.length).toEqual(0);
  });

  test('Multiple rows match', async () => {
    await integrationDomainOps.loadContractActions([
      {consensus_timestamp: '1676540001234390005', payer_account_id: entityId2000.num},
      {consensus_timestamp: '1676540001234390005', index: 2, payer_account_id: entityId2000.num},
    ]);
    const res = await ContractService.getContractActionsByConsensusTimestamp(
      '1676540001234390005',
      entityId2000.getEncodedId(),
      [],
      orderFilterValues.ASC,
      100
    );
    expect(res.length).toEqual(2);
  });

  test('One row match', async () => {
    await integrationDomainOps.loadContractActions([
      {consensus_timestamp: '1676540001234390005', payer_account_id: entityId2000.num},
      {consensus_timestamp: '1676540001234390006', payer_account_id: entityId2000.num},
    ]);
    const res = await ContractService.getContractActionsByConsensusTimestamp(
      '1676540001234390005',
      entityId2000.getEncodedId(),
      [],
      orderFilterValues.ASC,
      100
    );
    expect(res.length).toEqual(1);
  });
});

describe('ContractService.getContractStateByIdAndFilters tests', () => {
  const contractState = [
    {contract_id: entityId9999.num, slot: '01', value: 10},
    {contract_id: entityId9999.num, slot: '02', value: 20},
  ];

  const contractStateChanges = [
    {
      consensus_timestamp: 1,
      contract_id: entityId4.num,
      slot: Buffer.from([0x1]),
      value_read: '0101',
      value_written: 'a1a1',
    },
    {
      consensus_timestamp: 4,
      contract_id: entityId4.num,
      slot: Buffer.from([0x2]),
      value_read: '0202',
      value_written: 'a2a2',
    },
    {
      consensus_timestamp: 4,
      contract_id: entityId4.num,
      slot: Buffer.from([0x3]),
      value_read: '0202',
      value_written: 'a2a2',
    },
    {
      consensus_timestamp: 8,
      contract_id: entityId4.num,
      slot: Buffer.from([0x3]),
      value_read: 'a2a2',
      value_written: 'a222',
    },
  ];

  test('No match', async () => {
    const res = await ContractService.getContractStateByIdAndFilters([{query: 'contract_id =', param: 1000}]);

    expect(res.length).toEqual(0);
  });

  test('Multiple rows match', async () => {
    await integrationDomainOps.loadContractStates(contractState);
    const res = await ContractService.getContractStateByIdAndFilters([
      {query: 'contract_id =', param: entityId9999.getEncodedId()},
    ]);

    expect(res.length).toEqual(2);
  });

  test('Multiple rows match with timestamp', async () => {
    await integrationDomainOps.loadContractStateChanges(contractStateChanges);
    const res = await ContractService.getContractStateByIdAndFilters(
      [
        {query: 'contract_id =', param: entityId4.getEncodedId()},
        {query: 'consensus_timestamp <= ', param: 5},
      ],
      orderFilterValues.ASC,
      100,
      true
    );

    expect(res.length).toEqual(3);
  });

  test('Multiple rows match with timestamp and slot', async () => {
    await integrationDomainOps.loadContractStateChanges(contractStateChanges);
    const res = await ContractService.getContractStateByIdAndFilters(
      [
        {query: 'contract_id =', param: entityId4.getEncodedId()},
        {query: 'consensus_timestamp <= ', param: 5},
        {query: 'slot =', param: Buffer.from('03', 'hex')},
      ],
      orderFilterValues.ASC,
      100,
      true
    );

    expect(res.length).toEqual(1);
  });

  test('One row match by contract_id', async () => {
    await integrationDomainOps.loadContractStates([
      {contract_id: entityId9999.num, slot: '01', value: 10},
      {contract_id: entityId9000.num, slot: '02', value: 20},
    ]);
    const res = await ContractService.getContractStateByIdAndFilters([
      {query: 'contract_id =', param: entityId9999.getEncodedId()},
    ]);

    expect(res.length).toEqual(1);
  });

  test('Multiple rows match desc order', async () => {
    await integrationDomainOps.loadContractStates(contractState);
    const res = await ContractService.getContractStateByIdAndFilters([
      {query: 'contract_id =', param: entityId9999.getEncodedId()},
    ]);

    expect(res[0].slot.readInt8()).toEqual(1);
    expect(res[1].slot.readInt8()).toEqual(2);
    expect(res.length).toEqual(2);
  });

  test('One row match (limit=1)', async () => {
    await integrationDomainOps.loadContractStates(contractState);
    const res = await ContractService.getContractStateByIdAndFilters(
      [{query: 'contract_id =', param: entityId9999.getEncodedId()}],
      orderFilterValues.ASC,
      1
    );

    expect(res.length).toEqual(1);
  });
});

describe('ContractService.getEthereumTransactionsByPayerAndTimestampArray', () => {
  test('Empty', async () => {
    await expect(ContractService.getEthereumTransactionsByPayerAndTimestampArray([], [])).resolves.toBeEmpty();
  });

  test('No match', async () => {
    const expected = new Map([]);
    await expect(
      ContractService.getEthereumTransactionsByPayerAndTimestampArray([entityId10.getEncodedId()], [20])
    ).resolves.toEqual(expected);
  });

  test('All match', async () => {
    await integrationDomainOps.loadEthereumTransactions([
      {
        consensus_timestamp: 1690086061111222333n,
        chain_id: [0x1, 0x2a],
        hash: '0x3df8d8a9891a3f94dc07c70509c4a25f0069795365ba9de8c43e214d80f48fa8',
        max_fee_per_gas: '0xc83bfe9800', // 86 * 10^10 = 860000000000
        nonce: 10,
        payer_account_id: entityId500.num,
        value: [0xa0],
      },
      {
        consensus_timestamp: 1690086061111222555n,
        chain_id: [0x1, 0x2a],
        hash: '0xd96ea0ca4474b1f92c73af999eb81b1a3df71e3c750124fbf940da5fd0ff87ab',
        max_fee_per_gas: '0x104c533c000', // 112 * 10^10 = 1120000000000
        nonce: 6,
        payer_account_id: entityId600.num,
        value: [0xa6],
      },
    ]);
    const payers = [entityId500.getEncodedId(), entityId600.getEncodedId()];
    const timestamps = [1690086061111222333n, 1690086061111222555n];
    const expected = new Map([
      [
        1690086061111222333n,
        {
          accessList: null,
          authorizationList: [],
          chainId: '012a',
          consensusTimestamp: 1690086061111222333n,
          gasPrice: 'ad78ebc5ac620000',
          maxFeePerGas: 'c83bfe9800',
          maxPriorityFeePerGas: null,
          nonce: 10,
          recoveryId: 1,
          signatureR: 'd693b532a80fed6392b428604171fb32fdbf953728a3a7ecc7d4062b1652c042',
          signatureS: '24e9c602ac800b983b035700a14b23f78a253ab762deab5dc27e3555a750b354',
          signatureV: Buffer.from('1b', 'hex'),
          toAddress: '0000000000000000000000000000000000001389',
          type: 2,
          value: 'a0',
        },
      ],
      [
        1690086061111222555n,
        {
          accessList: null,
          authorizationList: [],
          chainId: '012a',
          consensusTimestamp: 1690086061111222555n,
          gasPrice: 'ad78ebc5ac620000',
          maxFeePerGas: '0104c533c000',
          maxPriorityFeePerGas: null,
          nonce: 6,
          recoveryId: 1,
          signatureR: 'd693b532a80fed6392b428604171fb32fdbf953728a3a7ecc7d4062b1652c042',
          signatureS: '24e9c602ac800b983b035700a14b23f78a253ab762deab5dc27e3555a750b354',
          signatureV: Buffer.from('1b', 'hex'),
          toAddress: '0000000000000000000000000000000000001389',
          type: 2,
          value: 'a6',
        },
      ],
    ]);

    await expect(ContractService.getEthereumTransactionsByPayerAndTimestampArray(payers, timestamps)).resolves.toEqual(
      expected
    );
  });

  test('Excludes authorization_list when feature flag is disabled', async () => {
    const originalFlagValue = config.response.enableDelegationAddress;
    config.response.enableDelegationAddress = false;
    await integrationDomainOps.loadEthereumTransactions([
      {
        consensus_timestamp: 1690086061111222333n,
        chain_id: [0x1, 0x2a],
        hash: '0x3df8d8a9891a3f94dc07c70509c4a25f0069795365ba9de8c43e214d80f48fa8',
        max_fee_per_gas: '0xc83bfe9800', // 86 * 10^10 = 860000000000
        nonce: 10,
        payer_account_id: entityId500.num,
        value: [0xa0],
      },
    ]);
    const payer = [entityId500.getEncodedId()];
    const timestamp = [1690086061111222333n];

    const result = await ContractService.getEthereumTransactionsByPayerAndTimestampArray(payer, timestamp);
    const ethTx = result.get(1690086061111222333n);
    expect(ethTx).not.toHaveProperty('authorizationList');

    config.response.enableDelegationAddress = originalFlagValue;
  });

  test('Returns empty authorizationList when null and feature flag is enabled', async () => {
    const originalFlagValue = config.response.enableDelegationAddress;
    config.response.enableDelegationAddress = true;
    await integrationDomainOps.loadEthereumTransactions([
      {
        consensus_timestamp: 1690086061111222333n,
        chain_id: [0x1, 0x2a],
        hash: '0x3df8d8a9891a3f94dc07c70509c4a25f0069795365ba9de8c43e214d80f48fa8',
        max_fee_per_gas: '0xc83bfe9800',
        nonce: 10,
        payer_account_id: entityId500.num,
        value: [0xa0],
      },
    ]);
    const payer = [entityId500.getEncodedId()];
    const timestamp = [1690086061111222333n];

    const result = await ContractService.getEthereumTransactionsByPayerAndTimestampArray(payer, timestamp);
    const ethTx = result.get(1690086061111222333n);
    expect(ethTx.authorizationList).toEqual([]);

    config.response.enableDelegationAddress = originalFlagValue;
  });
});

describe('ContractService.getContractTransactionDetailsByHash tests', () => {
  const ethereumTxHash = '4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392';
  const ethereumTxHashBuffer = Buffer.from(ethereumTxHash, 'hex');
  const ethereumTxType = TransactionType.getProtoId('ETHEREUMTRANSACTION');
  const contractCreateType = TransactionType.getProtoId('CONTRACTCREATEINSTANCE');
  const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
  const successTransactionResult = TransactionResult.getProtoId('SUCCESS');
  const wrongNonceTransactionResult = TransactionResult.getProtoId('WRONG_NONCE');

  const inputContractResults = [
    {
      consensus_timestamp: 1,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: successTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 11,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 2,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: duplicateTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 12,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 3,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: wrongNonceTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 13,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 4,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: successTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 14,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 5,
      payer_account_id: entityId10.num,
      type: contractCreateType,
      transaction_hash: '96ecf2e0cf1c8f7e2294ec731b2ad1aff95d9736f4ba15b5bbace1ad2766cc1c',
      transaction_nonce: 15,
      gasLimit: 1000,
    },
  ];

  const expectedTransactionDetails = [
    {
      consensusTimestamp: 1,
      entityId: entityId0.getEncodedId(),
      hash: ethereumTxHashBuffer,
      payerAccountId: entityId10.getEncodedId(),
      transactionResult: Number.parseInt(successTransactionResult),
    },
    {
      consensusTimestamp: 2,
      entityId: entityId0.getEncodedId(),
      hash: ethereumTxHashBuffer,
      payerAccountId: entityId10.getEncodedId(),
      transactionResult: Number.parseInt(duplicateTransactionResult),
    },
    {
      consensusTimestamp: 3,
      entityId: entityId0.getEncodedId(),
      hash: ethereumTxHashBuffer,
      payerAccountId: entityId10.getEncodedId(),
      transactionResult: Number.parseInt(wrongNonceTransactionResult),
    },
    {
      consensusTimestamp: 4,
      entityId: entityId0.getEncodedId(),
      hash: ethereumTxHashBuffer,
      payerAccountId: entityId10.getEncodedId(),
      transactionResult: Number.parseInt(successTransactionResult),
    },
  ];

  beforeEach(async () => {
    await integrationDomainOps.loadContractResults(inputContractResults);
  });

  test('No match', async () => {
    const contractDetails = await ContractService.getContractTransactionDetailsByHash(
      Buffer.from('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6393', 'hex')
    );

    expect(contractDetails).toHaveLength(0);
  });

  test('Match latest transaction by same hash', async () => {
    const transactionDetails = await ContractService.getContractTransactionDetailsByHash(ethereumTxHashBuffer);
    expect(transactionDetails).toEqual([expectedTransactionDetails[3]]);
  });

  test('Match latest transaction with no duplicates and wrong nonces', async () => {
    const transactionDetails = await ContractService.getContractTransactionDetailsByHash(ethereumTxHashBuffer);
    expect(transactionDetails).toEqual([expectedTransactionDetails[3]]);
  });
});

describe('ContractService.getContractTransactionDetailsByHash negative tests', () => {
  const ethereumTxHash = '4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392';
  const ethereumTxHashBuffer = Buffer.from(ethereumTxHash, 'hex');
  const ethereumTxType = TransactionType.getProtoId('ETHEREUMTRANSACTION');
  const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
  const consensusGasExhaustedTransactionResult = TransactionResult.getProtoId('CONSENSUS_GAS_EXHAUSTED');

  const inputContractResults = [
    {
      consensus_timestamp: 1,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: duplicateTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 11,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 2,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: consensusGasExhaustedTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 12,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 3,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: consensusGasExhaustedTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 12,
      gasLimit: 1000,
    },
  ];

  const expectedTransactionDetails = [
    {
      consensusTimestamp: 1,
      entityId: entityId0.getEncodedId(),
      hash: ethereumTxHashBuffer,
      payerAccountId: entityId10.getEncodedId(),
      transactionResult: Number.parseInt(duplicateTransactionResult),
    },
    {
      consensusTimestamp: 2,
      entityId: entityId0.getEncodedId(),
      hash: ethereumTxHashBuffer,
      payerAccountId: entityId10.getEncodedId(),
      transactionResult: Number.parseInt(consensusGasExhaustedTransactionResult),
    },
    {
      consensusTimestamp: 3,
      entityId: entityId0.getEncodedId(),
      hash: ethereumTxHashBuffer,
      payerAccountId: entityId10.getEncodedId(),
      transactionResult: Number.parseInt(consensusGasExhaustedTransactionResult),
    },
  ];

  beforeEach(async () => {
    await integrationDomainOps.loadContractResults(inputContractResults);
  });

  test('Match the latest non successful transaction', async () => {
    const transactionDetails = await ContractService.getContractTransactionDetailsByHash(ethereumTxHashBuffer);
    expect(transactionDetails).toEqual([expectedTransactionDetails[2]]);
  });
});

describe('ContractService.getInvolvedContractsByTimestampAndContractId tests', () => {
  const ethereumTxHash = '4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392';
  const ethereumTxType = TransactionType.getProtoId('ETHEREUMTRANSACTION');
  const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
  const successTransactionResult = TransactionResult.getProtoId('SUCCESS');
  const wrongNonceTransactionResult = TransactionResult.getProtoId('WRONG_NONCE');

  const inputContractResults = [
    {
      consensus_timestamp: 1,
      contract_id: entityId1.num,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: successTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 11,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 2,
      contract_id: 1,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: duplicateTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 12,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 3,
      contract_id: entityId1.num,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: wrongNonceTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 13,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 4,
      contract_id: entityId1.num,
      payer_account_id: entityId10.num,
      type: ethereumTxType,
      transaction_result: successTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      transaction_nonce: 14,
      gasLimit: 1000,
    },
  ];

  const inputContractStateChanges = [
    {
      consensus_timestamp: 1,
      contract_id: entityId10.num,
      payer_account_id: entityId10.num,
      slot: '01',
      value_read: '0101',
      value_written: 'a1a1',
    },
    {
      consensus_timestamp: 1,
      contract_id: entityId11.num,
      migration: true,
      payer_account_id: entityId10.num,
      slot: '02',
      value_read: '0202',
      value_written: 'a2a2',
    },
  ];

  const inputContractLogs = [
    {
      consensus_timestamp: 1,
      contract_id: entityId22.num,
      data: '0x0012',
      index: 0,
      payer_account_id: entityId10.num,
      root_contract_id: entityId1.num,
      topic0: '0x000a',
    },
    {
      consensus_timestamp: 1,
      contract_id: entityId21.num,
      data: '0x0013',
      index: 1,
      payer_account_id: entityId10.num,
      root_contract_id: entityId1.num,
      topic0: '0x000b',
    },
  ];

  beforeEach(async () => {
    await integrationDomainOps.loadContractResults(inputContractResults);
    await integrationDomainOps.loadContractTransactions(
      inputContractLogs,
      inputContractResults,
      inputContractStateChanges
    );
  });

  test('No match', async () => {
    const contractDetails = await ContractService.getInvolvedContractsByTimestampAndContractId(1, 0);

    expect(contractDetails).toBeNull();
  });

  test('Missing timestamp', async () => {
    const contractDetails = await ContractService.getInvolvedContractsByTimestampAndContractId(undefined, 1);

    expect(contractDetails).toBeNull();
  });

  test('Missing contractId', async () => {
    const contractDetails = await ContractService.getInvolvedContractsByTimestampAndContractId(1, undefined);

    expect(contractDetails).toBeNull();
  });

  test('Finds involved contract ids', async () => {
    const transactionDetails = await ContractService.getInvolvedContractsByTimestampAndContractId(
      1,
      entityId1.getEncodedId()
    );
    const expected = [
      entityId1.getEncodedId(),
      entityId21.getEncodedId(),
      entityId22.getEncodedId(),
      entityId11.getEncodedId(),
      entityId10.getEncodedId(),
    ];
    expect(transactionDetails.contractIds).toIncludeSameMembers(expected);
  });
});
