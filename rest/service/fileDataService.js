// SPDX-License-Identifier: Apache-2.0

import quickLru from 'quick-lru';
import isEmpty from 'lodash/isEmpty';
import isNumber from 'lodash/isNumber';

import BaseService from './baseService';
import config from '../config';
import {HederaFunctionality} from '../gen/services/basic_types_pb.js';
import {ExchangeRate, FeeSchedule, FileData} from '../model';
import * as utils from '../utils';
import EntityId from '../entityId';
import {MAX_LONG, NANOS_PER_SECOND} from '../constants.js';

const NANOSECONDS_PER_HOUR = 60n * 60n * NANOS_PER_SECOND;
const FEE_DIVISOR_FACTOR = 1000n;
const MAINNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP = 1779296400389248896n;
const TESTNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP = 1777482002529719510n;

/**
 * File data retrieval business logic
 */
class FileDataService extends BaseService {
  // placeholders to support where filtering for inner and outer calls
  static filterInnerPlaceholder = '<filterInnerPlaceHolder>';
  static filterOuterPlaceholder = '<filterOuterPlaceHolder>';
  // using this timestamp retrieve all recent file operations and combine contents for applicable file
  static latestFileContentsQuery = `with latest_create as (
      select max(${FileData.CONSENSUS_TIMESTAMP}) as ${FileData.CONSENSUS_TIMESTAMP}
      from ${FileData.tableName}
      where ${FileData.ENTITY_ID} = $1 and ${FileData.TRANSACTION_TYPE} in (17, 19) ${
    FileDataService.filterInnerPlaceholder
  }
      group by ${FileData.ENTITY_ID}
      order by ${FileData.CONSENSUS_TIMESTAMP} desc
    )
    select
      max(${FileData.tableAlias}.${FileData.CONSENSUS_TIMESTAMP}) as ${FileData.CONSENSUS_TIMESTAMP},
      min(${FileData.tableAlias}.${FileData.CONSENSUS_TIMESTAMP}) as first_consensus_timestamp,
      string_agg(${FileData.getFullName(FileData.FILE_DATA)}, '' order by ${FileData.getFullName(
    FileData.CONSENSUS_TIMESTAMP
  )}) as ${FileData.FILE_DATA}
    from ${FileData.tableName} ${FileData.tableAlias}
    join latest_create l on ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} >= l.${FileData.CONSENSUS_TIMESTAMP}
    where ${FileData.getFullName(FileData.ENTITY_ID)} = $1 and ${FileData.getFullName(
    FileData.TRANSACTION_TYPE
  )} in (16,17, 19)
      and ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} >= l.${FileData.CONSENSUS_TIMESTAMP} ${
    FileDataService.filterOuterPlaceholder
  }
    group by ${FileData.getFullName(FileData.ENTITY_ID)}`;

  // retrieve the largest timestamp of the most recent create/update operation on the file
  static getFileDataQuery = `select
         string_agg(
           ${FileData.FILE_DATA}, ''
           order by ${FileData.CONSENSUS_TIMESTAMP}
           ) data
        from ${FileData.tableName}
        where
           ${FileData.ENTITY_ID} = $1
        and ${FileData.CONSENSUS_TIMESTAMP} >= (
        select ${FileData.CONSENSUS_TIMESTAMP}
        from ${FileData.tableName}
        where ${FileData.ENTITY_ID} = $1
        and ${FileData.CONSENSUS_TIMESTAMP} <= $2
        and (${FileData.TRANSACTION_TYPE} = 17
             or ( ${FileData.TRANSACTION_TYPE} = 19
                  and
                  length(${FileData.FILE_DATA}) <> 0 ))
        order by ${FileData.CONSENSUS_TIMESTAMP} desc
        limit 1
        ) and ${FileData.CONSENSUS_TIMESTAMP} <= $2`;

