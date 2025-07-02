package org.hiero.mirror.common.util;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.regex.Pattern;

@UtilityClass
 public final class EndpointNormalizer {

    // normalization patterns, currently used for rest-java endpoints only.
    private static final Map<Pattern, String> endpointNormalizationPatterns = Map.ofEntries(
            Map.entry(Pattern.compile("^/api/v1/topics/(.+)$"), "/api/v1/topics/{id}"),
            Map.entry(Pattern.compile("^/api/v1/accounts/[^/]+/airdrops/pending$"), "/api/v1/accounts/{id}/airdrops/pending"),
            Map.entry(Pattern.compile("^/api/v1/accounts/[^/]+/airdrops/outstanding$"), "/api/v1/accounts/{id}/airdrops/outstanding"),
            Map.entry(Pattern.compile("^/api/v1/accounts/[^/]+/allowances/nfts$"), "/api/v1/accounts/{id}/allowances/nfts")
    );

    public static String normalize(final String rawPath) {
        for (final var entry : endpointNormalizationPatterns.entrySet()) {
            if (entry.getKey().matcher(rawPath).matches()) {
                return entry.getValue();
            }
        }
        return rawPath;
    }

}
