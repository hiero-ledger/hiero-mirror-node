// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import BaseService from './baseService';
import {getResponseLimit} from '../config';
import {filterKeys, HEX_PREFIX, MAX_LONG, orderFilterValues} from '../constants';
import EntityId from '../entityId';
import {NotFoundError} from '../errors';
import {OrderSpec} from '../sql';
import {
  ContractAction,
  ContractLog,
  ContractResult,
  ContractState,
  ContractStateChange,
  ContractTransactionHash,
  Entity,
  EthereumTransaction,
  Transaction,
  TransactionResult,
} from '../model';
import ContractTransaction from '../model/contractTransaction';
import {RecordFileService} from './index';

const {default: defaultLimit} = getResponseLimit();
const contractLogsFields = `${ContractLog.getFullName(ContractLog.BLOOM)},
${ContractLog.getFullName(ContractLog.CONTRACT_ID)},
${ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP)},
${ContractLog.getFullName(ContractLog.DATA)},
${ContractLog.getFullName(ContractLog.INDEX)},
${ContractLog.getFullName(ContractLog.ROOT_CONTRACT_ID)},
${ContractLog.getFullName(ContractLog.TOPIC0)},
${ContractLog.getFullName(ContractLog.TOPIC1)},
${ContractLog.getFullName(ContractLog.TOPIC2)},
${ContractLog.getFullName(ContractLog.TOPIC3)},
${ContractLog.getFullName(ContractLog.TRANSACTION_HASH)},
${ContractLog.getFullName(ContractLog.TRANSACTION_INDEX)}
`;

const contractResultsFields = `${ContractResult.getFullName(ContractResult.AMOUNT)},
${ContractResult.getFullName(ContractResult.BLOOM)},
${ContractResult.getFullName(ContractResult.CALL_RESULT)},
${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)},
${ContractResult.getFullName(ContractResult.CONTRACT_ID)},
${ContractResult.getFullName(ContractResult.CREATED_CONTRACT_IDS)},
${ContractResult.getFullName(ContractResult.ERROR_MESSAGE)},
${ContractResult.getFullName(ContractResult.FAILED_INITCODE)},
${ContractResult.getFullName(ContractResult.FUNCTION_PARAMETERS)},
case when ${ContractResult.getFullName(ContractResult.SENDER_ID)} is null
then ${ContractResult.getFullName(ContractResult.FUNCTION_RESULT)}
else '' end as ${ContractResult.FUNCTION_RESULT},
${ContractResult.getFullName(ContractResult.GAS_CONSUMED)},
${ContractResult.getFullName(ContractResult.GAS_LIMIT)},
${ContractResult.getFullName(ContractResult.GAS_USED)},
${ContractResult.getFullName(ContractResult.PAYER_ACCOUNT_ID)},
${ContractResult.getFullName(ContractResult.SENDER_ID)},
${ContractResult.getFullName(ContractResult.TRANSACTION_HASH)},
${ContractResult.getFullName(ContractResult.TRANSACTION_INDEX)},
${ContractResult.getFullName(ContractResult.TRANSACTION_NONCE)},
${ContractResult.getFullName(ContractResult.TRANSACTION_RESULT)}
`;
const successTransactionResult = TransactionResult.getProtoId('SUCCESS');

/**
 * Contract retrieval business logic
 */
class ContractService extends BaseService {
  static entityCTE = ` ${Entity.tableName} as (
    select ${Entity.EVM_ADDRESS}, ${Entity.ID} from ${Entity.tableName}
  ) `;

  static contractResultsWithEvmAddressQuery = `
    select
      ${contractResultsFields},
      coalesce(${Entity.getFullName(Entity.EVM_ADDRESS)},'') as ${Entity.EVM_ADDRESS}
    from ${ContractResult.tableName} ${ContractResult.tableAlias}
    `;

  static joinContractResultWithEvmAddress = `
      left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractResult.getFullName(ContractResult.CONTRACT_ID)}
   `;

