// SPDX-License-Identifier: Apache-2.0

import {getResponseLimit} from '../../config';
import * as constants from '../../constants';
import {BlockController} from '../../controllers';

const {default: defaultLimit} = getResponseLimit();

describe('Block Controller', () => {
  test('Verify extractOrderFromFilters', async () => {
    const order = BlockController.extractOrderFromFilters([]);
    expect(order).toEqual(constants.orderFilterValues.DESC);
  });

  test('Verify extractOrderFromFilters with param asc', async () => {
    const order = BlockController.extractOrderFromFilters([{key: 'order', operator: '=', value: 'asc'}]);
    expect(order).toEqual('asc');
  });

  test('Verify extractLimitFromFilters', async () => {
    const limit = BlockController.extractLimitFromFilters({});
    expect(limit).toEqual(defaultLimit);
  });

  test('Verify extractLimitFromFilters with param', async () => {
    const limit = BlockController.extractLimitFromFilters([{key: 'limit', operator: '=', value: 50}]);
    expect(limit).toEqual(50);
  });

  test('Verify extractSqlFromBlockFilters', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters([]);
    expect(queryObj).toEqual({order: 'desc', orderBy: 'consensus_end', limit: 25, whereQuery: []});
  });

  test('Verify extractSqlFromBlockFilters with block.number, order and limit params', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters([
      {key: 'block.number', operator: '>', value: 10},
      {key: 'order', operator: '=', value: 'asc'},
      {key: 'limit', operator: '=', value: 10},
    ]);

    expect(queryObj.order).toEqual('asc');
    expect(queryObj.orderBy).toEqual('index');
    expect(queryObj.limit).toEqual(10);
    expect(queryObj.whereQuery[0].query).toEqual('index >');
    expect(queryObj.whereQuery[0].param).toEqual(10);
  });

  test('Verify extractSqlFromBlockFilters with block.number, timestamp, order and limit params', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters([
      {key: 'block.number', operator: '>=', value: 10},
      {key: 'timestamp', operator: '<', value: '1676540001.234810000'},
      {key: 'order', operator: '=', value: 'asc'},
      {key: 'limit', operator: '=', value: 10},
    ]);

    expect(queryObj.order).toEqual('asc');
    expect(queryObj.orderBy).toEqual('index');
    expect(queryObj.limit).toEqual(10);
    expect(queryObj.whereQuery[0].query).toEqual('index >=');
    expect(queryObj.whereQuery[0].param).toEqual(10);
    expect(queryObj.whereQuery[1].query).toEqual('consensus_end <');
    expect(queryObj.whereQuery[1].param).toEqual('1676540001.234810000');
  });

  test('Verify extractSqlFromBlockFilters with timestamp and block.number', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters([
      {key: 'timestamp', operator: '<', value: '1676540001.234810000'},
      {key: 'block.number', operator: '>=', value: 10},
    ]);

    expect(queryObj.orderBy).toEqual('consensus_end');
  });

  test('Verify getFilterWhereCondition', async () => {
    const whereConditions = BlockController.getFilterWhereCondition('index', {operator: '=', value: 10});
    expect(whereConditions).toHaveProperty('query');
    expect(whereConditions).toHaveProperty('param');
    expect(whereConditions.query).toEqual('index =');
    expect(whereConditions.param).toEqual(10);
  });
});
