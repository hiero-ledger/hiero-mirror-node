// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaResponseCodes.sol";
import "./IHederaScheduleService.sol";

abstract contract HederaScheduleService {
    address constant scheduleSystemContractAddress = address(0x16b);

    /// Authorizes the calling contract as a signer to the schedule transaction.
    /// @param schedule the address of the schedule transaction.
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    function authorizeSchedule(address schedule) internal returns (int64 responseCode) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.authorizeSchedule.selector, schedule));
        responseCode = success ? abi.decode(result, (int64)) : HederaResponseCodes.UNKNOWN;
    }

    /// Allows for the signing of a schedule transaction given a protobuf encoded signature map
    /// The message signed by the keys is defined to be the concatenation of the shard, realm, and schedule transaction ID.
    /// @param schedule the address of the schedule transaction.
    /// @param signatureMap the protobuf encoded signature map
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    function signSchedule(address schedule, bytes memory signatureMap) internal returns (int64 responseCode) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.signSchedule.selector, schedule, signatureMap));
        responseCode = success ? abi.decode(result, (int64)) : HederaResponseCodes.UNKNOWN;
    }

    /// Allows for the creation of a schedule transaction for given a system contract address, abi encoded call data and payer address
    /// Currently supports the Hedera Token Service System Contract (0x167) with encoded call data for
    /// createFungibleToken, createNonFungibleToken, createFungibleTokenWithCustomFees, createNonFungibleTokenWithCustomFees
    /// and updateToken functions
    /// @param systemContractAddress the address of the system contract from which to create the schedule transaction
    /// @param callData the abi encoded call data for the system contract function
    /// @param payer the address of the account that will pay for the schedule transaction
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    /// @return scheduleAddress The address of the newly created schedule transaction.
    function scheduleNative(address systemContractAddress, bytes memory callData, address payer) internal returns (int64 responseCode, address scheduleAddress) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.scheduleNative.selector, systemContractAddress, callData, payer));
        (responseCode, scheduleAddress) = success ? abi.decode(result, (int64, address)) : (int64(HederaResponseCodes.UNKNOWN), address(0));
    }

    /// Returns the token information for a scheduled fungible token create transaction
    /// @param scheduleAddress the address of the schedule transaction
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    /// @return fungibleTokenInfo The token information for the scheduled fungible token create transaction
    function getScheduledCreateFungibleTokenInfo(address scheduleAddress) internal returns (int64 responseCode, IHederaTokenService.FungibleTokenInfo memory fungibleTokenInfo) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.getScheduledCreateFungibleTokenInfo.selector, scheduleAddress));
        IHederaTokenService.FungibleTokenInfo memory defaultTokenInfo;
        (responseCode, fungibleTokenInfo) = success ? abi.decode(result, (int64, IHederaTokenService.FungibleTokenInfo)) : (int64(HederaResponseCodes.UNKNOWN), defaultTokenInfo);
    }

    /// Returns the token information for a scheduled non fungible token create transaction
    /// @param scheduleAddress the address of the schedule transaction
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    /// @return nonFungibleTokenInfo The token information for the scheduled non fungible token create transaction
    function getScheduledCreateNonFungibleTokenInfo(address scheduleAddress) internal returns (int64 responseCode, IHederaTokenService.NonFungibleTokenInfo memory nonFungibleTokenInfo) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.getScheduledCreateNonFungibleTokenInfo.selector, scheduleAddress));
        IHederaTokenService.NonFungibleTokenInfo memory defaultTokenInfo;
        (responseCode, nonFungibleTokenInfo) = success ? abi.decode(result, (int64, IHederaTokenService.NonFungibleTokenInfo)) : (int64(HederaResponseCodes.UNKNOWN), defaultTokenInfo);
    }

    /// Allows for the creation of a schedule transaction to schedule any contract call for a given smart contract
    /// address, expiration time, the gas limit for the future call, the value to send with that call
    /// and the call data to use.
    /// @param to the address of the smart contract for the future call
    /// @param expirySecond an expiration time of the future call
    /// @param gasLimit a maximum limit to the amount of gas to use for future call
    /// @param value an amount of tinybar sent via this future contract call
    /// @param callData the smart contract function to call. This MUST contain The application binary interface (ABI)
    /// encoding of the function call per the Ethereum contract ABI standard, giving the function signature and
    /// arguments being passed to the function.
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    /// @return scheduleAddress The address of the newly created schedule transaction.
    function scheduleCall(address to, uint256 expirySecond, uint256 gasLimit, uint64 value, bytes memory callData)
    internal returns (int64 responseCode, address scheduleAddress) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.scheduleCall.selector, to, expirySecond, gasLimit, value, callData));
        (responseCode, scheduleAddress) = success ? abi.decode(result, (int64, address)) : (int64(HederaResponseCodes.UNKNOWN), address(0));
    }

    /// Allows for the creation of a schedule transaction to schedule any contract call for a given smart contract
    /// address, with a payer for the scheduled transaction, expiration time, the gas limit for the future call,
    /// the value to send with that call and the call data to use.
    /// Waits until the consensus second is not before `expirySecond` to execute.
    /// @param to the address of the smart contract for the future call
    /// @param payer an account identifier of a `payer` for the scheduled transaction
    /// @param expirySecond an expiration time of the future call
    /// @param gasLimit a maximum limit to the amount of gas to use for future call
    /// @param value an amount of tinybar sent via this future contract call
    /// @param callData the smart contract function to call. This MUST contain The application binary interface (ABI)
    /// encoding of the function call per the Ethereum contract ABI standard, giving the function signature and
    /// arguments being passed to the function.
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    /// @return scheduleAddress The address of the newly created schedule transaction.
    function scheduleCallWithPayer(address to, address payer, uint256 expirySecond, uint256 gasLimit, uint64 value, bytes memory callData)
    internal returns (int64 responseCode, address scheduleAddress) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.scheduleCallWithPayer.selector, to, payer, expirySecond, gasLimit, value, callData));
        (responseCode, scheduleAddress) = success ? abi.decode(result, (int64, address)) : (int64(HederaResponseCodes.UNKNOWN), address(0));
    }

    /// Allows for the creation of a schedule transaction to schedule any contract call for a given smart contract
    /// address, with a payer for the scheduled transaction, expiration time, the gas limit for the future call,
    /// the value to send with that call and the call data to use.
    /// Executes as soon as the payer signs (unless consensus time is already past the `expirySecond`, of course).
    /// @param to the address of the smart contract for the future call
    /// @param payer an account identifier of a `payer` for the scheduled transaction
    /// @param expirySecond an expiration time of the future call
    /// @param gasLimit a maximum limit to the amount of gas to use for future call
    /// @param value an amount of tinybar sent via this future contract call
    /// @param callData the smart contract function to call. This MUST contain The application binary interface (ABI)
    /// encoding of the function call per the Ethereum contract ABI standard, giving the function signature and
    /// arguments being passed to the function.
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    /// @return scheduleAddress The address of the newly created schedule transaction.
    function executeCallOnPayerSignature(address to, address payer, uint256 expirySecond, uint256 gasLimit, uint64 value, bytes memory callData)
    internal returns (int64 responseCode, address scheduleAddress) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.executeCallOnPayerSignature.selector, to, payer, expirySecond, gasLimit, value, callData));
        (responseCode, scheduleAddress) = success ? abi.decode(result, (int64, address)) : (int64(HederaResponseCodes.UNKNOWN), address(0));
    }

    /// Delete the targeted schedule transaction.
    /// @param scheduleAddress the address of the schedule transaction.
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    function deleteSchedule(address scheduleAddress) internal returns (int64 responseCode) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.deleteSchedule.selector, scheduleAddress));
        responseCode = success ? abi.decode(result, (int64)) : HederaResponseCodes.UNKNOWN;
    }

    /// Allows to check if the given second still has capacity to schedule a contract call with the specified gas limit.
    /// @param expirySecond an expiration time of the future call
    /// @param gasLimit a maximum limit to the amount of gas to use for future call
    /// @return hasCapacity returns `true` iff the given second still has capacity to schedule a contract call
    /// with the specified gas limit.
    function hasScheduleCapacity(uint256 expirySecond, uint256 gasLimit) view internal returns (bool hasCapacity) {
        (bool success, bytes memory result) = scheduleSystemContractAddress.staticcall(
            abi.encodeWithSelector(IHederaScheduleService.hasScheduleCapacity.selector, expirySecond, gasLimit));
        hasCapacity = success ? abi.decode(result, (bool)) : false;
    }
}
