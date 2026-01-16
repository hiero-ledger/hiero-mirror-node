// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenCreateTransactionSupplier tokenCreateTransactionSupplier = new TokenCreateTransactionSupplier();
        tokenCreateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());
        TokenCreateTransaction actual = tokenCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(null, TokenCreateTransaction::getAdminKey)
                .returns(ACCOUNT_ID, TokenCreateTransaction::getAutoRenewAccountId)
                .returns(10, TokenCreateTransaction::getDecimals)
                .returns(null, TokenCreateTransaction::getFeeScheduleKey)
                .returns(false, TokenCreateTransaction::getFreezeDefault)
                .returns(null, TokenCreateTransaction::getFreezeKey)
                .returns(1000000000L, TokenCreateTransaction::getInitialSupply)
                .returns(null, TokenCreateTransaction::getKycKey)
                .returns(0L, TokenCreateTransaction::getMaxSupply)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenCreateTransaction::getMaxTransactionFee)
                .returns(null, TokenCreateTransaction::getSupplyKey)
                .returns(TokenSupplyType.INFINITE, TokenCreateTransaction::getSupplyType)
                .returns(TokenType.FUNGIBLE_COMMON, TokenCreateTransaction::getTokenType)
                .returns(ACCOUNT_ID, TokenCreateTransaction::getTreasuryAccountId)
                .returns(null, TokenCreateTransaction::getWipeKey)
                .satisfies(a -> assertThat(a.getTokenName()).contains("_name"))
                .satisfies(a -> assertThat(a.getTokenSymbol()).hasSize(5))
                .extracting(TokenCreateTransaction::getTokenMemo, STRING)
                .contains("Mirror node created test token");
    }

    @Test
    void createWithCustomFungibleData() {
        PublicKey key = PrivateKey.generateED25519().getPublicKey();

        TokenCreateTransactionSupplier tokenCreateTransactionSupplier = new TokenCreateTransactionSupplier();
        tokenCreateTransactionSupplier.setAdminKey(key.toString());
        tokenCreateTransactionSupplier.setDecimals(1);
        tokenCreateTransactionSupplier.setFreezeDefault(true);
        tokenCreateTransactionSupplier.setInitialSupply(1);
        tokenCreateTransactionSupplier.setMaxSupply(1);
        tokenCreateTransactionSupplier.setMaxTransactionFee(1);
        tokenCreateTransactionSupplier.setSupplyType(TokenSupplyType.FINITE);
        tokenCreateTransactionSupplier.setSymbol("TEST");
        tokenCreateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());
        tokenCreateTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenCreateTransaction actual = tokenCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(key, TokenCreateTransaction::getAdminKey)
                .returns(ACCOUNT_ID, TokenCreateTransaction::getAutoRenewAccountId)
                .returns(1, TokenCreateTransaction::getDecimals)
                .returns(key, TokenCreateTransaction::getFeeScheduleKey)
                .returns(true, TokenCreateTransaction::getFreezeDefault)
                .returns(key, TokenCreateTransaction::getFreezeKey)
                .returns(1L, TokenCreateTransaction::getInitialSupply)
                .returns(key, TokenCreateTransaction::getKycKey)
                .returns(1L, TokenCreateTransaction::getMaxSupply)
                .returns(ONE_TINYBAR, TokenCreateTransaction::getMaxTransactionFee)
                .returns(key, TokenCreateTransaction::getSupplyKey)
                .returns(TokenSupplyType.FINITE, TokenCreateTransaction::getSupplyType)
                .returns(TokenType.FUNGIBLE_COMMON, TokenCreateTransaction::getTokenType)
                .returns(ACCOUNT_ID, TokenCreateTransaction::getTreasuryAccountId)
                .returns(key, TokenCreateTransaction::getWipeKey)
                .extracting(TokenCreateTransaction::getTokenMemo, STRING)
                .contains("Mirror node created test token");
    }

    @Test
    void createWithNonFungibleData() {
        TokenCreateTransactionSupplier tokenCreateTransactionSupplier = new TokenCreateTransactionSupplier();
        tokenCreateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());
        tokenCreateTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenCreateTransaction actual = tokenCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(null, TokenCreateTransaction::getAdminKey)
                .returns(ACCOUNT_ID, TokenCreateTransaction::getAutoRenewAccountId)
                .returns(0, TokenCreateTransaction::getDecimals)
                .returns(null, TokenCreateTransaction::getFeeScheduleKey)
                .returns(false, TokenCreateTransaction::getFreezeDefault)
                .returns(null, TokenCreateTransaction::getFreezeKey)
                .returns(0L, TokenCreateTransaction::getInitialSupply)
                .returns(null, TokenCreateTransaction::getKycKey)
                .returns(0L, TokenCreateTransaction::getMaxSupply)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenCreateTransaction::getMaxTransactionFee)
                .returns(null, TokenCreateTransaction::getSupplyKey)
                .returns(TokenSupplyType.INFINITE, TokenCreateTransaction::getSupplyType)
                .returns(TokenType.NON_FUNGIBLE_UNIQUE, TokenCreateTransaction::getTokenType)
                .returns(ACCOUNT_ID, TokenCreateTransaction::getTreasuryAccountId)
                .returns(null, TokenCreateTransaction::getWipeKey)
                .extracting(TokenCreateTransaction::getTokenMemo, STRING)
                .contains("Mirror node created test token");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenCreateTransactionSupplier.class;
    }
}
