// SPDX-License-Identifier: Apache-2.0

import BaseService from './baseService';
import {
  AddressBook,
  AddressBookEntry,
  AddressBookServiceEndpoint,
  Node,
  NetworkNode,
  NetworkStake,
  NodeStake,
} from '../model';
import {OrderSpec} from '../sql';
import EntityId from '../entityId';

/**
 * Network node business model
 */
class NetworkNodeService extends BaseService {
  // add node filter
  static networkNodesBaseQuery = `with ${AddressBook.tableAlias} as (
      select ${AddressBook.START_CONSENSUS_TIMESTAMP}, ${AddressBook.END_CONSENSUS_TIMESTAMP}, ${AddressBook.FILE_ID}
      from ${AddressBook.tableName} where ${AddressBook.FILE_ID} = $1
      order by ${AddressBook.START_CONSENSUS_TIMESTAMP} desc limit 1
    ),
    ${NodeStake.tableAlias} as (
      select ${NodeStake.MAX_STAKE}, ${NodeStake.MIN_STAKE}, ${NodeStake.NODE_ID}, ${NodeStake.REWARD_RATE},
             ${NodeStake.STAKE}, ${NodeStake.STAKE_NOT_REWARDED}, ${NodeStake.STAKE_REWARDED},
             ${NodeStake.STAKING_PERIOD}
      from ${NodeStake.tableName}
      where ${NodeStake.CONSENSUS_TIMESTAMP} =
        (select max(${NodeStake.CONSENSUS_TIMESTAMP}) from ${NodeStake.tableName})
    ),
    ${Node.tableAlias} as (
      select ${Node.ADMIN_KEY}, ${Node.NODE_ID}
      from ${Node.tableName}
    )
    select ${AddressBookEntry.getFullName(AddressBookEntry.DESCRIPTION)},
      ${AddressBookEntry.getFullName(AddressBookEntry.MEMO)},
      ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)},
      ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ACCOUNT_ID)},
      ${AddressBookEntry.getFullName(AddressBookEntry.NODE_CERT_HASH)},
      ${AddressBookEntry.getFullName(AddressBookEntry.PUBLIC_KEY)},
      ${AddressBook.getFullName(AddressBook.FILE_ID)},
      ${AddressBook.getFullName(AddressBook.START_CONSENSUS_TIMESTAMP)},
      ${AddressBook.getFullName(AddressBook.END_CONSENSUS_TIMESTAMP)},
      ${Node.getFullName(Node.ADMIN_KEY)},
      ${NodeStake.getFullName(NodeStake.MAX_STAKE)},
      ${NodeStake.getFullName(NodeStake.MIN_STAKE)},
      ${NodeStake.getFullName(NodeStake.REWARD_RATE)},
      ${NodeStake.getFullName(NodeStake.STAKE)},
      ${NodeStake.getFullName(NodeStake.STAKE_NOT_REWARDED)},
      ${NodeStake.getFullName(NodeStake.STAKE_REWARDED)},
      ${NodeStake.getFullName(NodeStake.STAKING_PERIOD)},
      coalesce((
        select jsonb_agg(jsonb_build_object(
        '${AddressBookServiceEndpoint.IP_ADDRESS_V4}', ${AddressBookServiceEndpoint.IP_ADDRESS_V4},
        '${AddressBookServiceEndpoint.PORT}', ${AddressBookServiceEndpoint.PORT},
        '${AddressBookServiceEndpoint.DOMAIN_NAME}', ${AddressBookServiceEndpoint.DOMAIN_NAME}
        ) order by ${AddressBookServiceEndpoint.IP_ADDRESS_V4} asc, ${AddressBookServiceEndpoint.PORT} asc)
        from ${AddressBookServiceEndpoint.tableName} ${AddressBookServiceEndpoint.tableAlias}
        where ${AddressBookServiceEndpoint.getFullName(AddressBookServiceEndpoint.CONSENSUS_TIMESTAMP)} =
          ${AddressBookEntry.getFullName(AddressBookEntry.CONSENSUS_TIMESTAMP)} and
          ${AddressBookServiceEndpoint.getFullName(AddressBookServiceEndpoint.NODE_ID)} =
          ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)}
      ), '[]') as service_endpoints
    from ${AddressBookEntry.tableName} ${AddressBookEntry.tableAlias}
    join ${AddressBook.tableAlias} on ${AddressBook.getFullName(AddressBook.START_CONSENSUS_TIMESTAMP)} =
      ${AddressBookEntry.getFullName(AddressBookEntry.CONSENSUS_TIMESTAMP)}
    left join ${NodeStake.tableAlias} on ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)} =
      ${NodeStake.getFullName(NodeStake.NODE_ID)}
    left join ${Node.tableAlias} on ${AddressBookEntry.getFullName(AddressBookEntry.NODE_ID)} =
      ${Node.getFullName(Node.NODE_ID)}`;

