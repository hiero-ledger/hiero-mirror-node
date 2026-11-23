// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.DATA_MAX_LENGTH;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.STATE_OVERRIDES_MAX_SIZE;
import static org.hiero.mirror.web3.viewmodel.StateOverride.CODE_MAX_LENGTH;
import static org.hiero.mirror.web3.viewmodel.StateOverride.STORAGE_MAX_SIZE;
import static org.hiero.mirror.web3.viewmodel.StorageEntry.SLOT_HEX_LENGTH;

import com.hedera.node.config.data.JumboTransactionsConfig;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;

@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    // Headroom for JSON syntax, field names, scalar fields and whitespace around a request's variable-length values
    private static final int JSON_OVERHEAD = 1024;

    // A storage entry at its limits: {"key":"0x<64 hex>","value":"0x<64 hex>"}, with headroom for whitespace
    private static final int STORAGE_ENTRY_MAX_LENGTH = 2 * (SLOT_HEX_LENGTH + 2) + 64;

    // A state override at its limits: full-size code plus a full state or state_diff list
    private static final int STATE_OVERRIDE_MAX_LENGTH =
            CODE_MAX_LENGTH + STORAGE_MAX_SIZE * STORAGE_ENTRY_MAX_LENGTH + JSON_OVERHEAD;

    // Configure JSON parsing limits to reject malicious input. Runs after Spring Boot's standard customizer (order 0),
    // so these limits replace the spring.jackson.factory.constraints defaults.
    @Bean
    JsonFactoryBuilderCustomizer jsonFactoryCustomizer(EvmProperties properties) {
        final var jumboConfig = properties.getVersionedConfiguration().getConfigData(JumboTransactionsConfig.class);
        final int maxSize = jumboConfig.ethereumMaxCallDataSize() * 2 + JSON_OVERHEAD;
        if (maxSize > DATA_MAX_LENGTH) {
            throw new IllegalArgumentException("Data size must be less than or equal to " + DATA_MAX_LENGTH);
        }

        final var streamReadConstraints = StreamReadConstraints.builder()
                // The largest valid request carries maximum call data plus every state override at its limits
                .maxDocumentLength(maxDocumentLength(maxSize))
                .maxNameLength(100)
                .maxNestingDepth(10)
                .maxNumberLength(19)
                .maxStringLength(maxSize)
                // Largest valid request is ~6,200 tokens: 10 state overrides with 100 storage entries each
                .maxTokenCount(10_000)
                .build();
        final var streamWriteConstraints =
                StreamWriteConstraints.builder().maxNestingDepth(100).build();
        return builder ->
                builder.streamReadConstraints(streamReadConstraints).streamWriteConstraints(streamWriteConstraints);
    }

    static long maxDocumentLength(final int maxSize) {
        return maxSize + (long) STATE_OVERRIDES_MAX_SIZE * STATE_OVERRIDE_MAX_LENGTH;
    }
}
