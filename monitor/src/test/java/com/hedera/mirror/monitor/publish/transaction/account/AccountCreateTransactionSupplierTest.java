// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class AccountCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(Hbar.fromTinybars(10_000_000), AccountCreateTransaction::getInitialBalance)
                .returns(MAX_TRANSACTION_FEE_HBAR, AccountCreateTransaction::getMaxTransactionFee)
                .returns(false, AccountCreateTransaction::getReceiverSignatureRequired)
                .satisfies(a -> assertThat(a.getKey()).isNotNull())
                .extracting(AccountCreateTransaction::getAccountMemo, STRING)
                .contains("Mirror node created test account");
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generateED25519().getPublicKey();

        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        accountCreateTransactionSupplier.setInitialBalance(1);
        accountCreateTransactionSupplier.setMaxTransactionFee(1);
        accountCreateTransactionSupplier.setReceiverSignatureRequired(true);
        accountCreateTransactionSupplier.setPublicKey(key.toString());
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, AccountCreateTransaction::getInitialBalance)
                .returns(key, AccountCreateTransaction::getKey)
                .returns(ONE_TINYBAR, AccountCreateTransaction::getMaxTransactionFee)
                .returns(true, AccountCreateTransaction::getReceiverSignatureRequired)
                .extracting(AccountCreateTransaction::getAccountMemo, STRING)
                .contains("Mirror node created test account");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return AccountCreateTransactionSupplier.class;
    }
}
