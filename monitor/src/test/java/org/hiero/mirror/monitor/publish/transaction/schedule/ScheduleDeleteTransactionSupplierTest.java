// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.ScheduleDeleteTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ScheduleDeleteTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ScheduleDeleteTransactionSupplier scheduleDeleteTransactionSupplier = new ScheduleDeleteTransactionSupplier();
        scheduleDeleteTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleDeleteTransaction actual = scheduleDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, ScheduleDeleteTransaction::getMaxTransactionFee)
                .returns(SCHEDULE_ID, ScheduleDeleteTransaction::getScheduleId);
    }

    @Test
    void createWithCustomData() {
        ScheduleDeleteTransactionSupplier scheduleDeleteTransactionSupplier = new ScheduleDeleteTransactionSupplier();
        scheduleDeleteTransactionSupplier.setMaxTransactionFee(1);
        scheduleDeleteTransactionSupplier.setScheduleId(SCHEDULE_ID.toString());
        ScheduleDeleteTransaction actual = scheduleDeleteTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, ScheduleDeleteTransaction::getMaxTransactionFee)
                .returns(SCHEDULE_ID, ScheduleDeleteTransaction::getScheduleId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ScheduleDeleteTransactionSupplier.class;
    }
}
