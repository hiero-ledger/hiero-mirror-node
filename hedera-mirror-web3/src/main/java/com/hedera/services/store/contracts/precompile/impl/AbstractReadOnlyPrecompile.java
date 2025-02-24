// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractReadOnlyPrecompile implements Precompile {
    private static final long MINIMUM_GAS_COST = 100L;

    protected final SyntheticTxnFactory syntheticTxnFactory;
    protected final EncodingFacade encoder;
    protected final PrecompilePricingUtils pricingUtils;

    protected AbstractReadOnlyPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.encoder = encoder;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        return syntheticTxnFactory.createTransactionCall(1L, input);
    }

    @Override
    public long getMinimumFeeInTinybars(
            Timestamp consensusTime, TransactionBody transactionBody, final AccountID sender) {
        return MINIMUM_GAS_COST;
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        return new EmptyRunResult();
    }

    @Override
    public long getGasRequirement(long blockTimestamp, Builder transactionBody, final AccountID sender) {
        final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
        return pricingUtils.computeViewFunctionGas(now, MINIMUM_GAS_COST);
    }
}
