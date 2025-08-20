// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.22;

import { IERC20 } from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import { Ownable } from "openzeppelin4/access/Ownable.sol";

/// @dev WARNING: Transferring tokens, when the token address is wrong, will fail silently.
contract Transfer is Ownable {
    error Transfer_TransferFailed();
    error Transfer_ApproveFailed();

    // @dev default this to 2300, but it is modifiable
    // @dev this is intended to provide just enough gas to receive native tokens.
    // @dev ie. empty fallbacks or EOA addresses
    uint256 internal transferGasLimit = 2300;

    function getTransferGasLimit() external view returns (uint256) {
        return transferGasLimit;
    }

    function setTransferGasLimit(uint256 _gasLimit) external onlyOwner {
        transferGasLimit = _gasLimit;
    }

    /// @notice Transfer native coin to an account
    /// @dev If gas is unlimited, we pass 63/64 of the gasleft()
    /// @dev This call may revert due to out of gas instead of returning false.
    /// @param _to The account to transfer native coin to
    /// @param _value The amount of native coin to transfer
    /// @param _gasLimited Whether to limit gas available for the 'fall-back'
    /// @return success Whether the transfer was successful
    function transferNative(address _to, uint256 _value, bool _gasLimited) internal returns (bool success) {
        uint256 gasForCall = _gasLimited ? transferGasLimit : gasleft();

        // @dev We dont care about the data returned here, only success or not.
        assembly {
            success := call(gasForCall, _to, _value, 0, 0, 0, 0)
        }
    }

    /// @notice Transfer an ERC20 token from the sender to an account
    /// @param _token The address of the ERC20 token to send
    /// @param _to The receiving account
    /// @param _value The amount of tokens to transfer
    /// @return success Whether the transfer was successful or not
    function transferToken(address _token, address _to, uint256 _value) internal returns (bool success) {
        success = _call(_token, abi.encodeWithSelector(IERC20(_token).transfer.selector, _to, _value));
    }

    /// @notice Transfer an ERC20 token from one account to another
    /// @param _token The address of the ERC20 token to send
    /// @param _from The source account
    /// @param _to The destination account
    /// @param _value The amount of tokens to transfer
    /// @return success Whether the transfer was successful or not
    function transferTokenFrom(
        address _token,
        address _from,
        address _to,
        uint256 _value
    ) internal returns (bool success) {
        success = _call(_token, abi.encodeWithSelector(IERC20(_token).transferFrom.selector, _from, _to, _value));
    }

    /// @notice Transfer either native coin or ERC20 token from the sender to an account
    /// @param _token The ERC20 address or 0x0 if native is desired
    /// @param _to The destination account
    /// @param _value the amount to transfer
    /// @param _gasLimited Whether to limit the amount of gas when doing a native transfer
    /// @return success Whether the transfer was successful or not
    function transfer(address _token, address _to, uint256 _value, bool _gasLimited) internal returns (bool success) {
        if (_token == address(0)) {
            success = transferNative(_to, _value, _gasLimited);
        } else {
            success = transferToken(_token, _to, _value);
        }
    }

    /// @notice Approve a given amount of token for an account
    /// @param _token The OFT contract to use for approval
    /// @param _spender The account to approve
    /// @param _value The amount of tokens to approve
    /// @return success Whether the approval succeeded
    function approveToken(address _token, address _spender, uint256 _value) internal returns (bool success) {
        success = _call(_token, abi.encodeWithSelector(IERC20(_token).approve.selector, _spender, _value));
    }

    /// @notice Transfer native coin to an account or revert
    /// @dev Reverts with TransferFailed if the transfer failed
    /// @param _to The account to transfer native coin to
    /// @param _value The amount of native coin to transfer
    /// @param _gasLimited Whether to limit the amount of gas to 2300
    function safeTransferNative(address _to, uint256 _value, bool _gasLimited) internal {
        if (!transferNative(_to, _value, _gasLimited)) revert Transfer_TransferFailed();
    }

    /// @notice Transfer an ERC20 token from one account to another or revert
    /// @dev Reverts with TransferFailed when the transfer fails
    /// @param _token The address of the ERC20 token to send
    /// @param _to The destination account
    /// @param _value The amount of tokens to transfer
    function safeTransferToken(address _token, address _to, uint256 _value) internal {
        if (!transferToken(_token, _to, _value)) revert Transfer_TransferFailed();
    }

    /// @notice Transfer an ERC20 token from one account to another
    /// @dev Reverts with TransferFailed when the transfer fails
    /// @param _token The address of the ERC20 token to send
    /// @param _from The source account
    /// @param _to The destination account
    /// @param _value The amount of tokens to transfer
    function safeTransferTokenFrom(address _token, address _from, address _to, uint256 _value) internal {
        if (!transferTokenFrom(_token, _from, _to, _value)) revert Transfer_TransferFailed();
    }

    /// @notice Transfer either native coin or ERC20 token from the sender to an account
    /// @dev Reverts with TransferFailed when the transfer fails
    /// @param _token The ERC20 address or 0x0 if native is desired
    /// @param _to The destination account
    /// @param _value the amount to transfer
    /// @param _gasLimited Whether to limit the amount of gas when doing a native transfer
    function safeTransfer(address _token, address _to, uint256 _value, bool _gasLimited) internal {
        if (!transfer(_token, _to, _value, _gasLimited)) revert Transfer_TransferFailed();
    }

    /// @notice Approve a given amount of token for an account or revert
    /// @dev Reverts with ApproveFailed if the approval failed
    /// @dev Consider using forceApproveToken(...) to ensure the approval is set correctly.
    /// @param _token The OFT contract to use for approval
    /// @param _spender The account to approve
    /// @param _value The amount of tokens to approve
    function safeApproveToken(address _token, address _spender, uint256 _value) internal {
        if (!approveToken(_token, _spender, _value)) revert Transfer_ApproveFailed();
    }

    /// @notice Force approve a given amount of token for an account by first resetting the approval
    /// @dev Some tokens that require the approval to be set to zero before setting it to a non-zero value, e.g. USDT.
    /// @param _token The OFT contract to use for approval
    /// @param _spender The account to approve
    /// @param _value The amount of tokens to approve
    function forceApproveToken(address _token, address _spender, uint256 _value) internal {
        if (!approveToken(_token, _spender, _value)) {
            safeApproveToken(_token, _spender, 0);
            safeApproveToken(_token, _spender, _value);
        }
    }

    function _call(address _token, bytes memory _data) private returns (bool success) {
        // solhint-disable-next-line avoid-low-level-calls
        (bool s, bytes memory returndata) = _token.call(_data);
        success = s ? returndata.length == 0 || abi.decode(returndata, (bool)) : false;
    }
}