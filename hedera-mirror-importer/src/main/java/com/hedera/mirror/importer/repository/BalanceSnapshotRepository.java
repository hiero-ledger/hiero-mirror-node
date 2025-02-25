// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

interface BalanceSnapshotRepository {

    /**
     * Generates a balance snapshot from state in database.
     *
     * @param consensusTimestamp The consensus timestamp of the balance snapshot.
     * @return The number of balance rows inserted
     */
    int balanceSnapshot(long consensusTimestamp);

    /**
     * Generates a balance snapshot from state in database.
     * Only adds entries for items with a balance_timestamp that is greater or equal to the maxConsensusTimestamp.
     *
     * For performant results the caller should guarantee that the table state is at the time of consensusTimestamp.
     *
     * @param maxConsensusTimestamp
     * @param consensusTimestamp  The consensus timestamp of the balance snapshot.
     * @return The number of balance rows inserted
     */
    int balanceSnapshotDeduplicate(long maxConsensusTimestamp, long consensusTimestamp);
}
