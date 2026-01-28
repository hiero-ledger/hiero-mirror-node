// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JacksonConfiguration {

    // Configure JSON parsing limits to reject malicious input
    @Bean
    @SuppressWarnings("removal")
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            var streamReadConstraints = StreamReadConstraints.builder()
                    .maxDocumentLength(1000)
                    .maxNameLength(100)
                    .maxNestingDepth(10)
                    .maxNumberLength(19)
                    .maxStringLength(1000)
                    .maxTokenCount(100)
                    .build();
            var streamWriteConstraints =
                    StreamWriteConstraints.builder().maxNestingDepth(100).build();
            var factory = new MappingJsonFactory();
            factory.setStreamReadConstraints(streamReadConstraints);
            factory.setStreamWriteConstraints(streamWriteConstraints);
            builder.factory(factory);
        };
    }
}
