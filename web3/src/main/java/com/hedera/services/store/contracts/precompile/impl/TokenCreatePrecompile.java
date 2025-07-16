// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FIXED_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FIXED_FEE_V2;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE_V2;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE_V2;
import static com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.FIXED_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.FRACTIONAL_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V2;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V3;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.ROYALTY_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;
import static com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType.INVALID_KEY;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.CreateParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateResult;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.txn.token.CreateLogic;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TokenCreatePrecompile extends AbstractWritePrecompile {
    private static final String CREATE_FUNGIBLE_TOKEN_STRING = "createFungibleToken(";
    private static final String CREATE_NON_FUNGIBLE_TOKEN_STRING = "createNonFungibleToken(";
    private static final String CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING = "createFungibleTokenWithCustomFees(";
    private static final String CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING = "createNonFungibleTokenWithCustomFees(";
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION =
            new Function(CREATE_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT + ",uint256,uint256)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR = Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_FUNGIBLE_DECODER =
            TypeFactory.create("(" + HEDERA_TOKEN_STRUCT_DECODER + ",uint256,uint256)");
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION_V2 =
            new Function(CREATE_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V2 + ",uint64,uint32)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION_V2.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION_V3 =
            new Function(CREATE_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V3 + ",int64,int32)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION_V3.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION =
            new Function(CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT
                    + ",uint256,uint256,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V2 =
            new Function(CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V2
                    + ",uint64,uint32,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_FUNGIBLE_WITH_FEES_DECODER = TypeFactory.create("("
            + HEDERA_TOKEN_STRUCT_DECODER
            + ",uint256,uint256,"
            + FIXED_FEE_DECODER
            + ARRAY_BRACKETS
            + ","
            + FRACTIONAL_FEE_DECODER
            + ARRAY_BRACKETS
            + ")");

    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V3 =
            new Function(CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V3
                    + ",int64,int32,"
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE_V2
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V3.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V2 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V2 + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_NON_FUNGIBLE_DECODER =
            TypeFactory.create("(" + HEDERA_TOKEN_STRUCT_DECODER + ")");
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V3 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V3 + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V3.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V2 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V2
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_DECODER = TypeFactory.create("("
            + HEDERA_TOKEN_STRUCT_DECODER
            + ","
            + FIXED_FEE_DECODER
            + ARRAY_BRACKETS
            + ","
            + ROYALTY_FEE_DECODER
            + ARRAY_BRACKETS
            + ")");
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V3 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V3
                    + ","
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE_V2
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V3.selector());
    private final EncodingFacade encoder;
    private final OptionValidator validator;
    private final CreateLogic createLogic;
    private final FeeCalculator feeCalculator;
    private final CreateChecks createChecks;

    public TokenCreatePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final OptionValidator validator,
            final CreateLogic createLogic,
            final FeeCalculator feeCalculator,
            final CreateChecks createChecks) {
        super(pricingUtils, syntheticTxnFactory);
        this.encoder = encoder;
        this.createLogic = createLogic;
        this.validator = validator;
        this.feeCalculator = feeCalculator;
        this.createChecks = createChecks;
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var createParams = ((CreateParams) bodyParams);
        final var functionId = createParams.functionId();
        final var tokenCreateOp =
                switch (functionId) {
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN -> decodeFungibleCreate(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES ->
                        decodeFungibleCreateWithFees(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN -> decodeNonFungibleCreate(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES ->
                        decodeNonFungibleCreateWithFees(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2 -> decodeFungibleCreateV2(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2 ->
                        decodeFungibleCreateWithFeesV2(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2 ->
                        decodeNonFungibleCreateV2(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2 ->
                        decodeNonFungibleCreateWithFeesV2(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3 -> decodeFungibleCreateV3(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3 ->
                        decodeFungibleCreateWithFeesV3(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3 ->
                        decodeNonFungibleCreateV3(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3 ->
                        decodeNonFungibleCreateWithFeesV3(input, aliasResolver);
                    default -> null;
                };

        verifySolidityInput(Objects.requireNonNull(tokenCreateOp));
        try {
            replaceInheritedProperties(createParams.account(), tokenCreateOp);
        } catch (InvalidKeyException e) {
            throw new InvalidTransactionException(e.getMessage(), ResponseCodeEnum.FAIL_INVALID);
        }

        return syntheticTxnFactory.createTokenCreate(tokenCreateOp);
    }

    @Override
    public long getMinimumFeeInTinybars(
            Timestamp consensusTime, TransactionBody transactionBody, final AccountID sender) {
        return 100_000L;
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var store = updater.getStore();
        final var tokenCreateOp = transactionBody.getTokenCreation();
        final var senderAddress = unalias(frame.getSenderAddress(), updater);
        Objects.requireNonNull(tokenCreateOp, "`body` method should be called before `run`");

        final var result = createChecks.validate().apply(transactionBody);
        validateTrue(result == OK, result);

        /* --- Execute the transaction and capture its results --- */
        createLogic.create(Instant.now().getEpochSecond(), senderAddress, validator, store, tokenCreateOp);
        return new TokenCreateResult(tokenIdFromEvmAddress(senderAddress));
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(
                AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
                AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2,
                AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3,
                AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
                AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2,
                AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3,
                AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
                AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2,
                AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3,
                AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES,
                AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2,
                AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3);
    }

    @Override
    public Bytes getSuccessResultFor(RunResult runResult) {
        final var tokenCreateResult = (TokenCreateResult) runResult;
        return encoder.encodeCreateSuccess(asTypedEvmAddress(tokenCreateResult.tokenId()));
    }

    @Override
    public Bytes getFailureResultFor(ResponseCodeEnum status) {
        return encoder.encodeCreateFailure(status);
    }

    @Override
    public void handleSentHbars(final MessageFrame frame, final TransactionBody.Builder transactionBody) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var store = updater.getStore();
        final var senderAddress = unalias(frame.getSenderAddress(), updater);
        final var timestampSeconds = frame.getBlockValues().getTimestamp();
        final var timestamp =
                Timestamp.newBuilder().setSeconds(timestampSeconds).build();
        final var gasPriceInTinybars = feeCalculator.estimatedGasPriceInTinybars(ContractCall, timestamp);
        final var calculatedFeeInTinybars = pricingUtils.gasFeeInTinybars(
                transactionBody.setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(timestamp)
                        .build()),
                timestamp);

        final var tinybarsRequirement = calculatedFeeInTinybars
                + (calculatedFeeInTinybars / 5)
                - getMinimumFeeInTinybars(timestamp, transactionBody.build(), null) * gasPriceInTinybars;

        validateTrue(frame.getValue().greaterOrEqualThan(Wei.of(tinybarsRequirement)), INSUFFICIENT_TX_FEE);

        final var sender = store.getAccount(senderAddress, OnMissing.THROW);
        final var updatedSender = sender.setBalance(sender.getBalance() - tinybarsRequirement);

        store.updateAccount(updatedSender);
    }

    @Override
    public long getGasRequirement(long blockTimestamp, Builder transactionBody, final AccountID sender) {
        return getMinimumFeeInTinybars(
                Timestamp.newBuilder().setSeconds(blockTimestamp).build(), transactionBody.build(), sender);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeFungibleCreateV2(). The selector for this function is derived from:
     * createFungibleToken((string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,bytes,address)
     * )[], (uint32,address,uint32)),uint256,uint256)
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreate(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_FUNGIBLE_SELECTOR, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
    }

    public static TokenCreateWrapper decodeTokenCreateWithoutFees(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final BigInteger initSupply,
            final BigInteger decimals,
            final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungible(tokenCreateStruct, isFungible, initSupply, decimals, aliasResolver);
    }

    public static TokenCreateWrapper decodeTokenCreateWithoutFeesV2(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final BigInteger initSupply,
            final BigInteger decimals,
            final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungible(tokenCreateStruct, isFungible, initSupply, decimals, aliasResolver);
    }

    private static TokenCreateWrapper getTokenCreateWrapperFungible(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final BigInteger initSupply,
            final BigInteger decimals,
            final UnaryOperator<byte[]> aliasResolver) {
        final var tokenName = (String) tokenCreateStruct.get(0);
        final var tokenSymbol = (String) tokenCreateStruct.get(1);
        final var tokenTreasury = convertLeftPaddedAddressToAccountId(tokenCreateStruct.get(2), aliasResolver);
        final var memo = (String) tokenCreateStruct.get(3);
        final var isSupplyTypeFinite = (Boolean) tokenCreateStruct.get(4);
        final var maxSupply = (long) tokenCreateStruct.get(5);
        final var isFreezeDefault = (Boolean) tokenCreateStruct.get(6);
        final var tokenKeys = decodeTokenKeys(tokenCreateStruct.get(7), aliasResolver);
        final var tokenExpiry = decodeTokenExpiry(tokenCreateStruct.get(8), aliasResolver);

        return new TokenCreateWrapper(
                isFungible,
                tokenName,
                tokenSymbol,
                (tokenTreasury.getShardNum() != 0 || tokenTreasury.getRealmNum() != 0) ? tokenTreasury : null,
                memo,
                isSupplyTypeFinite,
                initSupply,
                decimals,
                maxSupply,
                isFreezeDefault,
                tokenKeys,
                tokenExpiry);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),uint256,uint256,(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,uint32,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFees(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungibleWithFees(input, aliasResolver, TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR);
    }

    private static TokenCreateWrapper getTokenCreateWrapperFungibleWithFees(
            final Bytes input,
            final UnaryOperator<byte[]> aliasResolver,
            final Bytes tokenCreateFungibleWithFeesSelector) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, tokenCreateFungibleWithFeesSelector, TOKEN_CREATE_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper = decodeTokenCreateWithoutFees(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(3), aliasResolver);
        final var fractionalFees = decodeFractionalFees(decodedArguments.get(4), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setFractionalFees(fractionalFees);

        return tokenCreateWrapper;
    }

    public static List<FixedFeeWrapper> decodeFixedFees(
            @NonNull final Tuple[] fixedFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<FixedFeeWrapper> fixedFees = new ArrayList<>(fixedFeesTuples.length);
        for (final var fixedFeeTuple : fixedFeesTuples) {
            final var amount = (long) fixedFeeTuple.get(0);
            final var tokenId = convertAddressBytesToTokenID(fixedFeeTuple.get(1));
            final var useHbarsForPayment = (Boolean) fixedFeeTuple.get(2);
            final var useCurrentTokenForPayment = (Boolean) fixedFeeTuple.get(3);
            final var feeCollector = convertLeftPaddedAddressToAccountId(fixedFeeTuple.get(4), aliasResolver);
            fixedFees.add(new FixedFeeWrapper(
                    amount,
                    (tokenId.getShardNum() != 0 || tokenId.getRealmNum() != 0) ? tokenId : null,
                    useHbarsForPayment,
                    useCurrentTokenForPayment,
                    (feeCollector.getShardNum() != 0 || feeCollector.getRealmNum() != 0) ? feeCollector : null));
        }
        return fixedFees;
    }

    public static List<FractionalFeeWrapper> decodeFractionalFees(
            @NonNull final Tuple[] fractionalFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<FractionalFeeWrapper> fractionalFees = new ArrayList<>(fractionalFeesTuples.length);
        for (final var fractionalFeeTuple : fractionalFeesTuples) {
            final var numerator = (long) fractionalFeeTuple.get(0);
            final var denominator = (long) fractionalFeeTuple.get(1);
            final var minimumAmount = (long) fractionalFeeTuple.get(2);
            final var maximumAmount = (long) fractionalFeeTuple.get(3);
            final var netOfTransfers = (Boolean) fractionalFeeTuple.get(4);
            final var feeCollector = convertLeftPaddedAddressToAccountId(fractionalFeeTuple.get(5), aliasResolver);
            fractionalFees.add(new FractionalFeeWrapper(
                    numerator,
                    denominator,
                    minimumAmount,
                    maximumAmount,
                    netOfTransfers,
                    (feeCollector.getShardNum() != 0 || feeCollector.getRealmNum() != 0) ? feeCollector : null));
        }
        return fractionalFees;
    }

    public static TokenCreateWrapper decodeNonFungibleCreate(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_NON_FUNGIBLE_SELECTOR, TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    public static TokenCreateWrapper decodeNonFungibleCreateWithFees(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapper(input, aliasResolver, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR);
    }

    public static List<RoyaltyFeeWrapper> decodeRoyaltyFees(
            @NonNull final Tuple[] royaltyFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<RoyaltyFeeWrapper> decodedRoyaltyFees = new ArrayList<>(royaltyFeesTuples.length);
        for (final var royaltyFeeTuple : royaltyFeesTuples) {
            final var numerator = (long) royaltyFeeTuple.get(0);
            final var denominator = (long) royaltyFeeTuple.get(1);

            // When at least 1 of the following 3 values is different from its default value,
            // we treat it as though the user has tried to specify a fallbackFixedFee
            final var fixedFeeAmount = (long) royaltyFeeTuple.get(2);
            final var fixedFeeTokenId = convertAddressBytesToTokenID(royaltyFeeTuple.get(3));
            final var fixedFeeUseHbars = (Boolean) royaltyFeeTuple.get(4);
            FixedFeeWrapper fixedFee = null;
            if (fixedFeeAmount != 0 || fixedFeeTokenId.getTokenNum() != 0 || Boolean.TRUE.equals(fixedFeeUseHbars)) {
                fixedFee = new FixedFeeWrapper(
                        fixedFeeAmount,
                        fixedFeeTokenId.getTokenNum() != 0 ? fixedFeeTokenId : null,
                        fixedFeeUseHbars,
                        false,
                        null);
            }

            final var feeCollector = convertLeftPaddedAddressToAccountId(royaltyFeeTuple.get(5), aliasResolver);
            decodedRoyaltyFees.add(new RoyaltyFeeWrapper(
                    numerator,
                    denominator,
                    fixedFee,
                    (feeCollector.getShardNum() != 0 || feeCollector.getRealmNum() != 0) ? feeCollector : null));
        }
        return decodedRoyaltyFees;
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes decodeFungibleCreate(). The
     * selector for this function is derived from:
     * createFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],
     * (uint32,address,uint32)),uint64,uint32)
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_FUNGIBLE_SELECTOR_V2, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
    }

    public static TokenCreateWrapper decodeFungibleCreateV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_FUNGIBLE_SELECTOR_V3, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFeesV2(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version and is superseded by
     * decodeFungibleCreateWithFeesV3(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),uint64,uint32,(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,uint32,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFeesV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungibleWithFees(input, aliasResolver, TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V2);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(int64,address,int64)),int64,int32,(int64,address,bool,bool,address)[],
     * (int64,int64,int64,int64,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFeesV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungibleWithFees(input, aliasResolver, TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V3);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes decodeNonFungibleCreateV2().
     * The selector for this function is derived from:
     * createNonFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,
     * address))[], (uint32,address,uint32)))
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V2, TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    public static TokenCreateWrapper decodeNonFungibleCreateV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V3, TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is and old version and is superseded by
     * decodeNonFungibleCreateWithFeesV3(). The selector for this function is derived from:
     * createNonFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,address,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateWithFeesV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapper(input, aliasResolver, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V2);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeNonFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * createNonFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(int64,address,int64)),(int64,address,bool,bool,address)[],
     * (int64,int64,int64,address,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateWithFeesV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapper(input, aliasResolver, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V3);
    }

    private static TokenCreateWrapper getTokenCreateWrapper(
            final Bytes input,
            final UnaryOperator<byte[]> aliasResolver,
            final Bytes tokenCreateNonFungibleWithFeesSelector) {
        final Tuple decodedArguments = decodeFunctionCall(
                input, tokenCreateNonFungibleWithFeesSelector, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper = decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(1), aliasResolver);
        final var royaltyFees = decodeRoyaltyFees(decodedArguments.get(2), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setRoyaltyFees(royaltyFees);

        return tokenCreateWrapper;
    }

    private void verifySolidityInput(final TokenCreateWrapper tokenCreateOp) {
        /*
         * Verify initial supply and decimals fall withing the allowed ranges of the types
         * they convert to (long and int, respectively), since in the Solidity interface
         * they are specified as uint256s and illegal values may be passed as input.
         */
        if (tokenCreateOp.isFungible()) {
            validateTrue(
                    tokenCreateOp.getInitSupply().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 1,
                    INVALID_TRANSACTION_BODY);
            validateTrue(
                    tokenCreateOp.getDecimals().compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 1,
                    INVALID_TRANSACTION_BODY);
        }

        /*
         * Check keys validity. The `TokenKey` struct in `IHederaTokenService.sol`
         * defines a `keyType` bit field, which smart contract developers will use to
         * set the type of key the `KeyValue` field will be used for. For example, if the
         * `keyType` field is set to `00000001`, then the key value will be used for adminKey.
         * If it is set to `00000011` the key value will be used for both adminKey and kycKey.
         * Since an array of `TokenKey` structs is passed to the precompile, we have to
         * check if each one specifies the type of key it applies to (that the bit field
         * is not `00000000` and no bit bigger than 6 is set) and also that there are not multiple
         * keys values for the same key type (e.g. multiple `TokenKey` instances have the adminKey bit set)
         */
        final var tokenKeys = tokenCreateOp.getTokenKeys();
        checkKeysValidity(tokenKeys);

        /*
         * The denomination of a fixed fee depends on the values of tokenId, useHbarsForPayment
         * useCurrentTokenForPayment. Exactly one of the values of the struct should be set.
         */
        if (!tokenCreateOp.getFixedFees().isEmpty()) {
            for (final var fixedFee : tokenCreateOp.getFixedFees()) {
                validateTrue(fixedFee.getFixedFeePayment() != INVALID_PAYMENT, INVALID_TRANSACTION_BODY);
            }
        }

        /*
         * When a royalty fee with fallback fee is specified, we need to check that
         * the fallback fixed fee is valid.
         */
        validateFallbackFixedFee(tokenCreateOp);
    }

    private void checkKeysValidity(List<TokenKeyWrapper> tokenKeys) {
        if (!tokenKeys.isEmpty()) {
            for (int i = 0, tokenKeysSize = tokenKeys.size(); i < tokenKeysSize; i++) {
                final var tokenKey = tokenKeys.get(i);
                validateTrue(tokenKey.key().getKeyValueType() != INVALID_KEY, INVALID_TRANSACTION_BODY);
                final var tokenKeyBitField = tokenKey.keyType();
                validateTrue(tokenKeyBitField != 0 && tokenKeyBitField < 128, INVALID_TRANSACTION_BODY);
                for (int j = i + 1; j < tokenKeysSize; j++) {
                    validateTrue((tokenKeyBitField & tokenKeys.get(j).keyType()) == 0, INVALID_TRANSACTION_BODY);
                }
            }
        }
    }

    private void validateFallbackFixedFee(TokenCreateWrapper tokenCreateOp) {
        if (!tokenCreateOp.getRoyaltyFees().isEmpty()) {
            for (final var royaltyFee : tokenCreateOp.getRoyaltyFees()) {
                if (royaltyFee.fallbackFixedFee() != null) {
                    validateTrue(
                            royaltyFee.fallbackFixedFee().getFixedFeePayment() != INVALID_PAYMENT,
                            INVALID_TRANSACTION_BODY);
                }
            }
        }
    }

    private void replaceInheritedProperties(Account account, TokenCreateWrapper tokenCreateOp)
            throws InvalidKeyException {
        if (account == null) {
            return;
        }

        if (!tokenCreateOp.hasAutoRenewAccount()) {
            tokenCreateOp.inheritAutoRenewAccount(EntityIdUtils.toGrpcAccountId(account.getId()));
        }

        tokenCreateOp.setAllInheritedKeysTo(account.getKey());
    }
}
