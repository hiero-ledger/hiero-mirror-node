// SPDX-License-Identifier: Apache-2.0

import BaseService from './baseService';
import EntityId from '../entityId';
import {FileData} from '../model';
import FeeSchedule from '../model/feeSchedule';

const CACHE_TTL_MS = 60 * 1000;

class FeeScheduleService extends BaseService {
  static feeScheduleQuery = `
    select ${FileData.CONSENSUS_TIMESTAMP}, ${FileData.FILE_DATA}
    from ${FileData.tableName}
    where ${FileData.ENTITY_ID} = $1
    order by ${FileData.CONSENSUS_TIMESTAMP} desc
    limit 1`;

  static exchangeRateQuery = `
    select ${FileData.CONSENSUS_TIMESTAMP}, ${FileData.FILE_DATA}
    from ${FileData.tableName}
    where ${FileData.ENTITY_ID} = $1
    order by ${FileData.CONSENSUS_TIMESTAMP} desc
    limit 1`;

  #cache = null;
  #cacheExpiry = 0;

  #getFeeScheduleFileId() {
    return EntityId.systemEntity.feeScheduleFile.getEncodedId();
  }

  #getExchangeRateFileId() {
    return EntityId.systemEntity.exchangeRateFile.getEncodedId();
  }

  async getFeeSchedule() {
    const now = Date.now();
    if (this.#cache && now < this.#cacheExpiry) {
      return this.#cache;
    }

    const [feeScheduleFile, exchangeRateFile] = await Promise.all([
      this.getSingleRow(FeeScheduleService.feeScheduleQuery, [this.#getFeeScheduleFileId()]),
      this.getSingleRow(FeeScheduleService.exchangeRateQuery, [this.#getExchangeRateFileId()]),
    ]);

    if (!feeScheduleFile || !exchangeRateFile) {
      return null;
    }

    this.#cache = new FeeSchedule(feeScheduleFile, exchangeRateFile);
    this.#cacheExpiry = now + CACHE_TTL_MS;
    return this.#cache;
  }

  async getGasForType(type) {
    const feeSchedule = await this.getFeeSchedule();
    return feeSchedule?.getGasForType(type) ?? null;
  }
}

export default new FeeScheduleService();
