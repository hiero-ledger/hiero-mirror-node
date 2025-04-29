// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenMintTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        assertThat(actual)
                .returns(1L, TokenMintTransaction::getAmount)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenMintTransaction::getMetadata)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId);
    }

    @Test
    void createWithCustomFungibleData() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        assertThat(actual)
                .returns(2L, TokenMintTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenMintTransaction::getMetadata)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId);
    }

    @Test
    void createWithCustomNonFungibleDataMessageSize() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setMetadataSize(14);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();
        assertThat(actual)
                .returns(0L, TokenMintTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId)
                .extracting(TokenMintTransaction::getMetadata)
                .returns(2, List::size)
                .returns(14, metadataList -> metadataList.get(0).length)
                .returns(14, metadataList -> metadataList.get(1).length);
    }

    @Test
    void createWithCustomNonFungibleeDataMessage() {
        String metadata = "TokenMintTransactionSupplierTest";

        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setMetadata(metadata);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        assertThat(actual)
                .returns(0L, TokenMintTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId)
                .extracting(TokenMintTransaction::getMetadata, LIST)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        metadata.getBytes(StandardCharsets.UTF_8), metadata.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenMintTransactionSupplier.class;
    }
}
