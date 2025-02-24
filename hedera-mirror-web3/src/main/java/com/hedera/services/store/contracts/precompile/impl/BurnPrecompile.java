// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.BurnResult;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txn.token.BurnLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of BurnPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 */
public class BurnPrecompile extends AbstractWritePrecompile {

    public static final String ILLEGAL_AMOUNT_TO_BURN = "Illegal amount of tokens to burn";
    private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();

    private final EncodingFacade encoder;
    private final BurnLogic burnLogic;

    public BurnPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final BurnLogic burnLogic) {
        super(pricingUtils, syntheticTxnFactory);
        this.encoder = encoder;
        this.burnLogic = burnLogic;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        final var functionId = ((FunctionParam) bodyParams).functionId();
        final var burnAbi =
                switch (functionId) {
                    case AbiConstants.ABI_ID_BURN_TOKEN -> SystemContractAbis.BURN_TOKEN_V1;
                    case AbiConstants.ABI_ID_BURN_TOKEN_V2 -> SystemContractAbis.BURN_TOKEN_V2;
                    default -> throw new IllegalArgumentException("invalid selector to burn precompile");
                };
        final var burnOp = getBurnWrapper(input, burnAbi);
        return syntheticTxnFactory.createBurn(burnOp);
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        final var isNftBurn = transactionBody.getTokenBurn().getSerialNumbersCount() > 0;
        return pricingUtils.getMinimumPriceInTinybars(isNftBurn ? BURN_NFT : BURN_FUNGIBLE, consensusTime);
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");

        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var burnBody = transactionBody.getTokenBurn();
        final var tokenId = burnBody.getToken();

        final var validity = burnLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        final TokenModificationResult tokenModificationResult;
        if (burnBody.getSerialNumbersCount() > 0) {
            final var targetSerialNos = burnBody.getSerialNumbersList();
            tokenModificationResult = burnLogic.burn(Id.fromGrpcToken(tokenId), 0, targetSerialNos, store);
        } else {
            tokenModificationResult =
                    burnLogic.burn(Id.fromGrpcToken(tokenId), burnBody.getAmount(), NO_SERIAL_NOS, store);
        }

        final var modifiedToken = tokenModificationResult.token();
        return new BurnResult(
                modifiedToken.getTotalSupply(),
                TokenType.NON_FUNGIBLE_UNIQUE == modifiedToken.getType()
                        ? modifiedToken.removedUniqueTokens().stream()
                                .map(UniqueToken::getSerialNumber)
                                .toList()
                        : new ArrayList<>());
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_BURN_TOKEN, AbiConstants.ABI_ID_BURN_TOKEN_V2);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var burnResult = (BurnResult) runResult;
        return encoder.encodeBurnSuccess(burnResult.totalSupply());
    }

    @Override
    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return encoder.encodeBurnFailure(status);
    }

    public static BurnWrapper getBurnWrapper(final Bytes input, @NonNull final SystemContractAbis abi) {
        final Tuple decodedArguments = decodeFunctionCall(input, abi.selector, abi.decoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var fungibleAmount = SystemContractAbis.toLongSafely(decodedArguments.get(1));
        final var serialNumbers = (long[]) decodedArguments.get(2);

        if (fungibleAmount < 0 || (fungibleAmount == 0 && serialNumbers.length == 0)) {
            throw new IllegalArgumentException(ILLEGAL_AMOUNT_TO_BURN);
        }

        if (fungibleAmount > 0) {
            return BurnWrapper.forFungible(tokenID, fungibleAmount);
        } else {
            return BurnWrapper.forNonFungible(
                    tokenID, Arrays.stream(serialNumbers).boxed().toList());
        }
    }
}
