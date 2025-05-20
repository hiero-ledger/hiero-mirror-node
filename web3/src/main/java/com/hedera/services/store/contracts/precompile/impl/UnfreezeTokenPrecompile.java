// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UNFREEZE;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UNFREEZE;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.UnfreezeLogic;
import com.hederahashgraph.api.proto.java.*;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.Store;

/**
 * This class is a modified copy of UnfreezeTokenPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Implements validateSyntax and executeFreezeUnfreezeLogic from AbstractFreezeUnfreezePrecompile
 */
public class UnfreezeTokenPrecompile extends AbstractFreezeUnfreezePrecompile {
    private static final Function UNFREEZE_TOKEN_FUNCTION = new Function("unfreezeToken(address,address)", INT);
    private static final Bytes UNFREEZE_TOKEN_FUNCTION_SELECTOR = Bytes.wrap(UNFREEZE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> UNFREEZE_TOKEN_ACCOUNT_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    private final UnfreezeLogic unfreezeLogic;

    public UnfreezeTokenPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final UnfreezeLogic unfreezeLogic) {
        super(pricingUtils, syntheticTxnFactory);
        this.unfreezeLogic = unfreezeLogic;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        var freezeUnfreezeOp = decodeUnfreeze(input, aliasResolver);
        return syntheticTxnFactory.createUnfreeze(freezeUnfreezeOp);
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(UNFREEZE, consensusTime);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_UNFREEZE);
    }

    public static TokenFreezeUnfreezeWrapper<TokenID, AccountID> decodeUnfreeze(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, UNFREEZE_TOKEN_FUNCTION_SELECTOR, UNFREEZE_TOKEN_ACCOUNT_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        return TokenFreezeUnfreezeWrapper.forUnfreeze(tokenID, accountID);
    }

    @Override
    public ResponseCodeEnum validateSyntax(TransactionBody transactionBody) {
        return unfreezeLogic.validate(transactionBody);
    }

    @Override
    public void executeFreezeUnfreezeLogic(TransactionBody transactionBody, Store store) {
        final var tokenId = transactionBody.getTokenUnfreeze().getToken();
        final var accountId = transactionBody.getTokenUnfreeze().getAccount();
        unfreezeLogic.unfreeze(Id.fromGrpcToken(tokenId), Id.fromGrpcAccount(accountId), store);
    }
}
