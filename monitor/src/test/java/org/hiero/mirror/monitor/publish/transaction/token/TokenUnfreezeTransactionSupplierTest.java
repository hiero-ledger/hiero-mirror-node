// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenUnfreezeTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenUnfreezeTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenUnfreezeTransactionSupplier tokenUnfreezeTransactionSupplier = new TokenUnfreezeTransactionSupplier();
        tokenUnfreezeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenUnfreezeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUnfreezeTransaction actual = tokenUnfreezeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenUnfreezeTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUnfreezeTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenUnfreezeTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenUnfreezeTransactionSupplier tokenUnfreezeTransactionSupplier = new TokenUnfreezeTransactionSupplier();
        tokenUnfreezeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenUnfreezeTransactionSupplier.setMaxTransactionFee(1);
        tokenUnfreezeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUnfreezeTransaction actual = tokenUnfreezeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenUnfreezeTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenUnfreezeTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenUnfreezeTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenUnfreezeTransactionSupplier.class;
    }
}
