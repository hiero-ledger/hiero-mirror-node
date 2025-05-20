// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DISSOCIATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.DissociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractDissociatePrecompile implements Precompile {

    private final DissociateLogic dissociateLogic;

    protected final PrecompilePricingUtils pricingUtils;

    protected AbstractDissociatePrecompile(
            final DissociateLogic dissociateLogic, final PrecompilePricingUtils pricingUtils) {
        this.dissociateLogic = dissociateLogic;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long getMinimumFeeInTinybars(
            Timestamp consensusTime, TransactionBody transactionBody, final AccountID sender) {
        return pricingUtils.getMinimumPriceInTinybars(DISSOCIATE, consensusTime);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var accountId = Id.fromGrpcAccount(
                Objects.requireNonNull(transactionBody).getTokenDissociate().getAccount());

        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var validity = dissociateLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        dissociateLogic.dissociate(
                accountId.asEvmAddress(),
                transactionBody.getTokenDissociate().getTokensList().stream()
                        .map(EntityIdUtils::asTypedEvmAddress)
                        .toList(),
                store);

        return new EmptyRunResult();
    }
}
