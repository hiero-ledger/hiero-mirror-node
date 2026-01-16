// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenGrantKycTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenGrantKycTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenGrantKycTransactionSupplier tokenGrantKycTransactionSupplier = new TokenGrantKycTransactionSupplier();
        tokenGrantKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenGrantKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenGrantKycTransaction actual = tokenGrantKycTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenGrantKycTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenGrantKycTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenGrantKycTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenGrantKycTransactionSupplier tokenGrantKycTransactionSupplier = new TokenGrantKycTransactionSupplier();
        tokenGrantKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenGrantKycTransactionSupplier.setMaxTransactionFee(1);
        tokenGrantKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenGrantKycTransaction actual = tokenGrantKycTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenGrantKycTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenGrantKycTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenGrantKycTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenGrantKycTransactionSupplier.class;
    }
}
