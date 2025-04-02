// SPDX-License-Identifier: Apache-2.0

import {getResponseLimit} from './config';
import config from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {InvalidArgumentError, NotFoundError} from './errors';
import {TopicMessage} from './model';
import * as utils from './utils';
import {TopicMessageViewModel} from './viewmodel';

const {default: defaultLimit} = getResponseLimit();

const columnMap = {
  [constants.filterKeys.SEQUENCE_NUMBER]: TopicMessage.SEQUENCE_NUMBER,
  [constants.filterKeys.TIMESTAMP]: TopicMessage.CONSENSUS_TIMESTAMP,
};

/**
 * Verify consensusTimestamp meets seconds or seconds.upto 9 digits format
 */
const validateConsensusTimestampParam = (consensusTimestamp) => {
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    throw InvalidArgumentError.forParams(TopicMessage.CONSENSUS_TIMESTAMP);
  }
};

/**
 * Verify topicId and sequencenumber meet entity num format
 */
const validateGetSequenceMessageParams = (topicId, seqNum) => {
  const badParams = [];
  if (!EntityId.isValidEntityId(topicId, false)) {
    badParams.push(constants.filterKeys.TOPIC_ID);
  }

  if (!utils.isPositiveLong(seqNum)) {
    badParams.push(constants.filterKeys.SEQUENCE_NUMBER);
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }
};

/**
 * Verify topicId and sequencenumber meet entity num and limit format
 */
const validateGetTopicMessagesParams = (topicId) => {
  if (!EntityId.isValidEntityId(topicId, false)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.TOPIC_ID);
  }
};

/**
 * Handler function for /messages/:consensusTimestamp API.
 * Extracts and validates timestamp input, creates db query logic in preparation for db call to get message
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByConsensusTimestamp = async (req, res) => {
  utils.validateReq(req);
  const consensusTimestampParam = req.params.consensusTimestamp;
  validateConsensusTimestampParam(consensusTimestampParam);

  const consensusTimestamp = utils.parseTimestampParam(consensusTimestampParam);

  let query = `select consensus_timestamp, entity_id
    from transaction
    where consensus_timestamp = $1 and result = 22 and type = 27`;
  const {rows} = await pool.queryQuietly(query, consensusTimestamp);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  query = `select *
    from topic_message
    where topic_id = $1 and consensus_timestamp = $2`;
  const params = [rows[0].entity_id, rows[0].consensus_timestamp];

  res.locals[constants.responseDataLabel] = await getMessage(query, params);
};

/**
 * Handler function for /:id/messages/:sequenceNumber API.
 * Extracts and validates topic and sequence params and creates db query statement in preparation for db call to get
 * message
 *
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByTopicAndSequenceRequest = async (req, res) => {
  utils.validateReq(req);
  const topicIdStr = req.params.topicId;
  const seqNum = req.params.sequenceNumber;
  validateGetSequenceMessageParams(topicIdStr, seqNum);

  const topicId = EntityId.parseString(topicIdStr).getEncodedId();
  let query = `select *
                  from ${TopicMessage.tableName}
                  where ${TopicMessage.TOPIC_ID} = $1
                    and ${TopicMessage.SEQUENCE_NUMBER} = $2`;
  const params = [topicId, seqNum];

  if (config.query.topicMessageLookup) {
    const {begin, end} = await getTopicMessageTimestampRange(topicId, seqNum);
    params.push(begin, end);
    query += ` and ${TopicMessage.CONSENSUS_TIMESTAMP} >= $3 and ${TopicMessage.CONSENSUS_TIMESTAMP} < $4`;
  }

  res.locals[constants.responseDataLabel] = await getMessage(query, params);
};

/**
 * Handler function for /topics/:topicId API.
 * @returns {Promise} Promise for PostgreSQL query
 */
