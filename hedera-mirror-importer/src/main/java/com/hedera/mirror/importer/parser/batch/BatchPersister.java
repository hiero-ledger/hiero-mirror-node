// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.batch;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Performs bulk insertion of domain objects to the database. For some domain types it might be insert-only while others
 * may use upsert logic.
 */
public interface BatchPersister {

    Map<String, String> insertCsv = new TreeMap<>();

    String LATENCY_METRIC = "hedera.mirror.importer.batch.latency";

    void persist(Collection<? extends Object> items);
}
