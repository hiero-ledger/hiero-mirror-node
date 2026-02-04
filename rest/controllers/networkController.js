// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import BaseController from './baseController';
import {filterKeys, orderFilterValues, responseDataLabel} from '../constants';
import {InvalidArgumentError} from '../errors';
import {AddressBookEntry} from '../model';
import {NetworkNodeService} from '../service';
import * as utils from '../utils';
import {NetworkNodeViewModel} from '../viewmodel';
import EntityId from '../entityId';

const networkNodesDefaultSize = 10;
const networkNodesMaxSize = 25;

class NetworkController extends BaseController {
  acceptedNodeParameters = new Set([filterKeys.FILE_ID, filterKeys.LIMIT, filterKeys.NODE_ID, filterKeys.ORDER]);

  /**
   * Extracts SQL where conditions, params, order, and limit
   *
   * @param {[]} filters parsed and validated filters
   */
  extractNetworkNodesQuery = (filters) => {
    let limit = networkNodesDefaultSize;
    let order = orderFilterValues.ASC;
    let fileId = EntityId.systemEntity.addressBookFile102.getEncodedId(); // default fileId for mirror node
    const startPosition = 2; // 1st index is reserved for fileId
    const conditions = [];
    const params = [];
    const nodeInValues = [];
    let fileIdSpecified = false;

    for (const filter of filters) {
      if (_.isNil(filter)) {
        continue;
      }

      switch (filter.key) {
        case filterKeys.FILE_ID:
          if (fileIdSpecified) {
            throw new InvalidArgumentError(`Only a single instance is supported for ${filterKeys.FILE_ID}`);
          }
          if (utils.opsMap.eq !== filter.operator) {
            throw new InvalidArgumentError(
              `Only equals (eq) comparison operator is supported for ${filterKeys.FILE_ID}`
            );
          }
          fileId = filter.value;
          fileIdSpecified = true;
          break;
        case filterKeys.NODE_ID:
          this.updateConditionsAndParamsWithInValues(
            filter,
            nodeInValues,
            params,
            conditions,
            AddressBookEntry.getFullName(AddressBookEntry.NODE_ID),
            startPosition + conditions.length
          );
          break;
        case filterKeys.LIMIT:
          // response per address book node can be large so a reduced limit is enforced
          limit = filter.value <= networkNodesMaxSize ? filter.value : networkNodesMaxSize;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        default:
          break;
      }
    }

    this.updateQueryFiltersWithInValues(
      params,
      conditions,
      nodeInValues,
      AddressBookEntry.getFullName(AddressBookEntry.NODE_ID),
      params.length + startPosition
    );

    return {
      conditions,
      params: [fileId].concat(params),
      order,
      limit,
    };
  };

  /**
   * Handler function for /network/nodes API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getNetworkNodes = async (req, res) => {
    // extract filters from query param
    const filters = utils.buildAndValidateFilters(req.query, this.acceptedNodeParameters);

    const {conditions, params, order, limit} = this.extractNetworkNodesQuery(filters);
    const nodes = await NetworkNodeService.getNetworkNodes(conditions, params, order, limit);

    const response = {
      nodes: nodes.map((node) => new NetworkNodeViewModel(node)),
      links: {
        next: null,
      },
    };

    if (response.nodes.length === limit) {
      const lastRow = _.last(response.nodes);
      const last = {
        [filterKeys.NODE_ID]: lastRow.node_id,
      };
      response.links.next = utils.getPaginationLink(req, false, last, order);
    }

    res.locals[responseDataLabel] = response;
  };
}

export default new NetworkController();
