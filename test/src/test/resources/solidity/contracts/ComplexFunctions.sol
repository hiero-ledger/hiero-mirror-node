// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./ExpiryHelper.sol";
import "./KeyHelper.sol";

contract ComplexFunctions is HederaTokenService, ExpiryHelper, KeyHelper {
    function tokenLifecycle(address acc1, address acc2, address treasury) public payable {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(0, treasury, 8000000);

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken("TKN", "TK", treasury, "memo", true, 1000000, false, keys, expiry);
        (int code, address tokenAddr) = HederaTokenService.createFungibleToken(token, 1000000, 8);
        require(code == HederaResponseCodes.SUCCESS, "Token creation failed");

        require(HederaTokenService.associateToken(acc1, tokenAddr) == 22, "Token Associate of failed for acc1");
        require(HederaTokenService.associateToken(acc2, tokenAddr) == 22, "Token Associate failed for acc2");
        require(HederaTokenService.grantTokenKyc(tokenAddr, acc1) == 22, "GrantKyc failed for acc1");
        require(HederaTokenService.grantTokenKyc(tokenAddr, acc2) == 22, "GrantKyC failed for acc2");
        require(HederaTokenService.transferToken(tokenAddr, treasury, acc1, 100) == 22, "Transfer token failed from treasury to acc1");
        require(HederaTokenService.freezeToken(tokenAddr, acc1) == 22, "Freeze token failed for acc1");
        require(HederaTokenService.unfreezeToken(tokenAddr, acc1) == 22, "Unfreeze token failed for acc1");
        require(HederaTokenService.transferToken(tokenAddr, acc1, acc2, 50) == 22, "Transfer token failed from acc1 to acc2");
        require(HederaTokenService.wipeTokenAccount(tokenAddr, acc2, 10) == 22, "Wipe token failed for acc2");
        require(HederaTokenService.pauseToken(tokenAddr) == 22, "Pause token failed");
        require(HederaTokenService.unpauseToken(tokenAddr) == 22, "Unpause token failed");
    }

    function nftLifecycle(address acc1, address acc2, address treasury, bytes[] memory metadata) public payable {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            "NFT", "NFT", treasury, "memo", false, 0, false, keys, IHederaTokenService.Expiry(0, treasury, 8000000)
        );
        (int code, address tokenAddr) = HederaTokenService.createNonFungibleToken(token);
        require(code == HederaResponseCodes.SUCCESS, "NFT creation failed");

        require(HederaTokenService.associateToken(acc1, tokenAddr) == 22, "Associate failed for acc1");
        require(HederaTokenService.associateToken(acc2, tokenAddr) == 22, "Associate failed for acc2");
        require(HederaTokenService.grantTokenKyc(tokenAddr, acc1) == 22, "KYC failed for acc1");
        require(HederaTokenService.grantTokenKyc(tokenAddr, acc2) == 22, "KYC failed for acc2");

        (int mintResponse, , int64[] memory serials) =
                            HederaTokenService.mintToken(tokenAddr, 0, metadata);

        int64 firstSerial = serials[0];

        require(HederaTokenService.transferNFT(tokenAddr, treasury, acc1, firstSerial) == 22, "NFT transfer to acc1 failed");
        require(HederaTokenService.freezeToken(tokenAddr, acc1) == 22, "Freeze failed for acc1");
        require(HederaTokenService.unfreezeToken(tokenAddr, acc1) == 22, "Unfreeze failed for acc1");
        require(HederaTokenService.transferNFT(tokenAddr, acc1, acc2, firstSerial) == 22, "NFT transfer to acc2 failed");
        require(HederaTokenService.wipeTokenAccountNFT(tokenAddr, acc2, serials) == 22, "Wipe failed for acc2");
        require(HederaTokenService.pauseToken(tokenAddr) == 22, "Pause failed");
        require(HederaTokenService.unpauseToken(tokenAddr) == 22, "Unpause failed");
    }
}