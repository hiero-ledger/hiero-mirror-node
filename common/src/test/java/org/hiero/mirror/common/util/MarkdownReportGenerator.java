// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.aspect.RepositoryUsageTrackerAspect;

@CustomLog
@UtilityClass
public class MarkdownReportGenerator {
    private static final String NEWLINE = System.lineSeparator();

    public static void generateTableUsageReport() {
        final var apiTableQueries = RepositoryUsageTrackerAspect.getAPI_TABLE_QUERIES();
        final var path = Paths.get("build/table-usage-report.md");

        try (final var writer = Files.newBufferedWriter(path)) {
            writer.write("# API Repository Calls & Database Tables Report" + NEWLINE + NEWLINE);

            for (final var entry : apiTableQueries.entrySet()) {
                writer.write("## API Endpoint: " + entry.getKey() + NEWLINE);
                writeTableUsage(writer, entry.getValue());
                writer.write(NEWLINE);
            }

        } catch (IOException e) {
            log.warn("Unexpected error occurred: {}", e.getMessage());
        }
    }

    private static void writeTableUsage(final BufferedWriter writer, final Map<String, Set<String>> tableQueries)
            throws IOException {
        for (final var tableEntry : tableQueries.entrySet()) {
            writer.write("### Table: " + tableEntry.getKey() + NEWLINE);
            for (final var query : tableEntry.getValue()) {
                writer.write("- " + query + NEWLINE);
            }
            writer.write(NEWLINE);
        }
    }
}
