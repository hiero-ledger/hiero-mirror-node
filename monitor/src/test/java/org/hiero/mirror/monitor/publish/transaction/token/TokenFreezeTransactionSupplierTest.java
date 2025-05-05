// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenFreezeTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenFreezeTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenFreezeTransactionSupplier tokenFreezeTransactionSupplier = new TokenFreezeTransactionSupplier();
        tokenFreezeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenFreezeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenFreezeTransaction actual = tokenFreezeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenFreezeTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenFreezeTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenFreezeTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenFreezeTransactionSupplier tokenFreezeTransactionSupplier = new TokenFreezeTransactionSupplier();
        tokenFreezeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenFreezeTransactionSupplier.setMaxTransactionFee(1);
        tokenFreezeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenFreezeTransaction actual = tokenFreezeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenFreezeTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenFreezeTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenFreezeTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenFreezeTransactionSupplier.class;
    }
}
