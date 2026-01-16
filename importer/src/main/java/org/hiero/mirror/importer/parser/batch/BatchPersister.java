// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import java.util.Collection;

/**
 * Performs bulk insertion of domain objects to the database. For some domain types it might be insert-only while others
 * may use upsert logic.
 */
public interface BatchPersister {

    String LATENCY_METRIC = "hiero.mirror.importer.batch.latency";

    void persist(Collection<? extends Object> items);
}
