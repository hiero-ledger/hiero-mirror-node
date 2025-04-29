// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenPauseTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenPauseTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenPauseTransactionSupplier tokenPauseTransactionSupplier = new TokenPauseTransactionSupplier();

        tokenPauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenPauseTransaction actual = tokenPauseTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenPauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenPauseTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenPauseTransactionSupplier tokenPauseTransactionSupplier = new TokenPauseTransactionSupplier();
        tokenPauseTransactionSupplier.setMaxTransactionFee(1);
        tokenPauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenPauseTransaction actual = tokenPauseTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TokenPauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenPauseTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenFreezeTransactionSupplier.class;
    }
}
