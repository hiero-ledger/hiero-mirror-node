// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.schedule;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleSignTransaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;

@Data
public class ScheduleSignTransactionSupplier implements TransactionSupplier<ScheduleSignTransaction> {
    @NotBlank
    private String scheduleId;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public ScheduleSignTransaction get() {
        return new ScheduleSignTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setScheduleId(ScheduleId.fromString(scheduleId));
    }
}
