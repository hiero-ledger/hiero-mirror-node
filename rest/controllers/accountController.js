// SPDX-License-Identifier: Apache-2.0

import Bound from './bound';
import BaseController from './baseController';
import {getResponseLimit} from '../config';
import {filterKeys, orderFilterValues, responseDataLabel} from '../constants';
import {InvalidArgumentError, NotFoundError} from '../errors';
import {StakingRewardTransfer} from '../model';
import {EntityService, NftService, StakingRewardTransferService} from '../service';
import {NftViewModel, StakingRewardTransferViewModel} from '../viewmodel';
import * as utils from '../utils';
import _ from 'lodash';

const {default: defaultLimit} = getResponseLimit();

class AccountController extends BaseController {
  validateFilters(bounds, spenderIdFilters) {
    this.validateBounds(bounds);

    const spenderOperators = spenderIdFilters.map((f) => f.operator);
    if (
      spenderOperators.filter((o) => o === utils.opsMap.lte || o === utils.opsMap.lt).length > 1 ||
      spenderOperators.filter((o) => o === utils.opsMap.gte || o === utils.opsMap.gt).length > 1
    ) {
      throw new InvalidArgumentError(`Multiple range params not allowed for spender.id`);
    }

    if (spenderIdFilters.some((f) => f.operator === utils.opsMap.ne)) {
      throw new InvalidArgumentError(`Not equals (ne) comparison operator is not supported`);
    }
  }

  extractNftMultiUnionQuery(filters, ownerAccountId) {
    const bounds = {
      primary: new Bound(filterKeys.TOKEN_ID, 'token_id'),
      secondary: new Bound(filterKeys.SERIAL_NUMBER, 'serial_number'),
    };
    let limit = defaultLimit;
    let order = orderFilterValues.DESC;
    const spenderIdFilters = [];
    const spenderIdInFilters = [];

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.SERIAL_NUMBER:
          bounds.secondary.parse(filter);
          break;
        case filterKeys.TOKEN_ID:
          bounds.primary.parse(filter);
          break;
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        case filterKeys.SPENDER_ID:
          filter.operator === utils.opsMap.eq ? spenderIdInFilters.push(filter) : spenderIdFilters.push(filter);
          break;
        default:
          break;
      }
    }

    this.validateFilters(bounds, spenderIdFilters);

    const lower = this.getLowerFilters(bounds);
    const inner = this.getInnerFilters(bounds);
    const upper = this.getUpperFilters(bounds);
    return {
      bounds,
      lower,
      inner,
      upper,
      order,
      ownerAccountId,
      limit,
      spenderIdInFilters,
      spenderIdFilters,
    };
  }

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/nfts API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getNftsByAccountId = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const filters = utils.buildAndValidateFilters(req.query, acceptedNftAccountParameters);
    const query = this.extractNftMultiUnionQuery(filters, accountId);
    const nonFungibleTokens = await NftService.getNfts(query);
    const nfts = nonFungibleTokens.map((nft) => new NftViewModel(nft));

    res.locals[responseDataLabel] = {
      nfts,
      links: {
        next: this.getPaginationLink(req, nfts, query.bounds, query.limit, query.order),
      },
    };
  };

  /**
   * Extracts SQL where conditions, params, order, and limit
   *
   * @param {[]} filters parsed and validated filters
   */
  extractStakingRewardsQuery(filters) {
    let limit = defaultLimit;
    let order = orderFilterValues.DESC;
    const timestampInValues = [];
    const conditions = [];
    const params = [];
    const startPosition = 3; // caller will later insert accountId at $1 and limit at $2

    for (const filter of filters) {
      if (_.isNil(filter)) {
        continue;
      }

      switch (filter.key) {
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        case filterKeys.TIMESTAMP:
          if (utils.opsMap.ne === filter.operator) {
            throw new InvalidArgumentError(`Not equals (ne) operator is not supported for ${filterKeys.TIMESTAMP}`);
          }
          this.updateConditionsAndParamsWithInValues(
            filter,
            timestampInValues,
            params,
            conditions,
            StakingRewardTransfer.getFullName(StakingRewardTransfer.CONSENSUS_TIMESTAMP),
            startPosition + params.length
          );
          break;
        default:
          break;
      }
    }

    this.updateQueryFiltersWithInValues(
      params,
      conditions,
      timestampInValues,
      StakingRewardTransfer.getFullName(StakingRewardTransfer.CONSENSUS_TIMESTAMP),
      startPosition + params.length
    );

    return {
      order,
      limit,
      conditions,
      params,
    };
  }

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/rewards API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  listStakingRewardsByAccountId = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const isValidAccount = await EntityService.isValidAccount(accountId);
    if (!isValidAccount) {
      throw new NotFoundError();
    }
    const filters = utils.buildAndValidateFilters(req.query, acceptedRewardsParameters);
    const query = this.extractStakingRewardsQuery(filters);
    // insert account id at $1, and limit (at $2)
    query.params.unshift(accountId, query.limit);
    const stakingRewardsTransfers = await StakingRewardTransferService.getRewards(
      query.order,
      query.limit,
      query.conditions,
      query.params
    );
    const rewards = stakingRewardsTransfers.map((reward) => new StakingRewardTransferViewModel(reward));
    const response = {
      rewards,
      links: {
        next: null,
      },
    };

    if (response.rewards.length === query.limit) {
      const lastRow = _.last(response.rewards);
      const last = {
        [filterKeys.TIMESTAMP]: lastRow.timestamp,
      };
      response.links.next = utils.getPaginationLink(req, false, last, query.order);
    }

    res.locals[responseDataLabel] = response;
  };
}

const acceptedNftAccountParameters = new Set([
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.SERIAL_NUMBER,
  filterKeys.SPENDER_ID,
  filterKeys.TOKEN_ID,
]);

const acceptedRewardsParameters = new Set([filterKeys.LIMIT, filterKeys.ORDER, filterKeys.TIMESTAMP]);

export default new AccountController();
