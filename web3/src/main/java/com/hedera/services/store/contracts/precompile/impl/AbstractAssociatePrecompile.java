// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.ASSOCIATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of AssociatePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Run method is modified to return {@link RunResult}, so that getSuccessResultFor is based on this record
 *  4. Run method is modified to accept {@link Store} as a parameter, so that we abstract from the internal state that is used for the execution
 *  4. Run method does not handle signature verification and has a logic based on modified {@link AssociateLogic} that accepts the {@link Store} as a parameter
 */
public abstract class AbstractAssociatePrecompile implements Precompile {

    protected final PrecompilePricingUtils pricingUtils;
    protected final SyntheticTxnFactory syntheticTxnFactory;
    protected final AssociateLogic associateLogic;

    protected AbstractAssociatePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final AssociateLogic associateLogic) {
        this.pricingUtils = pricingUtils;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.associateLogic = associateLogic;
    }

    @Override
    public long getMinimumFeeInTinybars(
            Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        return pricingUtils.getMinimumPriceInTinybars(ASSOCIATE, consensusTime);
    }

    @Override
    public RunResult run(MessageFrame frame, final TransactionBody transactionBody) {
        final var accountId = Id.fromGrpcAccount(
                Objects.requireNonNull(transactionBody).getTokenAssociate().getAccount());
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();

        // --- Execute the transaction and capture its results ---

        final var validity = associateLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        associateLogic.associate(
                accountId.asEvmAddress(),
                transactionBody.getTokenAssociate().getTokensList().stream()
                        .map(EntityIdUtils::asTypedEvmAddress)
                        .toList(),
                store);

        return new EmptyRunResult();
    }
}
