// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class AccountDeleteTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountDeleteTransactionSupplier accountDeleteTransactionSupplier = new AccountDeleteTransactionSupplier();
        accountDeleteTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        AccountDeleteTransaction actual = accountDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, AccountDeleteTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, AccountDeleteTransaction::getMaxTransactionFee)
                .returns(AccountId.fromString("0.0.2"), AccountDeleteTransaction::getTransferAccountId);
    }

    @Test
    void createWithCustomData() {
        AccountDeleteTransactionSupplier accountDeleteTransactionSupplier = new AccountDeleteTransactionSupplier();
        accountDeleteTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        accountDeleteTransactionSupplier.setTransferAccountId(ACCOUNT_ID_2.toString());
        accountDeleteTransactionSupplier.setMaxTransactionFee(1);
        AccountDeleteTransaction actual = accountDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, AccountDeleteTransaction::getAccountId)
                .returns(ONE_TINYBAR, AccountDeleteTransaction::getMaxTransactionFee)
                .returns(ACCOUNT_ID_2, AccountDeleteTransaction::getTransferAccountId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return AccountDeleteTransactionSupplier.class;
    }
}
