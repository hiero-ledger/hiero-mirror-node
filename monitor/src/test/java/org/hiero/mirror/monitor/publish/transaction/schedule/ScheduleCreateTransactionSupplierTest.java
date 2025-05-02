// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ScheduleCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ScheduleCreateTransactionSupplier scheduleCreateTransactionSupplier = new ScheduleCreateTransactionSupplier();
        scheduleCreateTransactionSupplier.setOperatorAccountId(ACCOUNT_ID.toString());
        scheduleCreateTransactionSupplier.setPayerAccount(ACCOUNT_ID_2.toString());
        ScheduleCreateTransaction actual = scheduleCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(null, a -> a.getAdminKey())
                .returns(MAX_TRANSACTION_FEE_HBAR, ScheduleCreateTransaction::getMaxTransactionFee)
                .returns(ACCOUNT_ID_2, ScheduleCreateTransaction::getPayerAccountId)
                .extracting(ScheduleCreateTransaction::getScheduleMemo, STRING)
                .contains("Mirror node created test schedule");
    }

    @Test
    void createWithCustomData() {
        PublicKey adminKey = PrivateKey.generateED25519().getPublicKey();

        ScheduleCreateTransactionSupplier scheduleCreateTransactionSupplier = new ScheduleCreateTransactionSupplier();
        scheduleCreateTransactionSupplier.setAdminKey(adminKey.toString());
        scheduleCreateTransactionSupplier.setMaxTransactionFee(1);
        scheduleCreateTransactionSupplier.setOperatorAccountId(ACCOUNT_ID.toString());
        scheduleCreateTransactionSupplier.setPayerAccount(ACCOUNT_ID_2.toString());
        ScheduleCreateTransaction actual = scheduleCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(adminKey, a -> a.getAdminKey())
                .returns(ONE_TINYBAR, ScheduleCreateTransaction::getMaxTransactionFee)
                .returns(ACCOUNT_ID_2, ScheduleCreateTransaction::getPayerAccountId)
                .extracting(ScheduleCreateTransaction::getScheduleMemo, STRING)
                .contains("Mirror node created test schedule");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ScheduleCreateTransactionSupplier.class;
    }
}
