// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UNPAUSE_TOKEN;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UNPAUSE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.UnpauseWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.UnpauseLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of UnpausePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}
 */
public class UnpausePrecompile extends AbstractWritePrecompile {
    private static final Function UNPAUSE_TOKEN_FUNCTION = new Function("unpauseToken(address)", INT);
    private static final Bytes UNPAUSE_TOKEN_SELECTOR = Bytes.wrap(UNPAUSE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> UNPAUSE_TOKEN_DECODER = TypeFactory.create(BYTES32);
    private final UnpauseLogic unpauseLogic;

    public UnpausePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final UnpauseLogic unpauseLogic) {
        super(pricingUtils, syntheticTxnFactory);
        this.unpauseLogic = unpauseLogic;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        var unpauseOp = decodeUnpause(input);
        return syntheticTxnFactory.createUnpause(unpauseOp);
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(UNPAUSE, consensusTime);
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");

        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var tokenId = transactionBody.getTokenUnpause().getToken();
        final var validity = unpauseLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction --- */
        unpauseLogic.unpause(Id.fromGrpcToken(tokenId), store);

        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_UNPAUSE_TOKEN);
    }

    public static UnpauseWrapper decodeUnpause(final Bytes input) {
        final Tuple decodedArguments = decodeFunctionCall(input, UNPAUSE_TOKEN_SELECTOR, UNPAUSE_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

        return new UnpauseWrapper(tokenID);
    }
}
