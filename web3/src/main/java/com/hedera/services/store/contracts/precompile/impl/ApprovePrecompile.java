// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BOOL;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT_BOOL_PAIR;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_APPROVE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.NonZeroShardAndRealmUtils.getDefaultTokenIDInstance;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DELETE_NFT_APPROVE;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.ApproveDecodedNftInfo;
import com.hedera.services.store.contracts.precompile.codec.ApproveParams;
import com.hedera.services.store.contracts.precompile.codec.ApproveResult;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.DeleteAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

/**
 * This class is a modified copy of AssociatePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Using {@link Id} instead of EntityId as types for the owner and operator
 *  5. All the necessary fields used in run method are extracted from the txn body
 *  6. Added getLogForNftAllowanceRevocation because we are not
 *     setting the spender address in the txn body when revoking nft allowance
 *  7. All the necessary fields used in body method are extracted from ApproveParams
 *  8. Added {@link ApproveDecodedNftInfo} and decodeTokenIdAndSerialNum method in order to access tokenID from HtsPrecompiledContract and
 *     avoid passing Store unnecessarily
 *  9. Added check about added logic about handling non-zero shard and realm values
 */
public class ApprovePrecompile extends AbstractWritePrecompile {
    private static final Function ERC_TOKEN_APPROVE_FUNCTION = new Function("approve(address,uint256)", BOOL);
    private static final Bytes ERC_TOKEN_APPROVE_SELECTOR = Bytes.wrap(ERC_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TOKEN_APPROVE_DECODER = TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_TOKEN_APPROVE_FUNCTION =
            new Function("approve(address,address,uint256)", INT_BOOL_PAIR);
    private static final Bytes HAPI_TOKEN_APPROVE_SELECTOR = Bytes.wrap(HAPI_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_TOKEN_APPROVE_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_APPROVE_NFT_FUNCTION = new Function("approveNFT(address,address,uint256)", INT);
    private static final Bytes HAPI_APPROVE_NFT_SELECTOR = Bytes.wrap(HAPI_APPROVE_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_APPROVE_NFT_DECODER = TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);

    private final EncodingFacade encoder;
    private final ApproveAllowanceLogic approveAllowanceLogic;
    private final DeleteAllowanceLogic deleteAllowanceLogic;
    private final ApproveAllowanceChecks approveAllowanceChecks;
    private final DeleteAllowanceChecks deleteAllowanceChecks;

    public ApprovePrecompile(
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final ApproveAllowanceLogic approveAllowanceLogic,
            final DeleteAllowanceLogic deleteAllowanceLogic,
            final ApproveAllowanceChecks approveAllowanceChecks,
            final DeleteAllowanceChecks deleteAllowanceChecks) {
        super(pricingUtils, syntheticTxnFactory);
        this.encoder = encoder;
        this.approveAllowanceLogic = approveAllowanceLogic;
        this.deleteAllowanceLogic = deleteAllowanceLogic;
        this.approveAllowanceChecks = approveAllowanceChecks;
        this.deleteAllowanceChecks = deleteAllowanceChecks;
    }

    @Override
    public Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        Address tokenAddress;
        Address senderAddress;
        Id nftOwnerId;
        boolean isFungible;
        Builder transactionBody;

        if (bodyParams instanceof ApproveParams approveParams) {
            isFungible = approveParams.isFungible();
            nftOwnerId = approveParams.ownerId();
            tokenAddress = approveParams.tokenAddress();
            senderAddress = approveParams.senderAddress();
        } else {
            throw new InvalidTransactionException("Invalid body parameters", FAIL_INVALID, true);
        }

        final var nestedInput = tokenAddress.equals(Address.ZERO) ? input : input.slice(24);
        final var operatorId = Id.fromGrpcAccount(accountIdFromEvmAddress(senderAddress));
        final var approveOp = decodeTokenApprove(
                nestedInput,
                EntityIdUtils.tokenIdFromEvmAddress(tokenAddress.toArrayUnsafe()),
                isFungible,
                aliasResolver);

        if (approveOp.isFungible()) {
            transactionBody = syntheticTxnFactory.createFungibleApproval(approveOp, operatorId);
        } else {
            final var nominalOwnerId = nftOwnerId != null ? nftOwnerId : Id.DEFAULT;
            // Per the ERC-721 spec, "The zero address indicates there is no approved address"; so
            // translate this approveAllowance into a deleteAllowance
            if (isNftApprovalRevocation(approveOp)) {
                transactionBody = syntheticTxnFactory.createDeleteAllowance(approveOp, nominalOwnerId);
            } else {
                transactionBody = syntheticTxnFactory.createNonfungibleApproval(approveOp, nominalOwnerId, operatorId);
            }
        }

        return transactionBody;
    }

    @SuppressWarnings("java:S3776")
    @Override
    public RunResult run(final MessageFrame frame, TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var store = updater.getStore();
        final var senderAddress = unalias(frame.getSenderAddress(), updater);

        // fields needed to be extracted from transactionBody
        boolean isFungible;
        Id nftOwnerId = Id.DEFAULT;
        Id operatorId = Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress));
        Address spender = Address.ZERO;
        TokenID tokenId;
        long amount = 0;
        long serialNumber = 0;

