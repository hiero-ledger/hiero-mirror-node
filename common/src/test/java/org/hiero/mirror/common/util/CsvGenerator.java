// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.interceptor.RepositoryUsageInterceptor;

@UtilityClass
public final class CsvGenerator {

    public static void generateTableUsageReport() {
        final var apiTableQueries = RepositoryUsageInterceptor.getApiTableQueries();

        final var fullPath = Path.of("build/table-usage-report.csv");
        final var simplePath = Path.of("build/table-usage-report-without-sources.csv");

        try (final var fullWriter = Files.newBufferedWriter(fullPath);
                final var simpleWriter = Files.newBufferedWriter(simplePath)) {

            fullWriter.write("Endpoint,Table,Source\n");
            simpleWriter.write("Endpoint,Table\n");

            final var seenPairs = new HashSet<String>();

            for (final var entry : apiTableQueries.entrySet()) {
                final var endpoint = escapeCsv(entry.getKey());
                final var tableToSources = entry.getValue();

                for (final var tableEntry : tableToSources.entrySet()) {
                    final var table = escapeCsv(tableEntry.getKey());

                    for (final var source : tableEntry.getValue()) {
                        final var escapedSource = escapeCsv(source);
                        fullWriter.write(String.format("%s,%s,%s%n", endpoint, table, escapedSource));
                    }

                    final var pairKey = endpoint + "|" + table;
                    if (seenPairs.add(pairKey)) {
                        simpleWriter.write(String.format("%s,%s%n", endpoint, table));
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        var escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
