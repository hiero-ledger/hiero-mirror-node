// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction.account;

import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountDeleteTransactionSupplier implements TransactionSupplier<AccountDeleteTransaction> {

    @NotBlank
    private String accountId;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String transferAccountId = "0.0.2";

    @Override
    public AccountDeleteTransaction get() {

        return new AccountDeleteTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTransferAccountId(AccountId.fromString(transferAccountId));
    }
}
