// SPDX-License-Identifier: Apache-2.0

import {filterKeys, orderFilterValues, responseDataLabel} from '../constants';
import BaseController from './baseController';
import {InvalidArgumentError, NotFoundError} from '../errors';
import {EntityService, TokenService} from '../service';
import * as utils from '../utils';
import {TokenRelationshipViewModel} from '../viewmodel';
import {TokenAccount} from '../model';
import {getResponseLimit} from '../config';
import _ from 'lodash';

const {default: defaultLimit} = getResponseLimit();

class TokenController extends BaseController {
  /**
   * Extracts the sql where clause, params, order and limit values to be used from the provided token relationship query.
   * @param {[]} filters req filters
   * @param {BigInt} ownerAccountId Encoded owner entityId
   * @returns {conditions:{key:'token.id', operator:'=', value:10}, order: 'asc'|'desc',accountId: BigInt, limit: number}
   */
  extractTokensRelationshipQuery = (filters, ownerAccountId) => {
    const conditions = [];
    const inConditions = [];
    let limit = defaultLimit;
    let order = orderFilterValues.ASC;

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.TOKEN_ID:
          if (utils.opsMap.ne === filter.operator) {
            throw new InvalidArgumentError(`Not equal (ne) comparison operator is not supported for ${filter.key}`);
          }
          if (utils.opsMap.eq === filter.operator) {
            inConditions.push({key: TokenAccount.TOKEN_ID, operator: filter.operator, value: filter.value});
          } else {
            conditions.push({key: TokenAccount.TOKEN_ID, operator: filter.operator, value: filter.value});
          }
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

    return {
      conditions,
      inConditions,
      order,
      ownerAccountId,
      limit,
    };
  };

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/tokens API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getTokenRelationships = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const isValidAccount = await EntityService.isValidAccount(accountId);
    if (!isValidAccount) {
      throw new NotFoundError();
    }
    const filters = utils.buildAndValidateFilters(req.query, acceptedTokenParameters);
    const query = this.extractTokensRelationshipQuery(filters, accountId);
    const tokenRelationships = await TokenService.getTokenAccounts(query);
    const tokens = tokenRelationships.map((token) => new TokenRelationshipViewModel(token));

    let nextLink = null;
    if (tokens.length === query.limit) {
      const lastRow = _.last(tokens);
      const last = {
        [filterKeys.TOKEN_ID]: lastRow.token_id,
      };
      nextLink = utils.getPaginationLink(req, false, last, query.order);
    }

    res.locals[responseDataLabel] = {
      tokens,
      links: {
        next: nextLink,
      },
    };
  };
}

const acceptedTokenParameters = new Set([filterKeys.LIMIT, filterKeys.ORDER, filterKeys.TOKEN_ID]);

export default new TokenController();
