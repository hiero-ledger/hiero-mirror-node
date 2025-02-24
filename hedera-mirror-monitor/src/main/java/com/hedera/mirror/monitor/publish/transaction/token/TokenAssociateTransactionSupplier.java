// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction.token;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class TokenAssociateTransactionSupplier implements TransactionSupplier<TokenAssociateTransaction> {

    @NotBlank
    private String accountId;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String tokenId;

    @Override
    public TokenAssociateTransaction get() {

        return new TokenAssociateTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenIds(List.of(TokenId.fromString(tokenId)));
    }
}
