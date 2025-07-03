// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static org.hiero.mirror.common.util.Constants.UNKNOWN_ENDPOINT;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.interceptor.RepositoryUsageInterceptor;

@CustomLog
@UtilityClass
public final class MarkdownReportGenerator {
    private static final String NEWLINE = System.lineSeparator();
    private static final String LINE_BREAK = "<br>";

    public static void generateTableUsageReport() {
        final var apiTableQueries = RepositoryUsageInterceptor.getApiTableQueries();
        final var path = Paths.get("build/table-usage-report.md");

        try (final var writer = Files.newBufferedWriter(path)) {
            writeHeader(writer);

            // Sort endpoints so "UNKNOWN_ENDPOINT" is last
            final var sortedEndpoints = apiTableQueries.keySet().stream()
                    .sorted((e1, e2) -> {
                        // pushes "UNKNOWN_ENDPOINT" last
                        if (e1.equals(UNKNOWN_ENDPOINT)) return 1;
                        if (e2.equals(UNKNOWN_ENDPOINT)) return -1;
                        return e1.compareTo(e2);
                    })
                    .toList();

            for (final var endpoint : sortedEndpoints) {
                writeEndpointTables(writer, endpoint, apiTableQueries.get(endpoint));
            }

            writer.newLine();
            writer.newLine();

            // Second table header
            writeTableGroupedByTableHeader(writer);

            // Invert map: table -> (endpoint -> sources)
            final var tableToEndpointSources = invertMap(apiTableQueries);

            // Write second table rows sorted by table name
            for (final var tableEntry : new TreeMap<>(tableToEndpointSources).entrySet()) {
                final var table = tableEntry.getKey();
                final var endpointSources = tableEntry.getValue();

                writeTableGroupedByTableRow(writer, table, endpointSources);
            }
        } catch (final IOException e) {
            log.warn("Unexpected error occurred: {}", e.getMessage());
        }
    }

    private static void writeHeader(final BufferedWriter writer) throws IOException {
        writer.write("# Table Usage Report - Grouped by Endpoint" + NEWLINE + NEWLINE);
        writer.write("| Endpoint | Table | Source |" + NEWLINE);
        writer.write("|----------|-------|--------|" + NEWLINE);
    }

    private static void writeEndpointTables(
            final BufferedWriter writer, final String endpoint, final Map<String, Set<String>> tableToMethods)
            throws IOException {
        final var escapedEndpoint = escapeMarkdown(endpoint);

        // Sort tables alphabetically
        final var sortedTables = new TreeMap<>(tableToMethods);

        final var tablesBuilder = new StringBuilder();
        final var allSourcesSet = new HashSet<String>();

        for (final var tableEntry : sortedTables.entrySet()) {
            final var table = escapeMarkdown(tableEntry.getKey());
            tablesBuilder.append(table).append(LINE_BREAK);

            allSourcesSet.addAll(tableEntry.getValue().stream()
                    .map(MarkdownReportGenerator::escapeMarkdown)
                    .toList());
        }

        final var sortedSources = allSourcesSet.stream().sorted().toList();

        final var sourcesBuilder = new StringBuilder();
        for (final var source : sortedSources) {
            sourcesBuilder.append("- ").append(source).append(LINE_BREAK);
        }

        if (!sourcesBuilder.isEmpty()) {
            sourcesBuilder.setLength(sourcesBuilder.length() - LINE_BREAK.length());
        }
        if (!tablesBuilder.isEmpty()) {
            tablesBuilder.setLength(tablesBuilder.length() - LINE_BREAK.length());
        }

        writeTableRow(writer, escapedEndpoint, tablesBuilder.toString(), sourcesBuilder.toString());
    }

    private static void writeTableRow(
            final BufferedWriter writer, final String endpoint, final String table, final String methods)
            throws IOException {
        writer.write("| " + endpoint + " | " + table + " | " + methods + " |" + NEWLINE);
    }

    private static String escapeMarkdown(final String input) {
        return input == null ? "" : input.replace("|", "\\|");
    }

    private static void writeTableGroupedByTableHeader(final BufferedWriter writer) throws IOException {
        writer.write("# Table Usage Report - Grouped by Table" + NEWLINE + NEWLINE);
        writer.write("| Table | Endpoints | Source |" + NEWLINE);
        writer.write("|-------|-----------|--------|" + NEWLINE);
    }

    /**
     * Inverts the Map of endpoint -> Map<table, sources> into
     * table -> Map<endpoint, sources>
     */
    private static Map<String, Map<String, Set<String>>> invertMap(
            final Map<String, Map<String, Set<String>>> endpointTableSources) {
        final Map<String, Map<String, Set<String>>> result = new HashMap<>();

        for (final var endpointEntry : endpointTableSources.entrySet()) {
            final var endpoint = endpointEntry.getKey();
            final var tableSources = endpointEntry.getValue();

            for (final var tableEntry : tableSources.entrySet()) {
                final var table = tableEntry.getKey();
                final var sources = tableEntry.getValue();

                result.computeIfAbsent(table, t -> new HashMap<>())
                        .computeIfAbsent(endpoint, e -> new HashSet<>())
                        .addAll(sources);
            }
        }
        return result;
    }

    private static void writeTableGroupedByTableRow(
            final BufferedWriter writer, final String table, final Map<String, Set<String>> endpointSources)
            throws IOException {
        final var escapedTable = escapeMarkdown(table);

        // Build endpoints list with bullets, sorted alphabetically
        final var endpoints = new TreeSet<>(endpointSources.keySet());
        final var endpointList =
                endpoints.stream().map(e -> "- " + escapeMarkdown(e)).collect(Collectors.joining(LINE_BREAK));

        // Build source list from all sources in all endpoints, merged & sorted
        final var allSources =
                endpointSources.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(TreeSet::new));
        final var sourceList =
                allSources.stream().map(s -> "- " + escapeMarkdown(s)).collect(Collectors.joining(LINE_BREAK));

        writer.write("| " + escapedTable + " | " + endpointList + " | " + sourceList + " |" + NEWLINE);
    }
}
