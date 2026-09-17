// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext.MAX_DEPTH;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@SuppressWarnings("removal")
class JacksonConfigurationTest {

    @Test
    void writeConstraintsAllowRecursiveActionCalls() throws Exception {
        final var customizer = new JacksonConfiguration().jacksonCustomizer(new EvmProperties());
        final var builder = Jackson2ObjectMapperBuilder.json();
        customizer.customize(builder);
        final var mapper = builder.build();

        var node = new ActionResponse().from("0xleaf");
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            node = new ActionResponse().from("0x" + depth).calls(List.of(node));
        }
        final var root = new ActionResponse().calls(List.of(node));

        assertThat(mapper.writeValueAsString(root)).contains("0xleaf");
    }

    @Test
    void objectMapperUsesCustomFactory() {
        final var customizer = new JacksonConfiguration().jacksonCustomizer(new EvmProperties());
        final var builder = Jackson2ObjectMapperBuilder.json();
        customizer.customize(builder);
        final var mapper = builder.build();

        assertThat(mapper).isInstanceOf(ObjectMapper.class);
        assertThat(mapper.getFactory().streamWriteConstraints().getMaxNestingDepth())
                .isEqualTo(2 * MAX_DEPTH + 8);
    }
}