  static contractStateChangesQuery = `
    with ${ContractService.entityCTE}
    select ${ContractStateChange.CONSENSUS_TIMESTAMP},
           ${ContractStateChange.CONTRACT_ID},
           ${ContractStateChange.PAYER_ACCOUNT_ID},
           ${ContractStateChange.SLOT},
           ${ContractStateChange.VALUE_READ},
           ${ContractStateChange.VALUE_WRITTEN},
           coalesce(${Entity.getFullName(Entity.EVM_ADDRESS)},'') as ${Entity.EVM_ADDRESS}
    from ${ContractStateChange.tableName} ${ContractStateChange.tableAlias}
    left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractStateChange.getFullName(ContractStateChange.CONTRACT_ID)}
    `;

  static contractStateQuery = `
    with ${ContractService.entityCTE}
    select ${ContractState.MODIFIED_TIMESTAMP},
           ${ContractState.CONTRACT_ID},
           ${ContractState.SLOT},
           ${ContractState.VALUE},
           coalesce(${Entity.getFullName(Entity.EVM_ADDRESS)},'') as ${Entity.EVM_ADDRESS}
    from ${ContractState.tableName} ${ContractState.tableAlias}
    left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractState.getFullName(ContractState.CONTRACT_ID)}
    `;

  static contractStateTimestampQuery = `
      with ${ContractService.entityCTE}
      select DISTINCT on (${ContractStateChange.SLOT}) 
            ${ContractStateChange.CONTRACT_ID},
            ${ContractStateChange.SLOT},
            ${Entity.EVM_ADDRESS},
            coalesce(${ContractStateChange.VALUE_WRITTEN}, ${ContractStateChange.VALUE_READ}) as ${ContractState.VALUE},
            ${ContractStateChange.CONSENSUS_TIMESTAMP} as ${ContractState.MODIFIED_TIMESTAMP}
      from ${ContractStateChange.tableName} ${ContractStateChange.tableAlias}
      left join ${Entity.tableName} ${Entity.tableAlias}
        on ${Entity.getFullName(Entity.ID)} = ${ContractStateChange.getFullName(ContractStateChange.CONTRACT_ID)}
    `;

  static contractLogsWithEvmAddressQuery = `
    with ${ContractService.entityCTE}
    select
      ${contractLogsFields},
      ${Entity.getFullName(Entity.EVM_ADDRESS)} as ${Entity.EVM_ADDRESS}
    from ${ContractLog.tableName} ${ContractLog.tableAlias}
      left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractLog.getFullName(ContractLog.CONTRACT_ID)}
    `;

  static contractLogsExtendedQuery = `
    with  ${Entity.tableName} as (
      select ${Entity.EVM_ADDRESS}, ${Entity.ID}
      from ${Entity.tableName}
    )
    select ${contractLogsFields},
      ${Entity.EVM_ADDRESS}
    from ${ContractLog.tableName} ${ContractLog.tableAlias}
    left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.ID} = ${ContractLog.CONTRACT_ID}
  `;

  static contractIdByEvmAddressQuery = `
    select ${Entity.ID}
    from ${Entity.tableName} ${Entity.tableAlias}
    where ${Entity.DELETED} <> true and
      ${Entity.TYPE} = 'CONTRACT' and
      ${Entity.EVM_ADDRESS} = $1`;

