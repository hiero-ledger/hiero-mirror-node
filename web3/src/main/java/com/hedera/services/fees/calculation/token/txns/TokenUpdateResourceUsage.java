// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation.token.txns;

import static com.hedera.services.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.usage.token.TokenUpdateUsage;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.security.InvalidKeyException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;

/**
 * Copied ResourceUsage type from hedera-services. Differences with the original:
 * 1. Uses the store interface to get the token in usageGiven
 * 2. Moved GetTokenInfoResourceUsage::ifPresent to a local method and modified it to convert JKey to Key
 * and to accept Token instead of TokenInfo
 */
public class TokenUpdateResourceUsage extends AbstractTokenResourceUsage implements TxnResourceUsageEstimator {
    private static final BiFunction<TransactionBody, TxnUsageEstimator, TokenUpdateUsage> factory =
            TokenUpdateUsage::newEstimate;

    private final Store store;

    public TokenUpdateResourceUsage(final EstimatorFactory estimatorFactory, final Store store) {
        super(estimatorFactory);
        this.store = store;
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasTokenUpdate();
    }

    @Override
    public FeeData usageGiven(final TransactionBody txn, final SigValueObj svo) {
        final var op = txn.getTokenUpdate();
        final var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        final var token = store.getToken(asTypedEvmAddress(op.getToken()), OnMissing.DONT_THROW);
        if (!token.equals(Token.getEmptyToken())) {
            final var estimate = factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS))
                    .givenCurrentExpiry(token.getExpiry())
                    .givenCurrentAdminKey(ifPresent(token, Token::hasAdminKey, Token::getAdminKey))
                    .givenCurrentFreezeKey(ifPresent(token, Token::hasFreezeKey, Token::getFreezeKey))
                    .givenCurrentWipeKey(ifPresent(token, Token::hasWipeKey, Token::getWipeKey))
                    .givenCurrentSupplyKey(ifPresent(token, Token::hasSupplyKey, Token::getSupplyKey))
                    .givenCurrentKycKey(ifPresent(token, Token::hasKycKey, Token::getKycKey))
                    .givenCurrentFeeScheduleKey(ifPresent(token, Token::hasFeeScheduleKey, Token::getFeeScheduleKey))
                    .givenCurrentPauseKey(ifPresent(token, Token::hasPauseKey, Token::getPauseKey))
                    .givenCurrentMemo(token.getMemo())
                    .givenCurrentName(token.getName())
                    .givenCurrentSymbol(token.getSymbol());
            if (token.hasAutoRenewAccount()) {
                estimate.givenCurrentlyUsingAutoRenewAccount();
            }
            return estimate.get();
        } else {
            return FeeData.getDefaultInstance();
        }
    }

    private Optional<Key> ifPresent(
            final Token info, final Predicate<Token> check, final Function<Token, JKey> getter) {
        if (check.test(info)) {
            try {
                final var key = JKey.mapJKey(getter.apply(info));
                return Optional.of(key);
            } catch (InvalidKeyException e) {
                // empty
            }
        }
        return Optional.empty();
    }
}
