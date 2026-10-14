// SPDX-License-Identifier: Apache-2.0

import config from '../config.js';
import {clusterIdHeader} from '../constants.js';

const clusterId = config.clusterId;

const clusterIdHandler = async (req, res) => {
  res.set(clusterIdHeader, clusterId);
};

export {clusterId, clusterIdHandler};
