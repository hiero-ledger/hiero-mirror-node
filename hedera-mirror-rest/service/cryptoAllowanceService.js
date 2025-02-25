// SPDX-License-Identifier: Apache-2.0

import BaseService from './baseService';
import {CryptoAllowance} from '../model';
import {OrderSpec} from '../sql';

/**
 * CryptoAllowance business model
 */
class CryptoAllowanceService extends BaseService {
  static accountAllowanceQuery = `select * from ${CryptoAllowance.tableName}`;

  async getAccountCryptoAllowances(conditions, initParams, order, limit) {
    const {query, params} = this.getAccountAllowancesQuery(conditions, initParams, order, limit);
    const rows = await super.getRows(query, params);
    return rows.map((ca) => new CryptoAllowance(ca));
  }

  getAccountAllowancesQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;
    params.push(limit);
    const query = [
      CryptoAllowanceService.accountAllowanceQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(OrderSpec.from(CryptoAllowance.SPENDER, order)),
      super.getLimitQuery(params.length),
    ].join('\n');

    return {query, params};
  }
}

export default new CryptoAllowanceService();
