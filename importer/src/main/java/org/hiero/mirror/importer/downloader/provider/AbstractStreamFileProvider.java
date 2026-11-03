// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.jspecify.annotations.NullMarked;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NullMarked
@RequiredArgsConstructor
abstract class AbstractStreamFileProvider implements StreamFileProvider {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Pattern PATTERN = Pattern.compile("^(.+)(-(\\d{12}))$");

    protected final CommonDownloaderProperties downloaderProperties;

    @Override
    public Mono<String> discoverNetwork() {
        final var network = downloaderProperties.getImporterProperties().getNetwork();

        if (!HederaNetwork.isResettable(network)) {
            return Mono.just(network);
        }

        return doDiscoverNetwork()
                .filter(this::filterNetwork)
                .reduce((first, second) -> first.compareTo(second) > 0 ? first : second);
    }

    private boolean filterNetwork(String path) {
        try {
            final var networkProperty =
                    downloaderProperties.getImporterProperties().getNetwork();
            final var matcher = PATTERN.matcher(path);

            if (matcher.matches() && matcher.groupCount() >= 1) {
                final var network = matcher.group(1);

                if (!networkProperty.equalsIgnoreCase(network)) {
                    return false;
                }

                if (matcher.groupCount() == 3) {
                    final var suffix = matcher.group(3);
                    final var date = LocalDateTime.parse(suffix, FORMAT);
                    return date != null;
                }

                return true;
            }

            return false;
        } catch (final Exception e) {
            return false;
        }
    }

    abstract Flux<String> doDiscoverNetwork();
}
