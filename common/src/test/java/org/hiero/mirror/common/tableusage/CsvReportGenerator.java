// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.tableusage;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.interceptor.RepositoryUsageInterceptor;

@UtilityClass
public final class CsvReportGenerator {

    public static void generateReport() {
        final var path = Paths.get("build", "reports", "tableusage", "table-usage.csv");
        final var usageMap = RepositoryUsageInterceptor.getApiTableQueries();
        final var csvMapper = CsvMapper.builder().build();
        final var schema = csvMapper.schemaFor(TableUsage.class).withHeader().sortedBy("endpoint", "table", "source");
        path.getParent().toFile().mkdirs();

        try (final var writer = Files.newBufferedWriter(path)) {
            var sequenceWriter =
                    csvMapper.writerFor(TableUsage.class).with(schema).writeValues(writer);

            for (final var entry : usageMap.entrySet()) {
                final var endpoint = entry.getKey();

                for (final var tableEntry : entry.getValue().entrySet()) {
                    final var table = tableEntry.getKey();

                    for (final var source : tableEntry.getValue()) {
                        sequenceWriter.write(new TableUsage(endpoint, table, source));
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record TableUsage(String endpoint, String table, String source) {}
}
