// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.22;

import { SafeCast } from "@openzeppelin/contracts/utils/math/SafeCast.sol";

import { ILayerZeroEndpointV2 } from "@layerzerolabs/lz-evm-oapp-v2/contracts/oapp/interfaces/IOAppCore.sol";
import { Origin } from "@layerzerolabs/lz-evm-oapp-v2/contracts/oapp/OApp.sol";
// Solidity does not support splitting import across multiple lines
// solhint-disable-next-line max-line-length
import { OFTLimit, OFTFeeDetail, OFTReceipt, SendParam, MessagingReceipt, MessagingFee, IOFT } from "@layerzerolabs/lz-evm-oapp-v2/contracts/oft/interfaces/IOFT.sol";
import { OFTComposeMsgCodec } from "@layerzerolabs/lz-evm-oapp-v2/contracts/oft/libs/OFTComposeMsgCodec.sol";

import { IStargate, Ticket } from "./interfaces/IStargate.sol";
import { IStargateFeeLib, FeeParams } from "./interfaces/IStargateFeeLib.sol";
import { ITokenMessaging, RideBusParams, TaxiParams } from "./interfaces/ITokenMessaging.sol";
import { ITokenMessagingHandler } from "./interfaces/ITokenMessagingHandler.sol";
import { ICreditMessagingHandler, Credit, TargetCredit } from "./interfaces/ICreditMessagingHandler.sol";
import { Path } from "./libs/Path.sol";
import { Transfer } from "./libs/Transfer.sol";