  static contractActionsByConsensusTimestampQuery = `
    select ${ContractAction.CALLER},
           ${ContractAction.CALL_DEPTH},
           ${ContractAction.CALLER_TYPE},
           ${ContractAction.CALL_OPERATION_TYPE},
           ${ContractAction.CALL_TYPE},
           ${ContractAction.CALLER_TYPE},
           ${ContractAction.CONSENSUS_TIMESTAMP},
           ${ContractAction.GAS},
           ${ContractAction.GAS_USED},
           ${ContractAction.INDEX},
           ${ContractAction.INPUT},
           ${ContractAction.RECIPIENT_ACCOUNT},
           ${ContractAction.RECIPIENT_ADDRESS},
           ${ContractAction.RECIPIENT_CONTRACT},
           ${ContractAction.RESULT_DATA},
           ${ContractAction.RESULT_DATA_TYPE},
           ${ContractAction.VALUE}
    from ${ContractAction.tableName}
    where ${ContractAction.CONSENSUS_TIMESTAMP} = $1 and ${ContractAction.PAYER_ACCOUNT_ID} = $2`;

  static contractLogsPaginationColumns = {
    [filterKeys.TIMESTAMP]: ContractLog.CONSENSUS_TIMESTAMP,
    [filterKeys.INDEX]: ContractLog.INDEX,
  };

  static ethereumTransactionByPayerAndTimestampArrayQuery = `select
        encode(${EthereumTransaction.ACCESS_LIST}, 'hex') ${EthereumTransaction.ACCESS_LIST},
        encode(${EthereumTransaction.CHAIN_ID}, 'hex') ${EthereumTransaction.CHAIN_ID},
        ${EthereumTransaction.CONSENSUS_TIMESTAMP},
        encode(${EthereumTransaction.GAS_PRICE}, 'hex') ${EthereumTransaction.GAS_PRICE},
        encode(${EthereumTransaction.MAX_FEE_PER_GAS}, 'hex') ${EthereumTransaction.MAX_FEE_PER_GAS},
        encode(${EthereumTransaction.MAX_PRIORITY_FEE_PER_GAS}, 'hex') ${EthereumTransaction.MAX_PRIORITY_FEE_PER_GAS},
        ${EthereumTransaction.NONCE},
        encode(${EthereumTransaction.SIGNATURE_R}, 'hex') ${EthereumTransaction.SIGNATURE_R},
        encode(${EthereumTransaction.SIGNATURE_S}, 'hex') ${EthereumTransaction.SIGNATURE_S},
        ${EthereumTransaction.SIGNATURE_V},
        ${EthereumTransaction.TYPE},
        ${EthereumTransaction.RECOVERY_ID},
        encode(${EthereumTransaction.TO_ADDRESS}, 'hex') ${EthereumTransaction.TO_ADDRESS},
        encode(${EthereumTransaction.VALUE}, 'hex') ${EthereumTransaction.VALUE}
      from ${EthereumTransaction.tableName}
      where ${EthereumTransaction.PAYER_ACCOUNT_ID} = any($1)
        and ${EthereumTransaction.CONSENSUS_TIMESTAMP} = any($2)
        and ${EthereumTransaction.CONSENSUS_TIMESTAMP} >= $3
        and ${EthereumTransaction.CONSENSUS_TIMESTAMP} <= $4`;

  static involvedContractsQuery = `select ${ContractTransaction.PAYER_ACCOUNT_ID},
                                          ${ContractTransaction.ENTITY_ID},
                                          ${ContractTransaction.CONTRACT_IDS},
                                          ${ContractTransaction.CONSENSUS_TIMESTAMP}
                   from ${ContractTransaction.tableName}
                   where ${ContractTransaction.CONSENSUS_TIMESTAMP} = $1 and ${ContractTransaction.ENTITY_ID} = $2`;

  static ethereumTransactionsByHashQuery = `select * from ${ContractTransactionHash.tableName}
        where ${ContractTransactionHash.HASH} = $1 and ${ContractTransactionHash.CONSENSUS_TIMESTAMP} = (
          select coalesce((
              select ${ContractTransactionHash.CONSENSUS_TIMESTAMP}
              from ${ContractTransactionHash.tableName}
              where ${ContractTransactionHash.HASH} = $1 and ${ContractTransactionHash.TRANSACTION_RESULT} = ${successTransactionResult}
              order by ${ContractTransactionHash.CONSENSUS_TIMESTAMP} desc
              limit 1
          ), (
              select ${ContractTransactionHash.CONSENSUS_TIMESTAMP}
              from ${ContractTransactionHash.tableName}
              where ${ContractTransactionHash.HASH} = $1
              order by ${ContractTransactionHash.CONSENSUS_TIMESTAMP} desc
              limit 1
          ))
       )`;

  getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;

    const query = [
      ContractService.contractResultsWithEvmAddressQuery,
      ContractService.joinContractResultWithEvmAddress,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(OrderSpec.from(ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP), order)),
      super.getLimitQuery(whereParams.length + 1), // get limit param located at end of array
    ].join('\n');
    params.push(limit);

    return [query, params];
  }

  async getContractResultsByIdAndFilters(
    whereConditions = [],
    whereParams = [],
    order = orderFilterValues.DESC,
    limit = defaultLimit
  ) {
    const [query, params] = this.getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit);
    const rows = await super.getRows(query, params);
    return rows.map((cr) => {
      return {
        ...new ContractResult(cr),
        hash: cr.hash,
      };
    });
  }

  async getContractStateByIdAndFilters(
    whereConditions = [],
    order = orderFilterValues.ASC,
    limit = defaultLimit,
    timestamp = false
  ) {
    let orderClause = this.getOrderByQuery(OrderSpec.from(ContractStateChange.SLOT, order));
    const {where, params} = this.buildWhereSqlStatement(whereConditions);
    const limitClause = this.getLimitQuery(params.push(limit));
    let query = [ContractService.contractStateQuery, where, orderClause, limitClause].join(' ');

    if (timestamp) {
      //timestamp order needs to be always desc to get only the latest changes until the provided timestamp
      orderClause = this.getOrderByQuery(
        OrderSpec.from(ContractStateChange.SLOT, order),
        OrderSpec.from(ContractStateChange.CONSENSUS_TIMESTAMP, orderFilterValues.DESC)
      );

      query = [ContractService.contractStateTimestampQuery, where, orderClause, limitClause].join(' ');
    }
    const rows = await super.getRows(query, params);
    return rows.map((row) => new ContractState(row));
  }

  async getContractResultsByTimestamps(timestamps, involvedContractIds = []) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }
    const conditions = [`${ContractResult.CONSENSUS_TIMESTAMP} ${timestampsOpAndValue}`];
    if (involvedContractIds.length) {
      conditions.push(`${ContractResult.CONTRACT_ID} in (${involvedContractIds.join(',')})`);
    }
    const whereClause = ` where ${conditions.join(' and ')} `;
    const query = [
      `with ${ContractService.entityCTE} `,
      ContractService.contractResultsWithEvmAddressQuery,
      ContractService.joinContractResultWithEvmAddress,
      whereClause,
    ].join('\n');

    const rows = await super.getRows(query, params);

    return rows.map((row) => {
      return {
        ...new ContractResult(row),
        evmAddress: row.evm_address,
      };
    });
  }

  /**
   * Retrieves contract transaction details based on the eth hash
   *
   * @param {Buffer} hash eth transaction hash or 32-byte hedera transaction hash prefix
   * @return {Promise<{ContractTransactionHash}[]>}
   */
  async getContractTransactionDetailsByHash(hash) {
    const rows = await super.getRows(ContractService.ethereumTransactionsByHashQuery, [hash]);
    return rows.map((row) => new ContractTransactionHash(row));
  }

  async getInvolvedContractsByTimestampAndContractId(timestamp, contractId) {
    if (!timestamp || contractId === null || contractId === undefined) {
      return null;
    }
    const contractDetails = await super.getSingleRow(ContractService.involvedContractsQuery, [timestamp, contractId]);
    return _.isNull(contractDetails) ? null : new ContractTransaction(contractDetails);
  }

  /**
   * Build sql query for retrieving contract logs
   * @param query
   * @returns {[string, *[]]}
   */
  getContractLogsQuery({lower, inner, upper, params, conditions, order, limit}) {
    params.push(limit);
    const orderClause = super.getOrderByQuery(
      OrderSpec.from(ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP), order),
      OrderSpec.from(ContractLog.getFullName(ContractLog.INDEX), order)
    );
    const orderClauseNoAlias = super.getOrderByQuery(
      OrderSpec.from(ContractLog.CONSENSUS_TIMESTAMP, order),
      OrderSpec.from(ContractLog.INDEX, order)
    );
    const limitClause = super.getLimitQuery(params.length);

    const subQueries = [lower, inner, upper]
      .filter((filters) => filters.length !== 0)
      .map((filters) =>
        super.buildSelectQuery(
          ContractService.contractLogsExtendedQuery,
          params,
          conditions,
          orderClause,
          limitClause,
          filters.map((filter) => ({
            ...filter,
            column: ContractLog.getFullName(ContractService.contractLogsPaginationColumns[filter.key]),
          }))
        )
      );

    let sqlQuery;
    if (subQueries.length === 0) {
      // if all three filters are empty, the subqueries will be empty too, just create the query with empty filters
      sqlQuery = super.buildSelectQuery(
        ContractService.contractLogsExtendedQuery,
        params,
        conditions,
        orderClause,
        limitClause
      );
    } else if (subQueries.length === 1) {
      sqlQuery = subQueries[0];
    } else {
      sqlQuery = [subQueries.map((q) => `(${q})`).join('\nunion\n'), orderClauseNoAlias, limitClause].join('\n');
    }

    return [sqlQuery, params];
  }

  /**
   * Retrieves contract logs based on contract id and various filters
   *
   * @param query
   * @returns {Promise<ContractLog[]>} the result of the getContractLogs query
   */
  async getContractLogs(query) {
    const [sqlQuery, params] = this.getContractLogsQuery(query);
    const rows = await super.getRows(sqlQuery, params);
    if (rows.length === 0) {
      return rows;
    }

    const timestamps = [];
    // The timestamps are ordered, and may have duplicates, dedup them
    rows.forEach((row) => {
      if (row.consensus_timestamp !== timestamps[timestamps.length - 1]) {
        timestamps.push(row.consensus_timestamp);
      }
    });
    const recordFileMap = await RecordFileService.getRecordFileBlockDetailsFromTimestampArray(timestamps);

    return rows.map((cr) => new ContractLog(cr, recordFileMap.get(cr.consensus_timestamp)));
  }

  async getContractLogsByTimestamps(timestamps, involvedContractIds = []) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }

    const conditions = [`${ContractLog.CONSENSUS_TIMESTAMP} ${timestampsOpAndValue}`];
    if (involvedContractIds.length) {
      conditions.push(`${ContractLog.CONTRACT_ID} in (${involvedContractIds.join(',')})`);
    }
    const whereClause = `where ${conditions.join(' and ')}`;
    const orderClause = `order by ${ContractLog.CONSENSUS_TIMESTAMP}, ${ContractLog.INDEX}`;

    const query = [ContractService.contractLogsWithEvmAddressQuery, whereClause, orderClause].join('\n');
    const rows = await super.getRows(query, params);
    return rows.map((row) => new ContractLog(row));
  }

  async getContractStateChangesByTimestamps(timestamps, contractId = null, involvedContractIds = []) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }

    const conditions = [`${ContractStateChange.CONSENSUS_TIMESTAMP} ${timestampsOpAndValue}`];
    const contractIdsQuery = involvedContractIds?.length
      ? `${ContractStateChange.CONTRACT_ID} in (${involvedContractIds.join(',')})`
      : true;
    if (contractId) {
      params.push(contractId);
      conditions.push(
        `(${ContractStateChange.CONTRACT_ID} = $${params.length} or (${contractIdsQuery} and migration is false))`
      );
    } else {
      conditions.push(contractIdsQuery);
      conditions.push(`${ContractStateChange.MIGRATION} is false`);
    }

    const whereClause = 'where ' + conditions.join(' and ');
    const orderClause = `order by ${ContractStateChange.CONSENSUS_TIMESTAMP}, ${ContractStateChange.CONTRACT_ID}, ${ContractStateChange.SLOT}`;
    const query = [ContractService.contractStateChangesQuery, whereClause, orderClause].join('\n');
    const rows = await super.getRows(query, params);

    return rows.map((row) => {
      return {
        ...new ContractStateChange(row),
        evmAddress: row.evm_address,
      };
    });
  }

  async getContractIdByEvmAddress(evmAddressFilter) {
    const create2EvmAddress = evmAddressFilter.create2_evm_address;
    const rows = await super.getRows(ContractService.contractIdByEvmAddressQuery, [
      Buffer.from(create2EvmAddress, 'hex'),
    ]);
    if (rows.length === 0) {
      throw new NotFoundError(`No contract with the given evm address 0x${create2EvmAddress} has been found.`);
    }
    // since evm_address is not a unique index, it is important to make this check.
    if (rows.length > 1) {
      throw new Error(`More than one contract with the evm address 0x${create2EvmAddress} have been found.`);
    }

    return rows[0].id;
  }

  async computeContractIdFromString(contractIdValue) {
    const contractIdParts = EntityId.computeContractIdPartsFromContractIdValue(contractIdValue);

    if (contractIdParts.hasOwnProperty('create2_evm_address')) {
      return this.getContractIdByEvmAddress(contractIdParts);
    }

    return EntityId.parseString(contractIdValue, {paramName: filterKeys.CONTRACTID}).getEncodedId();
  }

  async getContractActionsByConsensusTimestamp(consensusTimestamp, payerAccountId, filters, order, limit) {
    const params = [consensusTimestamp, payerAccountId];
    return this.getContractActions(
      ContractService.contractActionsByConsensusTimestampQuery,
      params,
      filters,
      order,
      limit
    );
  }

  async getContractActions(baseQuery, params, filters, order, limit) {
    let whereClause = ``;
    if (filters && filters.length) {
      for (const filter of filters) {
        if (filter.key === 'index') {
          params.push(filter.value);
          whereClause += `\nand ${ContractAction.INDEX}${filter.operator}$${params.length}`;
        }
      }
    }

    const orderClause = super.getOrderByQuery(OrderSpec.from(ContractAction.INDEX, order));

    params.push(limit);
    const limitClause = super.getLimitQuery(params.length);

    const query = [baseQuery, whereClause, orderClause, limitClause].join('\n');

    const rows = await super.getRows(query, params);
    return rows.map((row) => new ContractAction(row));
  }

  /**
   * Get the ethereum transaction matching the payer and timestamp pairs. Note the payers array and timestamps array
   * should have equal length
   *
   * @param payers
   * @param timestamps
   * @returns {Promise<{Map}>}
   */
  async getEthereumTransactionsByPayerAndTimestampArray(payers, timestamps) {
    const transactionMap = new Map();
    if (_.isEmpty(payers) || _.isEmpty(timestamps)) {
      return transactionMap;
    }

    let maxTimestamp = -1n;
    let minTimestamp = MAX_LONG;
    timestamps.forEach((timestamp) => {
      if (timestamp > maxTimestamp) {
        maxTimestamp = timestamp;
      }
      if (timestamp < minTimestamp) {
        minTimestamp = timestamp;
      }
    });

    const rows = await super.getRows(ContractService.ethereumTransactionByPayerAndTimestampArrayQuery, [
      payers,
      timestamps,
      minTimestamp,
      maxTimestamp,
    ]);

    rows.forEach((row) => transactionMap.set(row.consensus_timestamp, new EthereumTransaction(row)));

    return transactionMap;
  }
}

export default new ContractService();
