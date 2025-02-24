// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_HRC_ASSOCIATE;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * This class is a modified copy of AssociatePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 */
public class AssociatePrecompile extends AbstractAssociatePrecompile {
    private static final Function ASSOCIATE_TOKEN_FUNCTION = new Function("associateToken(address,address)", INT);
    private static final Bytes ASSOCIATE_TOKEN_SELECTOR = Bytes.wrap(ASSOCIATE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> ASSOCIATE_TOKEN_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    public AssociatePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final AssociateLogic associateLogic) {
        super(pricingUtils, syntheticTxnFactory, associateLogic);
    }

    public static Association decodeAssociation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments = decodeFunctionCall(input, ASSOCIATE_TOKEN_SELECTOR, ASSOCIATE_TOKEN_DECODER);

        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(1));

        return Association.singleAssociation(accountID, tokenID);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        TokenID tokenId = null;
        final Address callerAccountAddress;
        AccountID callerAccountID = null;

        if (bodyParams instanceof final HrcParams hrcParams) {
            tokenId = hrcParams.token();
            callerAccountAddress = hrcParams.senderAddress();
            callerAccountID =
                    EntityIdUtils.accountIdFromEvmAddress(Objects.requireNonNull(callerAccountAddress.toArray()));
        }

        final var associateOp = tokenId == null
                ? decodeAssociation(input, aliasResolver)
                : Association.singleAssociation(Objects.requireNonNull(callerAccountID), tokenId);

        return syntheticTxnFactory.createAssociate(associateOp);
    }

    @Override
    public long getGasRequirement(
            final long blockTimestamp, final TransactionBody.Builder transactionBody, final AccountID sender) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody, sender);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_ASSOCIATE_TOKEN, ABI_ID_HRC_ASSOCIATE);
    }
}
