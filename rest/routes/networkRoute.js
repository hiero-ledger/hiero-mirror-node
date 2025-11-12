// SPDX-License-Identifier: Apache-2.0

// external libraries
import express from 'express';

import {NetworkController} from '../controllers';
import extendExpress from '../extendExpress';

const router = extendExpress(express.Router());

const resource = 'network';
router.getExt('/fees', NetworkController.getFees);
router.getExt('/nodes', NetworkController.getNetworkNodes);
router.getExt('/supply', NetworkController.getSupply);

export default {
  resource,
  router,
};
