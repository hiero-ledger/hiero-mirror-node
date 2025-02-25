// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V2;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V3;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.removeBrackets;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.TokenUpdateWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

/**
 * This class is a modified copy of TokenUpdatePrecompile from hedera-services repo.
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructor in order to achieve stateless behaviour
 */
public class TokenUpdatePrecompile extends AbstractTokenUpdatePrecompile {

    private static final String UPDATE_TOKEN_INFO_STRING = "updateTokenInfo(address,";
    private static final Function TOKEN_UPDATE_INFO_FUNCTION =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT + ")");
    private static final Bytes TOKEN_UPDATE_INFO_SELECTOR = Bytes.wrap(TOKEN_UPDATE_INFO_FUNCTION.selector());
    private static final ABIType<Tuple> TOKEN_UPDATE_INFO_DECODER =
            TypeFactory.create("(" + removeBrackets(BYTES32) + "," + HEDERA_TOKEN_STRUCT_DECODER + ")");
    private static final Function TOKEN_UPDATE_INFO_FUNCTION_V2 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V2 + ")");
    private static final Bytes TOKEN_UPDATE_INFO_SELECTOR_V2 = Bytes.wrap(TOKEN_UPDATE_INFO_FUNCTION_V2.selector());
    private static final Function TOKEN_UPDATE_INFO_FUNCTION_V3 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V3 + ")");
    private static final Bytes TOKEN_UPDATE_INFO_SELECTOR_V3 = Bytes.wrap(TOKEN_UPDATE_INFO_FUNCTION_V3.selector());

    public TokenUpdatePrecompile(
            PrecompilePricingUtils pricingUtils,
            TokenUpdateLogic tokenUpdateLogic,
            SyntheticTxnFactory syntheticTxnFactory,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            ContextOptionValidator contextOptionValidator) {
        super(tokenUpdateLogic, mirrorNodeEvmProperties, contextOptionValidator, pricingUtils, syntheticTxnFactory);
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var functionId = ((FunctionParam) bodyParams).functionId();
        final var updateOp =
                switch (functionId) {
                    case ABI_ID_UPDATE_TOKEN_INFO -> decodeUpdateTokenInfo(input, aliasResolver);
                    case ABI_ID_UPDATE_TOKEN_INFO_V2 -> decodeUpdateTokenInfoV2(input, aliasResolver);
                    case ABI_ID_UPDATE_TOKEN_INFO_V3 -> decodeUpdateTokenInfoV3(input, aliasResolver);
                    default -> null;
                };

        Objects.requireNonNull(updateOp, "Unable to decode function input");

        return syntheticTxnFactory.createTokenUpdate(updateOp);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_UPDATE_TOKEN_INFO, ABI_ID_UPDATE_TOKEN_INFO_V2, ABI_ID_UPDATE_TOKEN_INFO_V3);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeUpdateTokenInfoV2(). The selector for this function is derived from:
     * updateTokenInfo(address,(string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,bytes,address))[],(uint32,address,uint32)))
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenUpdateWrapper codec
     */
    public static TokenUpdateWrapper decodeUpdateTokenInfo(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return getTokenUpdateWrapper(input, aliasResolver, TOKEN_UPDATE_INFO_SELECTOR);
    }

    /**
     * Decodes the given bytes of the updateTokenInfo function.
     *
     * <p><b>Important: </b>This is an old version and is superseded by
     * decodeNonFungibleCreateWithFeesV3(). The selector for this function is derived from:
     * updateTokenInfo(address,(string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(uint32,address,uint32)))
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenUpdateWrapper codec
     */
    public static TokenUpdateWrapper decodeUpdateTokenInfoV2(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return getTokenUpdateWrapper(input, aliasResolver, TOKEN_UPDATE_INFO_SELECTOR_V2);
    }

    /**
     * Decodes the given bytes of the updateTokenInfo function.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeNonFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * updateTokenInfo(address,(string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)))
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenUpdateWrapper codec
     */
    public static TokenUpdateWrapper decodeUpdateTokenInfoV3(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return getTokenUpdateWrapper(input, aliasResolver, TOKEN_UPDATE_INFO_SELECTOR_V3);
    }

    private static TokenUpdateWrapper getTokenUpdateWrapper(
            Bytes input, UnaryOperator<byte[]> aliasResolver, Bytes tokenUpdateInfoSelector) {
        final Tuple decodedArguments = decodeFunctionCall(input, tokenUpdateInfoSelector, TOKEN_UPDATE_INFO_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

        final Tuple hederaTokenStruct = decodedArguments.get(1);
        final var tokenName = (String) hederaTokenStruct.get(0);
        final var tokenSymbol = (String) hederaTokenStruct.get(1);
        final var tokenTreasury = convertLeftPaddedAddressToAccountId(hederaTokenStruct.get(2), aliasResolver);
        final var tokenMemo = (String) hederaTokenStruct.get(3);
        final var tokenKeys = decodeTokenKeys(hederaTokenStruct.get(7), aliasResolver);
        final var tokenExpiry = decodeTokenExpiry(hederaTokenStruct.get(8), aliasResolver);
        return new TokenUpdateWrapper(
                tokenID,
                tokenName,
                tokenSymbol,
                tokenTreasury.getAccountNum() == 0 ? null : tokenTreasury,
                tokenMemo,
                tokenKeys,
                tokenExpiry);
    }
}
