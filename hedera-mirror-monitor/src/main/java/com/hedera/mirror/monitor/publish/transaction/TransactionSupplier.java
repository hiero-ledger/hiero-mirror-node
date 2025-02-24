// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction;

import com.hedera.hashgraph.sdk.Transaction;
import java.util.function.Supplier;

public interface TransactionSupplier<T extends Transaction<T>> extends Supplier<Transaction<T>> {}
