// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import quickLru from 'quick-lru';

import {getMirrorConfig} from './config';
import * as constants from './constants';
import {InvalidArgumentError} from './errors';
import {stripHexPrefix} from './utils.js';

const {
  common: {realm: systemRealm, shard: systemShard},
  rest: {
    cache: {entityId: entityIdCacheConfig},
  },
} = getMirrorConfig();

// format: |10-bit shard|16-bit realm|38-bit num|
const numBits = 38n;
const numMask = 2n ** numBits - 1n;
const maxNum = 2n ** numBits - 1n;

const realmBits = 16n;
const realmMask = 2n ** realmBits - 1n;
const realmScale = 2 ** Number(numBits);
const maxRealm = 2n ** realmBits - 1n;
const maxSafeRealm = 32767;

const shardBits = 10;
const shardOffset = numBits + realmBits;
const maxShard = 2n ** BigInt(shardBits) - 1n;

const maxEncodedId = 2n ** 63n - 1n;
const minEncodedId = BigInt.asIntN(64, 1n << 63n);

const entityIdRegex = /^(\d{1,4}\.)?\d{1,5}\.\d{1,12}$/;
const encodedEntityIdRegex = /^-?\d{1,19}$/;
const evmAddressShardRealmRegex = /^(\d{1,4}\.)?(\d{1,5}\.)?[A-Fa-f0-9]{40}$/;
const evmAddressRegex = /^(0x)?[A-Fa-f0-9]{40}$/;

class EntityId {
  /**
   * Creates an EntityId instance
   *
   * @param {Number|null} shard
   * @param {Number|null} realm
   * @param {Number|null} num
   * @param {string|null} evmAddress The hex encoded non-parsable evm address, without 0x prefix
   */
  constructor(shard, realm, num, evmAddress) {
    this.shard = shard;
    this.realm = realm;
    this.num = num;
    this.evmAddress = evmAddress;
  }

  /**
   * Encodes the shard.realm.num entity id into an integer. Returns null if num is null; returns a
   * number if the encoded integer is not larger than Number.MAX_SAFE_INTEGER; returns a BigInt otherwise.
   *
   * @returns {Number|BigInt|null} encoded id corresponding to this EntityId.
   */
  getEncodedId() {
    if (this.encodedId === undefined) {
      if (this.num === null) {
        this.encodedId = null;
      } else {
        this.encodedId =
          this.shard === 0 && this.realm <= maxSafeRealm
            ? this.realm * realmScale + this.num
            : BigInt.asIntN(
                64,
                (BigInt(this.shard) << shardOffset) | (BigInt(this.realm) << numBits) | BigInt(this.num)
              );
      }
    }
    return this.encodedId;
  }

  isAllZero() {
    return this.shard === 0 && this.realm === 0 && this.num === 0;
  }

  /**
   * Converts the entity id to the 20-byte EVM address in hex with '0x' prefix
   */
  toEvmAddress() {
    if (this.evmAddress) {
      return `${constants.HEX_PREFIX}${this.evmAddress}`;
    }

    if (this.isAllZero()) {
      return null;
    }

    // shard, realm, and num take 4, 8, and 8 bytes respectively from the left
    return this.num === null
      ? null
      : [
          constants.HEX_PREFIX,
          toHex(this.shard).padStart(8, '0'),
          toHex(this.realm).padStart(16, '0'),
          toHex(this.num).padStart(16, '0'),
        ].join('');
  }

  toString() {
    if (this.isAllZero()) {
      return null;
    }

    if (this.num === null && this.evmAddress === null) {
      return null;
    }

    return [this.shard, this.realm, this.num, this.evmAddress].filter((x) => x !== null).join('.');
  }
}

const toHex = (num) => {
  return num.toString(16);
};

const isValidEvmAddress = (address, evmAddressType = constants.EvmAddressType.ANY) => {
  if (typeof address !== 'string') {
    return false;
  }

  if (evmAddressType === constants.EvmAddressType.ANY) {
    return evmAddressRegex.test(address) || evmAddressShardRealmRegex.test(address);
  }
  if (evmAddressType === constants.EvmAddressType.NO_SHARD_REALM) {
    return evmAddressRegex.test(address);
  }
  return evmAddressShardRealmRegex.test(address);
};

