// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.DATA_MAX_LENGTH;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.hedera.node.config.data.JumboTransactionsConfig;
import java.util.List;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.viewmodel.ActionTraceRequest;
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {

    /** Separate aggregate limits for bundles; ordinary call parsing keeps its existing restrictions. */
    @Bean
    @SuppressWarnings("removal")
    WebMvcConfigurer actionTraceJsonLimits() {
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(final List<HttpMessageConverter<?>> converters) {
                for (final var converter : converters) {
                    if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                        final var mapper = jackson.getObjectMapper().copy();
                        mapper.getFactory()
                                .setStreamReadConstraints(StreamReadConstraints.builder()
                                        .maxDocumentLength(ActionTraceRequest.MAX_DOCUMENT_LENGTH)
                                        .maxNameLength(100)
                                        .maxNestingDepth(10)
                                        .maxNumberLength(19)
                                        .maxStringLength((int) DATA_MAX_LENGTH)
                                        .maxTokenCount(ActionTraceRequest.MAX_JSON_TOKENS)
                                        .build());
                        jackson.registerObjectMappersForType(
                                ActionTraceRequest.class,
                                registrations -> registrations.put(MediaType.APPLICATION_JSON, mapper));
                    }
                }
            }
        };
    }

    // Configure JSON parsing limits to reject malicious input
    @Bean
    @SuppressWarnings("removal")
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer(EvmProperties properties) {
        final var jumboConfig = properties.getVersionedConfiguration().getConfigData(JumboTransactionsConfig.class);
        final int maxSize = jumboConfig.ethereumMaxCallDataSize() * 2 + 1024;
        if (maxSize > DATA_MAX_LENGTH) {
            throw new IllegalArgumentException("Data size must be less than or equal to " + DATA_MAX_LENGTH);
        }
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
