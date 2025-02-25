// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.domain;

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;

/**
 * This service is used to centralize the conversion logic from record stream items to its internal ContractResult
 * related representations.
 */
public interface ContractResultService {
    void process(RecordItem recordItem, Transaction transaction);
}
