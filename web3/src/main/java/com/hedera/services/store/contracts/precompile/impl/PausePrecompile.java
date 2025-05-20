// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_PAUSE_TOKEN;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PAUSE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.PauseLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of PausePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}
 */
public class PausePrecompile extends AbstractWritePrecompile {
    private static final Function PAUSE_TOKEN_FUNCTION = new Function("pauseToken(address)", INT);
    private static final Bytes PAUSE_TOKEN_SELECTOR = Bytes.wrap(PAUSE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> PAUSE_TOKEN_DECODER = TypeFactory.create(BYTES32);
    private final PauseLogic pauseLogic;

    public PausePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PauseLogic pauseLogic) {
        super(pricingUtils, syntheticTxnFactory);
        this.pauseLogic = pauseLogic;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        var pauseOp = decodePause(input);
        return syntheticTxnFactory.createPause(pauseOp);
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(PAUSE, consensusTime);
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");

        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var tokenId = transactionBody.getTokenPause().getToken();
        final var validity = pauseLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction --- */
        pauseLogic.pause(Id.fromGrpcToken(tokenId), store);

        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_PAUSE_TOKEN);
    }

    public static PauseWrapper decodePause(final Bytes input) {
        final Tuple decodedArguments = decodeFunctionCall(input, PAUSE_TOKEN_SELECTOR, PAUSE_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

        return new PauseWrapper(tokenID);
    }
}