const getTopicMessages = async (req, res) => {
  const topicIdStr = req.params.topicId;
  validateGetTopicMessagesParams(topicIdStr);

  const encoding = req.query[constants.filterKeys.ENCODING];
  const filters = utils.buildAndValidateFilters(req.query, acceptedTopicsParameters);
  const topicId = EntityId.parseString(topicIdStr).getEncodedId();

  const topicMessagesResponse = {
    messages: [],
    links: {
      next: null,
    },
  };
  res.locals[constants.responseDataLabel] = topicMessagesResponse;

  // build sql query validated param and filters
  const {query, params, order, limit} = await extractSqlFromTopicMessagesRequest(topicId, filters);
  if (!query) {
    return;
  }

  // get results and return formatted response
  // if limit is not 1, set random_page_cost to 0 to make the cost estimation of using the index on
  // (topic_id, consensus_timestamp) lower than that of the primary key so pg planner will choose the better index
  // when querying topic messages by id
  const queryHint = limit !== 1 ? constants.zeroRandomPageCostQueryHint : undefined;
  const messages = await getMessages(query, params, queryHint);
  topicMessagesResponse.messages = messages.map((m) => new TopicMessageViewModel(m, encoding));

  // populate next
  const lastTimeStamp =
    topicMessagesResponse.messages.length > 0
      ? topicMessagesResponse.messages[topicMessagesResponse.messages.length - 1][TopicMessage.CONSENSUS_TIMESTAMP]
      : null;

  topicMessagesResponse.links.next = utils.getPaginationLink(
    req,
    topicMessagesResponse.messages.length !== limit,
    {
      [constants.filterKeys.TIMESTAMP]: lastTimeStamp,
    },
    order
  );
};

const extractSqlFromTopicMessagesRequest = async (topicId, filters) => {
  const conditions = [`${TopicMessage.TOPIC_ID} = $1`];
  const params = [topicId];
  const sequenceNumberFilters = [];
  const timestampFilters = [];
  const filtersMap = {
    [constants.filterKeys.SEQUENCE_NUMBER]: sequenceNumberFilters,
    [constants.filterKeys.TIMESTAMP]: timestampFilters,
  };
  const topicMessageLookupEnabled = config.query.topicMessageLookup;

  // add filters
  let limit = defaultLimit;
  let order = constants.orderFilterValues.ASC;

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        order = filter.value;
        break;
      default:
        const column = columnMap[filter.key];
        if (!column) {
          break;
        }

        if (topicMessageLookupEnabled) {
          filtersMap[filter.key].push(filter);
        }
        conditions.push(`${column}${filter.operator}$${params.push(filter.value)}`);
        break;
    }
  }

  if (topicMessageLookupEnabled) {
    const ranges = await getTopicMessageTimestampRanges(topicId, sequenceNumberFilters, timestampFilters, limit, order);
    if (ranges.length === 0) {
      return {};
    }

    const timestampRangeCondition = ranges
      .map((r) => `(consensus_timestamp >= $${params.push(r.begin)} and consensus_timestamp < $${params.push(r.end)})`)
      .join(' or ');
    conditions.push(`(${timestampRangeCondition})`);
  }

  const query = `select *
      from ${TopicMessage.tableName}
      where ${conditions.join(' and ')}
      order by ${TopicMessage.CONSENSUS_TIMESTAMP} ${order}
      limit $${params.push(limit)}`;

  return utils.buildPgSqlObject(query, params, order, limit);
};

const getRangeFromFilters = (filters, defaultLower = 0n) => {
  // Set upper to MAX_LONG - 1 to avoid overflow in Postgres, because
  // postgres=> select int8range(0::bigint, 9223372036854775807::bigint, '[]');
  // ERROR:  bigint out of range
  const range = {lower: defaultLower, upper: constants.MAX_LONG - 1n};
  for (const filter of filters) {
    let value = BigInt(filter.value);
    switch (filter.operator) {
      case utils.opsMap.eq:
        if (value < range.lower || value > range.upper) {
          // The range is effectively empty, so return null
          return null;
        }

        range.lower = range.upper = value;
        break;
      case utils.opsMap.gt:
        value += 1n;
      case utils.opsMap.gte:
        range.lower = utils.bigIntMax(range.lower, value);
        break;
      case utils.opsMap.lt:
        value -= 1n;
      case utils.opsMap.lte:
        range.upper = utils.bigIntMin(range.upper, value);
        break;
      case utils.opsMap.ne:
        throw new InvalidArgumentError(`Not equal (ne) operator is not supported for ${filter.key}`);
    }
  }

  return range.lower <= range.upper ? range : null;
};