  #gasPriceCache = new quickLru({
    maxAge: config.cache.feeSchedule.maxAge * 1000, // in millis
    maxSize: config.cache.feeSchedule.maxSize,
  });

  getFileData = async (fileId, timestamp) => {
    const params = [fileId, timestamp];
    const query = FileDataService.getFileDataQuery;
    const row = await super.getSingleRow(query, params);
    return row === null ? null : row.data;
  };

  #getLatestFileContentsQuery = (innerWhere = '') => {
    const outerWhere = innerWhere.replaceAll('and ', `and ${FileData.tableAlias}.`);
    return FileDataService.latestFileContentsQuery
      .replace(FileDataService.filterInnerPlaceholder, innerWhere)
      .replace(FileDataService.filterOuterPlaceholder, outerWhere);
  };

  getLatestFileDataContents = async (fileId, filterQueries) => {
    const {where, params} = super.buildWhereSqlStatement(filterQueries.whereQuery, [fileId]);
    return super.getSingleRow(this.#getLatestFileContentsQuery(where), params);
  };

  getExchangeRate = async (filterQueries) => {
    return this.#fallbackRetry(EntityId.systemEntity.exchangeRateFile.getEncodedId(), filterQueries, ExchangeRate);
  };

  getGasPrice = async (consensusTimestamp = null) => {
    const cacheKey = this.#getFeeScheduleKey(consensusTimestamp);
    const cached = this.#gasPriceCache.get(cacheKey);

    if (cached !== undefined) {
      return cached;
    }

    return this.#loadAndCacheGasPrice(cacheKey, consensusTimestamp);
  };

  getGasPrices = async (consensusTimestamps) => {
    const gasPriceByTimestamp = new Map();
    if (isEmpty(consensusTimestamps)) {
      return gasPriceByTimestamp;
    }

    const loadRequests = new Map();

    for (const consensusTimestamp of consensusTimestamps) {
      const cacheKey = this.#getFeeScheduleKey(consensusTimestamp);
      let gasPrice = this.#gasPriceCache.get(cacheKey);

      if (gasPrice === undefined) {
        gasPrice = loadRequests.get(cacheKey);
        if (gasPrice === undefined) {
          gasPrice = this.#loadAndCacheGasPrice(cacheKey, consensusTimestamp);
          loadRequests.set(cacheKey, gasPrice);
        }
      }

      gasPriceByTimestamp.set(consensusTimestamp, gasPrice);
    }

    for (const [consensusTimestamp, gasPrice] of gasPriceByTimestamp) {
      gasPriceByTimestamp.set(consensusTimestamp, (await gasPrice) ?? null);
    }

    return gasPriceByTimestamp;
  };

  getGasPriceFromSimpleFeeSchedule(feeSchedule, exchangeRate, refTimestampNanos) {
    const gasTinycents = feeSchedule.getGasPriceTinycents();
    if (gasTinycents == null) {
      return null;
    }
    const effectiveExchangeRate = this.getEffectiveExchangeRate(exchangeRate, refTimestampNanos);
    return this.convertGasPriceToTinyBars(
      gasTinycents,
      effectiveExchangeRate.hbarEquiv,
      effectiveExchangeRate.centEquiv
    );
  }

  async #loadAndCacheGasPrice(cacheKey, consensusTimestamp) {
    const filterQueries = this.#buildFilterQueries(consensusTimestamp);

    const [exchangeRate, feeSchedule] = await Promise.all([
      this.getExchangeRate(filterQueries),
      this.#getFeeSchedule(filterQueries, consensusTimestamp),
    ]);

    if (!feeSchedule || !exchangeRate) {
      this.#gasPriceCache.set(cacheKey, null);
      return null;
    }

    const gasPrice = this.getGasPriceForType(
      feeSchedule,
      exchangeRate,
      this.#getEffectiveRefTimestampNanos(consensusTimestamp)
    );
    this.#gasPriceCache.set(cacheKey, gasPrice);
    return gasPrice;
  }

  #getFeeSchedule = async (filterQueries, consensusTimestamp) => {
    const switchoverTimestamp = this.#getSimpleFeesSwitchoverTimestamp();
    const refTimestampNanos = consensusTimestamp != null ? BigInt(consensusTimestamp) : switchoverTimestamp;

    if (refTimestampNanos >= switchoverTimestamp) {
      return this.#fallbackRetry(
        EntityId.systemEntity.simpleFeeScheduleFile.getEncodedId(),
        filterQueries,
        FeeSchedule
      );
    }

    return this.#fallbackRetry(EntityId.systemEntity.feeScheduleFile.getEncodedId(), filterQueries, FeeSchedule);
  };

  #getSimpleFeesSwitchoverTimestamp() {
    const network = config.network?.toUpperCase();
    if (network === 'TESTNET') {
      return TESTNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP;
    }
    return MAINNET_SIMPLE_FEES_SWITCHOVER_TIMESTAMP;
  }

  #buildFilterQueries(consensusTimestamp) {
    if (consensusTimestamp == null) {
      return {whereQuery: []};
    }

    return {
      whereQuery: [
        {
          query: `${FileData.CONSENSUS_TIMESTAMP}${utils.opsMap.lte}`,
          param: consensusTimestamp,
        },
      ],
    };
  }

  getEffectiveExchangeRate(exchangeRate, refTimestampNanos) {
    if (
      exchangeRate.current_expiration != null &&
      refTimestampNanos > BigInt(exchangeRate.current_expiration) * NANOS_PER_SECOND
    ) {
      return {hbarEquiv: exchangeRate.next_hbar, centEquiv: exchangeRate.next_cent};
    }

    return {hbarEquiv: exchangeRate.current_hbar, centEquiv: exchangeRate.current_cent};
  }

  convertGasPriceToTinyBars(gasPrice, hbarEquiv, centEquiv) {
    const hbar = Number(hbarEquiv);
    const cent = Number(centEquiv);

    if (gasPrice == null || isNaN(hbar) || isNaN(cent) || cent === 0) {
      return null;
    }

    const fee = (BigInt(gasPrice) * BigInt(hbar)) / BigInt(cent);
    return utils.bigIntMax(fee, 1n);
  }

  convertLegacyGasPriceToTinyBars(gasPrice, hbarEquiv, centEquiv) {
    if (gasPrice == null) {
      return null;
    }
    const gasInTinyCents = BigInt(gasPrice) / FEE_DIVISOR_FACTOR;
    return this.convertGasPriceToTinyBars(gasInTinyCents, hbarEquiv, centEquiv);
  }

  #getEffectiveFeeSchedule(feeSchedules, refTimestampNanos) {
    const currentFeeSchedule = feeSchedules?.currentFeeSchedule;
    const feeScheduleExpirationTime = currentFeeSchedule?.expiryTime?.seconds;

    if (feeScheduleExpirationTime != null && refTimestampNanos > BigInt(feeScheduleExpirationTime) * NANOS_PER_SECOND) {
      return feeSchedules.nextFeeSchedule;
    }

    return currentFeeSchedule;
  }

  #lookupGasPrice(feeSchedule, exchangeRate) {
    if (!feeSchedule?.transactionFeeSchedule) {
      return null;
    }

    for (const schedule of feeSchedule.transactionFeeSchedule) {
      const type = schedule?.hederaFunctionality;
      if (type == null || !schedule?.fees?.length || type !== HederaFunctionality.ContractCall) {
        continue;
      }

      const feeData = schedule.fees[0];
      const serviceData = feeData?.servicedata ?? feeData?.serviceData;
      if (!serviceData) {
        continue;
      }

      const gas = serviceData.gas;
      return this.convertLegacyGasPriceToTinyBars(gas, exchangeRate.hbarEquiv, exchangeRate.centEquiv);
    }

    return null;
  }

  getGasPriceForType(feeSchedule, exchangeRate, refTimestampNanos) {
    if (feeSchedule.simpleFeeSchedule || typeof feeSchedule.getGasPriceTinycents === 'function') {
      const simpleGasPrice = this.getGasPriceFromSimpleFeeSchedule(feeSchedule, exchangeRate, refTimestampNanos);
      if (simpleGasPrice != null) {
        return simpleGasPrice;
      }
    }

    if (feeSchedule.feeSchedule) {
      const effectiveFeeSchedule = this.#getEffectiveFeeSchedule(feeSchedule.feeSchedule, refTimestampNanos);
      const effectiveExchangeRate = this.getEffectiveExchangeRate(exchangeRate, refTimestampNanos);
      return this.#lookupGasPrice(effectiveFeeSchedule, effectiveExchangeRate);
    }

    return null;
  }

  #fallbackRetry = async (fileEntityId, filterQueries, resultConstructor) => {
    const whereQuery = filterQueries.whereQuery ?? [];
    const filters = {whereQuery};

    let attempts = 0;
    while (++attempts <= config.query.maxFileAttempts) {
      const row = await this.getLatestFileDataContents(fileEntityId, filters);
      try {
        return row === null ? null : new resultConstructor(row);
      } catch (error) {
        logger.warn(
          `Attempt ${attempts} failed to load file ${fileEntityId} at ${row.consensus_timestamp}, falling back to previous file: ${error.message}`
        );

        filters.whereQuery = [
          ...whereQuery,
          {
            query: FileData.CONSENSUS_TIMESTAMP + utils.opsMap.lt,
            param: row.first_consensus_timestamp,
          },
        ];
      }
    }

    return null;
  };

  truncateToStartOfHour(refTimestampNanos) {
    return (refTimestampNanos / NANOSECONDS_PER_HOUR) * NANOSECONDS_PER_HOUR;
  }

  #getFeeScheduleKey(consensusTimestamp) {
    return consensusTimestamp == null ? MAX_LONG : this.truncateToStartOfHour(BigInt(consensusTimestamp));
  }

  #getEffectiveRefTimestampNanos(consensusTimestamp) {
    const refTimestampNanos = consensusTimestamp ?? BigInt(Date.now()) * 1_000_000n;
    return this.truncateToStartOfHour(BigInt(refTimestampNanos));
  }

  clearFeeScheduleCache = () => {
    this.#gasPriceCache.clear();
  };
}

export default new FileDataService();
