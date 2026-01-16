// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.TokenType;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenBurnTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenBurnTransactionSupplier tokenBurnTransactionSupplier = new TokenBurnTransactionSupplier();
        tokenBurnTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenBurnTransaction actual = tokenBurnTransactionSupplier.get();

        assertThat(actual)
                .returns(1L, TokenBurnTransaction::getAmount)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenBurnTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenBurnTransaction::getSerials)
                .returns(TOKEN_ID, TokenBurnTransaction::getTokenId);
    }

    @Test
    void createWithCustomFungibleData() {
        TokenBurnTransactionSupplier tokenBurnTransactionSupplier = new TokenBurnTransactionSupplier();
        tokenBurnTransactionSupplier.setAmount(2);
        tokenBurnTransactionSupplier.setMaxTransactionFee(1);
        tokenBurnTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenBurnTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenBurnTransaction actual = tokenBurnTransactionSupplier.get();

        assertThat(actual)
                .returns(2L, TokenBurnTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenBurnTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenBurnTransaction::getSerials)
                .returns(TOKEN_ID, TokenBurnTransaction::getTokenId);
    }

    @Test
    void createWithCustomNonFungibleData() {
        TokenBurnTransactionSupplier tokenBurnTransactionSupplier = new TokenBurnTransactionSupplier();
        tokenBurnTransactionSupplier.setAmount(2);
        tokenBurnTransactionSupplier.setMaxTransactionFee(1);
        tokenBurnTransactionSupplier.setSerialNumber(new AtomicLong(10));
        tokenBurnTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenBurnTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenBurnTransaction actual = tokenBurnTransactionSupplier.get();

        assertThat(actual)
                .returns(0L, TokenBurnTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenBurnTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenBurnTransaction::getTokenId)
                .returns(Arrays.asList(10L, 11L), TokenBurnTransaction::getSerials);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenBurnTransactionSupplier.class;
    }
}
