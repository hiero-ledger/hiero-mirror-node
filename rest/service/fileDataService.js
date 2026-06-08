// SPDX-License-Identifier: Apache-2.0

import quickLru from 'quick-lru';
import isNumber from 'lodash/isNumber';

import BaseService from './baseService';
import config from '../config';
import {HederaFunctionality} from '../gen/services/basic_types_pb.js';
import {ExchangeRate, FeeSchedule, FileData} from '../model';
import TransactionType from '../model/transactionType';
import * as utils from '../utils';
import EntityId from '../entityId';

const NANOSECONDS_PER_HOUR = 3_600_000_000_000n;
const FEE_DIVISOR_FACTOR = 1000n;

const FUNCTIONALITY_TO_TYPE = {
  [HederaFunctionality.ContractCall]: 'ContractCall',
  [HederaFunctionality.ContractCreate]: 'ContractCreate',
  [HederaFunctionality.EthereumTransaction]: 'EthereumTransaction',
};

/**
 * File data retrieval business logic
 */
class FileDataService extends BaseService {
  // placeholders to support where filtering for inner and outer calls
  static filterInnerPlaceholder = '<filterInnerPlaceHolder>';
  static filterOuterPlaceholder = '<filterOuterPlaceHolder>';

  #gasPriceCache = new quickLru({
    maxAge: config.cache.feeSchedule.maxAge * 1000, // in millis
    maxSize: config.cache.feeSchedule.maxSize,
  });

  // retrieve the largest timestamp of the most recent create/update operation on the file
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

  /**
   * The function returns the data for the fileId at the provided consensus timestamp.
   * @param fileId
   * @param timestamp
   * @return {data: string}
   */
  getFileData = async (fileId, timestamp) => {
    const params = [fileId, timestamp];
    const query = FileDataService.getFileDataQuery;
    const row = await super.getSingleRow(query, params);
    return row === null ? null : row.data;
  };

  getLatestFileContentsQuery = (innerWhere = '') => {
    const outerWhere = innerWhere.replaceAll('and ', `and ${FileData.tableAlias}.`);
    return FileDataService.latestFileContentsQuery
      .replace(FileDataService.filterInnerPlaceholder, innerWhere)
      .replace(FileDataService.filterOuterPlaceholder, outerWhere);
  };

  getLatestFileDataContents = async (fileId, filterQueries) => {
    const {where, params} = super.buildWhereSqlStatement(filterQueries.whereQuery, [fileId]);
    return super.getSingleRow(this.getLatestFileContentsQuery(where), params);
  };

  getExchangeRate = async (filterQueries) => {
    return this.fallbackRetry(EntityId.systemEntity.exchangeRateFile.getEncodedId(), filterQueries, ExchangeRate);
  };

  getFeeSchedule = async (filterQueries, transactionType = FUNCTIONALITY_TO_TYPE[HederaFunctionality.ContractCall]) => {
    const key = this.#getFeeScheduleKey(filterQueries, transactionType);

    const cached = this.#gasPriceCache.get(key);
    if (cached !== undefined) {
      return cached;
    }

    const [exchangeRate, feeSchedule] = await Promise.all([
      this.getExchangeRate(filterQueries),
      this.fallbackRetry(EntityId.systemEntity.feeScheduleFile.getEncodedId(), filterQueries, FeeSchedule),
    ]);

    if (!feeSchedule || !exchangeRate) {
      this.#gasPriceCache.set(key, null);
      return null;
    }

    const gasPrice = this.getGasPriceForType(
      feeSchedule,
      exchangeRate,
      this.#getEffectiveRefTimestampNanos(filterQueries),
      transactionType
    );
    this.#gasPriceCache.set(key, gasPrice);
    return gasPrice;
  };

  getEffectiveExchangeRate(exchangeRate, refTimestampNanos) {
    if (
      exchangeRate.current_expiration != null &&
      refTimestampNanos > BigInt(exchangeRate.current_expiration) * 1_000_000_000n
    ) {
      return {hbarEquiv: exchangeRate.next_hbar, centEquiv: exchangeRate.next_cent};
    }

    return {hbarEquiv: exchangeRate.current_hbar, centEquiv: exchangeRate.current_cent};
  }

  convertGasPriceToTinyBars(gasPrice, hbarEquiv, centEquiv) {
    if (gasPrice == null || !isNumber(hbarEquiv) || !isNumber(centEquiv)) {
      return null;
    }

    centEquiv = BigInt(centEquiv);
    if (centEquiv === 0n) {
      return null;
    }

    const fee = (BigInt(gasPrice) * BigInt(hbarEquiv)) / (centEquiv * FEE_DIVISOR_FACTOR);
    return utils.bigIntMax(fee, 1n);
  }

  getEffectiveFeeSchedule(feeSchedules, refTimestampNanos) {
    const currentFeeSchedule = feeSchedules.currentFeeSchedule;
    const feeScheduleExpirationTime = currentFeeSchedule.expiryTime?.seconds;

    if (feeScheduleExpirationTime != null && refTimestampNanos > feeScheduleExpirationTime * 1_000_000_000n) {
      return feeSchedules.nextFeeSchedule;
    }

    return currentFeeSchedule;
  }

  mapFees(feeSchedule, exchangeRate) {
    if (!feeSchedule?.transactionFeeSchedule) {
      return {};
    }

    const fees = {};
    for (const schedule of feeSchedule.transactionFeeSchedule) {
      const type = FUNCTIONALITY_TO_TYPE[schedule.hederaFunctionality];
      if (!type || !schedule.fees?.length) {
        continue;
      }

      const feeData = schedule.fees[0];
      const serviceData = feeData?.servicedata ?? feeData?.serviceData;
      if (!serviceData) {
        continue;
      }

      const gas = serviceData.gas;
      const tinyBars = this.convertGasPriceToTinyBars(gas, exchangeRate.hbarEquiv, exchangeRate.centEquiv);

      if (tinyBars !== null) {
        fees[type] = tinyBars;
      }
    }

    return fees;
  }

  getGasPriceForType(feeSchedule, exchangeRate, refTimestampNanos, transactionType) {
    const effectiveFeeSchedule = this.getEffectiveFeeSchedule(feeSchedule.feeSchedule, refTimestampNanos);
    const effectiveExchangeRate = this.getEffectiveExchangeRate(exchangeRate, refTimestampNanos);
    const fees = this.mapFees(effectiveFeeSchedule, effectiveExchangeRate);
    return fees[transactionType] ?? null;
  }

  getTransactionType(hederaTransactionType) {
    if (Number(hederaTransactionType) === Number(TransactionType.getProtoId('CONTRACTCREATEINSTANCE'))) {
      return FUNCTIONALITY_TO_TYPE[HederaFunctionality.ContractCreate];
    }

    return FUNCTIONALITY_TO_TYPE[HederaFunctionality.ContractCall];
  }

  fallbackRetry = async (fileEntityId, filterQueries, resultConstructor) => {
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
    const refTimestamp = BigInt(refTimestampNanos);
    return (refTimestamp / NANOSECONDS_PER_HOUR) * NANOSECONDS_PER_HOUR;
  }

  #getFeeScheduleKey(filterQueries, transactionType) {
    const refTimestamp = this.#getRefTimestamp(filterQueries);
    const timeKey = refTimestamp === null ? 'latest' : this.truncateToStartOfHour(refTimestamp).toString();
    return `${transactionType}:${timeKey}`;
  }

  #getEffectiveRefTimestampNanos(filterQueries) {
    const refTimestamp = this.#getRefTimestamp(filterQueries);
    const refTimestampNanos = refTimestamp ?? BigInt(Date.now()) * 1_000_000n;
    return this.truncateToStartOfHour(refTimestampNanos);
  }

  #getRefTimestamp(filterQueries) {
    const {whereQuery} = filterQueries;
    for (const filter of whereQuery) {
      if (filter.query.trim().startsWith(FileData.CONSENSUS_TIMESTAMP)) {
        return BigInt(filter.param);
      }
    }
    return null;
  }

  clearFeeScheduleCache = () => {
    this.#gasPriceCache.clear();
  };
}

export default new FileDataService();
