// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {NotFoundError} from './errors';
import {SignatureType} from './model';
import * as utils from './utils';

const {default: defaultLimit} = getResponseLimit();
const LONGER_SCHEDULE_CACHE_CONTROL_HEADER = {'cache-control': 'public, max-age=3600'};

// select columns
const sqlQueryColumns = {
  ACCOUNT: 'creator_account_id',
  SCHEDULE_ID: 's.schedule_id',
};

// query to column maps
const filterColumnMap = {
  [constants.filterKeys.ACCOUNT_ID]: sqlQueryColumns.ACCOUNT,
  [constants.filterKeys.SCHEDULE_ID]: sqlQueryColumns.SCHEDULE_ID,
};

const scheduleFields = [
  'consensus_timestamp',
  'creator_account_id',
  'executed_timestamp',
  'expiration_time',
  'payer_account_id',
  'schedule_id',
  'transaction_body',
  'wait_for_expiry',
].join(',\n');
const entityFields = ['deleted', 'id', 'key', 'memo'].join(',\n');
const transactionSignatureJsonAgg = `
  json_agg(json_build_object(
    'consensus_timestamp', ts.consensus_timestamp,
    'public_key_prefix', encode(ts.public_key_prefix, 'base64'),
    'signature', encode(ts.signature, 'base64'),
    'type', ts.type
  ) order by ts.consensus_timestamp)`;
const getScheduleByIdQuery = `
  select
    s.consensus_timestamp,
    s.creator_account_id,
    e.deleted,
    s.executed_timestamp,
    s.expiration_time,
    e.key,
    e.memo,
    s.payer_account_id,
    s.schedule_id,
    s.transaction_body,
    s.wait_for_expiry,
    ${transactionSignatureJsonAgg} as signatures
  from schedule s
  left join entity e on e.id = s.schedule_id
  left join transaction_signature ts on ts.entity_id = s.schedule_id
  where s.schedule_id = $1
  group by s.schedule_id, e.id`;
const schedulesMainQuery = `select ${scheduleFields} from schedule s`;

const scheduleLimitQuery = (paramCount) => `limit $${paramCount}`;
const scheduleOrderQuery = (order) => `order by s.schedule_id ${order}`;

/**
 * Get the schedules list sql query to be used given the where clause, order and param count
 * @param whereQuery
 * @param order
 * @param count
 * @returns {string}
 */
const getSchedulesQuery = (whereQuery, order, count) => {
  return [schedulesMainQuery, whereQuery, scheduleOrderQuery(order), scheduleLimitQuery(count)].join('\n');
};

const formatScheduleRow = (row) => {
  const signatures = row.signatures
    ? row.signatures
        .filter((signature) => signature.consensus_timestamp !== null)
        .map((signature) => ({
          consensus_timestamp: utils.nsToSecNs(signature.consensus_timestamp),
          public_key_prefix: signature.public_key_prefix,
          signature: signature.signature,
          type: SignatureType.getName(signature.type),
        }))
    : [];

  return {
    admin_key: utils.encodeKey(row.key),
    deleted: row.deleted,
    consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
    creator_account_id: EntityId.parse(row.creator_account_id).toString(),
    executed_timestamp: utils.nsToSecNs(row.executed_timestamp),
    expiration_time: utils.nsToSecNs(row.expiration_time),
    memo: row.memo,
    payer_account_id: EntityId.parse(row.payer_account_id).toString(),
    schedule_id: EntityId.parse(row.schedule_id).toString(),
    signatures,
    transaction_body: utils.encodeBase64(row.transaction_body),
    wait_for_expiry: row.wait_for_expiry,
  };
};

/**
 * Handler function for /schedules/:scheduleId API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getScheduleById = async (req, res) => {
  utils.validateReq(req);
  const parseOptions = {allowEvmAddress: false, paramName: constants.filterKeys.SCHEDULEID};
  const scheduleId = EntityId.parseString(req.params.scheduleId, parseOptions).getEncodedId();

  const {rows} = await pool.queryQuietly(getScheduleByIdQuery, scheduleId);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  const schedule = rows[0];
  res.locals[constants.responseHeadersLabel] = getScheduleCacheControlHeader(schedule);
  res.locals[constants.responseDataLabel] = formatScheduleRow(schedule);
};

const getScheduleCacheControlHeader = (schedule) => {
  const nowNs = utils.nowInNs();
  const executedTimestamp = schedule.executed_timestamp;
  const expirationTime = schedule.expiration_time;
  const consensusTimestamp = schedule.consensus_timestamp;

  const hasExecuted = executedTimestamp !== null || schedule.deleted;
  const hasAutoExpired =
    expirationTime === null && nowNs >= consensusTimestamp + constants.THIRTY_ONE_MINUTES * constants.NANOS_PER_SECOND;
  const hasExpired =
    expirationTime !== null && nowNs >= expirationTime + constants.SIXTY_SECONDS * constants.NANOS_PER_SECOND;

  if (hasExecuted || hasAutoExpired || hasExpired) {
    return LONGER_SCHEDULE_CACHE_CONTROL_HEADER;
  }
  return {};
};

/**
 * Extract the sql where clause, params, order and limit values from the provided schedule query param filters
 * If no modifying filters are provided the default of no where clause, the defaultLimit and asc order will be returned
 * @param filters
 * @returns {{limit: Number, params: [*], filterQuery: string, order: string}}
 */
