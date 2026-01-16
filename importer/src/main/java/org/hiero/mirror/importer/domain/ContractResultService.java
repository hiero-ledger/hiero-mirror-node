// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;

/**
 * This service is used to centralize the conversion logic from record stream items to its internal ContractResult
 * related representations.
 */
public interface ContractResultService {
    void process(RecordItem recordItem, Transaction transaction);
}
