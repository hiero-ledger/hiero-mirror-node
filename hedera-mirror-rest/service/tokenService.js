// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import quickLru from 'quick-lru';

import BaseService from './baseService';
import config from '../config';
import {CachedToken, TokenAccount} from '../model';
import {OrderSpec} from '../sql';
import {isTestEnv} from '../utils';

const tokenCache = new quickLru({
  maxSize: config.cache.token.maxSize,
});

/**
 * Token retrieval business logic
 */
class TokenService extends BaseService {
  static tokenRelationshipsQuery = `
        select ${TokenAccount.getFullName(TokenAccount.AUTOMATIC_ASSOCIATION)},
               ${TokenAccount.getFullName(TokenAccount.BALANCE)},
               ${TokenAccount.getFullName(TokenAccount.CREATED_TIMESTAMP)},
               ${TokenAccount.getFullName(TokenAccount.FREEZE_STATUS)},
               ${TokenAccount.getFullName(TokenAccount.KYC_STATUS)},
               ${TokenAccount.getFullName(TokenAccount.TOKEN_ID)}
        from ${TokenAccount.tableName} ${TokenAccount.tableAlias}
        where ${TokenAccount.tableAlias}.${TokenAccount.ACCOUNT_ID} = $1
        and ${TokenAccount.tableAlias}.${TokenAccount.ASSOCIATED} = true `;

  static tokenCacheQuery = `
    select
      decimals,
      freeze_status,
      kyc_status,
      token_id
    from token
    where token_id = any ($1)`;

  /**
   * Gets the full sql query and params to retrieve an account's token relationships
   *
   * @param query
   * @return {{sqlQuery: string, params: *[]}}
   */
  getTokenRelationshipsQuery(query) {
    const {conditions, inConditions, order, ownerAccountId, limit} = query;
    const params = [ownerAccountId, limit];
    // This is the inner query to get the latest balance for a token, account pair.
    const moreConditionsExist = conditions.length > 0 ? ` and ` : ``;
    const conditionClause =
      moreConditionsExist +
      conditions
        .map((condition) => {
          params.push(condition.value);
          return `${TokenAccount.getFullName(condition.key)} ${condition.operator} $${params.length}`;
        })
        .join(' and ');
    let inConditionClause = this.getInClauseSubQuery(inConditions, params);
    const limitClause = super.getLimitQuery(2);
    const orderClause = this.getOrderByQuery(OrderSpec.from(TokenAccount.getFullName(TokenAccount.TOKEN_ID), order));
    const sqlQuery = [
      TokenService.tokenRelationshipsQuery,
      conditionClause,
      inConditionClause,
      orderClause,
      limitClause,
    ].join('\n');
    return {sqlQuery, params};
  }

  /**
   * Gets the In clause query for Token.id
   * @param inConditions
   * @param params
   * @returns {string}
   */
  getInClauseSubQuery(inConditions, params) {
    const tokenIdInParams = [];
    inConditions.forEach((condition) => {
      tokenIdInParams.push(condition.value);
    });

    if (!_.isEmpty(tokenIdInParams)) {
      return ` and ${TokenAccount.getFullName(TokenAccount.TOKEN_ID)} in (${tokenIdInParams})`;
    }
  }

  /**
   * Gets the token accounts for the query as well as the decimal from the token
   * table.
   *
   * @param query
   * @return {Promise<TokenAccount[]>}
   */
  async getTokenAccounts(query) {
    const {sqlQuery, params} = this.getTokenRelationshipsQuery(query);
    const rows = await super.getRows(sqlQuery, params);
    if (rows.length === 0) {
      return [];
    }

    const tokenIds = rows.reduce((result, row) => result.add(row.token_id), new Set());
    const cachedTokens = await this.getCachedTokens(tokenIds);
    return rows.map((row) => {
      const cachedToken = cachedTokens.get(row.token_id);
      if (cachedToken) {
        row.decimals = cachedToken.decimals;
        row.freeze_status = row.freeze_status ?? cachedToken.freezeStatus;
        row.kyc_status = row.kyc_status ?? cachedToken.kycStatus;
      }

      return new TokenAccount(row);
    });
  }

  /**
   * Adds a value to the token cache if not exists
   * @param {Object} token - token object returned from SQL query
   */
  putTokenCache(token) {
    const tokenId = token.token_id;
    if (tokenCache.has(tokenId)) {
      return;
    }

    const cachedToken = new CachedToken(token);
    tokenCache.set(tokenId, cachedToken);
  }

  /**
   * Gets the token cache for a list of token ids
   * @param {Set<BigInt>} tokenIds
   * @return {Promise<Map<BigInt, CachedToken>>}
   */
  async getCachedTokens(tokenIds) {
    const cachedTokens = new Map();
    const uncachedTokenIds = [];
    tokenIds.forEach((tokenId) => {
      const cachedToken = tokenCache.get(tokenId);
      if (cachedToken) {
        cachedTokens.set(tokenId, cachedToken);
      } else {
        uncachedTokenIds.push(tokenId);
      }
    });

    if (uncachedTokenIds.length === 0) {
      return cachedTokens;
    }

    const rows = await super.getRows(TokenService.tokenCacheQuery, [uncachedTokenIds]);
    rows.forEach((row) => {
      const tokenId = row.token_id;
      const cachedToken = new CachedToken(row);
      tokenCache.set(tokenId, cachedToken);
      cachedTokens.set(tokenId, cachedToken);
    });

    return cachedTokens;
  }
}

if (isTestEnv()) {
  TokenService.prototype.clearTokenCache = () => tokenCache.clear();
}

export default new TokenService();
