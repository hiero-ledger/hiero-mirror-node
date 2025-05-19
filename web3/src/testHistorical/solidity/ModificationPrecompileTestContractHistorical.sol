// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;


import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract ModificationPrecompileTestContractHistorical is HederaTokenService {

    function cryptoTransferExternal(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

}
