// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;


import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

interface IHRC {
    function associate() external returns (uint256 responseCode);

    function dissociate() external returns (uint256 responseCode);

    function isAssociated() external returns (bool associated);
}

contract ModificationPrecompileTestContract is HederaTokenService {

    uint256 salt = 1234;

    function deployViaCreate2() public returns (address) {
        NestedContract newContract = new NestedContract{salt: bytes32(salt)}();

        return address(newContract);
    }

    function cryptoTransferExternal(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function mintTokenExternal(address token, int64 amount, bytes[] memory metadata) external
    returns (int responseCode, int64 newTotalSupply, int64[] memory serialNumbers)
    {
        (responseCode, newTotalSupply, serialNumbers) = HederaTokenService.mintToken(token, amount, metadata);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function burnTokenExternal(address token, int64 amount, int64[] memory serialNumbers) external
    returns (int responseCode, int64 newTotalSupply)
    {
        (responseCode, newTotalSupply) = HederaTokenService.burnToken(token, amount, serialNumbers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function associateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateTokens(account, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function associateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function dissociateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateTokens(account, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function dissociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function associate(address token) public returns (uint256 responseCode) {
        return IHRC(token).associate();
    }

    function dissociate(address token) public returns (uint256 responseCode) {
        return IHRC(token).dissociate();
    }

    function isAssociated(address token) public returns (bool associated) {
        return IHRC(token).isAssociated();
    }

    function createFungibleTokenExternal(IHederaTokenService.HederaToken memory token,
        int64 initialTotalSupply,
        int32 decimals) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function createFungibleTokenWithInheritKeysExternal() external payable returns (address)
    {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        IHederaTokenService.KeyValue memory inheritKey;
        inheritKey.inheritAccountKey = true;
        keys[0] = IHederaTokenService.TokenKey(1, inheritKey);
        keys[1] = IHederaTokenService.TokenKey(2, inheritKey);
        keys[2] = IHederaTokenService.TokenKey(4, inheritKey);
        keys[3] = IHederaTokenService.TokenKey(8, inheritKey);
        keys[4] = IHederaTokenService.TokenKey(16, inheritKey);

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, address(this), 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            "NAME", "SYMBOL", address(this), "memo", true, 1000, false, keys, expiry
        );

        (int responseCode, address tokenAddress) =
                            HederaTokenService.createFungibleToken(token, 10, 10);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        return tokenAddress;
    }

    function createFungibleTokenWithCustomFeesExternal(IHederaTokenService.HederaToken memory token,
        int64 initialTotalSupply,
        int32 decimals,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals, fixedFees, fractionalFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function createNonFungibleTokenExternal(IHederaTokenService.HederaToken memory token) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createNonFungibleToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function createNonFungibleTokenWithCustomFeesExternal(IHederaTokenService.HederaToken memory token,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createNonFungibleTokenWithCustomFees(token, fixedFees, royaltyFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function approveExternal(address token, address spender, uint256 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approve(token, spender, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferFromExternal(address token, address from, address to, uint256 amount) external
    returns (int64 responseCode)
    {
        responseCode = this.transferFrom(token, from, to, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferFromNFTExternal(address token, address from, address to, uint256 serialNumber) external
    returns (int64 responseCode)
    {
        responseCode = this.transferFromNFT(token, from, to, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function approveNFTExternal(address token, address approved, uint256 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approveNFT(token, approved, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function freezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.freezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function unfreezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.unfreezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function grantTokenKycExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.grantTokenKyc(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function revokeTokenKycExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.revokeTokenKyc(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function setApprovalForAllExternal(address token, address operator, bool approved) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.setApprovalForAll(token, operator, approved);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferTokensExternal(address token, address[] memory accountIds, int64[] memory amounts) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferTokens(token, accountIds, amounts);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferNFTsExternal(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferTokenExternal(address token, address sender, address receiver, int64 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferToken(token, sender, receiver, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferNFTExternal(address token, address sender, address receiver, int64 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFT(token, sender, receiver, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function pauseTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.pauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function unpauseTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.unpauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function wipeTokenAccountExternal(address token, address account, int64 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.wipeTokenAccount(token, account, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function wipeTokenAccountNFTExternal(address token, address account, int64[] memory serialNumbers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.wipeTokenAccountNFT(token, account, serialNumbers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function deleteTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.deleteToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateFungibleTokenCustomFeesExternal(address token, IHederaTokenService.FixedFee[] memory fixedFees, IHederaTokenService.FractionalFee[] memory fractionalFees) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.updateFungibleTokenCustomFees(token, fixedFees, fractionalFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function getCustomFeesForToken(address token) internal
    returns (
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees)
    {
        int responseCode;
        (responseCode, fixedFees, fractionalFees, royaltyFees) = HederaTokenService.getTokenCustomFees(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Failed to fetch custom fees");
        }

        return (fixedFees, fractionalFees, royaltyFees);
    }

    function updateFungibleTokenCustomFeesAndGetExternal(
        address token,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees) external
    returns (
        IHederaTokenService.FixedFee[] memory newFixedFees,
        IHederaTokenService.FractionalFee[] memory newFractionalFees,
        IHederaTokenService.RoyaltyFee[] memory newRoyaltyFees)
    {
        int64 responseCode = HederaTokenService.updateFungibleTokenCustomFees(token, fixedFees, fractionalFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Failed to update fungible token custom fees");
        }

        (newFixedFees, newFractionalFees, newRoyaltyFees) = getCustomFeesForToken(token);
        return (newFixedFees, newFractionalFees, newRoyaltyFees);
    }

    function updateNonFungibleTokenCustomFeesAndGetExternal(
        address token,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees) external
    returns (
        IHederaTokenService.FixedFee[] memory newFixedFees,
        IHederaTokenService.FractionalFee[] memory newFractionalFees,
        IHederaTokenService.RoyaltyFee[] memory newRoyaltyFees)
    {
        int64 responseCode = HederaTokenService.updateNonFungibleTokenCustomFees(token, fixedFees, royaltyFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Failed to update nft custom fees");
        }

        (newFixedFees, newFractionalFees, newRoyaltyFees) = getCustomFeesForToken(token);

        return (newFixedFees, newFractionalFees, newRoyaltyFees);
    }

    function updateTokenKeysExternal(address token, IHederaTokenService.TokenKey[] memory keys) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.updateTokenKeys(token, keys);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateTokenExpiryInfoExternal(address token, IHederaTokenService.Expiry memory expiryInfo) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.updateTokenExpiryInfo(token, expiryInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateTokenInfoExternal(address token, IHederaTokenService.HederaToken memory tokenInfo) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function associateWithRedirect(address token) external returns (bytes memory result)
    {
        (int response, bytes memory result) = this.redirectForToken(token, abi.encodeWithSelector(IHRC.associate.selector));
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Tokens association redirect failed");
        }
        return result;
    }

    function dissociateWithRedirect(address token) external returns (bytes memory result)
    {
        (int response, bytes memory result) = this.redirectForToken(token, abi.encodeWithSelector(IHRC.dissociate.selector));
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Tokens dissociation redirect failed");
        }
        return result;
    }

    function callNotExistingPrecompile(address token) public returns (bytes memory result)
    {
        (int response, bytes memory result) = this.redirectForToken(token, abi.encodeWithSelector(bytes4(keccak256("notExistingPrecompile()"))));
        return result;
    }

    function createContractViaCreate2AndTransferFromIt(address token, address sponsor, address receiver, int64 amount) external
    returns (int responseCode)
    {
        address create2Contract = deployViaCreate2();

        int associateSenderResponseCode = HederaTokenService.associateToken(create2Contract, token);
        if (associateSenderResponseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }

        int associateRecipientResponseCode = HederaTokenService.associateToken(receiver, token);
        if (associateRecipientResponseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }

        int grantTokenKycResponseCodeContract = grantTokenKyc(token, create2Contract);
        if (grantTokenKycResponseCodeContract != HederaResponseCodes.SUCCESS) {
            revert();
        }

        int grantTokenKycResponseCodeReceiver = grantTokenKyc(token, receiver);
        if (grantTokenKycResponseCodeReceiver != HederaResponseCodes.SUCCESS) {
            revert();
        }

        int sponsorTransferResponseCode = HederaTokenService.transferToken(token, sponsor, create2Contract, amount / 2);
        if (sponsorTransferResponseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }

        responseCode = HederaTokenService.transferToken(token, create2Contract, receiver, amount / 4);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function mintTokenNativePrecompileAndBurnExternal(
        address token,
        int64 mintAmount,
        int64 burnAmount,
        bytes[] memory metadata) external
    returns (int responseCode, int64 newTotalSupplyAfterMint, int64 newTotalSupplyAfterBurn)
    {
        int mintResponseCode;
        int burnResponseCode;
        
        (mintResponseCode, newTotalSupplyAfterMint,) = HederaTokenService.mintToken(token, mintAmount, metadata);
        if (mintResponseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }

        bytes memory input = abi.encodePacked("test");
        (bool success, bytes memory result) = address(0x02).call(input);
        require(success, "SHA256 precompile call failed");

        (burnResponseCode, newTotalSupplyAfterBurn) = HederaTokenService.burnToken(token, burnAmount, new int64[](0));
        if (burnResponseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        
        responseCode = HederaResponseCodes.SUCCESS;
    }

    receive() external payable {
    }
}

contract NestedContract {

}