        // get bodies
        final var approveAllowanceBody = transactionBody.getCryptoApproveAllowance();
        final var deleteAllowanceBody = transactionBody.getCryptoDeleteAllowance();
        final var isNftApprovalRevocation =
                !deleteAllowanceBody.equals(deleteAllowanceBody.getDefaultInstanceForType());
        isFungible = !approveAllowanceBody.getTokenAllowancesList().isEmpty();

        // extract needed fields from the transactionBody
        if (isNftApprovalRevocation) { // when revoking nft allowance
            final var deleteNftAllowanceBody = deleteAllowanceBody.getNftAllowances(0);
            nftOwnerId = Id.fromGrpcAccount(deleteNftAllowanceBody.getOwner());
            tokenId = deleteNftAllowanceBody.getTokenId();
            serialNumber = deleteNftAllowanceBody.getSerialNumbers(0);

        } else if (isFungible) { // when setting allowance for fungible token
            final var tokenAllowances = approveAllowanceBody.getTokenAllowances(0);
            spender = EntityIdUtils.asTypedEvmAddress(tokenAllowances.getSpender());
            tokenId = tokenAllowances.getTokenId();
            amount = tokenAllowances.getAmount();
        } else { // when setting allowance for non-fungible token
            final var nftAllowances = approveAllowanceBody.getNftAllowances(0);
            nftOwnerId = nftAllowances
                            .getDelegatingSpender()
                            .getDefaultInstanceForType()
                            .equals(nftAllowances.getDelegatingSpender())
                    ? Id.fromGrpcAccount(nftAllowances.getOwner())
                    : Id.fromGrpcAccount(nftAllowances.getDelegatingSpender());
            spender = EntityIdUtils.asTypedEvmAddress(nftAllowances.getSpender());
            tokenId = nftAllowances.getTokenId();
            serialNumber = nftAllowances.getSerialNumbers(0);
        }

