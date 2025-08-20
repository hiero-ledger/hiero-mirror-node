// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.0;

import { Origin } from "@layerzerolabs/lz-evm-oapp-v2/contracts/oapp/OApp.sol";

/// @dev This is an internal interface, defining the function to handle token message from the token messaging contract.
interface ITokenMessagingHandler {
    function receiveTokenBus(
        Origin calldata _origin,
        bytes32 _guid,
        uint8 _seatNumber,
        address _receiver,
        uint64 _amountSD
    ) external;

    function receiveTokenTaxi(
        Origin calldata _origin,
        bytes32 _guid,
        address _receiver,
        uint64 _amountSD,
        bytes calldata _composeMsg
    ) external;
}