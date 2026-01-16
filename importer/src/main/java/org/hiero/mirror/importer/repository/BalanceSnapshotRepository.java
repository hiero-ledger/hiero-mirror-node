// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

interface BalanceSnapshotRepository {

    /**
     * Generates a balance snapshot from state in database.
     *
     * @param consensusTimestamp The consensus timestamp of the balance snapshot.
     * @param treasuryAccountId The treasury account id
     * @return The number of balance rows inserted
     */
    int balanceSnapshot(long consensusTimestamp, long treasuryAccountId);

    /**
     * Generates a balance snapshot from state in database.
     * Only adds entries for items with a balance_timestamp that is greater than the minConsensusTimestamp.
     * For performant results the caller should guarantee that the table state is at the time of consensusTimestamp.
     *
     * @param minConsensusTimestamp The exclusive floor balance timestamp for a token balance to be included in the
     *                              snapshot
     * @param consensusTimestamp  The consensus timestamp of the balance snapshot
     * @param treasuryAccountId The treasury account id
     * @return The number of balance rows inserted
     */
    int balanceSnapshotDeduplicate(long minConsensusTimestamp, long consensusTimestamp, long treasuryAccountId);
}
