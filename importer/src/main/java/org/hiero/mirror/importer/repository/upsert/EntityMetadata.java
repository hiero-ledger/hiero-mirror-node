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

    public boolean hasHistorizeOnChangeColumns() {
        return columns.stream().anyMatch(ColumnMetadata::isHistorizeOnChange);
    }

    /** For use inside the raw {@code e}/{@code t} join, not a query selecting from the already-aliased CTE output. */
    public String historizeCondition() {
        return columns(ColumnMetadata::isHistorizeOnChange, "e.{0} is distinct from t.{0}", " or ");
    }

    /** Same join scope as {@link #historizeCondition()} -- see its note. */
    public String idExistsCondition() {
        return columns(ColumnMetadata::isId, "e.{0} is not null", " and ");
    }

    /** {@code t}-qualified id columns, for referencing inside the raw join where a bare id column is ambiguous. */
    public String tempIdColumns() {
        return columns(ColumnMetadata::isId, "t.{0}");
    }
}
