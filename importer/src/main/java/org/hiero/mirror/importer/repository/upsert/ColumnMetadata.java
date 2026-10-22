// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import java.text.MessageFormat;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.UpsertColumn;

@Value
class ColumnMetadata implements Comparable<ColumnMetadata> {

    private final Object defaultValue;

    @ToString.Exclude
    private final Function<Object, Object> getter;

    private final boolean id;
    private final String name;
    private final boolean nullable;

    @ToString.Exclude
    private final BiConsumer<Object, Object> setter;

    private final Class<?> type;
    private final boolean updatable;
    private final UpsertColumn upsertColumn;

    @Override
    public int compareTo(ColumnMetadata other) {
        return Comparator.comparing(ColumnMetadata::getName).compare(this, other);
    }

    String format(String pattern) {
        if (pattern.contains("coalesce") && upsertColumn != null) {
            if (!upsertColumn.shouldCoalesce()) {
                return name;
            }

            var coalesce = upsertColumn.coalesce();
            if (StringUtils.isNotBlank(coalesce)) {
                pattern = coalesce;
            }
        }

        return MessageFormat.format(pattern, name, defaultValue);
    }
}
