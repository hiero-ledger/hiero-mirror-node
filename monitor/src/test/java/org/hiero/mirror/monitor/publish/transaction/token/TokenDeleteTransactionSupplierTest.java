// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenDeleteTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenDeleteTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenDeleteTransactionSupplier tokenDeleteTransactionSupplier = new TokenDeleteTransactionSupplier();
        tokenDeleteTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenDeleteTransaction actual = tokenDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenDeleteTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenDeleteTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenDeleteTransactionSupplier tokenDeleteTransactionSupplier = new TokenDeleteTransactionSupplier();
        tokenDeleteTransactionSupplier.setMaxTransactionFee(1);
        tokenDeleteTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenDeleteTransaction actual = tokenDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TokenDeleteTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenDeleteTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenDeleteTransactionSupplier.class;
    }
}
