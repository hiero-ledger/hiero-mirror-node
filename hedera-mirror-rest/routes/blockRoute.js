// SPDX-License-Identifier: Apache-2.0

// external libraries
import {Router} from '@awaitjs/express';

import {BlockController} from '../controllers/index.js';

const router = Router();

const resource = 'blocks';
router.getAsync('/', BlockController.getBlocks);
router.getAsync('/:hashOrNumber', BlockController.getByHashOrNumber);

export default {
  resource,
  router,
};
