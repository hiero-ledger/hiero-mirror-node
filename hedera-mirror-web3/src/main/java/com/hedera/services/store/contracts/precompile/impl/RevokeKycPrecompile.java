// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REVOKE_TOKEN_KYC;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.REVOKE_KYC;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.RevokeKycLogic;
import com.hederahashgraph.api.proto.java.*;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of RevokeKycPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}
 */
public class RevokeKycPrecompile extends AbstractGrantRevokeKycPrecompile {

    private static final Function REVOKE_TOKEN_KYC_FUNCTION = new Function("revokeTokenKyc(address,address)", INT);
    private static final Bytes REVOKE_TOKEN_KYC_FUNCTION_SELECTOR = Bytes.wrap(REVOKE_TOKEN_KYC_FUNCTION.selector());
    private static final ABIType<Tuple> REVOKE_TOKEN_KYC_FUNCTION_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    private final RevokeKycLogic revokeKycLogic;

    public RevokeKycPrecompile(
            final RevokeKycLogic revokeKycLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils precompilePricingUtils) {
        super(syntheticTxnFactory, precompilePricingUtils);
        this.revokeKycLogic = revokeKycLogic;
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var revokeOp = decodeRevokeTokenKyc(input, aliasResolver);
        return syntheticTxnFactory.createRevokeKyc(revokeOp);
    }

    @Override
    public long getMinimumFeeInTinybars(
            Timestamp consensusTime, TransactionBody transactionBody, final AccountID sender) {
        requireNonNull(transactionBody, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(REVOKE_KYC, consensusTime);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        requireNonNull(transactionBody, "`body` method should be called before `run`");
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();

        final var tokenId = Id.fromGrpcToken(transactionBody.getTokenRevokeKyc().getToken());
        final var accountId =
                Id.fromGrpcAccount(transactionBody.getTokenRevokeKyc().getAccount());

        validateLogic(revokeKycLogic.validate(transactionBody));
        revokeKycLogic.revokeKyc(tokenId, accountId, store);

        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_REVOKE_TOKEN_KYC);
    }

    public static GrantRevokeKycWrapper<TokenID, AccountID> decodeRevokeTokenKyc(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, REVOKE_TOKEN_KYC_FUNCTION_SELECTOR, REVOKE_TOKEN_KYC_FUNCTION_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);

        return new GrantRevokeKycWrapper<>(tokenID, accountID);
    }

    private void validateLogic(ResponseCodeEnum validity) {
        validateTrue(validity == OK, validity);
    }
}
