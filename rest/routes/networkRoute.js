// SPDX-License-Identifier: Apache-2.0

// external libraries
import express from 'express';

import {NetworkController} from '../controllers';
import extendExpress from '../extendExpress';

const router = extendExpress(express.Router());

const resource = 'network';
router.getExt('/nodes', NetworkController.getNetworkNodes);

export default {
  resource,
  router,
};
