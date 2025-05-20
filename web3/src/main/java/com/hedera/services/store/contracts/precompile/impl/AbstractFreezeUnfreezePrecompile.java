// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of AbstractFreezeUnfreezePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}. It calls
 *     two abstract methods (validateSyntax and executeFreezeUnfreezeLogic) to delegate to the corresponding freeze/unfreeze
 *     implementation instead of executeForFreeze and executeForUnfreeze methods.
 */
public abstract class AbstractFreezeUnfreezePrecompile extends AbstractWritePrecompile {

    protected AbstractFreezeUnfreezePrecompile(
            final PrecompilePricingUtils pricingUtils, final SyntheticTxnFactory syntheticTxnFactory) {
        super(pricingUtils, syntheticTxnFactory);
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");

        final var validity = validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        executeFreezeUnfreezeLogic(transactionBody, store);

        return new EmptyRunResult();
    }

    public abstract ResponseCodeEnum validateSyntax(final TransactionBody transactionBody);

    public abstract void executeFreezeUnfreezeLogic(final TransactionBody transactionBody, final Store store);
}
