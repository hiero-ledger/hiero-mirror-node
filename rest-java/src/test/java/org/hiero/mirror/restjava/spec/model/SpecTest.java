// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hiero.mirror.restjava.spec.converter.JsonAsStringDeserializer;
import org.springframework.util.StringUtils;

public record SpecTest(
        String description,
        Map<String, String> responseHeaders,
        @JsonDeserialize(using = JsonAsStringDeserializer.class) String responseJson,
        int responseStatus,
        String url,
        List<String> urls) {

    public List<String> getNormalizedUrls() {
        return Stream.concat(Stream.ofNullable(url), Stream.ofNullable(urls).flatMap(List::stream))
                .filter(StringUtils::hasText)
                .toList();
    }
}
