// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.TOKEN_KEY;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateKeysWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class TokenUpdateKeysPrecompile extends AbstractTokenUpdatePrecompile {
    private static final Function TOKEN_UPDATE_KEYS_FUNCTION =
            new Function("updateTokenKeys(address," + TOKEN_KEY + ARRAY_BRACKETS + ")");
    private static final Bytes TOKEN_UPDATE_KEYS_SELECTOR = Bytes.wrap(TOKEN_UPDATE_KEYS_FUNCTION.selector());
    private static final ABIType<Tuple> TOKEN_UPDATE_KEYS_DECODER = TypeFactory.create("("
            + DecodingFacade.removeBrackets(BYTES32)
            + ","
            + DecodingFacade.TOKEN_KEY_DECODER
            + ARRAY_BRACKETS
            + ")");

    public TokenUpdateKeysPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final TokenUpdateLogic tokenUpdateLogic,
            OptionValidator optionValidator,
            MirrorNodeEvmProperties evmProperties) {
        super(tokenUpdateLogic, evmProperties, optionValidator, pricingUtils, syntheticTxnFactory);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var updateOp = decodeUpdateTokenKeys(input, aliasResolver);
        return syntheticTxnFactory.createTokenUpdateKeys(updateOp);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS);
    }

    public static TokenUpdateKeysWrapper decodeUpdateTokenKeys(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments = decodeFunctionCall(input, TOKEN_UPDATE_KEYS_SELECTOR, TOKEN_UPDATE_KEYS_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var tokenKeys = decodeTokenKeys(decodedArguments.get(1), aliasResolver);
        return new TokenUpdateKeysWrapper(tokenID, tokenKeys);
    }
}
