// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hiero.mirror.web3.viewmodel.ActionTraceRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@SuppressWarnings("removal")
class ActionTraceJsonLimitsTest {

    @Test
    void actionMapperAcceptsSixteenCallsWithoutRelaxingOrdinaryCallLimits() throws Exception {
        final var base = new ObjectMapper();
        base.getFactory()
                .setStreamReadConstraints(
                        StreamReadConstraints.builder().maxTokenCount(100).build());
        final var converter = new MappingJackson2HttpMessageConverter(base);
        new JacksonConfiguration()
                .actionTraceJsonLimits()
                .extendMessageConverters(List.<HttpMessageConverter<?>>of(converter));
        final var actionMapper =
                converter.getObjectMappersForType(ActionTraceRequest.class).get(MediaType.APPLICATION_JSON);
        final var transaction = "{\"to\":\"0x0000000000000000000000000000000000000001\",\"gas\":21000}";
        final var body = "{\"bundles\":[{\"transactions\":["
                + String.join(",", java.util.Collections.nCopies(16, transaction)) + "]}]}";

        assertThat(actionMapper
                        .readValue(body, ActionTraceRequest.class)
                        .getBundles()
                        .getFirst()
                        .getTransactions())
                .hasSize(16);
        assertThat(base.getFactory().streamReadConstraints().getMaxTokenCount()).isEqualTo(100);
        assertThat(actionMapper.getFactory().streamReadConstraints().getMaxDocumentLength())
                .isEqualTo(ActionTraceRequest.MAX_DOCUMENT_LENGTH);
        final var excessiveTokens = "[" + "0,".repeat((int) ActionTraceRequest.MAX_JSON_TOKENS) + "0]";
        assertThatThrownBy(() -> actionMapper.readTree(excessiveTokens))
                .isInstanceOf(com.fasterxml.jackson.core.exc.StreamConstraintsException.class);
        // Jackson checks document length when refilling its input buffer.
        final var oversizedDocument = " ".repeat((int) ActionTraceRequest.MAX_DOCUMENT_LENGTH + 65_536) + "{}";
        assertThatThrownBy(() -> actionMapper.readTree(oversizedDocument))
                .isInstanceOf(com.fasterxml.jackson.core.exc.StreamConstraintsException.class);
        assertThat(converter.getObjectMappersForType(ContractCallRequest.class)).isEmpty();
    }

    @Test
    void rejectsAggregateCalldataAndOverridesEvenWhenIndividualFieldsFit() {
        final var transaction = new ContractCallRequest();
        transaction.setData("0x" + "00".repeat((int) (ContractCallRequest.DATA_MAX_LENGTH - 2) / 2));
        final var bundle = new org.hiero.mirror.web3.viewmodel.ActionTraceBundle();
        bundle.setTransactions(java.util.Collections.nCopies(16, transaction));
        final var request = new ActionTraceRequest();
        request.setBundles(List.of(bundle));
        final var override = new org.hiero.mirror.web3.viewmodel.StateOverride();
        override.setAddress("0x0000000000000000000000000000000000000001");
        override.setCode("0x01");
        request.setStateOverrides(List.of(override));

        try (final var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anyMatch(violation -> violation.getMessage().contains("aggregate calldata"));
        }
    }
}
