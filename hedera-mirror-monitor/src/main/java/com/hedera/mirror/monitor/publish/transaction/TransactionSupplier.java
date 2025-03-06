// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction;

import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.mirror.monitor.validator.AccountIdValidator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface TransactionSupplier<T extends Transaction<T>> extends Supplier<Transaction<T>> {
    AtomicReference<AccountIdValidator> ACCOUNT_ID_VALIDATOR = new AtomicReference<>();
}
