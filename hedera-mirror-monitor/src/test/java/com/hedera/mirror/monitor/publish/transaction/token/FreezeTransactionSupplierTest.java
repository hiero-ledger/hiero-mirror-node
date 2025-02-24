// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FreezeTransaction;
import com.hedera.hashgraph.sdk.FreezeType;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.network.FreezeTransactionSupplier;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FreezeTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        var transactionSupplier = new FreezeTransactionSupplier();
        var actual = transactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, FreezeTransaction::getMaxTransactionFee)
                .returns(FreezeType.FREEZE_ONLY, FreezeTransaction::getFreezeType)
                .extracting(FreezeTransaction::getStartTime)
                .isNotNull();
    }

    @Test
    void createWithCustomData() {
        var transactionSupplier = new FreezeTransactionSupplier();
        transactionSupplier.setFileHash(new byte[] {});
        transactionSupplier.setFileId("0.0.1000");
        transactionSupplier.setMaxTransactionFee(1);
        transactionSupplier.setFreezeType(FreezeType.FREEZE_UPGRADE);
        transactionSupplier.setStartTime(Instant.EPOCH);
        var actual = transactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, FreezeTransaction::getMaxTransactionFee)
                .returns(transactionSupplier.getFileHash(), FreezeTransaction::getFileHash)
                .returns(FileId.fromString(transactionSupplier.getFileId()), FreezeTransaction::getFileId)
                .returns(transactionSupplier.getFreezeType(), FreezeTransaction::getFreezeType)
                .returns(transactionSupplier.getStartTime(), FreezeTransaction::getStartTime);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return FreezeTransactionSupplier.class;
    }
}
