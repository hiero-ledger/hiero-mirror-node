// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import {getResponseLimit} from '../config';
import {filterKeys, orderFilterValues, responseDataLabel} from '../constants';
import BaseController from './baseController';
import {InvalidArgumentError} from '../errors';
import {CryptoAllowance} from '../model';
import {CryptoAllowanceService, EntityService} from '../service';
import * as utils from '../utils';
import {CryptoAllowanceViewModel} from '../viewmodel';

const {default: defaultLimit} = getResponseLimit();

class CryptoAllowanceController extends BaseController {
  static ownerCondition = `${CryptoAllowance.OWNER} = $1`;
  static amountCondition = `${CryptoAllowance.AMOUNT} > 0`;

  /**
   * Extracts SQL where conditions, params, order, and limit from crypto allowances query
   *
   * @param {[]} filters parsed and validated filters
   * @param {Number} accountId parsed accountId from path
   */
  extractCryptoAllowancesQuery = (filters, accountId) => {
    let limit = defaultLimit;
    let order = orderFilterValues.DESC;
    const conditions = [CryptoAllowanceController.ownerCondition];
    const params = [accountId];
    const spenderInValues = [];

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.SPENDER_ID:
          if (utils.opsMap.ne === filter.operator) {
            throw new InvalidArgumentError(`Not equal (ne) comparison operator is not supported for ${filter.key}`);
          }
          this.updateConditionsAndParamsWithInValues(
            filter,
            spenderInValues,
            params,
            conditions,
            CryptoAllowance.SPENDER,
            conditions.length + 1
          );
          break;
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        default:
          break;
      }
    }

    this.updateQueryFiltersWithInValues(params, conditions, spenderInValues, CryptoAllowance.SPENDER);
    conditions.push(CryptoAllowanceController.amountCondition);

    return {
      conditions,
      params,
      order,
      limit,
    };
  };

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/allowances/crypto API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getAccountCryptoAllowances = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const filters = utils.buildAndValidateFilters(req.query, acceptedCryptoAllowanceParameters);
    const {conditions, params, order, limit} = this.extractCryptoAllowancesQuery(filters, accountId);
    const allowances = await CryptoAllowanceService.getAccountCryptoAllowances(conditions, params, order, limit);

    const response = {
      allowances: allowances.map((allowance) => new CryptoAllowanceViewModel(allowance)),
      links: {
        next: null,
      },
    };

    if (response.allowances.length === limit) {
      const lastRow = _.last(response.allowances);
      const lastValues = {
        [filterKeys.SPENDER_ID]: lastRow.spender,
      };
      response.links.next = utils.getPaginationLink(req, false, lastValues, order);
    }

    res.locals[responseDataLabel] = response;
  };
}

const acceptedCryptoAllowanceParameters = new Set([filterKeys.LIMIT, filterKeys.ORDER, filterKeys.SPENDER_ID]);

export default new CryptoAllowanceController();
