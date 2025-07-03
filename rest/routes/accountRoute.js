// SPDX-License-Identifier: Apache-2.0

// external libraries
import express from 'express';

import {filterKeys} from '../constants';
import {AccountController, CryptoAllowanceController, TokenAllowanceController, TokenController} from '../controllers';
import extendExpress from '../extendExpress';

const router = extendExpress(express.Router());

const getPath = (path) => `/:${filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS}/${path}`;

const resource = 'accounts';
router.getExt(getPath('nfts'), AccountController.getNftsByAccountId);
router.getExt(getPath('rewards'), AccountController.listStakingRewardsByAccountId);
router.getExt(getPath('allowances/crypto'), CryptoAllowanceController.getAccountCryptoAllowances);
router.getExt(getPath('allowances/tokens'), TokenAllowanceController.getAccountTokenAllowances);
router.getExt(getPath('tokens'), TokenController.getTokenRelationships);

export default {
  resource,
  router,
};
