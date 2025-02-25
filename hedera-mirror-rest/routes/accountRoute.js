// SPDX-License-Identifier: Apache-2.0

// external libraries
import {Router} from '@awaitjs/express';

import {filterKeys} from '../constants';
import {AccountController, CryptoAllowanceController, TokenAllowanceController, TokenController} from '../controllers';

const router = Router();

const getPath = (path) => `/:${filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS}/${path}`;

const resource = 'accounts';
router.getAsync(getPath('nfts'), AccountController.getNftsByAccountId);
router.getAsync(getPath('rewards'), AccountController.listStakingRewardsByAccountId);
router.getAsync(getPath('allowances/crypto'), CryptoAllowanceController.getAccountCryptoAllowances);
router.getAsync(getPath('allowances/tokens'), TokenAllowanceController.getAccountTokenAllowances);
router.getAsync(getPath('tokens'), TokenController.getTokenRelationships);

export default {
  resource,
  router,
};