        Objects.requireNonNull(operatorId);
        //  Per the ERC-721 spec, "Throws unless `msg.sender` is the current NFT owner, or
        //  an authorized operator of the current owner"
        if (!isFungible) {
            final var isApproved = operatorId.equals(nftOwnerId)
                    || store.hasApprovedForAll(nftOwnerId.asEvmAddress(), operatorId.asGrpcAccount(), tokenId);
            validateTrueOrRevert(isApproved, SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
        }

        final var payerAccount = store.getAccount(operatorId.asEvmAddress(), OnMissing.THROW);
        if (isNftApprovalRevocation) {
            final var revocationOp = transactionBody.getCryptoDeleteAllowance();
            final var revocationWrapper = revocationOp.getNftAllowancesList();
            final var status = deleteAllowanceChecks.deleteAllowancesValidation(revocationWrapper, payerAccount, store);
            validateTrueOrRevert(status == OK, status);
            deleteAllowanceLogic.deleteAllowance(
                    store, new ArrayList<>(), revocationWrapper, operatorId.asGrpcAccount());
        } else {
            final var status = approveAllowanceChecks.allowancesValidation(
                    transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                    transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                    transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                    payerAccount,
                    store);
            validateTrueOrRevert(status == OK, status);
            try {
                approveAllowanceLogic.approveAllowance(
                        store,
                        new TreeMap<>(),
                        new TreeMap<>(),
                        transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                        operatorId.asGrpcAccount());
            } catch (final InvalidTransactionException e) {
                throw new InvalidTransactionException(e.getResponseCode(), true);
            }
        }

        final var tokenAddress = asTypedEvmAddress(tokenId);
        if (isFungible) {
            frame.addLog(getLogForFungibleAdjustAllowance(tokenAddress, senderAddress, spender, amount, updater));
        } else if (!isNftApprovalRevocation) {
            frame.addLog(getLogForNftAdjustAllowance(tokenAddress, senderAddress, spender, serialNumber, updater));
        } else {
            frame.addLog(getLogForNftAllowanceRevocation(tokenAddress, senderAddress, serialNumber, updater));
        }
        final int functionId = frame.getInputData().getInt(0);
        return new ApproveResult(tokenId, isFungible, functionId == ABI_ID_REDIRECT_FOR_TOKEN);
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        final var deleteAllowanceBody = transactionBody.getCryptoDeleteAllowance();
        final var isNftApprovalRevocation = deleteAllowanceBody.isInitialized();
        if (isNftApprovalRevocation) {
            return pricingUtils.getMinimumPriceInTinybars(DELETE_NFT_APPROVE, consensusTime);
        } else {
            return pricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
        }
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var approveResult = (ApproveResult) runResult;
        if (approveResult.isErcOperaion()) {
            return encoder.encodeApprove(true);
        }
        if (approveResult.isFungible()) {
            return encoder.encodeApprove(SUCCESS.getNumber(), true);
        } else {
            return encoder.encodeApproveNFT(SUCCESS.getNumber());
        }
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_APPROVE, ABI_ID_APPROVE_NFT, ABI_ID_ERC_APPROVE);
    }

    public static ApproveWrapper decodeTokenApprove(
            final Bytes input,
            final TokenID impliedTokenId,
            final boolean isFungible,
            final UnaryOperator<byte[]> aliasResolver) {

        final var offset = impliedTokenId.equals(getDefaultTokenIDInstance()) ? 1 : 0;
        final Tuple decodedArguments;
        final TokenID tokenId;

        if (offset == 0) {
            decodedArguments = decodeFunctionCall(input, ERC_TOKEN_APPROVE_SELECTOR, ERC_TOKEN_APPROVE_DECODER);
            tokenId = impliedTokenId;
        } else if (isFungible) {
            decodedArguments = decodeFunctionCall(input, HAPI_TOKEN_APPROVE_SELECTOR, HAPI_TOKEN_APPROVE_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        } else {
            decodedArguments = decodeFunctionCall(input, HAPI_APPROVE_NFT_SELECTOR, HAPI_APPROVE_NFT_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        }
        final var spender = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);

        if (isFungible) {
            final var amount = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, amount, BigInteger.ZERO, true, offset == 0);
        } else {
            final var serialNumber = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, BigInteger.ZERO, serialNumber, false, offset == 0);
        }
    }

    public static ApproveDecodedNftInfo decodeTokenIdAndSerialNum(final Bytes input, final TokenID impliedTokenId) {

        final var offset = impliedTokenId.equals(getDefaultTokenIDInstance()) ? 1 : 0;
        final Tuple decodedArguments;
        final TokenID tokenId;

        if (offset == 0) {
            decodedArguments = decodeFunctionCall(input, ERC_TOKEN_APPROVE_SELECTOR, ERC_TOKEN_APPROVE_DECODER);
            tokenId = impliedTokenId;
        } else {
            decodedArguments = decodeFunctionCall(input, HAPI_APPROVE_NFT_SELECTOR, HAPI_APPROVE_NFT_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        }
        final var serialNumber = (BigInteger) decodedArguments.get(offset + 1);
        return new ApproveDecodedNftInfo(tokenId, serialNumber);
    }

    private boolean isNftApprovalRevocation(final ApproveWrapper approveOp) {
        return Objects.requireNonNull(approveOp, "`body` method should be called before `isNftApprovalRevocation`")
                        .spender()
                        .getAccountNum()
                == 0;
    }

    private Log getLogForFungibleAdjustAllowance(
            final Address logger,
            final Address senderAddress,
            final Address spenderAddress,
            final long amount,
            final HederaEvmStackedWorldStateUpdater updater) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(updater.priorityAddress(senderAddress))
                .forIndexedArgument(updater.priorityAddress(spenderAddress))
                .forDataItem(amount)
                .build();
    }

    private Log getLogForNftAdjustAllowance(
            final Address logger,
            final Address senderAddress,
            final Address spenderAddress,
            final long serialNumber,
            final HederaEvmStackedWorldStateUpdater updater) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(updater.priorityAddress(senderAddress))
                .forIndexedArgument(updater.priorityAddress(spenderAddress))
                .forIndexedArgument(serialNumber)
                .build();
    }

    private Log getLogForNftAllowanceRevocation(
            final Address logger,
            final Address senderAddress,
            final long serialNumber,
            final HederaEvmStackedWorldStateUpdater updater) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(updater.priorityAddress(senderAddress))
                .forIndexedArgument(AccountID.getDefaultInstance())
                .forIndexedArgument(serialNumber)
                .build();
    }
}
