// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.validator.AccountIdValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountDeleteTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @BeforeEach
    void setup() {
        TransactionSupplier.ACCOUNT_ID_VALIDATOR.set(new AccountIdValidator(0, 0));
    }

    @AfterEach
    void teardown() {
        TransactionSupplier.ACCOUNT_ID_VALIDATOR.set(null);
    }

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

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 0, 0.0.2
            1, 2, 1.2.2
            """)
    void defaultTransferAccountId(long shard, long realm, String expectedTransferAccountId) {
        TransactionSupplier.ACCOUNT_ID_VALIDATOR.set(new AccountIdValidator(shard, realm));
        var supplier = new AccountDeleteTransactionSupplier();
        supplier.setAccountId(ACCOUNT_ID.toString());
        supplier.setMaxTransactionFee(1);
        var actual = supplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, AccountDeleteTransaction::getAccountId)
                .returns(ONE_TINYBAR, AccountDeleteTransaction::getMaxTransactionFee)
                .returns(
                        expectedTransferAccountId, a -> a.getTransferAccountId().toString());
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            0, 1, 0.0.1000
            1, 0, 0.0.1000
            1, 2, 2.1.1000
            """)
    void shardRealmMismatchThrows(long shard, long realm, String transferAccountId) {
        TransactionSupplier.ACCOUNT_ID_VALIDATOR.set(new AccountIdValidator(shard, realm));
        var supplier = new AccountDeleteTransactionSupplier();
        supplier.setAccountId(ACCOUNT_ID.toString());
        supplier.setTransferAccountId(transferAccountId);

        assertThatThrownBy(supplier::get).isInstanceOf(IllegalArgumentException.class);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return AccountDeleteTransactionSupplier.class;
    }
}