  static networkStakeQuery = `select ${NetworkStake.MAX_STAKING_REWARD_RATE_PER_HBAR},
         ${NetworkStake.MAX_STAKE_REWARDED},
         ${NetworkStake.MAX_TOTAL_REWARD},
         ${NetworkStake.NODE_REWARD_FEE_DENOMINATOR},
         ${NetworkStake.NODE_REWARD_FEE_NUMERATOR},
         ${NetworkStake.RESERVED_STAKING_REWARDS},
         ${NetworkStake.REWARD_BALANCE_THRESHOLD},
         ${NetworkStake.STAKE_TOTAL},
         ${NetworkStake.STAKING_PERIOD},
         ${NetworkStake.STAKING_PERIOD_DURATION},
         ${NetworkStake.STAKING_PERIODS_STORED},
         ${NetworkStake.STAKING_REWARD_FEE_DENOMINATOR},
         ${NetworkStake.STAKING_REWARD_FEE_NUMERATOR},
         ${NetworkStake.STAKING_REWARD_RATE},
         ${NetworkStake.STAKING_START_THRESHOLD},
         ${NetworkStake.UNRESERVED_STAKING_REWARD_BALANCE}
      from ${NetworkStake.tableName}
      where ${NetworkStake.CONSENSUS_TIMESTAMP} =
            (select max(${NetworkStake.CONSENSUS_TIMESTAMP}) from ${NetworkStake.tableName})`;

  static unreleasedSupplyAccounts = (column) =>
    EntityId.systemEntity.unreleasedSupplyAccounts
      .map((range) => {
        const from = range.from.getEncodedId();
        const to = range.to.getEncodedId();

        if (from === to) {
          return `${column} = ${from}`;
        } else {
          return `(${column} >= ${from} and ${column} <= ${to})`;
        }
      })
      .join(' or ');

  static networkSupplyQuery = `
      select coalesce(sum(balance), 0) as unreleased_supply, coalesce(max(balance_timestamp), 0) as consensus_timestamp
      from entity
      where ${NetworkNodeService.unreleasedSupplyAccounts('id')}`;

  getNetworkSupplyByTimestampQuery = (conditions) => `
      with account_balances as (
          select distinct on (account_id) balance, consensus_timestamp
          from account_balance ab
          where ${conditions} and (${NetworkNodeService.unreleasedSupplyAccounts('account_id')})
          order by account_id asc, consensus_timestamp desc
      )
      select coalesce(sum(balance), 0) as unreleased_supply, max(consensus_timestamp) as consensus_timestamp
      from account_balances`;

  getNetworkNodes = async (whereConditions, whereParams, order, limit) => {
    const [query, params] = this.getNetworkNodesWithFiltersQuery(whereConditions, whereParams, order, limit);

    const rows = await super.getRows(query, params);
    return rows.map((x) => new NetworkNode(x));
  };

  getNetworkNodesWithFiltersQuery = (whereConditions, whereParams, nodeOrder, limit) => {
    const params = whereParams;
    params.push(limit);
    const query = [
      NetworkNodeService.networkNodesBaseQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      `${super.getOrderByQuery(OrderSpec.from(AddressBookEntry.getFullName(AddressBookEntry.NODE_ID), nodeOrder))}`,
      super.getLimitQuery(params.length),
    ].join('\n');

    return [query, params];
  };

  getNetworkStake = async () => {
    const row = await super.getSingleRow(NetworkNodeService.networkStakeQuery, []);
    return row && new NetworkStake(row);
  };

  getSupply = async (conditions, params) => {
    let query = NetworkNodeService.networkSupplyQuery;

    if (conditions.length > 0) {
      query = this.getNetworkSupplyByTimestampQuery(conditions.join(' and '));
    }

    return await super.getSingleRow(query, params);
  };
}

export default new NetworkNodeService();