const getTopicMessageTimestampRanges = async (topicId, sequenceNumberFilters, timestampFilters, limit, order) => {
  const sequenceNumberRange = getRangeFromFilters(sequenceNumberFilters, 1n);
  const timestampRange = getRangeFromFilters(timestampFilters);
  if (sequenceNumberRange === null || timestampRange === null) {
    return [];
  }

  const isOrderAsc = order === constants.orderFilterValues.ASC;
  let query;
  const params = [
    topicId,
    `[${sequenceNumberRange.lower},${sequenceNumberRange.upper}]`,
    `[${timestampRange.lower},${timestampRange.upper}]`,
  ];

  if (sequenceNumberRange.upper - sequenceNumberRange.lower + 1n <= limit) {
    // If the range size doesn't exceed the limit, use it as is. Note explicitly casting topic id to bigint is
    // required to utilize the btree gist index on (topic_id, sequence_number_range)
    query = `select timestamp_range
      from topic_message_lookup
      where topic_id = $1::bigint and sequence_number_range && $2::int8range and timestamp_range && $3::int8range
      order by sequence_number_range`;
  } else {
    // Depending on the order, first query the table to find the bound of the sequence number range and build the actual
    // range from the bound and the limit, then query the table with the actual range
    params.push(limit);
    // Since the information in the table can't tell the first sequence number satisfying the sequence number range and
    // the timestamp range, for instance, with sequencenumber=lt:500, timestamp=lt:1000, limit=25, and order=desc, the
    // first sequence number range might be [300, 400), the minimum value of the largest sequence number is 300, and
    // the maximum is 399. In order to get the correct timestamp range, we need to use an actual range which covers
    // all possible sequence number values, i.e., [276, 400)
    // Notes:
    // - for asc order, when expanding the upper, the delta least(limit, 9223372036854775807::bigint - upper(range)) is
    //   used to ensure no overflow occurs
    // - for desc order, lower bound is expanded by limit - 1, since the lower bound is inclusive
    // - upper($2::int8range) is always the exclusive upper bound, i.e., when the value passed in is '[1, 10]', pg
    //   upper returns 11
    const rangeExpression = isOrderAsc
      ? `int8range(lower(range), least(upper(range) + least($4, 9223372036854775807::bigint - upper(range)), upper($2::int8range)), '[)')`
      : `int8range(greatest(lower(range) - ($4 - 1), lower($2::int8range)), upper(range), '[)')`;

    query = `with actual_range as (
        select ${rangeExpression} as range
        from (
          select sequence_number_range as range
          from topic_message_lookup
          where topic_id = $1::bigint and
            sequence_number_range && $2::int8range and
            timestamp_range && $3::int8range
          order by sequence_number_range ${order}
          limit 1
        ) as t
      )
      select timestamp_range
      from topic_message_lookup as t, actual_range as a
      where topic_id = $1::bigint and
        sequence_number_range && a.range and
        timestamp_range && $3::int8range
      order by sequence_number_range`;
  }

  const {rows} = await pool.queryQuietly(query, params);
  return rows.map((r) => r.timestamp_range);
};

/**
 * Retrieves topic message from
 */
const getMessage = async (query, params) => {
  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  return new TopicMessageViewModel(new TopicMessage(rows[0]));
};

const getMessages = async (query, params, preQueryHint) => {
  const {rows} = await pool.queryQuietly(query, params, preQueryHint);
  logger.debug(`getMessages returning ${rows.length} entries`);
  return rows.map((row) => new TopicMessage(row));
};

const getTopicMessageTimestampRange = async (topicId, seqNum) => {
  // Get the timestamp range from the topic message lookup table. Note explicitly casting topic id to bigint is required
  // to utilize the btree gist index on (topic_id, sequence_number_range)
  const query = `select timestamp_range
                    from topic_message_lookup
                    where topic_id = $1::bigint and sequence_number_range @> $2::bigint
                    order by sequence_number_range`;
  const params = [topicId, seqNum];

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length === 0) {
    throw new NotFoundError();
  }

  return rows[0].timestamp_range;
};

const topicMessage = {
  getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest,
  getTopicMessages,
};

const acceptedTopicsParameters = new Set([
  constants.filterKeys.ENCODING,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.SEQUENCE_NUMBER,
  constants.filterKeys.TIMESTAMP,
]);

if (utils.isTestEnv()) {
  Object.assign(topicMessage, {
    extractSqlFromTopicMessagesRequest,
    getRangeFromFilters,
    getTopicMessageTimestampRanges,
    validateConsensusTimestampParam,
    validateGetSequenceMessageParams,
    validateGetTopicMessagesParams,
  });
}

export default topicMessage;
