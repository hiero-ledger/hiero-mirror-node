// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_HRC_DISSOCIATE;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.DissociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class DissociatePrecompile extends AbstractDissociatePrecompile {

    private static final Function DISSOCIATE_TOKEN_FUNCTION = new Function("dissociateToken(address,address)", INT);
    private static final Bytes DISSOCIATE_TOKEN_SELECTOR = Bytes.wrap(DISSOCIATE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> DISSOCIATE_TOKEN_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    private final SyntheticTxnFactory syntheticTxnFactory;

    public DissociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final DissociateLogic dissociateLogic) {
        super(dissociateLogic, precompilePricingUtils);
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    @Override
    public Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        TokenID tokenId = null;
        final Address callerAccountAddress;
        AccountID callerAccountID = null;

        if (bodyParams instanceof final HrcParams hrcParams) {
            tokenId = hrcParams.token();
            callerAccountAddress = hrcParams.senderAddress();
            callerAccountID =
                    EntityIdUtils.accountIdFromEvmAddress(Objects.requireNonNull(callerAccountAddress.toArray()));
        }

        final var dissociateOp = tokenId == null
                ? decodeDissociate(input, aliasResolver)
                : Dissociation.singleDissociation(Objects.requireNonNull(callerAccountID), tokenId);

        return syntheticTxnFactory.createDissociate(dissociateOp);
    }

    @Override
    public long getGasRequirement(final long blockTimestamp, final Builder transactionBody, final AccountID sender) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody, sender);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_DISSOCIATE_TOKEN, ABI_ID_HRC_DISSOCIATE);
    }

    public static Dissociation decodeDissociate(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments = decodeFunctionCall(input, DISSOCIATE_TOKEN_SELECTOR, DISSOCIATE_TOKEN_DECODER);

        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(1));

        return Dissociation.singleDissociation(accountID, tokenID);
    }
}
