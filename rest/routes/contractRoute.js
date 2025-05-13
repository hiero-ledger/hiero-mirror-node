// SPDX-License-Identifier: Apache-2.0

// external libraries
import {Router} from '@awaitjs/express';

import {ContractController} from '../controllers';

const router = Router();

const resource = 'contracts';
router.getAsync('/', ContractController.getContracts);
router.getAsync('/:contractId', ContractController.getContractById);
router.getAsync('/:contractId/results', ContractController.getContractResultsById);
router.getAsync('/:contractId/state', ContractController.getContractStateById);
router.getAsync('/:contractId/results/logs', ContractController.getContractLogsById);
router.getAsync('/:contractId/results/:consensusTimestamp([0-9.]+)', ContractController.getContractResultsByTimestamp);
router.getAsync('/results', ContractController.getContractResults);
router.getAsync('/results/logs', ContractController.getContractLogs);
router.getAsync('/results/:transactionIdOrHash', ContractController.getContractResultsByTransactionIdOrHash);
router.getAsync('/results/:transactionIdOrHash/actions', ContractController.getContractActions);

export default {
  resource,
  router,
};
