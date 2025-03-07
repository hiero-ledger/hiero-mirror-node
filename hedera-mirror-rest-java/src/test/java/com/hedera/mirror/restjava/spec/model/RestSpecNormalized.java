// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.model;

import java.util.List;
import org.springframework.util.CollectionUtils;

/**
 * Represents a normalized REST spec definition, containing at least one test scenario. Given the slightly different
 * formats of the original REST module JSON files (largely around the seemingly optional "tests" array and the use of
 * "url" vs "urls"), those issues have been resolved and are expressed concisely as an instance of this class.
 *
 * @param description
 * @param extendedDescription
 * @param matrix ignored for now
 * @param setup features and other config are not yet supported, just database entity definitions
 * @param tests at least one normalized test
 */
public record RestSpecNormalized(
        String description,
        List<String> extendedDescription,
        String matrix,
        SpecSetup setup,
        List<SpecTestNormalized> tests) {

    public RestSpecNormalized {
        if (CollectionUtils.isEmpty(tests)) {
            throw new IllegalArgumentException("At least one test is required");
        }
    }

    public static RestSpecNormalized from(RestSpec restSpec) {
        List<SpecTestNormalized> normalizedTests;
        if (CollectionUtils.isEmpty(restSpec.tests())) {
            var specTest = new SpecTest(
                    restSpec.responseHeaders(),
                    restSpec.responseJson(),
                    restSpec.responseStatus(),
                    restSpec.url(),
                    restSpec.urls());
            normalizedTests = List.of(SpecTestNormalized.from(specTest));
        } else {
            normalizedTests = SpecTestNormalized.allFrom(restSpec.tests());
        }
        return new RestSpecNormalized(
                restSpec.description(),
                restSpec.extendedDescription(),
                restSpec.matrix(),
                restSpec.setup(),
                normalizedTests);
    }
}
