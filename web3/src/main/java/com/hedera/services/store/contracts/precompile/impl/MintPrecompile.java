// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_NFT;
import static com.hedera.services.utils.MiscUtils.convertArrayToLong;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.hapi.utils.ByteStringUtils;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.MintResult;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txn.token.MintLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of MintPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult} which is needed for getSuccessResultFor
 *  5. getSuccessResultFor accepts {@link RunResult}
 */
public class MintPrecompile extends AbstractWritePrecompile {

    private static final List<ByteString> NO_METADATA = Collections.emptyList();
    private final EncodingFacade encoder;
    private final MintLogic mintLogic;

    public MintPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final MintLogic mintLogic) {
        super(pricingUtils, syntheticTxnFactory);
        this.encoder = encoder;
        this.mintLogic = mintLogic;
    }

    public static MintWrapper getMintWrapper(final Bytes input, @NonNull final SystemContractAbis abi) {
        final Tuple decodedArguments = decodeFunctionCall(input, abi.selector, abi.decoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var fungibleAmount = SystemContractAbis.toLongSafely(decodedArguments.get(1));
        final var metadataList = (byte[][]) decodedArguments.get(2);
        final List<ByteString> wrappedMetadata = new ArrayList<>();
        for (final var meta : metadataList) {
            wrappedMetadata.add(ByteStringUtils.wrapUnsafely(meta));
        }
        // We allow zero unit operations on fungible tokens
        if (fungibleAmount > 0 || (fungibleAmount == 0 && wrappedMetadata.isEmpty())) {
            return MintWrapper.forFungible(tokenID, fungibleAmount);
        } else {
            return MintWrapper.forNonFungible(tokenID, wrappedMetadata);
        }
    }

    @Override
    public Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        final var functionId = ((FunctionParam) bodyParams).functionId();
        final var mintAbi =
                switch (functionId) {
                    case AbiConstants.ABI_ID_MINT_TOKEN -> SystemContractAbis.MINT_TOKEN_V1;
                    case AbiConstants.ABI_ID_MINT_TOKEN_V2 -> SystemContractAbis.MINT_TOKEN_V2;
                    default -> throw new IllegalArgumentException("invalid selector to mint precompile");
                };
        final var mintOp = getMintWrapper(input, mintAbi);
        return syntheticTxnFactory.createMint(mintOp);
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        final var isNftMint = transactionBody.getTokenMint().getMetadataCount() > 0;
        return pricingUtils.getMinimumPriceInTinybars(isNftMint ? MINT_NFT : MINT_FUNGIBLE, consensusTime);
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");

        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var mintBody = transactionBody.getTokenMint();
        final var tokenId = mintBody.getToken();

        final var validity = mintLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        final TokenModificationResult tokenModificationResult;
        if (mintBody.getMetadataCount() > 0) {
            final var newMeta = mintBody.getMetadataList();
            tokenModificationResult = mintLogic.mint(Id.fromGrpcToken(tokenId), 0, newMeta, Instant.now(), store);
        } else {
            tokenModificationResult =
                    mintLogic.mint(Id.fromGrpcToken(tokenId), mintBody.getAmount(), NO_METADATA, Instant.EPOCH, store);
        }

        final var modifiedToken = tokenModificationResult.token();
        return new MintResult(
                modifiedToken.getTotalSupply(),
                TokenType.NON_FUNGIBLE_UNIQUE == modifiedToken.getType()
                        ? modifiedToken.mintedUniqueTokens().stream()
                                .map(UniqueToken::getSerialNumber)
                                .toList()
                        : new ArrayList<>());
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_MINT_TOKEN, AbiConstants.ABI_ID_MINT_TOKEN_V2);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var mintResult = (MintResult) runResult;
        return encoder.encodeMintSuccess(mintResult.totalSupply(), convertArrayToLong(mintResult.serialNumbers()));
    }

    @Override
    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return encoder.encodeMintFailure(status);
    }
}
