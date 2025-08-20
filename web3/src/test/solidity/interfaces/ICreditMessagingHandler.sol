// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.0;

import { TargetCredit } from "./ICreditMessaging.sol";

    struct Credit {
        uint32 srcEid;
        uint64 amount;
    }

/// @dev This is an internal interface, defining functions to handle messages/calls from the credit messaging contract.
interface ICreditMessagingHandler {
    function sendCredits(uint32 _dstEid, TargetCredit[] calldata _credits) external returns (Credit[] memory);

    function receiveCredits(uint32 _srcEid, Credit[] calldata _credits) external;
}