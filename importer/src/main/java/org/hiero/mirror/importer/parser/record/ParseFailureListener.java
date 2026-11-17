// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

/**
 * Notified when a batch's parse transaction fails, so an implementor that staged not-yet-durable state ahead of
 * that batch's own database write -- to serve a same-batch read before the write lands -- can undo it. The batch's
 * rollback only reverts the database; it can't reach state an implementor holds outside it.
 */
public interface ParseFailureListener {

    /**
     * @param startTimestamp the consensus start timestamp of the first record file in the failed batch
     */
    void handleParseFailure(long startTimestamp);
}
