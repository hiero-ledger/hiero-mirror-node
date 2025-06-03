// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import AccountAlias from '../accountAlias';
import BaseService from './baseService';
import {filterKeys} from '../constants';
import EntityId from '../entityId';
import {InvalidArgumentError, NotFoundError} from '../errors';
import {Entity} from '../model';

/**
 * Entity retrieval business logic
 */
class EntityService extends BaseService {
  static entityFromAliasQuery = `select ${Entity.ID}
                                 from ${Entity.tableName}
                                 where coalesce(${Entity.DELETED}, false) <> true
                                   and ${Entity.ALIAS} = $1`;

  static entityFromEvmAddressQuery = `select ${Entity.ID}
                                      from ${Entity.tableName}
                                      where ${Entity.DELETED} <> true
                                        and ${Entity.EVM_ADDRESS} = $1`;

  // use a small column in existence check to reduce return payload size
  static entityExistenceQuery = `select ${Entity.TYPE}
                                 from ${Entity.tableName}
                                 where ${Entity.ID} = $1`;

  static missingAccountAlias = 'No account with a matching alias found';
  static multipleAliasMatch = `Multiple alive entities matching alias`;
  static multipleEvmAddressMatch = `Multiple alive entities matching evm address`;

  /**
   * Retrieves the entity containing matching the given alias
   *
   * @param {AccountAlias} accountAlias accountAlias
   * @return {Promise<Entity>} raw entity object
   */
  async getAccountFromAlias(accountAlias) {
    const rows = await super.getRows(EntityService.entityFromAliasQuery, [accountAlias.alias]);

    if (_.isEmpty(rows)) {
      return null;
    } else if (rows.length > 1) {
      logger.error(`Incorrect db state: ${rows.length} alive entities matching alias ${accountAlias}`);
      throw new Error(EntityService.multipleAliasMatch);
    }

    return new Entity(rows[0]);
  }

  /**
   * Checks if provided accountId maps to a valid entity
   * @param {BigInt|Number} accountId
   * @returns {Promise<Boolean>} valid flag
   */
  async isValidAccount(accountId) {
    const entity = await super.getSingleRow(EntityService.entityExistenceQuery, [accountId]);
    return !_.isNil(entity);
  }

  /**
   * Gets the encoded account id from the account alias string.
   * @param {AccountAlias} accountAlias the account alias object
   * @param {Boolean} requireResult
   * @return {Promise<BigInt>}
   */
  async getAccountIdFromAlias(accountAlias, requireResult = true) {
    const entity = await this.getAccountFromAlias(accountAlias);
    if (_.isNil(entity)) {
      if (requireResult) {
        throw new NotFoundError(EntityService.missingAccountAlias);
      }
      return null;
    }

    return entity.id;
  }

  /**
   * Gets the encoded entity id from the evm address.
   *
   * @param {EntityId} entityId
   * @param {Boolean} requireResult
   * @return {Promise<BigInt|Number>}
   */
  async getEntityIdFromEvmAddress(entityId, requireResult = true) {
    const rows = await this.getRows(EntityService.entityFromEvmAddressQuery, [Buffer.from(entityId.evmAddress, 'hex')]);
    if (rows.length === 0) {
      if (requireResult) {
        throw new NotFoundError();
      }

      return null;
    } else if (rows.length > 1) {
      logger.error(`Incorrect db state: ${rows.length} alive entities matching evm address ${entityId}`);
      throw new Error(EntityService.multipleEvmAddressMatch);
    }

    return rows[0].id;
  }

  /**
   * Retrieve the encodedId of a validated EntityId from a shard.realm.num string, encoded id string, evm address with
   * optional shard and realm, or alias string.
   * Throws {@link InvalidArgumentError} if the entity id string is invalid
   * Throws {@link NotFoundError} if the account is not present when retrieving by alias or the entity is not present and requireResult is true
   * when retrieving by evm address
   *
   * @param {String} entityIdString
   * @param {Boolean} requireResult
   * @param {String} paramName the parameter name
   * @returns {Promise<BigInt|Number>} entityId
   */
  async getEncodedId(entityIdString, requireResult = true, paramName = filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS) {
    try {
      if (EntityId.isValidEntityId(entityIdString)) {
        const entityId = EntityId.parseString(entityIdString, {paramName});
        return entityId.evmAddress === null
          ? entityId.getEncodedId()
          : await this.getEntityIdFromEvmAddress(entityId, requireResult);
      } else if (AccountAlias.isValid(entityIdString)) {
        return await this.getAccountIdFromAlias(AccountAlias.fromString(entityIdString), requireResult);
      }
    } catch (ex) {
      if (ex instanceof InvalidArgumentError) {
        throw InvalidArgumentError.forParams(paramName);
      }
      // rethrow
      throw ex;
    }

    throw InvalidArgumentError.forParams(paramName);
  }
}

export default new EntityService();
