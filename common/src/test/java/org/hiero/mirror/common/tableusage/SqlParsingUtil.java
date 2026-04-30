// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.tableusage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class SqlParsingUtil {

    private static final Pattern CTE_PATTERN = Pattern.compile("(\\w+)\\s+as\\s*\\(");
    private static final Pattern FROM_JOIN_PATTERN = Pattern.compile("\\bfrom\\s+([\\w\\.]+)|\\bjoin\\s+([\\w\\.]+)");
    private static final Pattern INSERT_PATTERN =
            Pattern.compile("\\binsert\\s+into\\s+([\\w\\.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "\\bupdate\\s+(?!set\\b)([`\"]?[\\w]+[`\"]?(?:\\.[`\"]?[\\w]+[`\"]?)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN =
            Pattern.compile("\\bdelete\\s+from\\s+([\\w\\.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_INDEX_PATTERN = Pattern.compile(
            "\\bcreate\\s+(unique\\s+)?index\\s+(if\\s+not\\s+exists\\s+)?[\\w\\.]+\\s+on\\s+([\\w\\.]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
            "\\bdrop\\s+table\\s+(if\\s+exists\\s+)?([`\"]?[\\w]+[`\"]?(?:\\.[`\"]?[\\w]+[`\"]?)?)",
            Pattern.CASE_INSENSITIVE);
    private static final List<Pattern> TABLE_PATTERNS =
            List.of(FROM_JOIN_PATTERN, INSERT_PATTERN, UPDATE_PATTERN, DELETE_PATTERN, DROP_TABLE_PATTERN);

    public static Set<String> extractTableNamesFromSql(String sql) {
        final var tables = new HashSet<String>();
        if (sql == null || sql.isBlank()) {
            return tables;
        }

        final var loweredSql = sql.trim().toLowerCase();

        // Extract common table expressions (CTEs) since they can be used with FROM or JOIN clauses
        final var cteNames = new HashSet<String>();
        final var cteMatcher = CTE_PATTERN.matcher(loweredSql);
        while (cteMatcher.find()) {
            cteNames.add(cteMatcher.group(1));
        }

        for (final var pattern : TABLE_PATTERNS) {
            final var matcher = pattern.matcher(loweredSql);
            while (matcher.find()) {
                final var table = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (table != null && !cteNames.contains(table)) {
                    tables.add(table);
                }
            }
        }

        // Handle CREATE INDEX ON table
        final var createIndexMatcher = CREATE_INDEX_PATTERN.matcher(loweredSql);
        while (createIndexMatcher.find()) {
            final var table = createIndexMatcher.group(3);
            if (table != null) {
                tables.add(table);
            }
        }

        return tables;
    }
}
