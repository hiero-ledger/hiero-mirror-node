// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UPDATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of AbstractTokenUpdatePrecompile from hedera-services repo.
 * Differences with the original:
 * 1. Implements a modified {@link Precompile} interface
 * 2. Removed class fields and adapted constructor in order to achieve stateless behaviour
 * 3. The run method does not handle any signature verification and is modified to obtain
 * the update type function from the MessageFrame input data parameter. It will not be
 * overridden in the extending classes
 */
public abstract class AbstractTokenUpdatePrecompile extends AbstractWritePrecompile {

    private final TokenUpdateLogic tokenUpdateLogic;
    private final OptionValidator contextOptionValidator;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    protected AbstractTokenUpdatePrecompile(
            TokenUpdateLogic tokenUpdateLogic,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            OptionValidator contextOptionValidator,
            PrecompilePricingUtils pricingUtils,
            SyntheticTxnFactory syntheticTxnFactory) {
        super(pricingUtils, syntheticTxnFactory);
        this.tokenUpdateLogic = tokenUpdateLogic;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.contextOptionValidator = contextOptionValidator;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody, AccountID sender) {
        return pricingUtils.getMinimumPriceInTinybars(UPDATE, consensusTime);
    }

    @Override
    @SuppressWarnings("java:S131")
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var updateOp = transactionBody.getTokenUpdate();
        Objects.requireNonNull(updateOp);

        final var validity = tokenUpdateLogic.validate(transactionBody);
        validateTrue(validity == OK, validity);

        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var hederaTokenStore = initializeHederaTokenStore(store);
        final var functionId = frame.getInputData().getInt(0);
        switch (functionId) {
            case AbiConstants.ABI_ID_UPDATE_TOKEN_INFO,
                    AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2,
                    AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3 ->
                tokenUpdateLogic.updateToken(
                        transactionBody.getTokenUpdate(),
                        frame.getBlockValues().getTimestamp(),
                        store,
                        hederaTokenStore);
            case AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO, AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2 ->
                tokenUpdateLogic.updateTokenExpiryInfo(transactionBody.getTokenUpdate(), store, hederaTokenStore);
            case AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS ->
                tokenUpdateLogic.updateTokenKeys(transactionBody.getTokenUpdate(), 0L, hederaTokenStore);
        }

        return new EmptyRunResult();
    }

    private HederaTokenStore initializeHederaTokenStore(Store store) {
        return new HederaTokenStore(contextOptionValidator, mirrorNodeEvmProperties, store);
    }
}
