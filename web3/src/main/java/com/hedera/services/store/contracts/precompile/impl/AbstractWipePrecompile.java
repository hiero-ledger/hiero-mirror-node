// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.WipeResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txn.token.WipeLogic;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of AbstractWipePrecompile from hedera-services repo.
 * <p>
 * Differences with the original:
 * 1. Implements a modified {@link Precompile} interface
 * 2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 * 3. Run method is modified to accept {@link Store} and {@link TransactionBody} argument in order to achieve stateless behaviour
 */
public abstract class AbstractWipePrecompile extends AbstractWritePrecompile {

    private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();

    final WipeLogic wipeLogic;

    protected AbstractWipePrecompile(
            PrecompilePricingUtils pricingUtils, WipeLogic wipeLogic, SyntheticTxnFactory syntheticTxnFactory) {
        super(pricingUtils, syntheticTxnFactory);
        this.wipeLogic = wipeLogic;
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();

        final var wipeBody = transactionBody.getTokenWipe();
        final var tokenId = Id.fromGrpcToken(wipeBody.getToken());
        final var accountId = Id.fromGrpcAccount(wipeBody.getAccount());

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var validity = wipeLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        final TokenModificationResult tokenModificationResult;
        if (wipeBody.getSerialNumbersCount() > 0) {
            final var targetSerialNumbers = wipeBody.getSerialNumbersList();
            tokenModificationResult = wipeLogic.wipe(tokenId, accountId, 0, targetSerialNumbers, store);
        } else {
            tokenModificationResult = wipeLogic.wipe(tokenId, accountId, wipeBody.getAmount(), NO_SERIAL_NOS, store);
        }

        final var modifiedToken = tokenModificationResult.token();
        return new WipeResult(
                TokenType.FUNGIBLE_COMMON == modifiedToken.getType() ? modifiedToken.getTotalSupply() : 0L,
                TokenType.NON_FUNGIBLE_UNIQUE == modifiedToken.getType()
                        ? modifiedToken.mintedUniqueTokens().stream()
                                .map(UniqueToken::getSerialNumber)
                                .toList()
                        : new ArrayList<>());
    }
}
