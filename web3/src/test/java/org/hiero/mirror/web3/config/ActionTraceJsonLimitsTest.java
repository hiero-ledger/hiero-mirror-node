// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.web3.viewmodel.ActionTraceRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ActionTraceJsonLimitsTest {

    private static final String FULL_CALLDATA = "0x" + "00".repeat((int) (ContractCallRequest.DATA_MAX_LENGTH - 2) / 2);
    private static final String TO = "0x0000000000000000000000000000000000000001";

    private JsonFactory baseFactory;
    private JacksonJsonHttpMessageConverter converter;

    @BeforeEach
    void setUp() {
        baseFactory = JsonFactory.builder()
                .streamReadConstraints(
                        StreamReadConstraints.builder().maxTokenCount(100).build())
                .build();
        converter = new JacksonJsonHttpMessageConverter(
                JsonMapper.builder(baseFactory).build());
        final JsonMapperBuilderCustomizer snakeCase =
                builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        final var converters = HttpMessageConverters.forServer().withJsonConverter(converter);
        new JacksonConfiguration()
                .actionTraceJsonLimits(baseFactory, List.of(snakeCase))
                .customize(converters);
        converters.build();
    }

    @Test
    void actionMapperAcceptsSixteenCallsWithoutRelaxingOrdinaryCallLimits() {
        final var actionMapper = actionMapper();
        final var transaction = "{\"to\":\"%s\",\"gas\":21000}".formatted(TO);
        final var body = "{\"only_top_call\":true,\"bundles\":[{\"transactions\":["
                + String.join(",", Collections.nCopies(16, transaction)) + "]}]}";

        final var request = actionMapper.readValue(body, ActionTraceRequest.class);

        // only_top_call binds only when the Spring Boot customizers reach the trace mapper.
        assertThat(request.isOnlyTopCall()).isTrue();
        assertThat(request.getBundles().getFirst().getTransactions()).hasSize(16);
        assertThat(baseFactory.streamReadConstraints().getMaxTokenCount()).isEqualTo(100);
        assertThat(actionMapper.tokenStreamFactory().streamReadConstraints().getMaxDocumentLength())
                .isEqualTo(ActionTraceRequest.MAX_DOCUMENT_LENGTH);
        assertThat(converter.getMappersForType(ContractCallRequest.class)).isEmpty();
    }

    @Test
    void actionMapperRejectsExcessiveTokens() {
        final var excessiveTokens = "[" + "0,".repeat((int) ActionTraceRequest.MAX_JSON_TOKENS) + "0]";

        assertThatThrownBy(() -> actionMapper().readTree(stream(excessiveTokens)))
                .isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void actionMapperAcceptsFullCalldataOnEveryTransaction() {
        final var request = actionMapper().readValue(stream(traceBody(List.of())), ActionTraceRequest.class);

        assertThat(request.getBundles().getFirst().getTransactions())
                .hasSize(16)
                .allSatisfy(transaction -> assertThat(transaction.getData()).isEqualTo(FULL_CALLDATA));
    }

    @Test
    void actionMapperRejectsCalldataAndStateOverridesBeyondDocumentLimit() {
        final var code = "0x" + "01".repeat((StateOverride.CODE_MAX_LENGTH - 2) / 2);
        final var overrides = Collections.nCopies(10, "{\"address\":\"%s\",\"code\":\"%s\"}".formatted(TO, code));

        assertThatThrownBy(() -> actionMapper().readTree(stream(traceBody(overrides))))
                .isInstanceOf(StreamConstraintsException.class);
    }

    private JsonMapper actionMapper() {
        return converter.getMappersForType(ActionTraceRequest.class).get(MediaType.APPLICATION_JSON);
    }

    /** Request bytes are read from a stream, as in HTTP, so Jackson checks the document length on each refill. */
    private static ByteArrayInputStream stream(final String json) {
        return new ByteArrayInputStream(json.getBytes(UTF_8));
    }

    private static String traceBody(final List<String> stateOverrides) {
        final var transaction = "{\"to\":\"%s\",\"data\":\"%s\"}".formatted(TO, FULL_CALLDATA);
        return "{\"state_overrides\":[" + String.join(",", stateOverrides) + "],\"bundles\":[{\"transactions\":["
                + String.join(",", Collections.nCopies(16, transaction)) + "]}]}";
    }
}