/// @title The base contract for StargateOFT, StargatePool, StargatePoolMigratable, and StargatePoolNative.
abstract contract StargateBase is Transfer, IStargate, ITokenMessagingHandler, ICreditMessagingHandler {
    using SafeCast for uint256;

    // Stargate status
    uint8 internal constant NOT_ENTERED = 1;
    uint8 internal constant ENTERED = 2;
    uint8 internal constant PAUSED = 3;

    /// @dev The token for the Pool or OFT.
    /// @dev address(0) indicates native coin, such as ETH.
    address public immutable override token;
    /// @dev The shared decimals (lowest common decimals between chains).
    uint8 public immutable override sharedDecimals;
    /// @dev The rate between local decimals and shared decimals.
    uint256 internal immutable convertRate;

    /// @dev The local LayerZero EndpointV2.
    ILayerZeroEndpointV2 public immutable endpoint;
    /// @dev The local LayerZero endpoint ID
    uint32 public immutable localEid;

    address internal feeLib;
    /// @dev The StargateBase status.  Options include 1. NOT_ENTERED 2. ENTERED and 3. PAUSED.
    uint8 public status = NOT_ENTERED;
    /// @dev The treasury accrued fees, stored in SD.
    uint64 public treasuryFee;

    address internal creditMessaging;
    address internal lzToken;
    address internal planner;
    address internal tokenMessaging;
    address internal treasurer;

    /// @dev Mapping of paths from this chain to other chains identified by their endpoint ID.
    mapping(uint32 eid => Path path) public paths;

    /// @dev A store for tokens that could not be delivered because _outflow() failed.
    /// @dev retryReceiveToken() can be called to retry the receive.
    mapping(bytes32 guid => mapping(uint8 index => bytes32 hash)) public unreceivedTokens;

    modifier onlyCaller(address _caller) {
        if (msg.sender != _caller) revert Stargate_Unauthorized();
        _;
    }

    modifier nonReentrantAndNotPaused() {
        // On the first call to nonReentrant, _status will be _NOT_ENTERED
        if (status != NOT_ENTERED) {
            if (status == ENTERED) revert Stargate_ReentrantCall();
            revert Stargate_Paused();
        }
        // Any calls to nonReentrant after this point will fail
        status = ENTERED;
        _;
        status = NOT_ENTERED;
    }

    error Stargate_ReentrantCall();
    error Stargate_InvalidTokenDecimals();
    error Stargate_Unauthorized();
    error Stargate_SlippageTooHigh();
    error Stargate_UnreceivedTokenNotFound();
    error Stargate_OutflowFailed();
    error Stargate_InvalidAmount();
    error Stargate_InsufficientFare();
    error Stargate_InvalidPath();
    error Stargate_LzTokenUnavailable();
    error Stargate_Paused();
    error Stargate_RecoverTokenUnsupported();

    event AddressConfigSet(AddressConfig config);
    event CreditsSent(uint32 dstEid, Credit[] credits);
    event CreditsReceived(uint32 srcEid, Credit[] credits);
    event UnreceivedTokenCached(
        bytes32 guid,
        uint8 index,
        uint32 srcEid,
        address receiver,
        uint256 amountLD,
        bytes composeMsg
    );
    event OFTPathSet(uint32 dstEid, bool oft);
    event PauseSet(bool paused);
    event PlannerFeeWithdrawn(uint256 amount);
    event TreasuryFeeAdded(uint64 amountSD);
    event TreasuryFeeWithdrawn(address to, uint64 amountSD);

    struct AddressConfig {
        address feeLib;
        address planner;
        address treasurer;
        address tokenMessaging;
        address creditMessaging;
        address lzToken;
    }

    /// @notice Create a new Stargate contract
    /// @dev Reverts with InvalidTokenDecimals if the token decimals are smaller than the shared decimals.
    /// @param _token The token for the pool or oft. If the token is address(0), it is the native coin
    /// @param _tokenDecimals The number of decimals for this tokens implementation on this chain
    /// @param _sharedDecimals The number of decimals shared between all implementations of the OFT
    /// @param _endpoint The LZ endpoint contract
    /// @param _owner The owner of this contract
    constructor(address _token, uint8 _tokenDecimals, uint8 _sharedDecimals, address _endpoint, address _owner) {
        token = _token;
        if (_tokenDecimals < _sharedDecimals) revert Stargate_InvalidTokenDecimals();
        convertRate = 10 ** (_tokenDecimals - _sharedDecimals);
        sharedDecimals = _sharedDecimals;

        endpoint = ILayerZeroEndpointV2(_endpoint);
        localEid = endpoint.eid();
        _transferOwnership(_owner);
    }

    // ---------------------------------- Only Owner ------------------------------------------

    /// @notice Configure the roles for this contract.
    /// @param _config An AddressConfig object containing the addresses for the different roles used by Stargate.
    function setAddressConfig(AddressConfig calldata _config) external onlyOwner {
        feeLib = _config.feeLib;
        planner = _config.planner;
        treasurer = _config.treasurer;
        tokenMessaging = _config.tokenMessaging;
        creditMessaging = _config.creditMessaging;
        lzToken = _config.lzToken;
        emit AddressConfigSet(_config);
    }

    /// @notice Sets a given Path as using OFT or resets it from OFT.
    /// @dev Set the path as OFT if the remote chain is using OFT.
    /// @dev When migrating from OFT to pool on remote chain (e.g. migrate USDC to circles), reset the path to non-OFT.
    /// @dev Reverts with InvalidPath if the destination chain is the same as local.
    /// @param _dstEid The destination chain endpoint ID
    /// @param _oft Whether to set or reset the path
    function setOFTPath(uint32 _dstEid, bool _oft) external onlyOwner {
        if (_dstEid == localEid) revert Stargate_InvalidPath();
        paths[_dstEid].setOFTPath(_oft);
        emit OFTPathSet(_dstEid, _oft);
    }

    // ---------------------------------- Only Treasurer ------------------------------------------

    /// @notice Withdraw from the accrued fees in the treasury.
    /// @param _to The destination account
    /// @param _amountSD The amount to withdraw in SD
    function withdrawTreasuryFee(address _to, uint64 _amountSD) external onlyCaller(treasurer) {
        treasuryFee -= _amountSD;
        _safeOutflow(_to, _sd2ld(_amountSD));
        emit TreasuryFeeWithdrawn(_to, _amountSD);
    }

    /// @notice Add tokens to the treasury, from the senders account.
    /// @dev Only used for increasing the overall budget for transaction rewards
    /// @dev The treasuryFee is essentially the reward pool.
    /// @dev Rewards are capped to the treasury amount, which limits exposure so
    /// @dev Stargate does not pay beyond what it's charged.
    /// @param _amountLD The amount to add in LD
    function addTreasuryFee(uint256 _amountLD) external payable onlyCaller(treasurer) {
        _assertMsgValue(_amountLD);
        uint64 amountSD = _inflow(msg.sender, _amountLD);
        treasuryFee += amountSD;
        emit TreasuryFeeAdded(amountSD);
    }

    /// @dev Recover tokens sent to this contract by mistake.
    /// @dev Only the treasurer can recover the token.
    /// @dev Reverts with Stargate_RecoverTokenUnsupported if the treasurer attempts to withdraw StargateBase.token().
    /// @param _token the token to recover. if 0x0 then it is native token
    /// @param _to the address to send the token to
    /// @param _amount the amount to send
    function recoverToken(
        address _token,
        address _to,
        uint256 _amount
    ) public virtual nonReentrantAndNotPaused onlyCaller(treasurer) returns (uint256) {
        /// @dev Excess native is considered planner accumulated fees.
        if (_token == address(0)) revert Stargate_RecoverTokenUnsupported();
        Transfer.safeTransfer(_token, _to, _amount, false);
        return _amount;
    }

    // ---------------------------------- Only Planner ------------------------------------------

    /// @notice Pause or unpause a Stargate
    /// @dev Be careful with this call, as it unsets the re-entry guard.
    /// @param _paused Whether to pause or unpause the stargate
    function setPause(bool _paused) external onlyCaller(planner) {
        if (status == ENTERED) revert Stargate_ReentrantCall();
        status = _paused ? PAUSED : NOT_ENTERED;
        emit PauseSet(_paused);
    }

    function _plannerFee() internal view virtual returns (uint256) {
        return address(this).balance;
    }

    function plannerFee() external view returns (uint256 available) {
        available = _plannerFee();
    }

    /// @notice Withdraw planner fees accumulated in StargateBase.
    /// @dev The planner fee is accumulated in StargateBase to avoid the cost of passing msg.value to TokenMessaging.
    function withdrawPlannerFee() external virtual onlyCaller(planner) {
        uint256 available = _plannerFee();
        Transfer.safeTransferNative(msg.sender, available, false);
        emit PlannerFeeWithdrawn(available);
    }

    // ------------------------------- Public Functions ---------------------------------------

    /// @notice Send tokens through the Stargate
    /// @dev Emits OFTSent when the send is successful
    /// @param _sendParam The SendParam object detailing the transaction
    /// @param _fee The MessagingFee object describing the fee to pay
    /// @param _refundAddress The address to refund any LZ fees paid in excess
    /// @return msgReceipt The receipt proving the message was sent
    /// @return oftReceipt The receipt proving the OFT swap
    function send(
        SendParam calldata _sendParam,
        MessagingFee calldata _fee,
        address _refundAddress
    ) external payable override returns (MessagingReceipt memory msgReceipt, OFTReceipt memory oftReceipt) {
        (msgReceipt, oftReceipt, ) = sendToken(_sendParam, _fee, _refundAddress);
    }

    function sendToken(
        SendParam calldata _sendParam,
        MessagingFee calldata _fee,
        address _refundAddress
    )
    public
    payable
    override
    nonReentrantAndNotPaused
    returns (MessagingReceipt memory msgReceipt, OFTReceipt memory oftReceipt, Ticket memory ticket)
    {
        // step 1: assets inflows and apply the fee to the input amount
        (bool isTaxi, uint64 amountInSD, uint64 amountOutSD) = _inflowAndCharge(_sendParam);

        // step 2: generate the oft receipt
        oftReceipt = OFTReceipt(_sd2ld(amountInSD), _sd2ld(amountOutSD));

        // step 3: assert the messaging fee
        MessagingFee memory messagingFee = _assertMessagingFee(_fee, oftReceipt.amountSentLD);

        // step 4: send the token depending on the mode Taxi or Bus
        if (isTaxi) {
            msgReceipt = _taxi(_sendParam, messagingFee, amountOutSD, _refundAddress);
        } else {
            (msgReceipt, ticket) = _rideBus(_sendParam, messagingFee, amountOutSD, _refundAddress);
        }

        emit OFTSent(
            msgReceipt.guid,
            _sendParam.dstEid,
            msg.sender,
            oftReceipt.amountSentLD,
            oftReceipt.amountReceivedLD
        );
    }

    /// @notice Retry receiving a token that initially failed.
    /// @dev The message has been delivered by the Messaging layer, so it is ok for anyone to retry.
    /// @dev try to receive the token if the previous attempt failed in lzReceive
    /// @dev Reverts with UnreceivedTokenNotFound if the message is not found in the cache
    /// @dev Emits OFTReceived if the receive succeeds
    /// @param _guid The global unique ID for the message that failed
    /// @param _index The index of the message that failed
    /// @param _srcEid The source endpoint ID for the message that failed
    /// @param _receiver The account receiver for the message that failed
    /// @param _amountLD The amount of tokens in LD to transfer to the account
    /// @param _composeMsg The bytes representing the compose message in the message that failed
    function retryReceiveToken(
        bytes32 _guid,
        uint8 _index,
        uint32 _srcEid,
        address _receiver,
        uint256 _amountLD,
        bytes calldata _composeMsg
    ) external nonReentrantAndNotPaused {
        if (unreceivedTokens[_guid][_index] != keccak256(abi.encodePacked(_srcEid, _receiver, _amountLD, _composeMsg)))
            revert Stargate_UnreceivedTokenNotFound();
        delete unreceivedTokens[_guid][_index];

        _safeOutflow(_receiver, _amountLD);
        _postOutflow(_ld2sd(_amountLD));
        if (_composeMsg.length > 0) {
            endpoint.sendCompose(_receiver, _guid, 0, _composeMsg);
        }
        emit OFTReceived(_guid, _srcEid, _receiver, _amountLD);
    }

    // ------------------------------- Only Messaging ---------------------------------------

    /// @notice Entrypoint for receiving tokens
    /// @dev Emits OFTReceived when the OFT token is correctly received
    /// @dev Emits UnreceivedTokenCached when the OFT token is not received
    /// @param _origin The Origin struct describing the origin, useful for composing
    /// @param _guid The global unique ID for this message, useful for composing
    function receiveTokenBus(
        Origin calldata _origin,
        bytes32 _guid,
        uint8 _seatNumber,
        address _receiver,
        uint64 _amountSD
    ) external nonReentrantAndNotPaused onlyCaller(tokenMessaging) {
        uint256 amountLD = _sd2ld(_amountSD);

        bool success = _outflow(_receiver, amountLD);
        if (success) {
            _postOutflow(_amountSD);
            emit OFTReceived(_guid, _origin.srcEid, _receiver, amountLD);
        } else {
            /**
             * @dev The busRide mode does not support composeMsg in any form. Thus we hardcode it to ""
             */
            unreceivedTokens[_guid][_seatNumber] = keccak256(abi.encodePacked(_origin.srcEid, _receiver, amountLD, ""));
            emit UnreceivedTokenCached(_guid, _seatNumber, _origin.srcEid, _receiver, amountLD, "");
        }
    }

    // taxi mode
    function receiveTokenTaxi(
        Origin calldata _origin,
        bytes32 _guid,
        address _receiver,
        uint64 _amountSD,
        bytes calldata _composeMsg
    ) external nonReentrantAndNotPaused onlyCaller(tokenMessaging) {
        uint256 amountLD = _sd2ld(_amountSD);
        bool hasCompose = _composeMsg.length > 0;
        bytes memory composeMsg;
        if (hasCompose) {
            composeMsg = OFTComposeMsgCodec.encode(_origin.nonce, _origin.srcEid, amountLD, _composeMsg);
        }

        bool success = _outflow(_receiver, amountLD);
        if (success) {
            _postOutflow(_amountSD);
            // send the composeMsg to the endpoint
            if (hasCompose) {
                endpoint.sendCompose(_receiver, _guid, 0, composeMsg);
            }
            emit OFTReceived(_guid, _origin.srcEid, _receiver, amountLD);
        } else {
            /**
             * @dev We use the '0' index to represent the seat number. This is because for a type 'taxi' msg,
             *      there is only ever one corresponding receiveTokenTaxi function per GUID.
             */
            unreceivedTokens[_guid][0] = keccak256(abi.encodePacked(_origin.srcEid, _receiver, amountLD, composeMsg));
            emit UnreceivedTokenCached(_guid, 0, _origin.srcEid, _receiver, amountLD, composeMsg);
        }
    }

    function sendCredits(
        uint32 _dstEid,
        TargetCredit[] calldata _credits
    ) external nonReentrantAndNotPaused onlyCaller(creditMessaging) returns (Credit[] memory) {
        Credit[] memory credits = new Credit[](_credits.length);
        uint256 index = 0;
        for (uint256 i = 0; i < _credits.length; i++) {
            TargetCredit calldata c = _credits[i];
            uint64 decreased = paths[c.srcEid].tryDecreaseCredit(c.amount, c.minAmount);
            if (decreased > 0) credits[index++] = Credit(c.srcEid, decreased);
        }
        // resize the array to the actual number of credits
        assembly {
            mstore(credits, index)
        }
        emit CreditsSent(_dstEid, credits);
        return credits;
    }

    /// @notice Entrypoint for receiving credits into paths
    /// @dev Emits CreditsReceived when credits are received
    /// @param _srcEid The endpoint ID of the source of credits
    /// @param _credits An array indicating to which paths and how much credits to add
    function receiveCredits(
        uint32 _srcEid,
        Credit[] calldata _credits
    ) external nonReentrantAndNotPaused onlyCaller(creditMessaging) {
        for (uint256 i = 0; i < _credits.length; i++) {
            Credit calldata c = _credits[i];
            paths[c.srcEid].increaseCredit(c.amount);
        }
        emit CreditsReceived(_srcEid, _credits);
    }

    // ---------------------------------- View Functions ------------------------------------------

    /// @notice Provides a quote for sending OFT to another chain.
    /// @dev Implements the IOFT interface
    /// @param _sendParam The parameters for the send operation
    /// @return limit The information on OFT transfer limits
    /// @return oftFeeDetails The details of OFT transaction cost or reward
    /// @return receipt The OFT receipt information, indicating how many tokens would be sent and received
    function quoteOFT(
        SendParam calldata _sendParam
    ) external view returns (OFTLimit memory limit, OFTFeeDetail[] memory oftFeeDetails, OFTReceipt memory receipt) {
        // cap the transfer to the paths limit
        limit = OFTLimit(_sd2ld(1), _sd2ld(paths[_sendParam.dstEid].credit));

        // get the expected amount in the destination chain from FeeLib
        uint64 amountInSD = _ld2sd(_sendParam.amountLD > limit.maxAmountLD ? limit.maxAmountLD : _sendParam.amountLD);
        FeeParams memory params = _buildFeeParams(_sendParam.dstEid, amountInSD, _isTaxiMode(_sendParam.oftCmd));
        uint64 amountOutSD = IStargateFeeLib(feeLib).applyFeeView(params);

        // fill in the FeeDetails if there is a fee or reward
        if (amountOutSD != amountInSD) {
            oftFeeDetails = new OFTFeeDetail[](1);
            if (amountOutSD < amountInSD) {
                // fee
                oftFeeDetails[0] = OFTFeeDetail(-1 * _sd2ld(amountInSD - amountOutSD).toInt256(), "protocol fee");
            } else if (amountOutSD > amountInSD) {
                // reward
                uint64 reward = amountOutSD - amountInSD;
                (amountOutSD, reward) = _capReward(amountOutSD, reward);
                if (amountOutSD == amountInSD) {
                    // hide the Fee detail if the reward is capped to 0
                    oftFeeDetails = new OFTFeeDetail[](0);
                } else {
                    oftFeeDetails[0] = OFTFeeDetail(_sd2ld(reward).toInt256(), "reward");
                }
            }
        }

        receipt = OFTReceipt(_sd2ld(amountInSD), _sd2ld(amountOutSD));
    }

    /// @notice Provides a quote for the send() operation.
    /// @dev Implements the IOFT interface.
    /// @dev Reverts with InvalidAmount if send mode is drive but value is specified.
    /// @param _sendParam The parameters for the send() operation
    /// @param _payInLzToken Flag indicating whether the caller is paying in the LZ token
    /// @return fee The calculated LayerZero messaging fee from the send() operation
    /// @dev MessagingFee: LayerZero message fee
    ///   - nativeFee: The native fee.
    ///   - lzTokenFee: The LZ token fee.
    function quoteSend(
        SendParam calldata _sendParam,
        bool _payInLzToken
    ) external view returns (MessagingFee memory fee) {
        uint64 amountSD = _ld2sd(_sendParam.amountLD);
        if (amountSD == 0) revert Stargate_InvalidAmount();

        bool isTaxi = _isTaxiMode(_sendParam.oftCmd);
        if (isTaxi) {
            fee = ITokenMessaging(tokenMessaging).quoteTaxi(
                TaxiParams({
                    sender: msg.sender,
                    dstEid: _sendParam.dstEid,
                    receiver: _sendParam.to,
                    amountSD: amountSD,
                    composeMsg: _sendParam.composeMsg,
                    extraOptions: _sendParam.extraOptions
                }),
                _payInLzToken
            );
        } else {
            bool nativeDrop = _sendParam.extraOptions.length > 0;
            fee = ITokenMessaging(tokenMessaging).quoteRideBus(_sendParam.dstEid, nativeDrop);
        }
    }

    /// @notice Returns the current roles configured.
    /// @return An AddressConfig struct containing the current configuration
    function getAddressConfig() external view returns (AddressConfig memory) {
        return
            AddressConfig({
            feeLib: feeLib,
            planner: planner,
            treasurer: treasurer,
            tokenMessaging: tokenMessaging,
            creditMessaging: creditMessaging,
            lzToken: lzToken
        });
    }

    /// @notice Get the OFT version information
    /// @dev Implements the IOFT interface.
    /// @dev 0 version means the message encoding is not compatible with the default OFT.
    /// @return interfaceId The ERC165 interface ID for this contract
    /// @return version The cross-chain compatible message encoding version.
    function oftVersion() external pure override returns (bytes4 interfaceId, uint64 version) {
        return (type(IOFT).interfaceId, 0);
    }

    /// @notice Indicates whether the OFT contract requires approval of the 'token()' to send.
    /// @dev Implements the IOFT interface.
    /// @return Whether approval of the underlying token implementation is required
    function approvalRequired() external pure override returns (bool) {
        return true;
    }

    // ---------------------------------- Internal Functions ------------------------------------------

    /// @notice Ingest value into the contract and charge the Stargate fee.
    /// @dev This is triggered when value is transferred from an account into Stargate to execute a swap.
    /// @param _sendParam A SendParam struct containing the swap information
    function _inflowAndCharge(
        SendParam calldata _sendParam
    ) internal returns (bool isTaxi, uint64 amountInSD, uint64 amountOutSD) {
        isTaxi = _isTaxiMode(_sendParam.oftCmd);
        amountInSD = _inflow(msg.sender, _sendParam.amountLD);

        FeeParams memory feeParams = _buildFeeParams(_sendParam.dstEid, amountInSD, isTaxi);

        amountOutSD = _chargeFee(feeParams, _ld2sd(_sendParam.minAmountLD));

        paths[_sendParam.dstEid].decreaseCredit(amountOutSD); // remove the credit from the path
        _postInflow(amountOutSD); // post inflow actions with the amount deducted by the fee
    }

    /// @notice Consult the FeeLib the fee/reward for sending this token
    /// @dev Reverts with SlippageTooHigh when the slippage amount sent would be below the desired minimum or zero.
    /// @return amountOutSD The actual amount that would be sent after applying fees/rewards
    function _chargeFee(FeeParams memory _feeParams, uint64 _minAmountOutSD) internal returns (uint64 amountOutSD) {
        // get the output amount from the fee library
        amountOutSD = IStargateFeeLib(feeLib).applyFee(_feeParams);

        uint64 amountInSD = _feeParams.amountInSD;
        if (amountOutSD < amountInSD) {
            // fee
            treasuryFee += amountInSD - amountOutSD;
        } else if (amountOutSD > amountInSD) {
            // reward
            uint64 reward = amountOutSD - amountInSD;
            (amountOutSD, reward) = _capReward(amountOutSD, reward);
            if (reward > 0) treasuryFee -= reward;
        }

        if (amountOutSD < _minAmountOutSD || amountOutSD == 0) revert Stargate_SlippageTooHigh(); // 0 not allowed
    }

    function _taxi(
        SendParam calldata _sendParam,
        MessagingFee memory _messagingFee,
        uint64 _amountSD,
        address _refundAddress
    ) internal returns (MessagingReceipt memory receipt) {
        if (_messagingFee.lzTokenFee > 0) _payLzToken(_messagingFee.lzTokenFee); // handle lz token fee

        receipt = ITokenMessaging(tokenMessaging).taxi{ value: _messagingFee.nativeFee }(
            TaxiParams({
                sender: msg.sender,
                dstEid: _sendParam.dstEid,
                receiver: _sendParam.to,
                amountSD: _amountSD,
                composeMsg: _sendParam.composeMsg,
                extraOptions: _sendParam.extraOptions
            }),
            _messagingFee,
            _refundAddress
        );
    }

    function _rideBus(
        SendParam calldata _sendParam,
        MessagingFee memory _messagingFee,
        uint64 _amountSD,
        address _refundAddress
    ) internal virtual returns (MessagingReceipt memory receipt, Ticket memory ticket) {
        if (_messagingFee.lzTokenFee > 0) revert Stargate_LzTokenUnavailable();

        (receipt, ticket) = ITokenMessaging(tokenMessaging).rideBus(
            RideBusParams({
                sender: msg.sender,
                dstEid: _sendParam.dstEid,
                receiver: _sendParam.to,
                amountSD: _amountSD,
                nativeDrop: _sendParam.extraOptions.length > 0
            })
        );

        uint256 busFare = receipt.fee.nativeFee;
        uint256 providedFare = _messagingFee.nativeFee;

        // assert sufficient nativeFee was provided to cover the fare
        if (busFare == providedFare) {
            // return; Do nothing in this case
        } else if (providedFare > busFare) {
            uint256 refund;
            unchecked {
                refund = providedFare - busFare;
            }
            Transfer.transferNative(_refundAddress, refund, false); // no gas limit to refund
        } else {
            revert Stargate_InsufficientFare();
        }
    }

    /// @notice Pay the LZ fee in LZ tokens.
    /// @dev Reverts with LzTokenUnavailable if the LZ token OFT has not been set.
    /// @param _lzTokenFee The fee to pay in LZ tokens
    function _payLzToken(uint256 _lzTokenFee) internal {
        address lzTkn = lzToken;
        if (lzTkn == address(0)) revert Stargate_LzTokenUnavailable();
        Transfer.safeTransferTokenFrom(lzTkn, msg.sender, address(endpoint), _lzTokenFee);
    }

    /// @notice Translate an amount in SD to LD
    /// @dev Since SD <= LD by definition, convertRate >= 1, so there is no rounding errors in this function.
    /// @param _amountSD The amount in SD
    /// @return amountLD The same value expressed in LD
    function _sd2ld(uint64 _amountSD) internal view returns (uint256 amountLD) {
        unchecked {
            amountLD = _amountSD * convertRate;
        }
    }

    /// @notice Translate an value in LD to SD
    /// @dev Since SD <= LD by definition, convertRate >= 1, so there might be rounding during the cast.
    /// @param _amountLD The value in LD
    /// @return amountSD The same value expressed in SD
    function _ld2sd(uint256 _amountLD) internal view returns (uint64 amountSD) {
        unchecked {
            amountSD = SafeCast.toUint64(_amountLD / convertRate);
        }
    }

    /// @dev if _cmd is empty, Taxi mode. Otherwise, Bus mode
    function _isTaxiMode(bytes calldata _oftCmd) internal pure returns (bool) {
        return _oftCmd.length == 0;
    }

    // ---------------------------------- Virtual Functions ------------------------------------------

    /// @notice Limits the reward awarded when withdrawing value.
    /// @param _amountOutSD The amount of expected on the destination chain in SD
    /// @param _reward The initial calculated reward by FeeLib
    /// @return newAmountOutSD The actual amount to be delivered on the destination chain
    /// @return newReward The actual reward after applying any caps
    function _capReward(
        uint64 _amountOutSD,
        uint64 _reward
    ) internal view virtual returns (uint64 newAmountOutSD, uint64 newReward);

    /// @notice Hook called when there is ingress of value into the contract.
    /// @param _from The account from which to obtain the value
    /// @param _amountLD The amount of tokens to get from the account in LD
    /// @return amountSD The actual amount of tokens in SD that got into the Stargate
    function _inflow(address _from, uint256 _amountLD) internal virtual returns (uint64 amountSD);

    /// @notice Hook called when there is egress of value out of the contract.
    /// @return success Whether the outflow was successful
    function _outflow(address _to, uint256 _amountLD) internal virtual returns (bool success);

    /// @notice Hook called when there is egress of value out of the contract.
    /// @dev Reverts with OutflowFailed when the outflow hook fails
    function _safeOutflow(address _to, uint256 _amountLD) internal virtual {
        bool success = _outflow(_to, _amountLD);
        if (!success) revert Stargate_OutflowFailed();
    }

    /// @notice Ensure that the value passed through the message equals the native fee
    /// @dev the native fee should be the same as msg value by default
    /// @dev Reverts with InvalidAmount if the native fee does not match the value passed.
    /// @param _fee The MessagingFee object containing the expected fee
    /// @return The messaging fee object
    function _assertMessagingFee(
        MessagingFee memory _fee,
        uint256 /*_amountInLD*/
    ) internal view virtual returns (MessagingFee memory) {
        if (_fee.nativeFee != msg.value) revert Stargate_InvalidAmount();
        return _fee;
    }

    /// @notice Ensure the msg.value is as expected.
    /// @dev Override this contract to provide a specific validation.
    /// @dev This implementation will revert if value is passed, because we do not expect value except for
    /// @dev the native token when adding to the treasury.
    /// @dev Reverts with InvalidAmount if msg.value > 0
    function _assertMsgValue(uint256 /*_amountLD*/) internal view virtual {
        if (msg.value > 0) revert Stargate_InvalidAmount();
    }

    /// @dev Build the FeeParams object for the FeeLib
    /// @param _dstEid The destination endpoint ID
    /// @param _amountInSD The amount to send in SD
    /// @param _isTaxi Whether this send is riding the bus or taxing
    function _buildFeeParams(
        uint32 _dstEid,
        uint64 _amountInSD,
        bool _isTaxi
    ) internal view virtual returns (FeeParams memory);

    /// @notice Hook called after the inflow of value into the contract by sendToken().
    /// Function meant to be overridden
    // solhint-disable-next-line no-empty-blocks
    function _postInflow(uint64 _amountSD) internal virtual {}

    /// @notice Hook called after the outflow of value out of the contract by receiveToken().
    /// Function meant to be overridden
    // solhint-disable-next-line no-empty-blocks
    function _postOutflow(uint64 _amountSD) internal virtual {}
}