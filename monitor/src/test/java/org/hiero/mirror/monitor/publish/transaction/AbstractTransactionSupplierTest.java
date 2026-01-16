// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicId;
import org.junit.jupiter.api.Test;
import org.meanbean.test.BeanVerifier;

public abstract class AbstractTransactionSupplierTest {
    protected static final AccountId ACCOUNT_ID = AccountId.fromString("0.0.3");
    protected static final AccountId ACCOUNT_ID_2 = AccountId.fromString("0.0.4");
    protected static final Hbar MAX_TRANSACTION_FEE_HBAR = Hbar.fromTinybars(1_000_000_000);
    protected static final Hbar ONE_TINYBAR = Hbar.fromTinybars(1);
    protected static final ScheduleId SCHEDULE_ID = ScheduleId.fromString("0.0.30");
    protected static final TokenId TOKEN_ID = TokenId.fromString("0.0.10");
    protected static final TopicId TOPIC_ID = TopicId.fromString("0.0.20");

    protected abstract Class<? extends TransactionSupplier<?>> getSupplierClass();

    @Test
    void meanBean() {
        BeanVerifier.forClass(getSupplierClass()).verifyGettersAndSetters();
    }
}
