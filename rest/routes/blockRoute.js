// SPDX-License-Identifier: Apache-2.0

// external libraries
import express from 'express';

import {BlockController} from '../controllers';
import extendExpress from '../extendExpress';

const router = extendExpress(express.Router());

const resource = 'blocks';
router.getExt('/', BlockController.getBlocks);
router.getExt('/:hashOrNumber', BlockController.getByHashOrNumber);

export default {
  resource,
  router,
};
