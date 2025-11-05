// SPDX-License-Identifier: Apache-2.0

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as contractCallAllowance from './contractCallAllowance.js';
import * as contractCallApproved from './contractCallApproved.js';
import * as contractCallApprovedForAll from './contractCallApprovedForAll.js';
import * as contractCallBalance from './contractCallBalance.js';
import * as contractCallBalanceOf from './contractCallBalanceOf.js';
import * as contractCallDecimals from './contractCallDecimals.js';
import * as contractCallFungibleTokenInfo from './contractCallFungibleTokenInfo.js';
import * as contractCallIdentifier from './contractCallIdentifier.js';
import * as contractCallIsFrozen from './contractCallIsFrozen.js';
import * as contractCallIsKyc from './contractCallIsKyc.js';
import * as contractCallIsToken from './contractCallIsToken.js';
import * as contractCallMultiply from './contractCallMultiply.js';
import * as contractCallName from './contractCallName.js';
import * as contractCallNonFungibleTokenInfo from './contractCallNonFungibleTokenInfo.js';
import * as contractCallOwnerOf from './contractCallOwnerOf.js';
import * as contractCallReceive from './contractCallReceive.js';
import * as contractCallSender from './contractCallSender.js';
import * as contractCallSymbol from './contractCallSymbol.js';
import * as contractCallTokenCustomFees from './contractCallTokenCustomFees.js';
import * as contractCallTokenDefaultFreezeStatus from './contractCallTokenDefaultFreezeStatus.js';
import * as contractCallTokenDefaultKycStatus from './contractCallTokenDefaultKycStatus.js';
import * as contractCallTokenExpiryInfo from './contractCallTokenExpiryInfo.js';
import * as contractCallTokenInfo from './contractCallTokenInfo.js';
import * as contractCallTokenKey from './contractCallTokenKey.js';
import * as contractCallTokenType from './contractCallTokenType.js';
import * as contractCallTokenURI from './contractCallTokenURI.js';
import * as contractCallTotalSupply from './contractCallTotalSupply.js';
import * as contractCallEstimateFungibleTokenCustomFees from './contractCallEstimateFungibleTokenCustomFees.js';
import * as contractCallEstimateNftCustomFees from './contractCallEstimateNftCustomFees.js';
import * as contractCallEstimateCreateNft from './contractCallEstimateCreateNft.js';
import * as contractCallEstimateCreateFungibleToken from './contractCallEstimateCreateFungibleToken.js';
import * as contractCallEstimateTokenDissociate from './contractCallEstimateDissociateToken.js';
import * as contractCallEstimateApprove from './contractCallEstimateApprove.js';
import * as contractCallEstimateApproveNft from './contractCallEstimateApproveNft.js';
import * as contractCallEstimateAssociateTokens from './contractCallEstimateAssociateTokens.js';
import * as contractCallEstimateDissociateTokens from './contractCallEstimateDissociateTokens.js';
import * as contractCallEstimateERCApprove from './contractCallEstimateERCApprove.js';
import * as contractCallEstimateMintNft from './contractCallEstimateMintNft.js';
import * as contractCallEstimateReadStorage from './contractCallEstimateReadStorage.js';
import * as contractCallEstimateSetApprovalForAll from './contractCallEstimateSetApprovalForAll.js';
import * as contractCallEstimateFreezeToken from './contractCallEstimateFreezeToken.js';
import * as contractCallEstimateFreezeNft from './contractCallEstimateFreezeNft.js';
import * as contractCallEstimateUnfreezeToken from './contractCallEstimateUnfreezeToken.js';
import * as contractCallEstimateUnfreezeNft from './contractCallEstimateUnfreezeNft.js';
import * as contractCallEstimateTransferToken from './contractCallEstimateTransferToken.js';
import * as contractCallEstimateTransferNft from './contractCallEstimateTransferNft.js';
import * as contractCallPrecompileApprove from './modificationTests/contractCallPrecompileApprove.js';
import * as contractCallPrecompileAssociate from './modificationTests/contractCallPrecompileAssociate.js';
import * as contractCallPrecompileCryptoTransferHbars from './modificationTests/contractCallPrecompileCryptoTransferHbars.js';
import * as contractCallPrecompileCryptoTransferToken from './modificationTests/contractCallPrecompileCryptoTransferToken.js';
import * as contractCallPrecompileDeleteToken from './modificationTests/contractCallPrecompileDeleteToken.js';
import * as contractCallPrecompileDissociate from './modificationTests/contractCallPrecompileDissociate.js';
import * as contractCallPrecompileMintToken from './modificationTests/contractCallPrecompileMintToken.js';
import * as contractCallPrecompileNestedAssociate from './modificationTests/contractCallPrecompileNestedAssociate.js';
import * as contractCallPrecompileTransferFungibleToken from './modificationTests/contractCallPrecompileTransferFungibleToken.js';
import * as contractCallRedirectApprove from './modificationTests/contractCallRedirectApprove.js';
import * as contractCallComplexFunctionTokenLifecycle from './complex-functions/contractCallComplexFunctionsTokenLifecycle.js';
import * as contractCallComplexFunctionNFTLifecycle from './complex-functions/contractCallComplexFunctionsNFTLifecycle.js';
import * as contractResultsOpcodesAllPropertiesDisabled from './opcodes/contractResultsOpcodesAllPropertiesDisabled.js';
import * as contractResultsOpcodesAllPropertiesEnabled from './opcodes/contractResultsOpcodesAllPropertiesEnabled.js';
import * as rampUp from './rampUp.js';

