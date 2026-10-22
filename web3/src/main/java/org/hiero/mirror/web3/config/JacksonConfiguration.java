// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.DATA_MAX_LENGTH;

import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.hedera.node.config.data.JumboTransactionsConfig;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;

@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {

    static final int MAX_STRING_LENGTH = (int) DATA_MAX_LENGTH;

    /** Sixteen max-size call-data fields plus one state override and JSON framing. */
    static final long MAX_DOCUMENT_LENGTH = 8L * 1024 * 1024;

    /**
     * Above one valid {@code eth_call} access list (7,000 entries, about seven tokens each) and a 16-transaction
     * trace. Still rejects an unbounded array of tiny values.
     */
    static final long MAX_TOKEN_COUNT = 60_000L;

    static final int MAX_READ_NESTING_DEPTH = 32;

    /** Two JSON levels per EVM depth (0..1023) plus the response envelope. */
    static final int MAX_WRITE_NESTING_DEPTH = 2 * ActionContext.MAX_DEPTH + 32;

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_NUMBER_LENGTH = 19;

    @Bean
    JsonFactoryBuilderCustomizer jsonFactoryCustomizer() {
        return builder -> builder.streamReadConstraints(readConstraints()).streamWriteConstraints(writeConstraints());
    }

    // Jackson 2 is not the HTTP mapper. Keep the same limits so a preferred-json-mapper switch cannot drop them.
    @Bean
    @SuppressWarnings("removal")
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer(EvmProperties properties) {
        final var jumboConfig = properties.getVersionedConfiguration().getConfigData(JumboTransactionsConfig.class);
        final int maxSize = jumboConfig.ethereumMaxCallDataSize() * 2 + 1024;
        if (maxSize > DATA_MAX_LENGTH) {
            throw new IllegalArgumentException("Data size must be less than or equal to " + DATA_MAX_LENGTH);
        }
        return builder -> {
            final var streamReadConstraints = com.fasterxml.jackson.core.StreamReadConstraints.builder()
                    .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                    .maxNameLength(MAX_NAME_LENGTH)
                    .maxNestingDepth(MAX_READ_NESTING_DEPTH)
                    .maxNumberLength(MAX_NUMBER_LENGTH)
                    .maxStringLength(MAX_STRING_LENGTH)
                    .maxTokenCount(MAX_TOKEN_COUNT)
                    .build();
            final var streamWriteConstraints = com.fasterxml.jackson.core.StreamWriteConstraints.builder()
                    .maxNestingDepth(MAX_WRITE_NESTING_DEPTH)
                    .build();
            final var factory = new MappingJsonFactory();
            factory.setStreamReadConstraints(streamReadConstraints);
            factory.setStreamWriteConstraints(streamWriteConstraints);
            builder.factory(factory);
        };
    }

    private static StreamReadConstraints readConstraints() {
        return StreamReadConstraints.builder()
                .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                .maxNameLength(MAX_NAME_LENGTH)
                .maxNestingDepth(MAX_READ_NESTING_DEPTH)
                .maxNumberLength(MAX_NUMBER_LENGTH)
                .maxStringLength(MAX_STRING_LENGTH)
                .maxTokenCount(MAX_TOKEN_COUNT)
                .build();
    }

    private static StreamWriteConstraints writeConstraints() {
        return StreamWriteConstraints.builder()
                .maxNestingDepth(MAX_WRITE_NESTING_DEPTH)
                .build();
    }
}
