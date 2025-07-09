// SPDX-License-Identifier: Apache-2.0

import {apiPrefix, requestPathLabel} from '../constants';

import AccountRoutes from './accountRoute';
import BlockRoutes from './blockRoute';
import ContractRoutes from './contractRoute';
import NetworkRoutes from './networkRoute';

/**
 * Router middleware to record the complete registered request path as res.locals[requestPathLabel]
 *
 * @param req
 * @param res
 * @returns {Promise<void>}
 */
const recordRequestPath = async (req, res) => {
  const path = req.route?.path;
  if (path && !path.startsWith(apiPrefix) && !res.locals[requestPathLabel]) {
    res.locals[requestPathLabel] = `${req.baseUrl}${req.route.path}`.replace(/\/+$/g, '');
  }
};

[AccountRoutes, BlockRoutes, ContractRoutes, NetworkRoutes].forEach(({router}) => router.useExt(recordRequestPath));

export {AccountRoutes, BlockRoutes, ContractRoutes, NetworkRoutes};
