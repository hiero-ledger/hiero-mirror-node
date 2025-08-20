// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.22;

import { IERC20Metadata } from "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";

import { StargateType } from "./IStargate.sol";
import { IERC20Minter } from "./IERC20Minter.sol";
import { StargateBase, FeeParams } from "./StargateBase.sol";

import { Ownable } from "@openzeppelin/contracts/access/Ownable.sol";

/// @title A Stargate contract representing an OFT. This contract will burn OFTs when sending tokens
/// @title to other chains and mint tokens when receiving them from other chains.
abstract contract StargateOFT is StargateBase {
    /// @notice Create a StargateOFT contract administering an OFT.
    /// @param _token The OFT to administer
    /// @param _sharedDecimals The minimum number of decimals used to represent value in this OFT
    /// @param _endpoint The LZ endpoint address
    /// @param _owner The account owning this contract
    constructor(
        address _token,
        uint8 _sharedDecimals,
        address _endpoint,
        address _owner
    ) StargateBase(_token, IERC20Metadata(_token).decimals(), _sharedDecimals, _endpoint, _owner) {}

    /// @notice Transfer ownership of the token to a new owner.
    /// @param _newOwner The account to set as owner
    function transferTokenOwnership(address _newOwner) external onlyOwner {
        Ownable(token).transferOwnership(_newOwner);
    }

    /// @notice Burn tokens to represent their removal from the local chain
    /// @param _from The address to burn tokens from
    /// @param _amount How many tokens to burn in LD
    /// @return amountSD The amount burned in SD
    function _inflow(address _from, uint256 _amount) internal virtual override returns (uint64 amountSD) {
        amountSD = _ld2sd(_amount);
        IERC20Minter(token).burnFrom(_from, _sd2ld(amountSD)); // remove dust and burn
    }

    /// @notice Mint tokens to represent their lading into the local chain
    /// @param _to The account to mint tokens for
    /// @param _amount The amount of tokens to mint
    /// @return success Whether the minting was successful
    function _outflow(address _to, uint256 _amount) internal virtual override returns (bool success) {
        try IERC20Minter(token).mint(_to, _amount) {
            success = true;
        } catch {} // solhint-disable-line no-empty-blocks
    }

    /// @notice Limits the reward awarded when withdrawing value.
    /// @dev Concretes the StargateBase contract.
    /// @dev Reward is not allowed for OFT, so 0 is returned.
    /// @dev reward is calculated as amountOut - amountIn, so amountIn = amountOut - reward,
    /// @dev this removes the reward and sets the exchange rate to 1:1 local:remote
    /// @param _amountOutSD The amount of tokens expected on the destination chain in SD
    /// @param _reward The initially calculated reward by FeeLib
    /// @return newAmountOutSD The actual amount to be withdrawn expected on the destination chain
    /// @return newReward The actual reward after applying any caps
    function _capReward(
        uint64 _amountOutSD,
        uint64 _reward
    ) internal pure override returns (uint64 newAmountOutSD, uint64 newReward) {
        unchecked {
            newAmountOutSD = _amountOutSD - _reward;
        }
        newReward = 0;
    }

    /// @notice Returns the type of Stargate contract.
    /// @dev Fulfills the IStargate interface.
    /// @return The type of Stargate contract
    function stargateType() external pure override returns (StargateType) {
        return StargateType.OFT;
    }

    function _buildFeeParams(
        uint32 _dstEid,
        uint64 _amountInSD,
        bool _isTaxi
    ) internal view override returns (FeeParams memory) {
        return FeeParams(msg.sender, _dstEid, _amountInSD, 0, paths[_dstEid].isOFTPath(), _isTaxi);
    }
}