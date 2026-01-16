// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;


import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./ExpiryHelper.sol";
import "./KeyHelper.sol";

contract EstimatePrecompileContract is HederaTokenService, ExpiryHelper, KeyHelper {

    string name = "tokenName";
    string symbol = "TKY";
    string memo = "memo";
    int64 initialTotalSupply = 1000;
    int64 maxSupply = 1000;
    int32 decimals = 8;
    bool freezeDefaultStatus = false;

    address constant PRNG_PRECOMPILE_ADDRESS = address(0x169);
    address constant EXCHANGE_RATE_PRECOMPILE_ADDRESS = address(0x168);
    uint256 constant TINY_PARTS_PER_WHOLE = 100_000_000;

    // Helper function to handle the common logic
    function handleResponseCode(int responseCode) internal pure {
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    // associate & dissociate
    function associateTokenExternal(address account, address token) external {
        int responseCode = HederaTokenService.associateToken(account, token);
        handleResponseCode(responseCode);
    }

    function nestedAssociateTokenExternal(address account, address token) external {
        HederaTokenService.associateToken(account, token);
        int responseCode = HederaTokenService.associateToken(account, token);
        handleResponseCode(responseCode);
    }

    function dissociateAndAssociateTokenExternal(address account, address token) external {
        HederaTokenService.dissociateToken(account, token);
        int responseCode = HederaTokenService.associateToken(account, token);
        handleResponseCode(responseCode);
    }

    function dissociateTokenExternal(address account, address token) external {
        int responseCode = HederaTokenService.dissociateToken(account, token);
        handleResponseCode(responseCode);
    }
    //associate & dissociate - many
    function associateTokensExternal(address account, address[] memory tokens) external {
        int responseCode = HederaTokenService.associateTokens(account, tokens);
        handleResponseCode(responseCode);
    }

    function dissociateTokensExternal(address account, address[] memory tokens) external {
        int responseCode = HederaTokenService.dissociateTokens(account, tokens);
        handleResponseCode(responseCode);
    }

    //approve
    function approveExternal(address token, address spender, uint256 amount) external {
        int responseCode = HederaTokenService.approve(token, spender, amount);
        handleResponseCode(responseCode);
    }

    function approveNFTExternal(address token, address approved, uint256 serialNumber) external {
        int responseCode = HederaTokenService.approveNFT(token, approved, serialNumber);
        handleResponseCode(responseCode);
    }

    //transfer
    function transferFromExternal(address token, address from, address to, uint256 amount) external {
        int responseCode = this.transferFrom(token, from, to, amount);
        handleResponseCode(responseCode);
    }

    function transferFromNFTExternal(address token, address from, address to, uint256 serialNumber) external {
        int responseCode = this.transferFromNFT(token, from, to, serialNumber);
        handleResponseCode(responseCode);
    }

    function transferTokenExternal(address token, address sender, address receiver, int64 amount) external {
        int responseCode = HederaTokenService.transferToken(token, sender, receiver, amount);
        handleResponseCode(responseCode);
    }

    function transferNFTExternal(address token, address sender, address receiver, int64 serialNumber) external {
        int responseCode = HederaTokenService.transferNFT(token, sender, receiver, serialNumber);
        handleResponseCode(responseCode);
    }

    //transfer-many
    function transferTokensExternal(address token, address[] memory accountIds, int64[] memory amounts) external {
        int responseCode = HederaTokenService.transferTokens(token, accountIds, amounts);
        handleResponseCode(responseCode);
    }

    function transferNFTsExternal(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        int responseCode = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        handleResponseCode(responseCode);
    }

    function cryptoTransferExternal(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        int responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        handleResponseCode(responseCode);
    }

    function mintTokenExternal(address token, int64 amount, bytes[] memory metadata) external
    returns (int64 newTotalSupply, int64[] memory serialNumbers) {
        (int responseCode, int64 internalNewTotalSupply, int64[] memory internalSerialNumbers) = HederaTokenService.mintToken(token, amount, metadata);
        handleResponseCode(responseCode);
        return (internalNewTotalSupply, internalSerialNumbers);
    }

    function burnTokenExternal(address token, int64 amount, int64[] memory serialNumbers) external
    returns (int64 newTotalSupply) {
        (int responseCode, int64 internalNewTotalSupply) = HederaTokenService.burnToken(token, amount, serialNumbers);
        handleResponseCode(responseCode);
        return internalNewTotalSupply;
    }

    //create operations
    function createFungibleTokenPublic(address treasury) public payable returns (address) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        (int responseCode, address createdTokenAddress) =
                            HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        handleResponseCode(responseCode);

        return createdTokenAddress;
    }

    function createNonFungibleTokenPublic(address treasury) public payable returns (address) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        (int responseCode, address createdTokenAddress) =
                            HederaTokenService.createNonFungibleToken(token);

        handleResponseCode(responseCode);

        return createdTokenAddress;
    }

    function createFungibleTokenWithCustomFeesPublic(address treasury, address fixedFeeTokenAddress) public payable returns (address){
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.ADMIN, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, false, keys, expiry
        );

        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        fixedFees[0] = IHederaTokenService.FixedFee(1, fixedFeeTokenAddress, false, false, treasury);

        IHederaTokenService.FractionalFee[] memory fractionalFees = new IHederaTokenService.FractionalFee[](1);
        fractionalFees[0] = IHederaTokenService.FractionalFee(4, 5, 10, 30, false, treasury);

        (int responseCode, address createdTokenAddress) =
                            HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals, fixedFees, fractionalFees);

        handleResponseCode(responseCode);

        return createdTokenAddress;
    }

    function createNonFungibleTokenWithCustomFeesPublic(address treasury, address fixedFeeTokenAddress) public payable returns (address){
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        fixedFees[0] = IHederaTokenService.FixedFee(1, fixedFeeTokenAddress, false, false, treasury);

        IHederaTokenService.RoyaltyFee[] memory royaltyFees = new IHederaTokenService.RoyaltyFee[](1);
        royaltyFees[0] = IHederaTokenService.RoyaltyFee(4, 5, 10, fixedFeeTokenAddress, false, treasury);

        (int responseCode, address createdTokenAddress) =
                            HederaTokenService.createNonFungibleTokenWithCustomFees(token, fixedFees, royaltyFees);

        handleResponseCode(responseCode);

        return createdTokenAddress;
    }

    function wipeTokenAccountExternal(address token, address account, int64 amount) external {
        int responseCode = HederaTokenService.wipeTokenAccount(token, account, amount);
        handleResponseCode(responseCode);
    }

    function wipeTokenAccountNFTExternal(address token, address account, int64[] memory serialNumbers) external {
        int responseCode = HederaTokenService.wipeTokenAccountNFT(token, account, serialNumbers);
        handleResponseCode(responseCode);
    }

    function setApprovalForAllExternal(address token, address account, bool approved) external {
        int responseCode = HederaTokenService.setApprovalForAll(token, account, approved);
        handleResponseCode(responseCode);
    }

    function grantTokenKycExternal(address token, address account) external {
        int responseCode = HederaTokenService.grantTokenKyc(token, account);
        handleResponseCode(responseCode);
    }

    function revokeTokenKycExternal(address token, address account) external {
        int responseCode = HederaTokenService.revokeTokenKyc(token, account);
        handleResponseCode(responseCode);
    }

    function nestedGrantAndRevokeTokenKYCExternal(address token, address account) external {
        HederaTokenService.grantTokenKyc(token, account);
        int responseCode = HederaTokenService.revokeTokenKyc(token, account);
        handleResponseCode(responseCode);
    }

    function freezeTokenExternal(address token, address account) external {
        int responseCode = HederaTokenService.freezeToken(token, account);
        handleResponseCode(responseCode);
    }

    function unfreezeTokenExternal(address token, address account) external {
        int responseCode = HederaTokenService.unfreezeToken(token, account);
        handleResponseCode(responseCode);
    }

    function nestedFreezeUnfreezeTokenExternal(address token, address account) external {
        HederaTokenService.freezeToken(token, account);
        int responseCode = HederaTokenService.unfreezeToken(token, account);
        handleResponseCode(responseCode);
    }

    function deleteTokenExternal(address token) external {
        int responseCode = HederaTokenService.deleteToken(token);
        handleResponseCode(responseCode);
    }

    function pauseTokenExternal(address token) external {
        int responseCode = HederaTokenService.pauseToken(token);
        handleResponseCode(responseCode);
    }

    function unpauseTokenExternal(address token) external {
        int responseCode = HederaTokenService.unpauseToken(token);
        handleResponseCode(responseCode);
    }

    function nestedPauseUnpauseTokenExternal(address token) external {
        HederaTokenService.pauseToken(token);
        int responseCode = HederaTokenService.unpauseToken(token);
        handleResponseCode(responseCode);
    }

    function updateTokenExpiryInfoExternal(address token, address treasury) external {
        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );
        int responseCode = HederaTokenService.updateTokenExpiryInfo(token, expiry);
        handleResponseCode(responseCode);
    }

    function updateTokenInfoExternal(address token, address treasury) external {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 7000000
        );

        IHederaTokenService.HederaToken memory tokenInfo = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        int responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        handleResponseCode(responseCode);
    }

    function updateTokenKeysExternal(address token) external {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](4);
        keys[0] = getSingleKey(KeyType.KYC, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));
        keys[1] = getSingleKey(KeyType.FREEZE, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));
        keys[2] = getSingleKey(KeyType.SUPPLY, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));
        keys[3] = getSingleKey(KeyType.WIPE, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));

        int responseCode = HederaTokenService.updateTokenKeys(token, keys);
        handleResponseCode(responseCode);
    }

    function getTokenExpiryInfoExternal(address token) external
    returns(int64, address, int64) {
        (int responseCode, IHederaTokenService.Expiry memory expiryInfo) = HederaTokenService.getTokenExpiryInfo(token);
        handleResponseCode(responseCode);
        return (
            expiryInfo.second,
            expiryInfo.autoRenewAccount,
            expiryInfo.autoRenewPeriod
        );
    }

    function isTokenExternal(address token) external returns(bool) {
        (int responseCode, bool isTokenFlag) = HederaTokenService.isToken(token);
        handleResponseCode(responseCode);
        return isTokenFlag;
    }

    function getTokenKeyExternal(address token, uint keyType) external
    returns(bool, address, bytes memory, bytes memory, address) {
        (int responseCode, IHederaTokenService.KeyValue memory key) = HederaTokenService.getTokenKey(token, keyType);
        handleResponseCode(responseCode);
        return (
            key.inheritAccountKey,
            key.contractId,
            key.ed25519,
            key.ECDSA_secp256k1,
            key.delegatableContractId
        );
    }

    function allowanceExternal(address token, address owner, address spender) external returns(uint256) {
        (int responseCode, uint256 amount) = HederaTokenService.allowance(token, owner, spender);
        handleResponseCode(responseCode);
        return amount;
    }

    function getApprovedExternal(address token, uint256 serialNumber) external returns(address) {
        (int responseCode, address approvedAddress) = HederaTokenService.getApproved(token, serialNumber);
        handleResponseCode(responseCode);
        return approvedAddress;
    }

    function isApprovedForAllExternal(address token, address owner, address operator) external returns(bool) {
        (int responseCode, bool approved) = HederaTokenService.isApprovedForAll(token, owner, operator);
        handleResponseCode(responseCode);
        return approved;
    }

    function getPseudorandomSeed() external returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRNG_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("getPseudorandomSeed()"));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }

    /**
     * Returns a pseudorandom number in the range [lo, hi) using the seed generated from "getPseudorandomSeed"
     */
    function getPseudorandomNumber(uint32 lo, uint32 hi) external returns (uint32) {
        (bool success, bytes memory result) = PRNG_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("getPseudorandomSeed()"));
        require(success);
        uint32 choice;
        assembly {
            choice := mload(add(result, 0x20))
        }
        return lo + (choice % (hi - lo));
    }

    function tinycentsToTinybars(uint256 tinycents) external returns (uint256 tinybars) {
        (bool success, bytes memory result) = EXCHANGE_RATE_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("tinycentsToTinybars(uint256)", tinycents));
        require(success);
        tinybars = abi.decode(result, (uint256));
    }

    function tinybarsToTinycents(uint256 tinybars) external returns (uint256 tinycents) {
        (bool success, bytes memory result) = EXCHANGE_RATE_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("tinybarsToTinycents(uint256)", tinybars));
        require(success);
        tinycents = abi.decode(result, (uint256));
    }
}
