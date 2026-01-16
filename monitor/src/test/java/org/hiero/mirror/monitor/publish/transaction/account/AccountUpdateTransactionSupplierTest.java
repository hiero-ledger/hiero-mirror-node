// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class AccountUpdateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountUpdateTransactionSupplier accountUpdateTransactionSupplier = new AccountUpdateTransactionSupplier();
        accountUpdateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        AccountUpdateTransaction actual = accountUpdateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, AccountUpdateTransaction::getAccountId)
                .returns(null, AccountUpdateTransaction::getKey)
                .returns(MAX_TRANSACTION_FEE_HBAR, AccountUpdateTransaction::getMaxTransactionFee)
                .returns(null, AccountUpdateTransaction::getProxyAccountId)
                .returns(false, AccountUpdateTransaction::getReceiverSignatureRequired)
                .satisfies(a -> assertThat(a.getExpirationTime()).isNotNull())
                .extracting(AccountUpdateTransaction::getAccountMemo, STRING)
                .contains("Mirror node updated test account");
    }

    @Test
    void createWithCustomData() {
        Instant expirationTime = Instant.now().plus(1, ChronoUnit.DAYS);
        PublicKey key = PrivateKey.generateED25519().getPublicKey();

        AccountUpdateTransactionSupplier accountUpdateTransactionSupplier = new AccountUpdateTransactionSupplier();
        accountUpdateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        accountUpdateTransactionSupplier.setExpirationTime(expirationTime);
        accountUpdateTransactionSupplier.setMaxTransactionFee(1);
        accountUpdateTransactionSupplier.setProxyAccountId(ACCOUNT_ID_2.toString());
        accountUpdateTransactionSupplier.setPublicKey(key.toString());
        accountUpdateTransactionSupplier.setReceiverSignatureRequired(true);
        AccountUpdateTransaction actual = accountUpdateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, AccountUpdateTransaction::getAccountId)
                .returns(expirationTime, AccountUpdateTransaction::getExpirationTime)
                .returns(key, AccountUpdateTransaction::getKey)
                .returns(ONE_TINYBAR, AccountUpdateTransaction::getMaxTransactionFee)
                .returns(ACCOUNT_ID_2, AccountUpdateTransaction::getProxyAccountId)
                .returns(true, AccountUpdateTransaction::getReceiverSignatureRequired)
                .extracting(AccountUpdateTransaction::getAccountMemo, STRING)
                .contains("Mirror node updated test account");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return AccountUpdateTransactionSupplier.class;
    }
}