const extractSqlFromScheduleFilters = (filters) => {
  const filterQuery = {
    filterQuery: '',
    params: [defaultLimit],
    order: constants.orderFilterValues.ASC,
    limit: defaultLimit,
  };

  // if no filters return default filter of no where clause, defaultLimit and asc order
  if (filters && filters.length === 0) {
    return filterQuery;
  }

  const pgSqlParams = [];
  let whereQuery = '';
  let applicableFilters = 0; // track the number of schedule specific filters
  let paramCount = 1; // track the param count used for substitution, not affected by order and executed params

  for (const filter of filters) {
    if (filter.key === constants.filterKeys.LIMIT) {
      filterQuery.limit = filter.value;
      continue;
    }

    if (filter.key === constants.filterKeys.ORDER) {
      filterQuery.order = filter.value;
      continue;
    }

    const columnKey = filterColumnMap[filter.key];
    if (columnKey === undefined) {
      continue;
    }

    // add prefix. 'where' for the 1st param and 'and' for subsequent
    whereQuery += applicableFilters === 0 ? `where ` : ` and `;
    applicableFilters++;

    whereQuery += `${filterColumnMap[filter.key]}${filter.operator}$${paramCount}`;
    paramCount++;
    pgSqlParams.push(filter.value);
  }

  // add limit
  pgSqlParams.push(filterQuery.limit);

  filterQuery.filterQuery = whereQuery;
  filterQuery.params = pgSqlParams;

  return filterQuery;
};

/**
 * Merge schedule's entity properties and signatures
 * @param schedules
 * @param entities
 * @param signatures
 * @returns {*}
 */
const mergeScheduleEntities = (schedules, entities, signatures) => {
  const entityMap = entities.reduce((result, entity) => {
    result[entity.id] = _.omit(entity, ['id']);
    return result;
  }, {});
  const signatureMap = signatures.reduce((result, signature) => {
    result[signature.entity_id] = _.omit(signature, ['entity_id']);
    return result;
  }, {});

  return schedules.map((schedule) =>
    Object.assign(schedule, entityMap[schedule.schedule_id] ?? {}, signatureMap[schedule.schedule_id] ?? {})
  );
};

/**
 * Handler function for /schedules API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getSchedules = async (req, res) => {
  // extract filters from query param
  const filters = utils.buildAndValidateFilters(req.query, acceptedSchedulesParameters);

  // get sql filter query, params, order and limit from query filters
  const {filterQuery, params, order, limit} = extractSqlFromScheduleFilters(filters);
  const schedulesQuery = getSchedulesQuery(filterQuery, order, params.length);
  const {rows: schedules} = await pool.queryQuietly(schedulesQuery, params);

  const schedulesResponse = {schedules: [], links: {next: null}};
  res.locals[constants.responseDataLabel] = schedulesResponse;

  if (schedules.length === 0) {
    return;
  }

  const entityIds = schedules.map((s) => s.schedule_id);
  const positions = _.range(1, entityIds.length + 1)
    .map((i) => `$${i}`)
    .join(',');
  const entityQuery = `select ${entityFields} from entity where id in (${positions}) order by id ${order}`;
  const signatureQuery = `select entity_id, ${transactionSignatureJsonAgg} as signatures
    from transaction_signature ts
    where entity_id in (${positions})
    group by entity_id
    order by entity_id ${order}`;

  const [{rows: entities}, {rows: signatures}] = await Promise.all([
    pool.queryQuietly(entityQuery, entityIds),
    pool.queryQuietly(signatureQuery, entityIds),
  ]);

  schedulesResponse.schedules = mergeScheduleEntities(schedules, entities, signatures).map(formatScheduleRow);

  // populate next link
  const lastScheduleId =
    schedulesResponse.schedules.length > 0
      ? schedulesResponse.schedules[schedulesResponse.schedules.length - 1].schedule_id
      : null;

  schedulesResponse.links.next = utils.getPaginationLink(
    req,
    schedulesResponse.schedules.length !== limit,
    {
      [constants.filterKeys.SCHEDULE_ID]: lastScheduleId,
    },
    order
  );
};

const schedules = {
  getScheduleById,
  getSchedules,
  getScheduleCacheControlHeader,
};

const acceptedSchedulesParameters = new Set([
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.SCHEDULE_ID,
]);

if (utils.isTestEnv()) {
  Object.assign(schedules, {
    extractSqlFromScheduleFilters,
    formatScheduleRow,
    mergeScheduleEntities,
  });
}

export default schedules;
