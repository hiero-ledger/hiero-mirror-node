// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TokenWipeTransaction;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenWipeTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenWipeTransactionSupplier tokenWipeTransactionSupplier = new TokenWipeTransactionSupplier();
        tokenWipeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenWipeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenWipeTransaction actual = tokenWipeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenWipeTransaction::getAccountId)
                .returns(1L, TokenWipeTransaction::getAmount)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenWipeTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenWipeTransaction::getSerials)
                .returns(TOKEN_ID, TokenWipeTransaction::getTokenId);
    }

    @Test
    void createWithCustomFungibleData() {
        TokenWipeTransactionSupplier tokenWipeTransactionSupplier = new TokenWipeTransactionSupplier();
        tokenWipeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenWipeTransactionSupplier.setAmount(2);
        tokenWipeTransactionSupplier.setMaxTransactionFee(1);
        tokenWipeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenWipeTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenWipeTransaction actual = tokenWipeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenWipeTransaction::getAccountId)
                .returns(2L, TokenWipeTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenWipeTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenWipeTransaction::getSerials)
                .returns(TOKEN_ID, TokenWipeTransaction::getTokenId);
    }

    @Test
    void createWithCustomNonFungibleData() {
        TokenWipeTransactionSupplier tokenWipeTransactionSupplier = new TokenWipeTransactionSupplier();
        tokenWipeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenWipeTransactionSupplier.setAmount(2);
        tokenWipeTransactionSupplier.setMaxTransactionFee(1);
        tokenWipeTransactionSupplier.setSerialNumber(new AtomicLong(10));
        tokenWipeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenWipeTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenWipeTransaction actual = tokenWipeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenWipeTransaction::getAccountId)
                .returns(0L, TokenWipeTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenWipeTransaction::getMaxTransactionFee)
                .returns(List.of(10L, 11L), TokenWipeTransaction::getSerials)
                .returns(TOKEN_ID, TokenWipeTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenWipeTransactionSupplier.class;
    }
}
