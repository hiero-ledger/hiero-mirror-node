// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.account;

import static org.hiero.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.CRYPTO;
import static org.hiero.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.NFT;
import static org.hiero.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier.TransferType.TOKEN;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransferTransaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import lombok.Getter;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;

@Data
public class CryptoTransferTransactionSupplier implements TransactionSupplier<TransferTransaction> {

    @Min(1)
    private long amount = 1;

    @Min(1)
    private long maxTransactionFee = 10_000_000L;

    private String nftTokenId;

    @NotBlank
    private String recipientAccountId;

    @NotBlank
    private String senderAccountId;

    private AtomicLong serialNumber = new AtomicLong(1); // The serial number to transfer.  Increments over time.

    private String tokenId;

    @NotNull
    private Set<TransferType> transferTypes = EnumSet.of(CRYPTO);

    @Getter(lazy = true)
    private final AccountId recipientId = AccountId.fromString(recipientAccountId);

    @Getter(lazy = true)
    private final AccountId senderId = AccountId.fromString(senderAccountId);

    @Getter(lazy = true)
    private final TokenId transferTokenId = TokenId.fromString(tokenId);

    @Getter(lazy = true)
    private final TokenId transferNftTokenId = TokenId.fromString(nftTokenId);

    @Override
    public TransferTransaction get() {

        TransferTransaction transferTransaction =
                new TransferTransaction().setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee));

        if (transferTypes.contains(CRYPTO)) {
            addCryptoTransfers(transferTransaction, getRecipientId(), getSenderId());
        }

        if (transferTypes.contains(NFT)) {
            addNftTransfers(transferTransaction, getTransferNftTokenId(), getRecipientId(), getSenderId());
        }

        if (transferTypes.contains(TOKEN)) {
            addTokenTransfers(transferTransaction, getTransferTokenId(), getRecipientId(), getSenderId());
        }

        return transferTransaction;
    }

    private void addCryptoTransfers(
            TransferTransaction transferTransaction, AccountId recipientId, AccountId senderId) {
        Hbar hbarAmount = Hbar.fromTinybars(amount);
        transferTransaction.addHbarTransfer(recipientId, hbarAmount).addHbarTransfer(senderId, hbarAmount.negated());
    }

    private void addNftTransfers(
            TransferTransaction transferTransaction, TokenId token, AccountId recipientId, AccountId senderId) {
        for (int i = 0; i < amount; i++) {
            transferTransaction.addNftTransfer(new NftId(token, serialNumber.getAndIncrement()), senderId, recipientId);
        }
    }

    private void addTokenTransfers(
            TransferTransaction transferTransaction, TokenId token, AccountId recipientId, AccountId senderId) {
        transferTransaction
                .addTokenTransfer(token, recipientId, amount)
                .addTokenTransfer(token, senderId, Math.negateExact(amount));
    }

    public enum TransferType {
        CRYPTO,
        NFT,
        TOKEN
    }
}
