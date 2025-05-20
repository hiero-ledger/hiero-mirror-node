// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;

/**
 * This class is a modified copy of UpdateTokenExpiryInfoPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 */
public class UpdateTokenExpiryInfoPrecompile extends AbstractTokenUpdatePrecompile {

    public UpdateTokenExpiryInfoPrecompile(
            TokenUpdateLogic tokenUpdateLogic,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            ContextOptionValidator contextOptionValidator,
            SyntheticTxnFactory syntheticTxnFactory,
            PrecompilePricingUtils precompilePricingUtils) {
        super(
                tokenUpdateLogic,
                mirrorNodeEvmProperties,
                contextOptionValidator,
                precompilePricingUtils,
                syntheticTxnFactory);
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var functionId = ((FunctionParam) bodyParams).functionId();
        final var updateExpiryInfoAbi =
                switch (functionId) {
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO -> SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V1;
                    case ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2 -> SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V2;
                    default -> throw new IllegalArgumentException("invalid selector to updateExpiryInfo precompile");
                };
        final var updateExpiryInfoOp = getTokenUpdateExpiryInfoWrapper(input, aliasResolver, updateExpiryInfoAbi);
        return syntheticTxnFactory.createTokenUpdateExpiryInfo(updateExpiryInfoOp);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO, ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2);
    }

    public static TokenUpdateExpiryInfoWrapper getTokenUpdateExpiryInfoWrapper(
            Bytes input, UnaryOperator<byte[]> aliasResolver, @NonNull final SystemContractAbis abi) {
        final Tuple decodedArguments = decodeFunctionCall(input, abi.selector, abi.decoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final Tuple tokenExpiryStruct = decodedArguments.get(1);
        final var tokenExpiry = decodeTokenExpiry(tokenExpiryStruct, aliasResolver);
        return new TokenUpdateExpiryInfoWrapper(tokenID, tokenExpiry);
    }
}
