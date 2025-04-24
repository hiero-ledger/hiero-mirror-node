// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hedera.mirror.restjava.spec.converter.JsonAsStringDeserializer;
import java.util.List;
import java.util.Map;

public record RestSpec(
        String description,
        List<String> extendedDescription,
        String matrix,
        Map<String, String> responseHeaders,
        @JsonDeserialize(using = JsonAsStringDeserializer.class) String responseJson,
        int responseStatus,
        SpecSetup setup,
        List<SpecTest> tests,
        String url,
        List<String> urls,
        Map<String, Object> responseJsonMatrix) {}
