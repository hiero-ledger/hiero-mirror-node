// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.projections;

/**
 * Projection for a contract_transaction_hash row. A dedicated projection is used because the entity's id is the hash,
 * which is not unique across the results sharing it.
 */
public interface ContractTransactionHashLookup {

    long getConsensusTimestamp();

    long getEntityId();

    long getPayerAccountId();

    Integer getTransactionResult();
}
