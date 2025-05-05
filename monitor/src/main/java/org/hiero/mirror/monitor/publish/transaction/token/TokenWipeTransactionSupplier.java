// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.token;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TokenWipeTransaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;

@Data
public class TokenWipeTransactionSupplier implements TransactionSupplier<TokenWipeTransaction> {

    @NotBlank
    private String accountId;

    @Min(1)
    private long amount = 1;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    private AtomicLong serialNumber = new AtomicLong(1); // The serial number to transfer.  Increments over time.

    @NotBlank
    private String tokenId;

    @NotNull
    private TokenType type = TokenType.FUNGIBLE_COMMON;

    @Override
    public TokenWipeTransaction get() {

        TokenWipeTransaction transaction = new TokenWipeTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenId(TokenId.fromString(tokenId));

        if (type == TokenType.NON_FUNGIBLE_UNIQUE) {
            for (int i = 0; i < amount; i++) {
                transaction.addSerial(serialNumber.getAndIncrement());
            }
        } else {
            transaction.setAmount(amount);
        }

        return transaction;
    }
}
