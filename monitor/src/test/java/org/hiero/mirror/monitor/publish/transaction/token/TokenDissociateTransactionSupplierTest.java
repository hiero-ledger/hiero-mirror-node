// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenDissociateTransaction;
import java.util.List;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenDissociateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenDissociateTransactionSupplier tokenDissociateTransactionSupplier =
                new TokenDissociateTransactionSupplier();
        tokenDissociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenDissociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenDissociateTransaction actual = tokenDissociateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenDissociateTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenDissociateTransaction::getMaxTransactionFee)
                .returns(List.of(TOKEN_ID), TokenDissociateTransaction::getTokenIds);
    }

    @Test
    void createWithCustomData() {
        TokenDissociateTransactionSupplier tokenDissociateTransactionSupplier =
                new TokenDissociateTransactionSupplier();
        tokenDissociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenDissociateTransactionSupplier.setMaxTransactionFee(1);
        tokenDissociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenDissociateTransaction actual = tokenDissociateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenDissociateTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenDissociateTransaction::getMaxTransactionFee)
                .returns(List.of(TOKEN_ID), TokenDissociateTransaction::getTokenIds);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenDissociateTransactionSupplier.class;
    }
}
