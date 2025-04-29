// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import java.util.List;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenAssociateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenAssociateTransactionSupplier tokenAssociateTransactionSupplier = new TokenAssociateTransactionSupplier();
        tokenAssociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenAssociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenAssociateTransaction actual = tokenAssociateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenAssociateTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenAssociateTransaction::getMaxTransactionFee)
                .returns(List.of(TOKEN_ID), TokenAssociateTransaction::getTokenIds);
    }

    @Test
    void createWithCustomData() {
        TokenAssociateTransactionSupplier tokenAssociateTransactionSupplier = new TokenAssociateTransactionSupplier();
        tokenAssociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenAssociateTransactionSupplier.setMaxTransactionFee(1);
        tokenAssociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenAssociateTransaction actual = tokenAssociateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenAssociateTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenAssociateTransaction::getMaxTransactionFee)
                .returns(List.of(TOKEN_ID), TokenAssociateTransaction::getTokenIds);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenAssociateTransactionSupplier.class;
    }
}
