// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const selector = '0xa6218810'; //cryptoTransferExternal
const sender = __ENV.ACCOUNT_ADDRESS;
const receiver = __ENV.ASSOCIATED_ACCOUNT;
const token = __ENV.TOKEN_ADDRESS;
const runMode = __ENV.RUN_WITH_VARIABLES;
//ABI encoded parameters used for crypto transfer token
const data1 =
  '0000000000000000000000000000000000000000000000000000000000000040' +
  '0000000000000000000000000000000000000000000000000000000000000080' +
  '0000000000000000000000000000000000000000000000000000000000000020' +
  '0000000000000000000000000000000000000000000000000000000000000000' +
  '0000000000000000000000000000000000000000000000000000000000000001' +
  '0000000000000000000000000000000000000000000000000000000000000020';
const data2 =
  '0000000000000000000000000000000000000000000000000000000000000060' +
  '0000000000000000000000000000000000000000000000000000000000000140' +
  '0000000000000000000000000000000000000000000000000000000000000002';
const data3 =
  'fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd' +
  '0000000000000000000000000000000000000000000000000000000000000000';
const data4 =
  '0000000000000000000000000000000000000000000000000000000000000003' +
  '0000000000000000000000000000000000000000000000000000000000000000' +
  '0000000000000000000000000000000000000000000000000000000000000000';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate('contractCallPrecompileCryptoTransferToken', false)
    : new ContractCallTestScenarioBuilder()
        .name('contractCallPrecompileCryptoTransferToken') // use unique scenario name among all tests
        .selector(selector)
        .args([data1, token, data2, sender, data3, receiver, data4])
        .to(contract)
        .blocks(getMixedBlocks())
        .build();

export {options, run};
