// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.common.util.CommonUtils.instant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.ApiProperties;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.service.PrestateService;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.utils.TransactionProviderEnum;
import org.hiero.mirror.web3.viewmodel.GenericErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;

@AutoConfigureMockMvc
class PrestateControllerTest extends Web3IntegrationTest {

    private static final String PRESTATE_URI = "/api/v1/contracts/results/{transactionIdOrHash}/prestate";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PrestateProperties prestateProperties;

    @Resource
    private Web3Properties web3Properties;

    @MockitoBean
    private ThrottleManager throttleManager;

    @MockitoSpyBean
    private PrestateService prestateService;

    @BeforeEach
    void setUp() {
        prestateProperties.setEnabled(true);

        final var request = new ApiProperties.RequestProperties();
        request.getHeaders().put("Access-Control-Allow-Origin", "*");
        request.getHeaders().put("Cache-Control", "public, max-age=600");
        final var api = new ApiProperties();
        api.setRequest(request);
        web3Properties.getApi().put(Web3Properties.ApiEndpointName.PRESTATE, api);
    }

    TransactionIdOrHashParameter persistTransaction(final TransactionProviderEnum provider) {
        provider.init(domainBuilder);

        final var transaction = provider.getTransaction().persist();
        provider.getContractTransactionHash().persist();
        provider.getContractResult().persist();

        if (provider.hasEthTransaction()) {
            return new TransactionHashParameter(Bytes.of(provider.getHash()));
        }
        return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
    }

    TransactionIdOrHashParameter persistTransactionWithoutContractResult(final TransactionProviderEnum provider) {
        provider.init(domainBuilder);

        final var transaction = provider.getTransaction().persist();
        provider.getContractTransactionHash().persist();

        if (provider.hasEthTransaction()) {
            return new TransactionHashParameter(Bytes.of(provider.getHash()));
        }
        return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
    }

    // --- Positive tests ---

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void successfulCallReturnsOk(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = persistTransaction(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Cache-Control", "public, max-age=600"));
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, true",
        "false, true, true",
        "true, false, true",
        "true, true, false",
        "false, false, true",
        "false, true, false",
        "true, false, false",
        "false, false, false"
    })
    void callWithDifferentCombinationsOfFlags(final boolean diff, final boolean code, final boolean storage)
            throws Exception {
        final var transactionIdOrHash = persistTransaction(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash)
                        .queryParam("diff", String.valueOf(diff))
                        .queryParam("code", String.valueOf(code))
                        .queryParam("storage", String.valueOf(storage)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithDefaultQueryParams(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = persistTransaction(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callSuccessCors(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = persistTransaction(providerEnum);

        final String param =
                switch (transactionIdOrHash) {
                    case TransactionHashParameter hashParameter ->
                        hashParameter.hash().toHexString();
                    case TransactionIdParameter transactionIdParameter ->
                        transactionIdString(
                                transactionIdParameter.payerAccountId(), transactionIdParameter.validStart());
                };

        mockMvc.perform(options(PRESTATE_URI, param)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,HEAD,POST"));
    }

    // --- Negative / Error tests ---

    @ParameterizedTest
    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "00000000001239847e",
                "0.0.1234-1234567890",
                "0.0.1234-0-1234567890",
                "0.0.1234-1-123456789-",
            })
    void callInvalidTransactionIdOrHash(final String transactionIdOrHash) throws Exception {
        final var expectedMessage = StringUtils.hasText(transactionIdOrHash)
                ? "Unsupported ID format: '%s'".formatted(transactionIdOrHash)
                : "Missing transaction ID or hash";

        mockMvc.perform(prestateRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains(expectedMessage)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callThrowsExceptionAndExpectDetailMessage(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = persistTransactionWithoutContractResult(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isNotFound());
    }

    @Test
    void callWhenTracerDisabled() throws Exception {
        prestateProperties.setEnabled(false);
        final var transactionIdOrHash = persistTransaction(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isNotImplemented());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void exceedingRateLimit(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = persistTransaction(providerEnum);

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isOk());
        }

        doThrow(new ThrottleException("Requests per second rate limit exceeded."))
                .when(throttleManager)
                .throttlePrestateRequest();

        mockMvc.perform(prestateRequest(transactionIdOrHash))
                .andExpect(status().isTooManyRequests())
                .andExpect(responseBody(new GenericErrorResponse(
                        TOO_MANY_REQUESTS.getReasonPhrase(), "Requests per second rate limit exceeded.")));
    }

    // --- Corner case tests ---

    @Test
    void callWithOnlyDiffParamSet() throws Exception {
        final var transactionIdOrHash = persistTransaction(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("diff", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void callWithOnlyCodeParamSet() throws Exception {
        final var transactionIdOrHash = persistTransaction(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("code", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void callWithOnlyStorageParamSet() throws Exception {
        final var transactionIdOrHash = persistTransaction(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("storage", "true"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithAllFlagsEnabled(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = persistTransaction(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash)
                        .queryParam("diff", "true")
                        .queryParam("code", "true")
                        .queryParam("storage", "true"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithServiceThrowingRuntimeException(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = persistTransaction(providerEnum);

        doThrow(new RuntimeException("Unexpected error")).when(prestateService).processPrestateCall(any());

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isInternalServerError());
    }

    // --- Helper methods ---

    private MockHttpServletRequestBuilder prestateRequest(final TransactionIdOrHashParameter parameter) {
        final String transactionIdOrHash =
                switch (parameter) {
                    case TransactionHashParameter hashParameter ->
                        hashParameter.hash().toHexString();
                    case TransactionIdParameter transactionIdParameter ->
                        transactionIdString(
                                transactionIdParameter.payerAccountId(), transactionIdParameter.validStart());
                };

        return prestateRequest(transactionIdOrHash);
    }

    private MockHttpServletRequestBuilder prestateRequest(final String transactionIdOrHash) {
        return get(PRESTATE_URI, transactionIdOrHash)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResultMatcher responseBody(final Object expectedBody) throws JsonProcessingException {
        return content().string(objectMapper.writeValueAsString(expectedBody));
    }

    private static String transactionIdString(final EntityId payerAccountId, final Instant validStart) {
        return "%s-%d-%d".formatted(payerAccountId, validStart.getEpochSecond(), validStart.getNano());
    }
}
