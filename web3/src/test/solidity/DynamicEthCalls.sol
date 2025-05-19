// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.18;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";

contract DynamicEthCalls is HederaTokenService {

    // Mint fungible/non-fungible token + get token info total supply+ get balance of the treasury
    function mintTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, bytes[] memory metadata, address treasury) external {
        uint256 balanceBeforeMint = 0;
        if(amount > 0 && metadata.length == 0) {
            balanceBeforeMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeMint = IERC721(token).balanceOf(treasury);
        }

        int totalSupplyBeforeMint = getTokenTotalSupply(token, "Failed to retrieve token info before mint");

        int responseCode;
        int newTotalSupply;
        int64[] memory serialNumbers;
        (responseCode, newTotalSupply, serialNumbers) = HederaTokenService.mintToken(token, amount, metadata);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to mint token");

        int totalSupplyAfterMint = getTokenTotalSupply(token, "Failed to retrieve token info after mint");

        if(amount > 0 && metadata.length == 0) {
            if((totalSupplyBeforeMint + amount != totalSupplyAfterMint) || (newTotalSupply != totalSupplyAfterMint)) revert("Total supply mismatch after mint (Fungible)");
        } else {
            if(serialNumbers.length != metadata.length) revert("Serial numbers mismatch after mint (NFT)");
            if((totalSupplyBeforeMint + int256(metadata.length) != totalSupplyAfterMint) || (newTotalSupply != totalSupplyAfterMint)) revert("Total supply mismatch after mint (NFT)");
        }

        if(amount > 0 && metadata.length == 0) {
            uint256 balanceAfterMint = IERC20(token).balanceOf(treasury);
            if(balanceBeforeMint + uint256(int256(amount)) != balanceAfterMint) revert("Balance mismatch after mint (Fungible)");
        } else {
            uint256 balanceAfterMint  = IERC721(token).balanceOf(treasury);
            if(balanceBeforeMint + uint256(int256(metadata.length)) != balanceAfterMint) revert("Balance mismatch after mint (NFT)");
        }
    }

    function mintMultipleNftTokensGetTotalSupplyExternal(address token, bytes[] memory metadata1, bytes[] memory metadata2) external
    returns (int64[] memory serialNumbers1, int64[] memory serialNumbers2)
    {
        int totalSupplyBeforeFirstMint = getTokenTotalSupply(token,"Failed to retrieve token info before first mint");

        int responseCode;
        int newTotalSupply;
        (responseCode, newTotalSupply, serialNumbers1) = mintNft(token, metadata1, "Failed to mint the first nft.");

        int totalSupplyAfterFirstMint = getTokenTotalSupply(token, "Failed to retrieve token info after the first mint.");
        if (totalSupplyBeforeFirstMint + 1 != totalSupplyAfterFirstMint) revert("Total supply mismatch after the first mint.");

        (responseCode, newTotalSupply, serialNumbers2) = mintNft(token, metadata2, "Failed to mint the second nft.");

        int totalSupplyAfterSecondMint = getTokenTotalSupply(token, "Failed to retrieve token info after the second mint.");
        if (totalSupplyAfterFirstMint + 1 != totalSupplyAfterSecondMint) revert("Total supply mismatch after the second mint.");

        return (serialNumbers1, serialNumbers2);
    }

    function mintNftAndBurnNft(address token, bytes[] memory metadata) external {
        int totalSupplyBeforeMint = getTokenTotalSupply(token, "Failed to retrieve token info before mint");

        (int responseCode, int newTotalSupply,int64[] memory serialNumbers) = mintNft(token, metadata, "Failed to mint nft");

        int totalSupplyAfterMint = getTokenTotalSupply(token, "Failed to retrieve token info after mint");

        if((totalSupplyBeforeMint + int256(metadata.length) != totalSupplyAfterMint) || (newTotalSupply != totalSupplyAfterMint)) revert("Total supply mismatch after mint nft");

        (responseCode, newTotalSupply) = HederaTokenService.burnToken(token, 0, serialNumbers);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to burn nft");
    }

    // Burn fungible/non-fungible token + get token info total supply + get balance of the treasury
    function burnTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, int64[] memory serialNumbers, address treasury) external {
        uint256 balanceBeforeBurn = 0;
        if(amount > 0 && serialNumbers.length == 0) {
            balanceBeforeBurn = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeBurn = IERC721(token).balanceOf(treasury);
        }

        int totalSupplyBeforeBurn = getTokenTotalSupply(token, "Failed to retrieve token info before burn");

        (int responseCode, int newTotalSupply) = HederaTokenService.burnToken(token, amount, serialNumbers);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to burn token");

        int totalSupplyAfterBurn = getTokenTotalSupply(token, "Failed to retrieve token info after burn");

        if(amount > 0 && serialNumbers.length == 0) {
            if((totalSupplyBeforeBurn - amount != totalSupplyAfterBurn)  || (newTotalSupply != totalSupplyAfterBurn)) revert("Total supply mismatch after burn (Fungible)");
        } else {
            if((totalSupplyBeforeBurn - int256(serialNumbers.length) != totalSupplyAfterBurn) || ((newTotalSupply != totalSupplyAfterBurn))) revert("Total supply mismatch after burn (NFT)");
        }

        if(amount > 0 && serialNumbers.length == 0) {
            uint256 balanceAfterBurn = IERC20(token).balanceOf(treasury);
            if(balanceBeforeBurn - uint256(int256(amount)) != balanceAfterBurn) revert("Balance mismatch after burn (Fungible)");
        } else {
            uint256 balanceAfterBurn  = IERC721(token).balanceOf(treasury);
            if(balanceBeforeBurn - uint256(int256(serialNumbers.length)) != balanceAfterBurn) revert("Balance mismatch after burn (NFT)");
        }
    }

    // Wipe + get token info total supply + get balance of the account which balance was wiped
    function wipeTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, int64[] memory serialNumbers, address treasury) external {
        uint256 balanceBeforeWipe = 0;
        if(amount > 0 && serialNumbers.length == 0) {
            balanceBeforeWipe = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeWipe = IERC721(token).balanceOf(treasury);
        }

        int totalSupplyBeforeWipe = getTokenTotalSupply(token, "Failed to retrieve token info before wipe");
        int responseCode;

        if(amount > 0 && serialNumbers.length == 0) {
            responseCode = HederaTokenService.wipeTokenAccount(token, treasury, amount);
        } else {
            responseCode = HederaTokenService.wipeTokenAccountNFT(token, treasury, serialNumbers);
        }
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to wipe token");

        IHederaTokenService.TokenInfo memory retrievedTokenInfo;

        (responseCode, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to retrieve token info after wipe");

        int totalSupplyAfterWipe = retrievedTokenInfo.totalSupply;
        if(amount > 0 && serialNumbers.length == 0) {
            if(totalSupplyBeforeWipe - amount != totalSupplyAfterWipe) revert("Total supply mismatch after wipe (Fungible)");
        } else {
            if(totalSupplyBeforeWipe - int256(serialNumbers.length) != totalSupplyAfterWipe) revert("Total supply mismatch after burn (NFT)");
        }

        if(amount > 0 && serialNumbers.length == 0) {
            uint256 balanceAfterWipe = IERC20(token).balanceOf(treasury);
            if(balanceBeforeWipe - uint256(int256(amount)) != balanceAfterWipe) revert("Balance mismatch after wipe (Fungible)");
        } else {
            uint256 balanceAfterWipe = IERC721(token).balanceOf(treasury);
            if(balanceBeforeWipe - uint256(int256(serialNumbers.length)) != balanceAfterWipe) revert("Balance mismatch after wipe (NFT)");
        }
    }

    // Pause fungible/non-fungible token + get token info pause status + unpause + get token info pause status
    function pauseTokenGetPauseStatusUnpauseGetPauseStatus(address token) external {
        int responseCode = HederaTokenService.pauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to pause token");

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (response != HederaResponseCodes.SUCCESS) revert("Failed to get token info after pause");
        if(!retrievedTokenInfo.pauseStatus) revert("Token is not paused");

        responseCode = HederaTokenService.unpauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to unpause token");

        (response, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if(response != HederaResponseCodes.SUCCESS) revert("Failed to retrieve token info after unpause");
        if(retrievedTokenInfo.pauseStatus) revert("Token is still paused");
    }

    // Freeze fungible/non-fungible token + get token info freeze status + unfreeze + get token info freeze status
    function freezeTokenGetPauseStatusUnpauseGetPauseStatus(address token, address account) external {
        int responseCode = HederaTokenService.freezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to freeze token for the account");

        (int response, bool isFrozen) = HederaTokenService.isFrozen(token, account);
        if (response != HederaResponseCodes.SUCCESS) revert("Failed to check freeze status of account");
        if(!isFrozen) revert("Account is not frozen");

        responseCode = HederaTokenService.unfreezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to unfreeze account");

        (response, isFrozen) = HederaTokenService.isFrozen(token, account);
        if (response != HederaResponseCodes.SUCCESS) revert("Failed to check unfreeze status of account");
        if(isFrozen) revert("Account is still frozen");
    }

    // Associate fungible/non-fungible token transfer (should pass) + dissociate + transfer (should fail)
    function associateTokenDissociateFailTransfer(address token, address from, address to, uint256 amount, uint256 serialNumber) external {
        address[] memory tokens = new address[](1);
        tokens[0] = token;
        int responseCode = HederaTokenService.associateTokens(from, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to associate tokens");

        responseCode = HederaTokenService.dissociateTokens(from, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to dissociate tokens");

        if(amount > 0 && serialNumber == 0) {
            try IERC20(token).transferFrom(from, to, amount) returns (bool success) {
            } catch {
                revert("IERC20: failed to transfer");
            }
        } else {
            try IERC721(token).transferFrom(from, to, serialNumber) {
            } catch {
                revert("IERC721: failed to transfer");
            }
        }
    }

    // Approve fungible/non-fungible token + allowance
    function approveTokenGetAllowance(address token, address spender, uint256 amount, uint256 serialNumber) external {
        if(amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve token");
            if(IERC20(token).allowance(address(this), spender) != amount) revert("Allowance mismatch");
        } else {
            int responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT");
            if(IERC721(token).getApproved(serialNumber) != spender) revert("NFT approval mismatch");
        }
    }

    // Associate fungible/non-fungible token transfer + transfer
    function associateTokenTransfer(address token, address from, address to, uint256 amount, uint256 serialNumber) external {
        address[] memory tokens = new address[](1);
        tokens[0] = token;
        int responseCode = HederaTokenService.associateTokens(to, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to associate tokens");

        if(amount > 0 && serialNumber == 0) {
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(to);
            responseCode = HederaTokenService.transferToken(token, from, to, int64(uint64(amount)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer fungible token");
            if(IERC20(token).balanceOf(to) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");
        } else {
            responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), to, int64(int256(serialNumber)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            if(IERC721(token).ownerOf(serialNumber) != to) revert("NFT ownership mismatch after transfer");
        }
    }

    // Approve fungible/non-fungible token + transferFrom with spender + allowance + balance
    function approveTokenTransferFromGetAllowanceGetBalance(address token, address to, uint256 amount, uint256 serialNumber) external {
        address _spender = address(new SpenderContract());
        address[] memory tokens = new address[](1);
        tokens[0] = token;
        int responseCode = HederaTokenService.associateTokens(_spender, tokens);
        if(amount > 0 && serialNumber == 0) {
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to associate token");
            responseCode = HederaTokenService.approve(token, _spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve token for transfer");
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(to);
            if(IERC20(token).allowance(address(this), _spender) != amount) revert("Allowance mismatch before transfer");
            SpenderContract(_spender).spendFungible(token, amount, address(this), to);
            if(IERC20(token).balanceOf(to) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");
            if(IERC20(token).allowance(address(this), _spender) != 0) revert("Fungible token allowance mismatch after transfer");
        } else {
            responseCode = HederaTokenService.approveNFT(token, _spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
            if(IERC721(token).getApproved(serialNumber) != _spender) revert("NFT approval mismatch before transfer");
            SpenderContract(_spender).spendNFT(token, serialNumber, address(this), to);
            if(IERC721(token).ownerOf(serialNumber) != to) revert("NFT ownership mismatch after transfer");
            if(IERC721(token).getApproved(serialNumber) == _spender) revert("NFT allowance mismatch after transfer");
        }
    }

    // Approve fungible/non-fungible token + transfer with spender + allowance + balance
    function approveTokenTransferGetAllowanceGetBalance(address token, address spender, uint256 amount, uint256 serialNumber) external {
        if(amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve token for transfer");
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(spender);
            if(IERC20(token).allowance(address(this), spender) != amount) revert("Allowance mismatch before transfer");
            responseCode = HederaTokenService.transferToken(token, address(this), spender, int64(uint64(amount)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer fungible token");
            if(IERC20(token).balanceOf(spender) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");
        } else {
            int responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
            responseCode = HederaTokenService.transferNFT(token, address(this), spender, int64(uint64(serialNumber)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
        }
    }

    // Approve fungible/non-fungible token + cryptoTransfer with spender + allowance + balance
    function approveTokenCryptoTransferGetAllowanceGetBalance(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        address token = tokenTransfers[0].token;
        address spender = address(0);
        uint256 amount = 0;
        uint256 serialNumber = 0;
        if(tokenTransfers[0].transfers.length > 0) {
            spender = tokenTransfers[0].transfers[1].accountID;
            amount = uint256(uint64(tokenTransfers[0].transfers[1].amount));
        } else {
            spender = tokenTransfers[0].nftTransfers[0].receiverAccountID;
            serialNumber = uint256(uint64(tokenTransfers[0].nftTransfers[0].serialNumber));
        }
        if(amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve token for transfer");
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(spender);
            if(IERC20(token).allowance(address(this), spender) != amount) revert("Allowance mismatch before transfer");
            responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer fungible token");
            if(IERC20(token).balanceOf(spender) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");
        } else {
            int responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
            responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
            if(IERC721(token).getApproved(serialNumber) == spender) revert("NFT allowance mismatch after transfer");
        }
    }

    // Approve for all an nft + transferFrom with spender + isApprovedForAll
    function approveForAllTokenTransferFromGetAllowance(address token, address spender, uint256 serialNumber) external {
        int responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
        responseCode = HederaTokenService.setApprovalForAll(token, spender, true);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        if(!IERC721(token).isApprovedForAll(address(this), spender)) revert("NFT approval mismatch before transfer");
        IERC721(token).transferFrom(address(this), spender, serialNumber);
        if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
        responseCode = HederaTokenService.setApprovalForAll(token, spender, false);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        if(IERC721(token).isApprovedForAll(address(this), spender)) revert("NFT approval mismatch before transfer");
    }

    // Approve for all an nft + transfer with spender + isApprovedForAll
    function approveForAllTokenTransferGetAllowance(address token, address spender, uint256 serialNumber) external {
        int responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
        responseCode = HederaTokenService.setApprovalForAll(token, spender, true);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        if(!IERC721(token).isApprovedForAll(address(this), spender)) revert("NFT approval mismatch before transfer");
        responseCode = HederaTokenService.transferNFT(token, address(this), spender, int64(uint64(serialNumber)));
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
        if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
        responseCode = HederaTokenService.setApprovalForAll(token, spender, false);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        (int response, bool approved) = HederaTokenService.isApprovedForAll(token, address(this), spender);
        if (response != HederaResponseCodes.SUCCESS) revert("Failed to get approval for NFT");
        if(approved) revert("NFT approval mismatch before transfer");
    }

    // Approve for all an nft + cryptoTransfer with spender + isApprovedForAll
    function approveForAllCryptoTransferGetAllowance(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        address token = tokenTransfers[0].token;
        address spender = tokenTransfers[0].nftTransfers[0].receiverAccountID;
        uint256 serialNumber = uint256(uint64(tokenTransfers[0].nftTransfers[0].serialNumber));

        int responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
        responseCode = HederaTokenService.setApprovalForAll(token, spender, true);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        if(!IERC721(token).isApprovedForAll(address(this), spender)) revert("NFT approval mismatch before transfer");
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
        if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
        responseCode = HederaTokenService.setApprovalForAll(token, spender, false);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        (int response, bool approved) = HederaTokenService.isApprovedForAll(token, address(this), spender);
        if (response != HederaResponseCodes.SUCCESS) revert("Failed to get approveal for NFT");
        if(approved) revert("NFT approval mismatch before transfer");
    }

    // TransferFrom an nft + ownerOf
    function transferFromNFTGetAllowance(address token, uint256 serialNumber) external {
        try IERC721(token).transferFrom(IERC721(token).ownerOf(serialNumber), address(this), serialNumber) {
        } catch {
            revert("IERC721: failed to transfer");
        }
        if(IERC721(token).ownerOf(serialNumber) != address(this)) revert("NFT ownership mismatch after transfer");
    }

    // Transfer fungible/non-fungible token + allowance + balance
    function transferFromGetAllowanceGetBalance(address token, address spender, uint256 amount, uint256 serialNumber) external {
        if(amount > 0 && serialNumber == 0) {
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(spender);
            int responseCode = HederaTokenService.transferToken(token, address(this), spender, int64(uint64(amount)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer fungible token");
            if(IERC20(token).balanceOf(spender) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");

        } else {
            int responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
            //responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if(responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            if(IERC721(token).ownerOf(serialNumber) != address(this)) revert("NFT ownership mismatch after transfer");
        }
    }

    // CryptoTransfer fungible/non-fungible token + allowance + balance
    function cryptoTransferFromGetAllowanceGetBalance(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        address token = tokenTransfers[0].token;
        address spender = address(0);
        uint256 amount = 0;
        uint256 serialNumber = 0;
        if(tokenTransfers[0].transfers.length > 0) {
            spender = tokenTransfers[0].transfers[1].accountID;
            amount = uint256(uint64(tokenTransfers[0].transfers[1].amount));
        } else {
            spender = tokenTransfers[0].nftTransfers[0].receiverAccountID;
            serialNumber = uint256(uint64(tokenTransfers[0].nftTransfers[0].serialNumber));
        }
        if(amount > 0 && serialNumber == 0) {
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(spender);
            int responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer fungible token");
            if(IERC20(token).balanceOf(spender) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");
        } else {
            int responseCode = HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
        }
    }

    // GrantKyc for fungible/non-fungible token + IsKyc + RevokeKyc + IsKyc
    function grantKycRevokeKyc(address token, address account) external {
        int responseCode = HederaTokenService.grantTokenKyc(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Grant kyc operation failed");
        (int response, bool isKyc) = HederaTokenService.isKyc(token, account);
        if (response != HederaResponseCodes.SUCCESS) revert("Is kyc operation failed");
        if(!isKyc) revert("Kyc status mismatch");

        responseCode = HederaTokenService.revokeTokenKyc(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Grant kyc operation failed");
        (response, isKyc) = HederaTokenService.isKyc(token, account);
        if (response != HederaResponseCodes.SUCCESS) revert("Is kyc operation failed");
        if(isKyc) revert("Kyc status mismatch");
    }

    function getAddressThis() public view returns(address) {
        return address(this);
    }

    function getTokenTotalSupply(address token, string memory errorMessage) internal returns (int) {
        (int responseCode, IHederaTokenService.TokenInfo memory tokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert(errorMessage);
        return tokenInfo.totalSupply;
    }

    function mintNft(
        address token,
        bytes[] memory metadata,
        string memory errorMessage
    ) internal returns (int responseCode, int newTotalSupply, int64[] memory serialNumbers)
    {
        (responseCode, newTotalSupply, serialNumbers) = HederaTokenService.mintToken(token, 0, metadata);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert(errorMessage);
        }
        return (responseCode, newTotalSupply, serialNumbers);
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
