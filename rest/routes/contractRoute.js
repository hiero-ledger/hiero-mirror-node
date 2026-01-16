// SPDX-License-Identifier: Apache-2.0

// external libraries
import express from 'express';

import {ContractController} from '../controllers';
import extendExpress from '../extendExpress';

const router = extendExpress(express.Router());

const resource = 'contracts';
router.getExt('/', ContractController.getContracts);
router.getExt('/:contractId', ContractController.getContractById);
router.getExt('/:contractId/results', ContractController.getContractResultsById);
router.getExt('/:contractId/state', ContractController.getContractStateById);
router.getExt('/:contractId/results/logs', ContractController.getContractLogsById);
// must add after '/:contractId/results/logs' for proper conflict resolution
router.getExt('/:contractId/results/:consensusTimestamp', ContractController.getContractResultsByTimestamp);
router.getExt('/results', ContractController.getContractResults);
router.getExt('/results/logs', ContractController.getContractLogs);
router.getExt('/results/:transactionIdOrHash', ContractController.getContractResultsByTransactionIdOrHash);
router.getExt('/results/:transactionIdOrHash/actions', ContractController.getContractActions);

export default {
  resource,
  router,
};
