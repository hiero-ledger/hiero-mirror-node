// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenUnpauseTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenUnpauseTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenUnpauseTransactionSupplier tokenUnpauseTransactionSupplier = new TokenUnpauseTransactionSupplier();

        tokenUnpauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUnpauseTransaction actual = tokenUnpauseTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUnpauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenUnpauseTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenUnpauseTransactionSupplier tokenUnpauseTransactionSupplier = new TokenUnpauseTransactionSupplier();
        tokenUnpauseTransactionSupplier.setMaxTransactionFee(1);
        tokenUnpauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUnpauseTransaction actual = tokenUnpauseTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TokenUnpauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenUnpauseTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenFreezeTransactionSupplier.class;
    }
}
