// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import java.time.Duration;
import java.time.Instant;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenUpdateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenUpdateTransactionSupplier tokenUpdateTransactionSupplier = new TokenUpdateTransactionSupplier();
        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUpdateTransaction actual = tokenUpdateTransactionSupplier.get();

        assertThat(actual)
                .returns(null, TokenUpdateTransaction::getAdminKey)
                .returns(null, TokenUpdateTransaction::getAutoRenewAccountId)
                .returns(Duration.ofSeconds(8000000), TokenUpdateTransaction::getAutoRenewPeriod)
                .returns(null, TokenUpdateTransaction::getExpirationTime)
                .returns(null, TokenUpdateTransaction::getFeeScheduleKey)
                .returns(null, TokenUpdateTransaction::getFreezeKey)
                .returns(null, TokenUpdateTransaction::getKycKey)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUpdateTransaction::getMaxTransactionFee)
                .returns(null, TokenUpdateTransaction::getSupplyKey)
                .returns(TOKEN_ID, TokenUpdateTransaction::getTokenId)
                .returns("HMNT_name", TokenUpdateTransaction::getTokenName)
                .returns("HMNT", TokenUpdateTransaction::getTokenSymbol)
                .returns(null, TokenUpdateTransaction::getTreasuryAccountId)
                .returns(null, TokenUpdateTransaction::getWipeKey)
                .extracting(TokenUpdateTransaction::getTokenMemo, STRING)
                .contains("Mirror node updated test token");
    }

    @Test
    void createWithCustomData() {
        Duration autoRenewPeriod = Duration.ofSeconds(1);
        PublicKey key = PrivateKey.generateED25519().getPublicKey();

        TokenUpdateTransactionSupplier tokenUpdateTransactionSupplier = new TokenUpdateTransactionSupplier();
        tokenUpdateTransactionSupplier.setAdminKey(key.toString());
        tokenUpdateTransactionSupplier.setAutoRenewPeriod(autoRenewPeriod);
        tokenUpdateTransactionSupplier.setMaxTransactionFee(1);
        tokenUpdateTransactionSupplier.setSymbol("TEST");
        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenUpdateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());

        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUpdateTransaction actual = tokenUpdateTransactionSupplier.get();
        assertThat(actual)
                .returns(key, TokenUpdateTransaction::getAdminKey)
                .returns(ACCOUNT_ID, TokenUpdateTransaction::getAutoRenewAccountId)
                .returns(autoRenewPeriod, TokenUpdateTransaction::getAutoRenewPeriod)
                .returns(null, TokenUpdateTransaction::getExpirationTime)
                .returns(key, TokenUpdateTransaction::getFeeScheduleKey)
                .returns(key, TokenUpdateTransaction::getFreezeKey)
                .returns(key, TokenUpdateTransaction::getKycKey)
                .returns(ONE_TINYBAR, TokenUpdateTransaction::getMaxTransactionFee)
                .returns(key, TokenUpdateTransaction::getSupplyKey)
                .returns(TOKEN_ID, TokenUpdateTransaction::getTokenId)
                .returns("TEST_name", TokenUpdateTransaction::getTokenName)
                .returns("TEST", TokenUpdateTransaction::getTokenSymbol)
                .returns(ACCOUNT_ID, TokenUpdateTransaction::getTreasuryAccountId)
                .returns(key, TokenUpdateTransaction::getWipeKey)
                .extracting(TokenUpdateTransaction::getTokenMemo, STRING)
                .contains("Mirror node updated test token");
    }

    @Test
    void createWithExpiration() {
        TokenUpdateTransactionSupplier tokenUpdateTransactionSupplier = new TokenUpdateTransactionSupplier();
        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenUpdateTransactionSupplier.setExpirationTime(Instant.MAX);
        TokenUpdateTransaction actual = tokenUpdateTransactionSupplier.get();

        assertThat(actual)
                .returns(null, TokenUpdateTransaction::getAdminKey)
                .returns(null, TokenUpdateTransaction::getAutoRenewAccountId)
                .returns(null, TokenUpdateTransaction::getAutoRenewPeriod)
                .returns(Instant.MAX, TokenUpdateTransaction::getExpirationTime)
                .returns(null, TokenUpdateTransaction::getFeeScheduleKey)
                .returns(null, TokenUpdateTransaction::getFreezeKey)
                .returns(null, TokenUpdateTransaction::getKycKey)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUpdateTransaction::getMaxTransactionFee)
                .returns(null, TokenUpdateTransaction::getSupplyKey)
                .returns(TOKEN_ID, TokenUpdateTransaction::getTokenId)
                .returns("HMNT_name", TokenUpdateTransaction::getTokenName)
                .returns("HMNT", TokenUpdateTransaction::getTokenSymbol)
                .returns(null, TokenUpdateTransaction::getTreasuryAccountId)
                .returns(null, TokenUpdateTransaction::getWipeKey)
                .extracting(TokenUpdateTransaction::getTokenMemo, STRING)
                .contains("Mirror node updated test token");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenUpdateTransactionSupplier.class;
    }
}
