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
import org.hiero.mirror.common.interceptor.RepositoryUsageInterceptor;

@CustomLog
@UtilityClass
public final class MarkdownReportGenerator {
    private static final String NEWLINE = System.lineSeparator();

    public static void generateTableUsageReport() {
        final var apiTableQueries = RepositoryUsageInterceptor.getApiTableQueries();
        final var path = Paths.get("build/table-usage-report.md");

        try (final var writer = Files.newBufferedWriter(path)) {
            writeHeader(writer);

            for (final var endpointEntry : apiTableQueries.entrySet()) {
                writeEndpointTables(writer, endpointEntry.getKey(), endpointEntry.getValue());
            }
        } catch (final IOException e) {
            log.warn("Unexpected error occurred: {}", e.getMessage());
        }
    }

    private static void writeHeader(final BufferedWriter writer) throws IOException {
        writer.write("# Table Usage Report" + NEWLINE + NEWLINE);
        writer.write("| Endpoint | Table | Source |" + NEWLINE);
        writer.write("|----------|-------|--------|" + NEWLINE);
    }

    private static void writeEndpointTables(
            final BufferedWriter writer, final String endpoint, final Map<String, Set<String>> tableToMethods)
            throws IOException {
        final var escapedEndpoint = escapeMarkdown(endpoint);
        var firstRow = true;

        for (final var tableEntry : tableToMethods.entrySet()) {
            final var escapedTable = escapeMarkdown(tableEntry.getKey());
            final var methods = tableEntry.getValue();

            final var methodList = buildMethodList(methods);
            final var endpointCell = firstRow ? escapedEndpoint : "";
            // Indenting table cell to visually separate
            final var tableCell = "  " + escapedTable;

            writeTableRow(writer, endpointCell, tableCell, methodList);
            firstRow = false;
        }
    }

    private static String buildMethodList(final Set<String> methods) {
        final var sb = new StringBuilder();
        for (final var method : methods) {
            sb.append("- ").append(escapeMarkdown(method)).append("<br>");
        }
        // Remove last <br>
        if (sb.length() > 4) {
            sb.setLength(sb.length() - 4);
        }
        return sb.toString();
    }

    private static void writeTableRow(
            final BufferedWriter writer, final String endpoint, final String table, final String methods)
            throws IOException {
        writer.write("| " + endpoint + " | " + table + " | " + methods + " |" + NEWLINE);
    }

    private static String escapeMarkdown(final String input) {
        return input == null ? "" : input.replace("|", "\\|");
    }
}
