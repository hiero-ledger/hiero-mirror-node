// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Metadata.sol";


contract PrecompileTestContract is HederaTokenService {
    function isTokenAddress(address token) external returns (bool) {
        (int256 response, bool tokenFlag) = HederaTokenService.isToken(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert("Token isTokenAddress failed!");
        }
        return tokenFlag;
    }

    function isTokenFrozen(address token, address account) external returns (bool) {
        (int256 response, bool frozen) = HederaTokenService.isFrozen(token, account);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Token isFrozen failed!");
        }
        return frozen;
    }

    function isKycGranted(address token, address account) external returns (bool){
        (int256 response, bool kycGranted) = HederaTokenService.isKyc(token, account);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Token isKyc failed!");
        }
        return kycGranted;
    }

    function getTokenDefaultFreeze(address token) external returns (bool) {
        (int256 response, bool frozen) = HederaTokenService.getTokenDefaultFreezeStatus(token);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("getTokenDefaultFreezeStatus failed!");
        }
        return frozen;
    }

    function getTokenDefaultKyc(address token) external returns (bool) {
        (int256 response, bool kyc) = HederaTokenService.getTokenDefaultKycStatus(token);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("getTokenDefaultKycStatus failed!");
        }
        return kyc;
    }

    function getCustomFeesForToken(address token) external returns (
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees
    )
    {
        int64 responseCode;
        (responseCode, fixedFees, fractionalFees, royaltyFees) = HederaTokenService.getTokenCustomFees(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function getInformationForToken(address token) external returns (IHederaTokenService.TokenInfo memory tokenInfo)
    {
        (int256 responseCode,IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        tokenInfo = retrievedTokenInfo;
    }

    function getInformationForFungibleToken(address token) external returns (IHederaTokenService.FungibleTokenInfo memory fungibleTokenInfo)
    {
        (int256 responseCode,IHederaTokenService.FungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getFungibleTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        fungibleTokenInfo = retrievedTokenInfo;
    }

    function getInformationForNonFungibleToken(address token, int64 serialNumber) external returns (
        IHederaTokenService.NonFungibleTokenInfo memory nonFungibleTokenInfo
    )
    {
        (int256 responseCode,IHederaTokenService.NonFungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getNonFungibleTokenInfo(token, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        nonFungibleTokenInfo = retrievedTokenInfo;
    }

    function getType(address token) external returns (int256) {
        (int256 statusCode, int256 tokenType) = HederaTokenService.getTokenType(token);
        if (statusCode != HederaResponseCodes.SUCCESS) {
            revert("Token type appraisal failed!");
        }
        return tokenType;
    }

    function getExpiryInfoForToken(address token) external returns (IHederaTokenService.Expiry memory expiry)
    {
        (int256 responseCode,IHederaTokenService.Expiry memory retrievedExpiry) = HederaTokenService.getTokenExpiryInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        expiry = retrievedExpiry;
    }

    function getTokenKeyPublic(address token, uint256 keyType) public returns (IHederaTokenService.KeyValue memory)
    {
        (int256 responseCode,IHederaTokenService.KeyValue memory key) = HederaTokenService.getTokenKey(token, keyType);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return key;
    }

    function balanceOfRedirect(address token, address account) external
    returns (bytes memory result)
    {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.balanceOf.selector, account));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return responseResult;
    }

    function nameRedirect(address token) external
    returns (bytes memory result)
    {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20Metadata.name.selector));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return responseResult;
    }

    function symbolRedirect(address token) external
    returns (bytes memory result)
    {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20Metadata.symbol.selector));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return responseResult;
    }

    function nameNFTRedirect(address token) external
    returns (bytes memory result)
    {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721Metadata.name.selector));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return responseResult;
    }

    function symbolNFTRedirect(address token) external
    returns (bytes memory result)
    {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721Metadata.symbol.selector));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return responseResult;
    }

    function decimalsRedirect(address token) external
    returns (bytes memory result)
    {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20Metadata.decimals.selector));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return responseResult;
    }

    function totalSupplyRedirect(address token) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.totalSupply.selector));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("Token redirect failed");
        }
        return responseResult;
    }

    function allowanceRedirect(address token, address owner, address spender) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.allowance.selector, owner, spender));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function getApprovedRedirect(address token, uint256 tokenId) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.getApproved.selector, tokenId));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function getOwnerOfRedirect(address token, uint256 serialNo) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.ownerOf.selector, serialNo));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function tokenURIRedirect(address token, uint256 tokenId) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721Metadata.tokenURI.selector, tokenId));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function isApprovedForAllRedirect(address token, address owner, address operator) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.isApprovedForAll.selector, owner, operator));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function transferRedirect(address token, address recipient, uint256 amount) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.transfer.selector, recipient, amount));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function transferFromRedirect(address token, address sender, address recipient, uint256 amount) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.transferFrom.selector, sender, recipient, amount));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function approveRedirect(address token, address spender, uint256 amount) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.approve.selector, spender, amount));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function transferFromNFTRedirect(address token, address from, address to, uint256 tokenId) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.transferFrom.selector, from, to, tokenId));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function setApprovalForAllRedirect(address token, address operator, bool approved) external
    returns (bytes memory result) {
        (int responseCode, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.setApprovalForAll.selector, operator, approved));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        return responseResult;
    }

    function mintTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, bytes[] memory metadata, address treasury) external
    returns (uint256, uint256, int, int) {
        uint256 balanceBeforeMint;
        uint256 balanceAfterMint;
        int totalSupplyBeforeMint;
        int totalSupplyAfterMint;

        if (amount > 0 && metadata.length == 0) {
            balanceBeforeMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeMint = IERC721(token).balanceOf(treasury);
        }

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        handleResponseCode(responseCode, "Failed to retrieve token info.");
        totalSupplyBeforeMint = retrievedTokenInfo.totalSupply;

        (responseCode, totalSupplyAfterMint,) = HederaTokenService.mintToken(token, amount, metadata);
        handleResponseCode(responseCode, "Failed to mint token.");

        if (amount > 0 && metadata.length == 0) {
            balanceAfterMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceAfterMint = IERC721(token).balanceOf(treasury);
        }

        return (balanceBeforeMint, balanceAfterMint, totalSupplyBeforeMint, totalSupplyAfterMint);
    }

    function burnTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, int64[] memory serialNumbers, address treasury) external
    returns (uint256, uint256, int, int){
        uint256 balanceBeforeBurn;
        uint256 balanceAfterBurn;
        int totalSupplyBeforeBurn;
        int totalSupplyAfterBurn;

        if (amount > 0 && serialNumbers.length == 0) {
            balanceBeforeBurn = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeBurn = IERC721(token).balanceOf(treasury);
        }

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        handleResponseCode(responseCode, "Failed to retrieve token info before burn.");
        totalSupplyBeforeBurn = retrievedTokenInfo.totalSupply;

        (responseCode, totalSupplyAfterBurn) = HederaTokenService.burnToken(token, amount, serialNumbers);
        handleResponseCode(responseCode, "Failed to burn token.");

        if (amount > 0 && serialNumbers.length == 0) {
            balanceAfterBurn = IERC20(token).balanceOf(treasury);
        } else {
            balanceAfterBurn = IERC721(token).balanceOf(treasury);
        }
        return (balanceBeforeBurn, balanceAfterBurn, totalSupplyBeforeBurn, totalSupplyAfterBurn);
    }

    function wipeTokenGetTotalSupplyAndBalanceOfAccount(address token, int64 amount, int64[] memory serialNumbers, address account) external
    returns (uint256, uint256, int, int){
        uint256 balanceBeforeWipe;
        uint256 balanceAfterWipe;
        int totalSupplyBeforeWipe;
        int totalSupplyAfterWipe;

        balanceBeforeWipe = 0;
        if (amount > 0 && serialNumbers.length == 0) {
            balanceBeforeWipe = IERC20(token).balanceOf(account);
        } else {
            balanceBeforeWipe = IERC721(token).balanceOf(account);
        }

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        handleResponseCode(responseCode, "Failed to retrieve token info before wipe.");
        totalSupplyBeforeWipe = retrievedTokenInfo.totalSupply;

        if (amount > 0 && serialNumbers.length == 0) {
            responseCode = HederaTokenService.wipeTokenAccount(token, account, amount);
        } else {
            responseCode = HederaTokenService.wipeTokenAccountNFT(token, account, serialNumbers);
        }
        handleResponseCode(responseCode, "Failed to wipe token.");

        (responseCode, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        handleResponseCode(responseCode, "Failed to retrieve token info after wipe.");
        totalSupplyAfterWipe = retrievedTokenInfo.totalSupply;

        if (amount > 0 && serialNumbers.length == 0) {
            balanceAfterWipe = IERC20(token).balanceOf(account);
        } else {
            balanceAfterWipe = IERC721(token).balanceOf(account);
        }
        return (balanceBeforeWipe, balanceAfterWipe, totalSupplyBeforeWipe, totalSupplyAfterWipe);
    }

    function pauseTokenGetPauseStatusUnpauseGetPauseStatus(address token) external
    returns (bool, bool){
        bool statusAfterPause;
        bool statusAfterUnpause;
        int responseCode = HederaTokenService.pauseToken(token);
        handleResponseCode(responseCode, "Failed to pause token.");

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        handleResponseCode(responseCode, "Failed to get token info after pause.");
        statusAfterPause = retrievedTokenInfo.pauseStatus;

        responseCode = HederaTokenService.unpauseToken(token);
        handleResponseCode(responseCode, "Failed to unpause token.");

        (response, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        handleResponseCode(responseCode, "Failed to retrieve token info after unpause.");
        statusAfterUnpause = retrievedTokenInfo.pauseStatus;

        return (statusAfterPause, statusAfterUnpause);
    }

    function freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus(address token, address account) external
    returns (bool, bool){
        bool statusAfterFreeze;
        bool statusAfterUnfreeze;

        int responseCode = HederaTokenService.freezeToken(token, account);
        handleResponseCode(responseCode, "Failed to freeze token for the account.");

        (int response, bool isFrozen) = HederaTokenService.isFrozen(token, account);
        handleResponseCode(responseCode, "Failed to check freeze status of account.");
        statusAfterFreeze = isFrozen;

        responseCode = HederaTokenService.unfreezeToken(token, account);
        handleResponseCode(responseCode, "Failed to unfreeze account.");

        (response, isFrozen) = HederaTokenService.isFrozen(token, account);
        handleResponseCode(responseCode, "Failed to check unfreeze status of account.");
        statusAfterUnfreeze = isFrozen;

        return (statusAfterFreeze, statusAfterUnfreeze);
    }

    function approveTokenGetAllowance(address token, address spender, uint256 amount, uint256 serialNumber) external
    returns (uint256, address) {
        if (amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            handleResponseCode(responseCode, "Failed to approve Fungible token.");

            uint256 allowance = IERC20(token).allowance(address(this), spender);
            return (allowance, address(0));
        } else {
            int responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            handleResponseCode(responseCode, "Failed to approve NFT.");

            address approvedAddress = IERC721(token).getApproved(serialNumber);
            return (0, approvedAddress);
        }
    }

    function approveFungibleTokenTransferFromGetAllowanceGetBalance(address token, address receiver, uint256 amount) external
    returns (uint256, uint256, uint256, uint256){

        address _spender = address(new SpenderContract());
        uint256 allowanceBeforeApprove = IERC20(token).allowance(address(this), _spender);
        uint256 balanceBefore = IERC20(token).balanceOf(receiver);

        int responseCode = HederaTokenService.approve(token, _spender, amount);
        handleResponseCode(responseCode, "Failed to approve Fungible token.");
        uint256 allowanceBeforeTransfer = IERC20(token).allowance(address(this), _spender);
        if (allowanceBeforeTransfer != amount) {
            revert("Allowance mismatch!");
        }

        SpenderContract(_spender).spendFungible(token, amount, address(this), receiver);
        handleResponseCode(responseCode, "Failed to transfer Fungible token.");

        uint256 allowanceAfter = IERC20(token).allowance(address(this), _spender);
        uint256 balanceAfter = IERC20(token).balanceOf(receiver);

        return (allowanceBeforeApprove, balanceBefore, allowanceAfter, balanceAfter);
    }

    function approveNftAndTransfer(address token, address receiver, uint256 serialNumber) external
    returns (address, address, address, address){
        address _spender = address(new SpenderContract());

        int responseCode = HederaTokenService.approveNFT(token, _spender, serialNumber);
        handleResponseCode(responseCode, "Failed to approve NFT.");

        address approvedAddress = IERC721(token).getApproved(serialNumber);

        SpenderContract(_spender).spendNFT(token, serialNumber, address(this), receiver);

        address ownerAfterTransfer = IERC721(token).ownerOf(serialNumber);
        address allowedAfterTransfer = IERC721(token).getApproved(serialNumber);

        return (approvedAddress, ownerAfterTransfer, allowedAfterTransfer, _spender);
    }

    function associateTokenDissociateFailTransfer(address token, address from, address to, uint256 amount, uint256 serialNumber) external
    returns (int, int){
        address[] memory tokens = new address[](1);
        int transferStatusAfterAssociate;
        int transferTokenStatusAfterDissociate;
        tokens[0] = token;

        int responseCode = HederaTokenService.associateToken(to, token);
        handleResponseCode(responseCode, "Failed to associate tokens.");

        if (amount > 0 && serialNumber == 0) {
            transferStatusAfterAssociate = HederaTokenService.transferToken(token, from, to, int64(uint64(amount)));
        } else {
            transferStatusAfterAssociate = HederaTokenService.transferNFT(token, from, to, int64(uint64(serialNumber)));
        }

        //transfer the tokens back in order to be able to dissociate the account
        if (amount > 0 && serialNumber == 0) {
            responseCode = HederaTokenService.transferToken(token, to, from, int64(uint64(amount)));
            handleResponseCode(responseCode, "Failed to transfer Fungible token.");
        } else {
            responseCode = HederaTokenService.transferNFT(token, to, from, int64(uint64(serialNumber)));
            handleResponseCode(responseCode, "Failed to transfer NFT token.");
        }

        responseCode = HederaTokenService.dissociateToken(to, token);
        handleResponseCode(responseCode, "Failed to dissociate token.");

        if (amount > 0 && serialNumber == 0) {
            transferTokenStatusAfterDissociate = HederaTokenService.transferToken(token, from, to, int64(uint64(amount)));
        } else {
            transferTokenStatusAfterDissociate = HederaTokenService.transferNFT(token, from, to, int64(uint64(serialNumber)));
        }

        return (transferStatusAfterAssociate, transferTokenStatusAfterDissociate);
    }

    function grantKycRevokeKyc(address token, address from, address account, uint256 amount) external
    returns (bool, int256, bool, int256, int){
        bool isKycAfterGrant;
        bool isKycAfterRevoke;
        int256 kycGrantStatus;
        int256 kycRevokeStatus;
        int transferStatusAfterRevoke;
        int responseCode = HederaTokenService.grantTokenKyc(token, account);
        handleResponseCode(responseCode, "Grant kyc failed.");

        (kycGrantStatus, isKycAfterGrant) = HederaTokenService.isKyc(token, account);
        if (kycGrantStatus != HederaResponseCodes.SUCCESS) revert("Is kyc operation failed");
        if (!isKycAfterGrant) revert("Kyc status mismatch");

        responseCode = HederaTokenService.revokeTokenKyc(token, account);
        handleResponseCode(responseCode, "Revoke kyc failed.");

        transferStatusAfterRevoke = HederaTokenService.transferToken(token, from, account, int64(uint64(amount)));


        (kycRevokeStatus, isKycAfterRevoke) = HederaTokenService.isKyc(token, account);

        return (isKycAfterGrant, kycGrantStatus, isKycAfterRevoke, kycRevokeStatus, transferStatusAfterRevoke);
    }

    function intToString(int _i) internal pure returns (string memory) {
        if (_i == 0) {
            return "0";
        }
        bool negative = _i < 0;
        uint absValue = uint(negative ? - _i : _i);
        bytes memory buffer = new bytes(10);
        uint i = 0;
        while (absValue > 0) {
            buffer[i++] = bytes1(uint8(absValue % 10 + 48));
            absValue /= 10;
        }
        bytes memory result = new bytes(negative ? ++i : i);
        if (negative) {
            result[0] = '-';
        }
        for (uint j = 0; j < i; j++) {
            result[result.length - j - 1] = buffer[j];
        }
        return string(result);
    }

    // Helper function to handle the common logic
    function handleResponseCode(int responseCode, string memory message) internal pure {
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert(message);
        }
    }
}

contract SpenderContract {
    function spendFungible(address token, uint256 amount, address from, address to) public {
        IERC20(token).transferFrom(from, to, amount);
    }

    function spendNFT(address token, uint256 serialNumber, address from, address to) public {
        IERC721(token).transferFrom(from, to, serialNumber);
    }
}
