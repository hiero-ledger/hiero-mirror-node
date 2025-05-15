// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenRevokeKycTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenRevokeKycTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenRevokeKycTransactionSupplier tokenRevokeKycTransactionSupplier = new TokenRevokeKycTransactionSupplier();
        tokenRevokeKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenRevokeKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenRevokeKycTransaction actual = tokenRevokeKycTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenRevokeKycTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenRevokeKycTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenRevokeKycTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenRevokeKycTransactionSupplier tokenRevokeKycTransactionSupplier = new TokenRevokeKycTransactionSupplier();
        tokenRevokeKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenRevokeKycTransactionSupplier.setMaxTransactionFee(1);
        tokenRevokeKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenRevokeKycTransaction actual = tokenRevokeKycTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenRevokeKycTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenRevokeKycTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenRevokeKycTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenRevokeKycTransactionSupplier.class;
    }
}
