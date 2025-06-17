// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import {InvalidArgumentError} from '../errors';
import * as utils from '../utils';

class BaseController {
  updateConditionsAndParamsWithInValues = (
    filter,
    invalues,
    existingParams,
    existingConditions,
    fullName,
    position = existingParams.length
  ) => {
    if (filter.operator === utils.opsMap.eq) {
      // aggregate '=' conditions and use the sql 'in' operator
      invalues.push(filter.value);
    } else {
      existingParams.push(filter.value);
      existingConditions.push(`${fullName}${filter.operator}$${position}`);
    }
  };

  updateQueryFiltersWithInValues = (
    existingParams,
    existingConditions,
    invalues,
    fullName,
    start = existingParams.length + 1
  ) => {
    if (!_.isNil(invalues) && !_.isEmpty(invalues)) {
      // add the condition 'c.id in ()'
      existingParams.push(...invalues);
      const positions = _.range(invalues.length)
        .map((position) => position + start)
        .map((position) => `$${position}`);
      existingConditions.push(`${fullName} in (${positions})`);
    }
  };

  /**
   * Validate that the bounds are valid. Validates the following:
   *  Bound Range
   *  Secondary Bound is not present without Primary Bound
   *  Lower Bounds
   *  Upper Bounds.
   *
   * @param {Bound}[] bounds
   * @throws {InvalidArgumentError}
   */
  validateBounds(bounds) {
    this.validateBoundsRange(bounds);
    this.validateSecondaryBound(bounds);
    this.validateLowerBounds(bounds);
    this.validateUpperBounds(bounds);
  }

  /**
   * Validate that if the primary bound is empty the secondary bound is empty as well.
   *
   * @param {Bound}[] bounds
   * @throws {InvalidArgumentError}
   */
  validateSecondaryBound(bounds) {
    if (bounds.primary.isEmpty() && !bounds.secondary.isEmpty()) {
      throw new InvalidArgumentError(
        `${bounds.secondary.filterKey} without a ${bounds.primary.filterKey} parameter filter`
      );
    }
  }

  /**
   * Validate that the Lower Bounds are valid.
   *
   * @param {Bound}[] bounds
   * @throws {InvalidArgumentError}
   */
  validateLowerBounds(bounds) {
    const {primary, secondary} = bounds;
    if (
      !primary.hasEqual() &&
      secondary.hasLower() &&
      (!primary.hasLower() || primary.lower.operator === utils.opsMap.gt)
    ) {
      throw new InvalidArgumentError(`${primary.filterKey} must have gte or eq operator`);
    }
  }

  /**
   * Validate that the Upper Bounds are valid.
   *
   * @param {Bound}[] bounds
   * @throws {InvalidArgumentError}
   */
  validateUpperBounds(bounds) {
    const {primary, secondary} = bounds;
    if (
      !primary.hasEqual() &&
      secondary.hasUpper() &&
      (!primary.hasUpper() || primary.upper.operator === utils.opsMap.lt)
    ) {
      throw new InvalidArgumentError(`${primary.filterKey} must have lte or eq operator`);
    }
  }

  /**
   * Validate the bound range and equal combination
   *
   * @param {Bound}[] bounds
   * @throws {InvalidArgumentError}
   */
  validateBoundsRange(bounds) {
    Object.keys(bounds).forEach((key) => {
      if (bounds[key].hasBound() && bounds[key].hasEqual()) {
        throw new InvalidArgumentError(`Can't support both range and equal for ${bounds[key].filterKey}`);
      }
    });
  }

  /**
   * Gets filters for the lower part of the multi-union query.
   *
   * @param {Bound}[] bounds
   * @return {{key: string, operator: string, value: *}[]}
   */
  getLowerFilters(bounds) {
    let filters = [];
    const {primary, secondary} = bounds;
    if (!secondary.hasBound()) {
      // no secondary bound filters or no secondary filters at all, everything goes into the lower part and there
      // shouldn't be inner or upper part.
      filters = [primary.equal, primary.lower, primary.upper, secondary.equal];
    } else if (primary.hasLower() && secondary.hasLower()) {
      // both have lower. If primary has lower and secondary doesn't have lower, the lower bound of primary
      // will go into the inner part.
      filters = [{...primary.lower, operator: utils.opsMap.eq}, secondary.lower];
    } else if (primary.hasEqual()) {
      filters = [primary.equal, primary.lower, primary.upper, secondary.lower, secondary.equal, secondary.upper];
    }
    return filters.filter((f) => !_.isNil(f));
  }

  /**
   * Gets filters for the inner part of the multi-union query
   *
   * @param {Bound}[] Bounds
   * @return {{key: string, operator: string, value: *}[]}
   */
  getInnerFilters(bounds) {
    const {primary, secondary} = bounds;
    if (!primary.hasBound() || !secondary.hasBound()) {
      return [];
    }

    return [
      // if secondary has lower bound, the primary filter should be > ?
      {filter: primary.lower, newOperator: secondary.hasLower() ? utils.opsMap.gt : null},
      // if secondary has upper bound, the primary filter should be < ?
      {filter: primary.upper, newOperator: secondary.hasUpper() ? utils.opsMap.lt : null},
    ]
      .filter((f) => !_.isNil(f.filter))
      .map((f) => ({...f.filter, operator: f.newOperator || f.filter.operator}));
  }

  /**
   * Gets filters for the upper part of the multi-union query
   *
   * @param {Bound}[] Bounds
   * @return {{key: string, operator: string, value: *}[]}
   */
  getUpperFilters(bounds) {
    const {primary, secondary} = bounds;
    if (!primary.hasUpper() || !secondary.hasUpper()) {
      return [];
    }
    // the upper part should always have primary filter = ?
    return [{...primary.upper, operator: utils.opsMap.eq}, secondary.upper];
  }

  /**
   * Gets the pagination link based on 2 main query params
   *
   * @param {Request} req
   * @param {*[]} rows
   * @param {Bound} bounds
   * @param {number} limit
   * @param {string} order
   * @return {string|null}
   */
  getPaginationLink(req, rows, bounds, limit, order) {
    const primaryBound = bounds.primary;
    const secondaryBound = bounds.secondary;
    const isEnd = rows.length < limit;

    if (!bounds.next && (isEnd || (primaryBound.hasEqual() && secondaryBound.hasEqual()))) {
      // fetched all matching rows or the query is for a specific combination
      return null;
    }

    const lastRow = _.last(rows);
    const lastValues = {};
    if (primaryBound.hasBound() || primaryBound.isEmpty()) {
      // the primary param has bound or no primary param at all
      // primary param should be exclusive when the secondary operator is eq
      lastValues[primaryBound.filterKey] = {
        inclusive: !secondaryBound.hasEqual(),
        primary: true,
        value: !isEnd ? lastRow[primaryBound.viewModelKey] : bounds.next,
      };
    }

    if (secondaryBound.hasBound() || secondaryBound.isEmpty()) {
      // the secondary param has bound or no secondary param at all
      lastValues[secondaryBound.filterKey] = {value: lastRow[secondaryBound.viewModelKey]};
    }

    return utils.getPaginationLink(req, false, lastValues, order);
  }

  /**
   * Formats the filter where condition
   * @param key
   * @param filter
   * @returns {{param, query: string}}
   */
  getFilterWhereCondition = (key, filter) => {
    return {
      query: `${key} ${filter.operator}`,
      param: filter.value,
    };
  };
}

export default BaseController;
