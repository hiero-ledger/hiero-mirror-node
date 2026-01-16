// SPDX-License-Identifier: Apache-2.0

import BaseService from '../../service/baseService';
import {OrderSpec} from '../../sql';

describe('getOrderByQuery', () => {
  const baseService = new BaseService();

  test('single', () => {
    expect(baseService.getOrderByQuery(OrderSpec.from('a', 'asc'))).toEqual('order by a asc');
  });

  test('multiple', () => {
    expect(baseService.getOrderByQuery(OrderSpec.from('a', 'asc'), OrderSpec.from('b', 'desc'))).toEqual(
      'order by a asc, b desc'
    );
  });
});

test('getLimitQuery', () => {
  const baseService = new BaseService();
  expect(baseService.getLimitQuery(1)).toEqual('limit $1');
});
