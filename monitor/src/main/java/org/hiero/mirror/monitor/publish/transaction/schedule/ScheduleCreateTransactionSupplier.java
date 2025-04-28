// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.schedule;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.TransferTransaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.CustomLog;
import lombok.Data;
import lombok.Getter;
import org.hiero.mirror.monitor.publish.transaction.AdminKeyable;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.hiero.mirror.monitor.util.Utility;

@Data
@CustomLog
public class ScheduleCreateTransactionSupplier implements TransactionSupplier<ScheduleCreateTransaction>, AdminKeyable {

    private String adminKey;

    @Getter(lazy = true)
    private final Key adminPublicKey = adminKey != null ? PublicKey.fromString(adminKey) : null;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String operatorAccountId;

    @Getter(lazy = true)
    private final AccountId operatorId = AccountId.fromString(operatorAccountId);

    @NotBlank
    private String payerAccount;

    @Getter(lazy = true)
    private final AccountId payerAccountId = AccountId.fromString(payerAccount);

    @Override
    public ScheduleCreateTransaction get() {
        Hbar maxHbarTransactionFee = Hbar.fromTinybars(getMaxTransactionFee());
        TransferTransaction innerTransaction = new TransferTransaction()
                .setMaxTransactionFee(maxHbarTransactionFee)
                .addHbarTransfer(getOperatorId(), Hbar.fromTinybars(1L).negated())
                .addHbarTransfer(getPayerAccountId(), Hbar.fromTinybars(1L));

        return new ScheduleCreateTransaction()
                .setAdminKey(getAdminPublicKey())
                .setMaxTransactionFee(maxHbarTransactionFee)
                .setPayerAccountId(getPayerAccountId())
                .setScheduleMemo(Utility.getMemo("Mirror node created test schedule"))
                .setScheduledTransaction(innerTransaction);
    }
}
