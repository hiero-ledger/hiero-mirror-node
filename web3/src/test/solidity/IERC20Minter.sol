// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.0;

/// @title An interface for minting and burning ERC20s.
/// @dev Implemented by OFT contracts.
interface IERC20Minter {
    /// @notice Mint tokens and transfer them to the given account.
    /// @param _to The account to mint the tokens to
    /// @param _amount How many tokens to mint
    function mint(address _to, uint256 _amount) external;

    /// @notice Burn tokens from a given account.
    /// @param _from The account to burn tokens from
    /// @param _amount How many tokens to burn
    function burnFrom(address _from, uint256 _amount) external;
}