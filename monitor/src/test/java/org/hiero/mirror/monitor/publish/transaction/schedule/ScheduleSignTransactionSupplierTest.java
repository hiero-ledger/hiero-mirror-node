// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.ScheduleSignTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ScheduleSignTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ScheduleSignTransactionSupplier scheduleSignTransactionSupplier = new ScheduleSignTransactionSupplier();
        scheduleSignTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleSignTransaction actual = scheduleSignTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, ScheduleSignTransaction::getMaxTransactionFee)
                .returns(SCHEDULE_ID, ScheduleSignTransaction::getScheduleId);
    }

    @Test
    void createWithCustomData() {
        ScheduleSignTransactionSupplier scheduleSignTransactionSupplier = new ScheduleSignTransactionSupplier();
        scheduleSignTransactionSupplier.setMaxTransactionFee(1);
        scheduleSignTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleSignTransaction actual = scheduleSignTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, ScheduleSignTransaction::getMaxTransactionFee)
                .returns(SCHEDULE_ID, ScheduleSignTransaction::getScheduleId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ScheduleSignTransactionSupplier.class;
    }
}
