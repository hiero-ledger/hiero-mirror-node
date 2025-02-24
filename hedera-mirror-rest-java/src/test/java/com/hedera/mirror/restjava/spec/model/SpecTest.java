// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hedera.mirror.restjava.spec.converter.JsonAsStringDeserializer;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;

public record SpecTest(
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
