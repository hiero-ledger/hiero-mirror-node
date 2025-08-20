// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.22;

/// @dev The Path struct contains the bus base fare multiplier bps and the credit in the same slot for gas saving.
    struct Path {
        uint64 credit; // available credit for the path, in SD
    }

    using PathLib for Path global;

/**
 * @title A library to operate on Paths.
 * @dev A Path is a route through which value can be sent. It entails the local chain and a destination chain, and has
 *      a given amount of credit associated with it. Every time the value is sent from A to B, the credit on A is
 *      decreased and credit on B is increased. If credit hits 0 then the path can no longer be used.
 */
library PathLib {
    uint64 internal constant UNLIMITED_CREDIT = type(uint64).max;

    // solhint-disable-next-line event-name-camelcase
    event Path_CreditBurned(uint64 amountSD);

    error Path_InsufficientCredit();
    error Path_AlreadyHasCredit();
    error Path_UnlimitedCredit();

    /// @notice Increase credit for a given Path.
    /// @dev Reverts with Path_UnlimitedCredit if the increase would hit the maximum amount of credit (reserved value)
    /// @param _path The Path for which to increase credit
    /// @param _amountSD The amount by which to increase credit
    function increaseCredit(Path storage _path, uint64 _amountSD) internal {
        uint64 credit = _path.credit;
        if (credit == UNLIMITED_CREDIT) return;
        credit += _amountSD;
        if (credit == UNLIMITED_CREDIT) revert Path_UnlimitedCredit();
        _path.credit = credit;
    }

    /// @notice Decrease credit for a given Path.
    /// @dev Reverts with InsufficientCredit if there is not enough credit
    /// @param _path The Path for which to decrease credit
    /// @param _amountSD The amount by which to decrease credit
    function decreaseCredit(Path storage _path, uint64 _amountSD) internal {
        uint64 currentCredit = _path.credit;
        if (currentCredit == UNLIMITED_CREDIT) return;
        if (currentCredit < _amountSD) revert Path_InsufficientCredit();
        unchecked {
            _path.credit = currentCredit - _amountSD;
        }
    }

    /// @notice Decrease credit for a given path, even if only a partial amount is possible.
    /// @param _path The Path for which to decrease credit
    /// @param _amountSD The amount by which try to decrease credit
    /// @param _minKept The minimum amount of credit to keep after the decrease
    /// @return decreased The actual amount of credit decreased
    function tryDecreaseCredit(
        Path storage _path,
        uint64 _amountSD,
        uint64 _minKept
    ) internal returns (uint64 decreased) {
        uint64 currentCredit = _path.credit;
        // not allowed to try to decrease unlimited credit
        if (currentCredit == UNLIMITED_CREDIT) revert Path_UnlimitedCredit();
        if (_minKept < currentCredit) {
            unchecked {
                uint64 maxDecreased = currentCredit - _minKept;
                decreased = _amountSD > maxDecreased ? maxDecreased : _amountSD;
                _path.credit = currentCredit - decreased;
            }
        }
    }

    /// @notice Set a given path as OFT or reset an OFT path to 0 credit.
    /// @dev A Path for which the asset is using an OFT on destination gets unlimited credit because value transfers
    /// @dev do not spend value.
    /// @dev Such a path is expected to not have credit before.
    /// @dev Reverts with AlreadyHasCredit if the Path already had credit assigned to it
    /// @param _path The Path to set
    /// @param _oft Whether to set it as OFT or reset it from OFT
    function setOFTPath(Path storage _path, bool _oft) internal {
        uint64 currentCredit = _path.credit;
        if (_oft) {
            // only allow un-limiting from 0
            if (currentCredit != 0) revert Path_AlreadyHasCredit();
            _path.credit = UNLIMITED_CREDIT;
        } else {
            // only allow resetting from unlimited
            if (currentCredit != UNLIMITED_CREDIT) revert Path_AlreadyHasCredit();
            _path.credit = 0;
        }
    }

    /// @notice Check whether a given Path is set as OFT.
    /// @param _path The path to examine
    /// @return whether the Path is set as OFT
    function isOFTPath(Path storage _path) internal view returns (bool) {
        return _path.credit == UNLIMITED_CREDIT;
    }

    /// @notice Burn credit for a given Path during bridged token migration.
    function burnCredit(Path storage _path, uint64 _amountSD) internal {
        decreaseCredit(_path, _amountSD);
        emit Path_CreditBurned(_amountSD);
    }
}