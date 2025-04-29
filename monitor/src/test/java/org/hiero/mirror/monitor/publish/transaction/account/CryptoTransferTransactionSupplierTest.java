// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.hiero.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.CRYPTO;
import static org.hiero.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.NFT;
import static org.hiero.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.TOKEN;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransferTransaction;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class CryptoTransferTransactionSupplierTest extends AbstractTransactionSupplierTest {

    private static final Hbar MAX_TRANSACTION_FEE_HBAR = Hbar.fromTinybars(10_000_000L);

    @Test
    void createWithMinimumData() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TransferTransaction::getMaxTransactionFee)
                .returns(Collections.emptyMap(), TransferTransaction::getTokenNftTransfers)
                .returns(Collections.emptyMap(), TransferTransaction::getTokenTransfers)
                .extracting(TransferTransaction::getHbarTransfers, MAP)
                .hasSize(2)
                .containsEntry(ACCOUNT_ID, ONE_TINYBAR.negated())
                .containsEntry(ACCOUNT_ID_2, ONE_TINYBAR);
    }

    @Test
    void createWithCustomCryptoTransfer() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(10);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(CRYPTO));
        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        Hbar transferAmount = Hbar.fromTinybars(10);
        assertThat(actual)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .returns(Collections.emptyMap(), TransferTransaction::getTokenNftTransfers)
                .returns(Collections.emptyMap(), TransferTransaction::getTokenTransfers)
                .extracting(TransferTransaction::getHbarTransfers, MAP)
                .hasSize(2)
                .containsEntry(ACCOUNT_ID, transferAmount.negated())
                .containsEntry(ACCOUNT_ID_2, transferAmount);
    }

    @Test
    void createWithCustomTokenTransfer() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(10);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setTokenId(TOKEN_ID.toString());
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(TOKEN));
        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        assertThat(actual)
                .returns(Collections.emptyMap(), TransferTransaction::getHbarTransfers)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .returns(Collections.emptyMap(), TransferTransaction::getTokenNftTransfers)
                .extracting(TransferTransaction::getTokenTransfers, MAP)
                .hasSize(1)
                .extractingByKey(TOKEN_ID, MAP)
                .hasSize(2)
                .containsEntry(ACCOUNT_ID, -10L)
                .containsEntry(ACCOUNT_ID_2, 10L);
    }

    @Test
    void createWithCustomNftTransfer() {
        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(2);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setNftTokenId(TOKEN_ID.toString());
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setSerialNumber(new AtomicLong(10));
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(NFT));

        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        assertThat(actual)
                .returns(Collections.emptyMap(), TransferTransaction::getHbarTransfers)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .returns(Collections.emptyMap(), TransferTransaction::getTokenTransfers)
                .extracting(TransferTransaction::getTokenNftTransfers, MAP)
                .hasSize(1)
                .extractingByKey(TOKEN_ID, LIST)
                .hasSize(2)
                .extracting("serial", "sender", "receiver")
                .containsExactlyInAnyOrder(tuple(10L, ACCOUNT_ID, ACCOUNT_ID_2), tuple(11L, ACCOUNT_ID, ACCOUNT_ID_2));
    }

    @Test
    void createWithCustomAllTransfer() {
        TokenId nftTokenId = TokenId.fromString("0.0.21");
        Hbar transferAmount = Hbar.fromTinybars(2);

        CryptoTransferTransactionSupplier cryptoTransferTransactionSupplier = new CryptoTransferTransactionSupplier();
        cryptoTransferTransactionSupplier.setAmount(2);
        cryptoTransferTransactionSupplier.setMaxTransactionFee(1);
        cryptoTransferTransactionSupplier.setNftTokenId(nftTokenId.toString());
        cryptoTransferTransactionSupplier.setRecipientAccountId(ACCOUNT_ID_2.toString());
        cryptoTransferTransactionSupplier.setSenderAccountId(ACCOUNT_ID.toString());
        cryptoTransferTransactionSupplier.setSerialNumber(new AtomicLong(10));
        cryptoTransferTransactionSupplier.setTokenId(TOKEN_ID.toString());
        cryptoTransferTransactionSupplier.setTransferTypes(Set.of(CRYPTO, NFT, TOKEN));

        TransferTransaction actual = cryptoTransferTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TransferTransaction::getMaxTransactionFee)
                .satisfies(a -> assertThat(a.getHbarTransfers())
                        .containsEntry(ACCOUNT_ID, transferAmount.negated())
                        .containsEntry(ACCOUNT_ID_2, transferAmount))
                .satisfies(a -> assertThat(a)
                        .extracting(TransferTransaction::getTokenNftTransfers, MAP)
                        .hasSize(1)
                        .extractingByKey(nftTokenId, LIST)
                        .extracting("serial", "sender", "receiver")
                        .containsExactlyInAnyOrder(
                                tuple(10L, ACCOUNT_ID, ACCOUNT_ID_2), tuple(11L, ACCOUNT_ID, ACCOUNT_ID_2)))
                .extracting(TransferTransaction::getHbarTransfers, MAP)
                .hasSize(2)
                .containsEntry(ACCOUNT_ID, transferAmount.negated())
                .containsEntry(ACCOUNT_ID_2, transferAmount);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return CryptoTransferTransactionSupplier.class;
    }
}
