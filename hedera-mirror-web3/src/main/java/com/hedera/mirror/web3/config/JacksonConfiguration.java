// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JacksonConfiguration {

    // Configure JSON parsing limits to reject malicious input
    @Bean
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer(MirrorNodeEvmProperties properties) {
        final int maxSize = (int) properties.getMaxDataSize().toBytes() * 2 + 1024;
        return builder -> {
            var streamReadConstraints = StreamReadConstraints.builder()
                    .maxDocumentLength(maxSize)
                    .maxNameLength(100)
                    .maxNestingDepth(10)
                    .maxNumberLength(19)
                    .maxStringLength(maxSize)
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
