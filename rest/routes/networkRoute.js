// SPDX-License-Identifier: Apache-2.0

// external libraries
import {Router} from '@awaitjs/express';

import {NetworkController} from '../controllers';

const router = Router();

const resource = 'network';
router.getAsync('/exchangerate', NetworkController.getExchangeRate);
router.getAsync('/fees', NetworkController.getFees);
router.getAsync('/nodes', NetworkController.getNetworkNodes);
router.getAsync('/stake', NetworkController.getNetworkStake);
router.getAsync('/supply', NetworkController.getSupply);

export default {
  resource,
  router,
};
