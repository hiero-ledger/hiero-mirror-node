// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

import "./IHederaTokenService.sol";

contract SpendOnBehalf {
    address constant HTS_ADDRESS = address(0x167);

    event HbarTransferExecuted(address indexed owner, address indexed receiver, int64 amount, int64 responseCode);
    event HtsTransferExecuted(address indexed token, address indexed owner, address indexed receiver, int64 amount, int64 responseCode);
    event TokenAssociated(address indexed token, int64 responseCode);

    receive() external payable {}

    /// Associates an HTS token with this contract.
    /// @param token The HTS token Solidity address
    function associateHTSToken(address token) external returns (int64 responseCode) {
        responseCode = IHederaTokenService(HTS_ADDRESS).associateToken(address(this), token);
        emit TokenAssociated(token, responseCode);
        require(responseCode == 22, "HTS token association failed");
    }

    /// Spends HBAR on behalf of the owner using the allowance.
    /// @param owner The account that authorized the contract (spender)
    /// @param receiver The recipient of the HBAR
    /// @param amount The amount of HBAR to transfer in tinybars
    function spendHbar(address owner, address receiver, int64 amount) external returns (int64 responseCode) {
        IHederaTokenService.AccountAmount[] memory transfers = new IHederaTokenService.AccountAmount[](2);
        
        // Owner sends HBAR (negative amount, isApproval = true)
        transfers[0] = IHederaTokenService.AccountAmount({
            accountID: owner,
            amount: -amount,
            isApproval: true
        });
        
        // Receiver receives HBAR (positive amount, isApproval = false)
        transfers[1] = IHederaTokenService.AccountAmount({
            accountID: receiver,
            amount: amount,
            isApproval: false
        });
        
        IHederaTokenService.TransferList memory transferList = IHederaTokenService.TransferList({
            transfers: transfers
        });
        
        IHederaTokenService.TokenTransferList[] memory tokenTransfers = new IHederaTokenService.TokenTransferList[](0);
        
        // Call the precompile directly via interface
        responseCode = IHederaTokenService(HTS_ADDRESS).cryptoTransfer(transferList, tokenTransfers);
        
        emit HbarTransferExecuted(owner, receiver, amount, responseCode);
        require(responseCode == 22, "cryptoTransfer failed for HBAR");
    }

    /// Spends HTS tokens on behalf of the owner using the allowance.
    /// @param token The HTS token Solidity address
    /// @param owner The account that authorized the contract (spender)
    /// @param receiver The recipient of the HTS tokens
    /// @param amount The amount of tokens to transfer
    function spendHts(address token, address owner, address receiver, int64 amount) external returns (int64 responseCode) {
        IHederaTokenService.AccountAmount[] memory transfers = new IHederaTokenService.AccountAmount[](2);
        
        // Owner sends tokens (negative amount, isApproval = true)
        transfers[0] = IHederaTokenService.AccountAmount({
            accountID: owner,
            amount: -amount,
            isApproval: true
        });
        
        // Receiver receives tokens (positive amount, isApproval = false)
        transfers[1] = IHederaTokenService.AccountAmount({
            accountID: receiver,
            amount: amount,
            isApproval: false
        });
        
        IHederaTokenService.TokenTransferList[] memory tokenTransfers = new IHederaTokenService.TokenTransferList[](1);
        tokenTransfers[0] = IHederaTokenService.TokenTransferList({
            token: token,
            transfers: transfers,
            nftTransfers: new IHederaTokenService.NftTransfer[](0)
        });
        
        IHederaTokenService.TransferList memory emptyHbarTransfers = IHederaTokenService.TransferList({
            transfers: new IHederaTokenService.AccountAmount[](0)
        });
        
        // Call the precompile directly via interface
        responseCode = IHederaTokenService(HTS_ADDRESS).cryptoTransfer(emptyHbarTransfers, tokenTransfers);
        
        emit HtsTransferExecuted(token, owner, receiver, amount, responseCode);
        require(responseCode == 22, "cryptoTransfer failed for HTS");
    }
}
