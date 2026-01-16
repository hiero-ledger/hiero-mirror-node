// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const selector = '0xa6218810'; //cryptoTransferExternal
const sender = __ENV.ACCOUNT_ADDRESS;
const receiver = __ENV.SPENDER_ADDRESS;
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'contractCallPrecompileCryptoTransferHbars';
//ABI encoded parameters used for crypto transfer of Hbars
const data1 =
  '0000000000000000000000000000000000000000000000000000000000000040' +
  '0000000000000000000000000000000000000000000000000000000000000140' +
  '0000000000000000000000000000000000000000000000000000000000000020' +
  '0000000000000000000000000000000000000000000000000000000000000002';
const data2 =
  'fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6' +
  '0000000000000000000000000000000000000000000000000000000000000000';
const data3 =
  '000000000000000000000000000000000000000000000000000000000000000a' +
  '0000000000000000000000000000000000000000000000000000000000000000' +
  '0000000000000000000000000000000000000000000000000000000000000000';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([data1, sender, data2, receiver, data3])
        .to(contract)
        .build();

export {options, run};
