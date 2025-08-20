// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.0;

import { MessagingReceipt, MessagingFee, Ticket } from "./IStargate.sol";

/// @notice Payload for sending a taxi message.
/// @dev A taxi message is sent immediately and is not stored on the bus.
    struct TaxiParams {
        address sender;
        uint32 dstEid;
        bytes32 receiver;
        uint64 amountSD;
        bytes composeMsg;
        bytes extraOptions;
    }

/// @notice Payload for riding the bus.
/// @dev Riding the bus is a two-step process:
/// @dev - The message is sent to the bus,
/// @dev - The bus is driven to the destination.
    struct RideBusParams {
        address sender;
        uint32 dstEid;
        bytes32 receiver;
        uint64 amountSD;
        bool nativeDrop;
    }

/// @title Token Messaging API.
/// @notice This interface defines the API for sending a taxi message, riding the bus, and driving the bus, along with
/// corresponding quote functions.
interface ITokenMessaging {
    /// @notice Sends a taxi message
    /// @param _params The taxi message payload
    /// @param _messagingFee The messaging fee for sending a taxi message
    /// @param _refundAddress The address to refund excess LayerZero MessagingFees
    /// @return receipt The MessagingReceipt resulting from sending the taxi
    function taxi(
        TaxiParams calldata _params,
        MessagingFee calldata _messagingFee,
        address _refundAddress
    ) external payable returns (MessagingReceipt memory receipt);

    /// @notice Quotes the messaging fee for sending a taxi message
    /// @param _params The taxi message payload
    /// @param _payInLzToken Whether to pay the fee in LZ token
    /// @return fee The MessagingFee for sending the taxi message
    function quoteTaxi(TaxiParams calldata _params, bool _payInLzToken) external view returns (MessagingFee memory fee);

    /// @notice Sends a message to ride the bus, queuing the passenger in preparation for the drive.
    /// @notice The planner will later driveBus to the destination endpoint.
    /// @param _params The rideBus message payload
    /// @return receipt The MessagingReceipt resulting from sending the rideBus message
    /// @return ticket The Ticket for riding the bus
    function rideBus(
        RideBusParams calldata _params
    ) external returns (MessagingReceipt memory receipt, Ticket memory ticket);

    /// @notice Quotes the messaging fee for riding the bus
    /// @param _dstEid The destination LayerZero endpoint ID.
    /// @param _nativeDrop Whether to pay for a native drop on the destination.
    /// @return fee The MessagingFee for riding the bus
    function quoteRideBus(uint32 _dstEid, bool _nativeDrop) external view returns (MessagingFee memory fee);

    /// @notice Drives the bus to the destination.
    /// @param _dstEid The destination LayerZero endpoint ID.
    /// @param _passengers The passengers to drive to the destination.
    /// @return receipt The MessagingReceipt resulting from driving the bus
    function driveBus(
        uint32 _dstEid,
        bytes calldata _passengers
    ) external payable returns (MessagingReceipt memory receipt);

    /// @notice Quotes the messaging fee for driving the bus to the destination.
    /// @param _dstEid The destination LayerZero endpoint ID.
    /// @param _passengers The passengers to drive to the destination.
    /// @return fee The MessagingFee for driving the bus
    function quoteDriveBus(uint32 _dstEid, bytes calldata _passengers) external view returns (MessagingFee memory fee);
}