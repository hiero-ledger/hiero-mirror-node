// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.DATA_MAX_LENGTH;

import com.hedera.node.config.data.JumboTransactionsConfig;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;

@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    // Configure JSON parsing limits to reject malicious input. Runs after Spring Boot's standard customizer (order 0),
    // so these limits replace the spring.jackson.factory.constraints defaults.
    @Bean
    JsonFactoryBuilderCustomizer jsonFactoryCustomizer(EvmProperties properties) {
        final var jumboConfig = properties.getVersionedConfiguration().getConfigData(JumboTransactionsConfig.class);
        final int maxSize = jumboConfig.ethereumMaxCallDataSize() * 2 + 1024;
        if (maxSize > DATA_MAX_LENGTH) {
            throw new IllegalArgumentException("Data size must be less than or equal to " + DATA_MAX_LENGTH);
        }

        final var streamReadConstraints = StreamReadConstraints.builder()
                .maxDocumentLength(maxSize)
                .maxNameLength(100)
                .maxNestingDepth(10)
                .maxNumberLength(19)
                .maxStringLength(maxSize)
                // Largest valid request is ~6,200 tokens: 10 state overrides with 100 storage entries each
                .maxTokenCount(7_000)
                .build();
        final var streamWriteConstraints =
                StreamWriteConstraints.builder().maxNestingDepth(100).build();
        return builder ->
                builder.streamReadConstraints(streamReadConstraints).streamWriteConstraints(streamWriteConstraints);
    }
}
