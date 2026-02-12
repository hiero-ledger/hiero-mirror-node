// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.jspecify.annotations.NullMarked;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NullMarked
@RequiredArgsConstructor
abstract class AbstractStreamFileProvider implements StreamFileProvider {

    protected final CommonProperties commonProperties;
    protected final CommonDownloaderProperties downloaderProperties;

    @Override
    public Mono<String> discoverNetwork() {
        final var network = downloaderProperties.getImporterProperties().getNetwork();
        final var networkFilter = Pattern.compile("^%s-.+$".formatted(network)).asPredicate();
        return doDiscoverNetwork()
                .filter(networkFilter)
                .reduce((first, second) -> first.compareTo(second) > 0 ? first : second);
    }

    abstract Flux<String> doDiscoverNetwork();
}
