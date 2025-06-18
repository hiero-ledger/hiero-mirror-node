// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.CustomLog;
import org.hiero.mirror.common.aspect.RepositoryUsageTrackerAspect;

@CustomLog
public class MarkdownReportGenerator {

    public static void generateTableUsageReport() {
        final var apiTableQueries = RepositoryUsageTrackerAspect.getApiTableQueries();
        final var path = Paths.get("build/table-usage-report.md");

        try (final var writer = Files.newBufferedWriter(path)) {
            writer.write("# API Repository Calls & Database Tables Report\n\n");

            for (final var entry : apiTableQueries.entrySet()) {
                writer.write("## API Endpoint: " + entry.getKey() + "\n");

                for (final var tableEntry : entry.getValue().entrySet()) {
                    writer.write("### Table: " + tableEntry.getKey() + "\n");

                    for (final var query : tableEntry.getValue()) {
                        writer.write("- " + query + "\n");
                    }
                    writer.write("\n");
                }
                writer.write("\n");
            }

        } catch (IOException e) {
            log.warn("Unexpected error occurred: {}", e.getMessage());
        }
    }
}
