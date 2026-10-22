// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Value;
import org.hiero.mirror.common.domain.Upsertable;

/**
 * Contains the metadata associated with an @Upsertable entity. Used to generate dynamic upsert SQL.
 */
@Value
class EntityMetadata {

    private final String tableName;
    private final Upsertable upsertable;
    private final Set<ColumnMetadata> columns;

    public String column(Predicate<ColumnMetadata> filter, String pattern) {
        return columns.stream()
                .filter(filter)
                .findFirst()
                .map(c -> c.format(pattern))
                .orElse("");
    }

    public String columns(String pattern) {
        return columns(c -> true, pattern, ",");
    }

    public String columns(Predicate<ColumnMetadata> filter, String pattern) {
        return columns(filter, pattern, ",");
    }

    public String columns(Predicate<ColumnMetadata> filter, String pattern, String separator) {
        return columns.stream().filter(filter).map(c -> c.format(pattern)).collect(Collectors.joining(separator));
    }

    public String columns(String defaultPattern, Predicate<ColumnMetadata> predicate, String predicatePattern) {
        return columns.stream()
                .map(c -> predicate.test(c) ? c.format(predicatePattern) : c.format(defaultPattern))
                .collect(Collectors.joining(","));
    }
}
