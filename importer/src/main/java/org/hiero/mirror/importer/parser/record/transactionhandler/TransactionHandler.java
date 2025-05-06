// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;

/**
 * TransactionHandler interface abstracts the logic for processing different kinds for transactions. For each
 * transaction type, there exists an unique implementation of TransactionHandler which encapsulates all logic specific
 * to processing of that transaction type. A single {@link com.hederahashgraph.api.proto.java.Transaction} and its
 * associated info (TransactionRecord, deserialized TransactionBody, etc) are all encapsulated together in a single
 * {@link RecordItem}. Hence, most functions of this interface require RecordItem as a parameter.
 */
public interface TransactionHandler {

    /**
     * @return main entity associated with this transaction
     */
    default EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    /**
     * @return the transaction type associated with this handler
     */
    TransactionType getType();

    /**
     * Override to update fields of the ContractResult's (domain) fields.
     */
    default void updateContractResult(ContractResult contractResult, RecordItem recordItem) {}

    /**
     * Override to update fields of the Transaction's (domain) fields.
     */
    default void updateTransaction(Transaction transaction, RecordItem recordItem) {}
}
