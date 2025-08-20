// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.0;

import { MessagingFee } from "@layerzerolabs/lz-evm-oapp-v2/contracts/oft/interfaces/IOFT.sol";

/// @notice Stores the information related to a batch of credit transfers.
    struct TargetCreditBatch {
        uint16 assetId;
        TargetCredit[] credits;
    }

/// @notice Stores the information related to a single credit transfer.
    struct TargetCredit {
        uint32 srcEid;
        uint64 amount; // the amount of credits to intended to send
        uint64 minAmount; // the minimum amount of credits to keep on local chain after sending
    }

/// @title Credit Messaging API
/// @dev This interface defines the API for quoting and sending credits to other chains.
interface ICreditMessaging {
    /// @notice Sends credits to the destination endpoint.
    /// @param _dstEid The destination LayerZero endpoint ID.
    /// @param _creditBatches The credit batch payloads to send to the destination LayerZero endpoint ID.
    function sendCredits(uint32 _dstEid, TargetCreditBatch[] calldata _creditBatches) external payable;

    /// @notice Quotes the fee for sending credits to the destination endpoint.
    /// @param _dstEid The destination LayerZero endpoint ID.
    /// @param _creditBatches The credit batch payloads to send to the destination LayerZero endpoint ID.
    /// @return fee The fee for sending the credits to the destination endpoint.
    function quoteSendCredits(
        uint32 _dstEid,
        TargetCreditBatch[] calldata _creditBatches
    ) external view returns (MessagingFee memory fee);
}