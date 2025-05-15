// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.model;

import java.util.List;
import java.util.Map;
import org.springframework.util.CollectionUtils;

/**
 * Defines a normalized spec test. Given the slightly different formats of the original REST module JSON files
 * (largely around the seemingly optional "tests" array and the use of "url" vs "urls"), this class represents a
 * valid instance of a single test to be contained by {@link RestSpecNormalized}.
 *
 * @param responseHeaders expected HTTP headers returned in response
 * @param responseJson JSON response expected from the API upon success
 * @param responseStatus expected integer HTTP status code expected upon success
 * @param urls the API URL(s) to be invoked
 */
public record SpecTestNormalized(
        Map<String, String> responseHeaders, String responseJson, int responseStatus, List<String> urls) {

    public SpecTestNormalized {
        if (CollectionUtils.isEmpty(urls)) {
            throw new IllegalArgumentException("At least one url is required");
        }
    }

    static SpecTestNormalized from(SpecTest specTest) {
        return new SpecTestNormalized(
                specTest.responseHeaders(),
                specTest.responseJson(),
                specTest.responseStatus(),
                specTest.getNormalizedUrls());
    }

    static List<SpecTestNormalized> allFrom(List<SpecTest> specTests) {
        if (CollectionUtils.isEmpty(specTests)) {
            return List.of();
        }
        return specTests.stream().map(SpecTestNormalized::from).toList();
    }
}