// add test modules here
const tests = {
  contractCallAllowance,
  contractCallApproved,
  contractCallApprovedForAll,
  contractCallBalance,
  contractCallBalanceOf,
  contractCallDecimals,
  contractCallFungibleTokenInfo,
  contractCallIdentifier,
  contractCallIsFrozen,
  contractCallIsKyc,
  contractCallIsToken,
  contractCallMultiply,
  contractCallName,
  contractCallNonFungibleTokenInfo,
  contractCallOwnerOf,
  contractCallReceive,
  contractCallSender,
  contractCallSymbol,
  contractCallTokenCustomFees,
  contractCallTokenDefaultFreezeStatus,
  contractCallTokenDefaultKycStatus,
  contractCallTokenExpiryInfo,
  contractCallTokenInfo,
  contractCallTokenKey,
  contractCallTokenType,
  contractCallTokenURI,
  contractCallTotalSupply,
  rampUp,
};

if (__ENV.RUN_ESTIMATE_TESTS !== 'false') {
  Object.assign(tests, {
    contractCallEstimateFungibleTokenCustomFees,
    contractCallEstimateNftCustomFees,
    contractCallEstimateCreateNft,
    contractCallEstimateCreateFungibleToken,
    contractCallEstimateTokenDissociate,
    contractCallEstimateApprove,
    contractCallEstimateApproveNft,
    contractCallEstimateAssociateTokens,
    contractCallEstimateDissociateTokens,
    contractCallEstimateERCApprove,
    contractCallEstimateMintNft,
    contractCallEstimateReadStorage,
    contractCallEstimateSetApprovalForAll,
    contractCallEstimateFreezeToken,
    contractCallEstimateFreezeNft,
    contractCallEstimateUnfreezeToken,
    contractCallEstimateUnfreezeNft,
    contractCallEstimateTransferToken,
    contractCallEstimateTransferNft,
  });
}

if (__ENV.RUN_MODIFICATION_TESTS !== 'false') {
  Object.assign(tests, {
    contractCallPrecompileApprove,
    contractCallPrecompileAssociate,
    contractCallPrecompileCryptoTransferHbars,
    contractCallPrecompileCryptoTransferToken,
    contractCallPrecompileDeleteToken,
    contractCallPrecompileDissociate,
    contractCallPrecompileMintToken,
    contractCallPrecompileNestedAssociate,
    contractCallPrecompileTransferFungibleToken,
    contractCallRedirectApprove,
  });
}

if (__ENV.RUN_COMPLEX_TESTS !== 'false') {
  Object.assign(tests, {
    contractCallComplexFunctionTokenLifecycle,
    contractCallComplexFunctionNFTLifecycle,
  });
}

if (__ENV.RUN_OPCODE_TESTS !== 'false' && __ENV.TRANSACTION_IDS) {
  Object.assign(tests, {
    contractResultsOpcodesAllPropertiesDisabled,
    contractResultsOpcodesAllPropertiesEnabled,
  });
}

const {funcs, options, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(tests, 'WEB3');

export {funcs, options, scenarioDurationGauge, scenarios};