const isValidEntityId = (entityId, allowEvmAddress = true, evmAddressType = constants.EvmAddressType.ANY) => {
  if ((typeof entityId === 'string' && entityIdRegex.test(entityId)) || encodedEntityIdRegex.test(entityId)) {
    // Accepted forms: shard.realm.num, realm.num, or encodedId
    return true;
  }

  return allowEvmAddress && isValidEvmAddress(entityId, evmAddressType);
};

/**
 * Checks whether the given EVM address is not an account num alias where the first 12 bytes reflect the shard and realm
 * @param evmAddress
 * @returns {boolean}
 */
const isEvmAddressAlias = (evmAddress) => {
  if (!isValidEvmAddress(evmAddress)) {
    return false;
  }
  const parts = parseFromEvmAddress(evmAddress);
  return parts[0] !== systemShard || parts[1] !== systemRealm || parts[2] > maxNum;
};

/**
 * Creates EntityId from shard, realm, and num.
 *
 * @param {BigInt|Number} shard
 * @param {BigInt|Number} realm
 * @param {BigInt|Number} num
 * @param {string|null} evmAddress The hex encoded non-parsable evm address
 * @return {EntityId}
 */
const of = (shard, realm, num, evmAddress = null) => {
  const toNumber = (val) => (typeof val === 'bigint' ? Number(val) : val);
  return new EntityId(toNumber(shard), toNumber(realm), toNumber(num), evmAddress);
};

const nullEntityId = of(null, null, null, null);
const nullEntityIdError = new InvalidArgumentError('Null entity ID');

/**
 * Checks if the id is null. When null, returns the nullEntityId if allowed, otherwise throws error; When not
 * null, returns undefined
 * @param {BigInt|int|string} id
 * @param {boolean} isNullable
 * @return {EntityId}
 */
const checkNullId = (id, isNullable) => {
  let entityId;
  if (_.isNil(id)) {
    if (!isNullable) {
      throw nullEntityIdError;
    }
    entityId = nullEntityId;
  }

  return entityId;
};

// without and with 0x prefix
const isValidEvmAddressLength = (len) => len === 40 || len === 42;

/**
 * Parses shard, realm, num from encoded ID string.
 * @param {string} id
 * @param {Function} error
 * @return {[BigInt, BigInt, BigInt, null]}
 */
const parseFromEncodedId = (id, error) => {
  const encodedId = BigInt(id);
  if (encodedId > maxEncodedId || encodedId < minEncodedId) {
    throw error();
  }

  const num = encodedId & numMask;
  const shardRealm = encodedId >> numBits;
  const realm = shardRealm & realmMask;
  const shard = BigInt.asUintN(shardBits, shardRealm >> realmBits);
  return [shard, realm, num, null];
};

/**
 * Parses shard, realm, num from EVM address string.
 * @param {string} evmAddress
 * @return {bigint[3]}
 */
const parseFromEvmAddress = (evmAddress) => {
  // extract shard from index 0->8, realm from 8->23, num from 24->40 and parse from hex to decimal
  const hexDigits = stripHexPrefix(_.last(evmAddress.split('.')));
  return [
    BigInt(constants.HEX_PREFIX + hexDigits.slice(0, 8)), // shard
    BigInt(constants.HEX_PREFIX + hexDigits.slice(8, 24)), // realm
    BigInt(constants.HEX_PREFIX + hexDigits.slice(24, 40)), // num
  ];
};

/**
 * Parses entity id string, accepts the following formats:
 *   - shard.realm.num
 *   - realm.num
 *   - shard.realm.evmAddress
 *   - evmAddress with or without 0x prefix
 *
 * @param {string} id
 * @param {Function} error The error function
 * @return {[BigInt|null, BigInt|null, BigInt|null, string|null]}
 */
