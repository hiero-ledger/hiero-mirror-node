// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.projections;

/**
 * Projection for a contract_transaction_hash row. A dedicated projection is required because the entity's id is the
 * hash, which is not unique across the results sharing it, so materializing rows as entities would collapse them to a
 * single instance via the persistence context.
 */
public interface ContractTransactionHashLookup {

    long getConsensusTimestamp();

    long getEntityId();

    long getPayerAccountId();

    Integer getTransactionResult();
}
