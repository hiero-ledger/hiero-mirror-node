// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.0;

/// @notice Parameters used to assess fees to send tokens to a destination endpoint.
    struct FeeParams {
        address sender;
        uint32 dstEid;
        uint64 amountInSD;
        uint64 deficitSD;
        bool toOFT;
        bool isTaxi;
    }

/// @title Interface for assessing fees to send tokens to a destination endpoint.
interface IStargateFeeLib {
    /// @notice Apply a fee for a given request, allowing for state modification.
    /// @dev This is included for future proofing potential implementations
    /// @dev where state is modified in the feeLib based on a FeeParams

    function applyFee(FeeParams calldata _params) external returns (uint64 amountOutSD);
    /// @notice Apply a fee for a given request, without modifying state.
    function applyFeeView(FeeParams calldata _params) external view returns (uint64 amountOutSD);
}