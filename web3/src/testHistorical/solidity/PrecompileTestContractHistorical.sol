// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract PrecompileTestContractHistorical is HederaTokenService {


    function isTokenAddress(address token) external returns (bool){
        (int response,bool tokenFlag) = HederaTokenService.isToken(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token isTokenAddress failed!");
        }
        return tokenFlag;
    }

    function isTokenFrozen(address token, address account) external returns (bool){
        (int response,bool frozen) = HederaTokenService.isFrozen(token, account);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token isFrozen failed!");
        }
        return frozen;
    }

    function isKycGranted(address token, address account) external returns (bool){
        (int response,bool kycGranted) = HederaTokenService.isKyc(token, account);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token isKyc failed!");
        }
        return kycGranted;
    }

    function getTokenDefaultFreeze(address token) external returns (bool) {
        (int response,bool frozen) = HederaTokenService.getTokenDefaultFreezeStatus(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("getTokenDefaultFreezeStatus failed!");
        }
        return frozen;
    }

    function getTokenDefaultKyc(address token) external returns (bool) {
        (int response,bool kyc) = HederaTokenService.getTokenDefaultKycStatus(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("getTokenDefaultKycStatus failed!");
        }
        return kyc;
    }

    function getCustomFeesForToken(address token) external returns (
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees) {
        int responseCode;
        (responseCode, fixedFees, fractionalFees, royaltyFees) = HederaTokenService.getTokenCustomFees(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

    function getInformationForToken(address token) external returns (IHederaTokenService.TokenInfo memory tokenInfo) {
        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        tokenInfo = retrievedTokenInfo;
    }

    function getInformationForFungibleToken(address token) external returns (IHederaTokenService.FungibleTokenInfo memory fungibleTokenInfo) {
        (int responseCode, IHederaTokenService.FungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getFungibleTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        fungibleTokenInfo = retrievedTokenInfo;
    }

    function getInformationForNonFungibleToken(address token, int64 serialNumber) external returns (IHederaTokenService.NonFungibleTokenInfo memory nonFungibleTokenInfo) {
        (int responseCode, IHederaTokenService.NonFungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getNonFungibleTokenInfo(token, serialNumber);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        nonFungibleTokenInfo = retrievedTokenInfo;
    }

    function getType(address token) external returns (int) {
        (int statusCode, int tokenType) = HederaTokenService.getTokenType(token);

        if (statusCode != HederaResponseCodes.SUCCESS) {
            revert ("Token type appraisal failed!");
        }
        return tokenType;
    }

    function getExpiryInfoForToken(address token) external returns (
        IHederaTokenService.Expiry memory expiry) {
        (int responseCode,
            IHederaTokenService.Expiry memory retrievedExpiry) = HederaTokenService.getTokenExpiryInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        expiry = retrievedExpiry;
    }

    function getTokenKeyPublic(address token, uint keyType) public returns (IHederaTokenService.KeyValue memory) {
        (int responseCode, IHederaTokenService.KeyValue memory  key) = HederaTokenService.getTokenKey(token, keyType);
        //"(bool,address,bytes,bytes,address)";


        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }

        return key;
    }

    function htsGetApproved(address token, uint256 serialNumber) public returns (address approved) {
        int _responseCode;
        (_responseCode, approved) = HederaTokenService.getApproved(token, serialNumber);
    }

    function htsAllowance(address token, address owner, address spender) public returns (uint256 amount){
        int _responseCode;
        (_responseCode, amount) = HederaTokenService.allowance(token, owner, spender);
    }

    function htsIsApprovedForAll(address token, address owner, address operator) public returns (bool approved) {
        int _responseCode;
        (_responseCode, approved) = HederaTokenService.isApprovedForAll(token, owner, operator);
    }

    function callMissingPrecompile() public returns (bool success, bytes memory result) {
        (success, result) = address(0x167).call(
            abi.encodeWithSignature("fakeSignature()"));
        require(success);
    }

    function hrcIsAssociated(address token) public returns (bool isAssociated) {
        isAssociated = IHRC(token).isAssociated();
    }
}

interface IHRC {
    function isAssociated() external returns (bool associated);
}