const parseFromString = (id, error) => {
  const parts = id.split('.');
  const numOrEvmAddress = parts[parts.length - 1];
  const shard = parts.length === 3 ? BigInt(parts.shift()) : systemShard;
  const realm = parts.length === 2 ? BigInt(parts.shift()) : systemRealm;

  if (isValidEvmAddressLength(numOrEvmAddress.length)) {
    if (shard !== systemShard || realm !== systemRealm) {
      throw error(`Invalid shard or realm for EVM address ${id}`);
    }

    const evmAddress = stripHexPrefix(numOrEvmAddress);
    let [addressShard, addressRealm, num] = parseFromEvmAddress(numOrEvmAddress);

    if (addressShard !== systemShard || addressRealm !== systemRealm || num > maxNum) {
      return [shard, realm, null, evmAddress]; // Opaque EVM address
    } else {
      return [addressShard, addressRealm, num, null]; // Account num alias
    }
  }

  return [shard, realm, BigInt(numOrEvmAddress), null];
};

const parseIdParameterToEntityId = (id) => {
  const idPieces = ('' + id).split('.');
  idPieces.unshift(...[systemShard, systemRealm].slice(0, 3 - idPieces.length));
  idPieces[2] = stripHexPrefix(idPieces[2]);

  return parse(idPieces.join('.'));
};

const computeContractIdPartsFromContractIdValue = (contractId) => {
  const idPieces = contractId.split('.');
  idPieces.unshift(...[null, null].slice(0, 3 - idPieces.length));
  const contractIdParts = {shard: idPieces[0], realm: idPieces[1]};
  const evmAddressOrNum = stripHexPrefix(idPieces[2]);

  if (isEvmAddressAlias(evmAddressOrNum)) {
    contractIdParts.create2_evm_address = evmAddressOrNum;
  } else {
    contractIdParts.shard = contractIdParts.shard || systemShard;
    contractIdParts.realm = contractIdParts.realm || systemRealm;
    contractIdParts.num = evmAddressOrNum;
  }

  return contractIdParts;
};

const cache = new quickLru({
  maxAge: entityIdCacheConfig.maxAge * 1000, // in millis
  maxSize: entityIdCacheConfig.maxSize,
});

/**
 * Parses entity ID string, can be shard.realm.num, realm.num, the encoded entity ID or an evm address.
 * @param {string} id
 * @param {boolean} allowEvmAddress
 * @param {number} evmAddressType
 * @param {Function} error
 * @return {EntityId}
 */
// Some calls from num here
const parseCached = (id, allowEvmAddress, evmAddressType, error) => {
  const key = `${id}_${allowEvmAddress}_${evmAddressType}`;
  const value = cache.get(key);
  if (value) {
    return value;
  }

  if (!isValidEntityId(id, allowEvmAddress, evmAddressType)) {
    throw error();
  }
  const [shard, realm, num, evmAddress] =
    id.includes('.') || isValidEvmAddressLength(id.length) ? parseFromString(id, error) : parseFromEncodedId(id, error);
  if (evmAddress === null && (num > maxNum || realm > maxRealm || shard > maxShard)) {
    throw error();
  }

  const entityId = of(shard, realm, num, evmAddress);
  cache.set(key, entityId);
  return entityId;
};

/**
 * Parses entity ID string. The entity ID string can be shard.realm.num, realm.num, shard.realm.evm_address, evm_address,
 * or the encoded entity ID string.
 *
 * @param id
 * @param options
 * @return {EntityId}
 */
const parse = (id, {allowEvmAddress, evmAddressType, isNullable, paramName} = {}) => {
  // defaults
  allowEvmAddress = allowEvmAddress === undefined ? true : allowEvmAddress;
  evmAddressType = evmAddressType === undefined ? constants.EvmAddressType.ANY : evmAddressType;
  isNullable = isNullable === undefined ? false : isNullable;
  paramName = paramName === undefined ? '' : paramName;

  // lazily create error object
  const error = () =>
    paramName ? InvalidArgumentError.forParams(paramName) : new InvalidArgumentError(`Invalid entity ID "${id}"`);
  return checkNullId(id, isNullable) || parseCached(`${id}`, allowEvmAddress, evmAddressType, error);
};

export default {
  isValidEntityId,
  isValidEvmAddress,
  computeContractIdPartsFromContractIdValue,
  of,
  parse,
  parseIdParameterToEntityId,
};
