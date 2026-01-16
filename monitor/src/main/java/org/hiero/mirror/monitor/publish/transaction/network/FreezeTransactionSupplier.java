// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.network;

import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FreezeTransaction;
import com.hedera.hashgraph.sdk.FreezeType;
import com.hedera.hashgraph.sdk.Hbar;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;

@Data
public class FreezeTransactionSupplier implements TransactionSupplier<FreezeTransaction> {

    private byte[] fileHash;

    private String fileId;

    @NotNull
    private FreezeType freezeType = FreezeType.FREEZE_ONLY;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotNull
    private Instant startTime = Instant.now();

    @Override
    public FreezeTransaction get() {
        var freezeTransaction = new FreezeTransaction()
                .setFreezeType(freezeType)
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setStartTime(startTime);

        if (fileHash != null) {
            freezeTransaction.setFileHash(fileHash);
        }

        if (StringUtils.isNotBlank(fileId)) {
            freezeTransaction.setFileId(FileId.fromString(fileId));
        }

        return freezeTransaction;
    }
}
