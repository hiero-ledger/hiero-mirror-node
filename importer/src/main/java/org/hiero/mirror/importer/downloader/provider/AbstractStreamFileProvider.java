// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
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

    static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuuMMddHHmm")
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneId.of("UTC"));
    private static final Pattern PATTERN = Pattern.compile("^(.+?)(-(\\d{12}))?$");

    protected final CommonDownloaderProperties downloaderProperties;

    @Override
    public Mono<String> discoverNetwork() {
        final var network = downloaderProperties.getImporterProperties().getNetwork();

        if (!HederaNetwork.isResettable(network)) {
            return Mono.just(network);
        }

        return doDiscoverNetwork()
                .filter(path -> filterNetwork(network, path))
                .reduce((first, second) -> first.compareTo(second) > 0 ? first : second);
    }

    private boolean filterNetwork(final String networkProperty, final String path) {
        try {
            final var matcher = PATTERN.matcher(path);

            if (!matcher.matches()) {
                return false;
            }

            final var network = matcher.group(1);

            if (!networkProperty.equalsIgnoreCase(network)) {
                return false;
            }

            final var suffix = matcher.group(3);
            if (suffix != null) {
                final var date = LocalDateTime.parse(suffix, FORMATTER);
                return date != null;
            }

            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    abstract Flux<String> doDiscoverNetwork();
}
