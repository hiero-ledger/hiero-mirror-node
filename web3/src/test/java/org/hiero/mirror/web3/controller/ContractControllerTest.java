// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.SIMULATE;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.hiero.mirror.web3.validation.HexValidator.MESSAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.web3.ApiProperties;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.evm.exception.PrecompileNotSupportedException;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.ContractSimulateService;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.GenericErrorResponse;
import org.hiero.mirror.web3.viewmodel.SimulateBlockStateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCallResult;
import org.hiero.mirror.web3.viewmodel.SimulateLog;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hiero.mirror.web3.viewmodel.SimulateResponse;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls;
import org.hiero.mirror.web3.web3j.generated.ERCTestContractHistorical;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hiero.mirror.web3.web3j.generated.EvmCodesHistorical;
import org.hiero.mirror.web3.web3j.generated.ExchangeRatePrecompileHistorical;
import org.hiero.mirror.web3.web3j.generated.NestedCallsHistorical;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContractHistorical;
import org.hiero.mirror.web3.web3j.generated.TestAddressThis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith({MockitoExtension.class, SpringExtension.class, OutputCaptureExtension.class})
@WebMvcTest(controllers = ContractController.class)
final class ContractControllerTest {

    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final String SIMULATE_URI = "/api/v1/contracts/simulate";
    private static final long THROTTLE_GAS_LIMIT = 10_000_000L;
    private static final String INIT_CODE = "0x6080604052348015600f57600080fd5b5060a38061001c6000396000f3";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private Web3Properties web3Properties;

    @MockitoBean
    private ContractExecutionService service;

    @MockitoBean
    private ContractSimulateService contractSimulateService;

    @MockitoBean
    private ThrottleManager throttleManager;

    private static java.util.stream.Stream<ResponseCodeEnum> serverResponseCodes() {
        return GenericControllerAdvice.SERVER_RESPONSE_CODES.stream();
    }

    @BeforeEach
    void setUp() {
        web3Properties.setEnableStateOverrides(true);
        throttleManager.throttle(any(ContractCallRequest.class));
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }

    @SneakyThrows
    private ResultActions contractCall(ContractCallRequest request) {
        return mockMvc.perform(post(CALL_URI)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convert(request)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0x00000000000000000000000000000000000007e7", "0x00000000000000000000000000000000000004e2"})
    void estimateGas(String to) throws Exception {
        final var request = request();
        request.setEstimate(true);
        request.setValue(0);
        request.setTo(to);
        contractCall(request).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                DynamicEthCalls.BINARY,
                ERCTestContractHistorical.BINARY,
                EthCall.BINARY,
                EvmCodes.BINARY,
                EvmCodesHistorical.BINARY,
                ExchangeRatePrecompileHistorical.BINARY,
                NestedCallsHistorical.BINARY,
                PrecompileTestContractHistorical.BINARY,
                TestAddressThis.BINARY
            })
    void estimateGasContractDeploy(final String data) throws Exception {
        final var request = request();
        request.setEstimate(true);
        request.setValue(0);
        request.setTo(null);
        request.setData(data);
        contractCall(request).andExpect(status().isOk());
    }

    @ValueSource(longs = {2000, -2000, 16_000_000L, 0})
    @ParameterizedTest
    void estimateGasWithInvalidGasParameter(long gas) throws Exception {
        clearInvocations(throttleManager);
        final var errorString = gas < 21000L
                ? numberErrorString("gas", "greater", 21000L)
                : numberErrorString("gas", "less", 15_000_000L);
        final var request = request();
        request.setEstimate(true);
        request.setGas(gas);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), errorString))));
        verify(throttleManager, never()).throttle(any());
        verify(throttleManager, never()).restore(anyLong());
    }

    @Test
    void exceedingRateLimit() throws Exception {
        var request = request();
        doThrow(new ThrottleException("")).when(throttleManager).throttle(request);
        contractCall(request).andExpect(status().isTooManyRequests());
    }

    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "0x000000000000000000000000000000Z0000007e7",
                "00000000001239847e"
            })
    @ParameterizedTest
    void callInvalidTo(String to) throws Exception {
        final var request = request();
        request.setValue(0);
        request.setTo(to);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("to field")));
    }

    @Test
    void callInvalidToDueToTransfer() throws Exception {
        final var request = request();
        request.setTo(null);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("to field")));
    }

    @Test
    void callMissingTo() throws Exception {
        final var exceptionMessage = "No such contract or token";
        final var request = request();

        given(service.processCall(any())).willThrow(new EntityNotFoundException(exceptionMessage));

        contractCall(request)
                .andExpect(status().isNotFound())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(NOT_FOUND.getReasonPhrase(), exceptionMessage))));
    }

    @Test
    void notFound() throws Exception {
        final var request = request();
        mockMvc.perform(post("/invalid")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(convert(request)))
                .andExpect(status().isNotFound())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                NOT_FOUND.getReasonPhrase(), "No static resource invalid for request '/invalid'."))));
    }

    @EmptySource
    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "0x000000000000000000000000000000Z0000007e7",
                "00000000001239847e"
            })
    @ParameterizedTest
    void callInvalidFrom(String from) throws Exception {
        final var errorString = "from field ".concat(MESSAGE);
        final var request = request();
        request.setFrom(from);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), errorString))));
    }

    @Test
    void callInvalidValue() throws Exception {
        final var error = "value field must be greater than or equal to 0";
        final var request = request();
        request.setValue(-1L);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), error))));
    }

    @Test
    void callWithExplicitNullValue() throws Exception {
        // Test contract call with explicit null value in JSON (reproduces the curl issue)
        // With spring.jackson.use-jackson2-defaults=true, Jackson should treat null as the default
        // value (0L) for primitive long fields
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\": \"0x00000000000000000000000000000000000004e4\", "
                                + "\"estimate\": null, "
                                + "\"value\": null, "
                                + "\"from\": null, "
                                + "\"block\": \"latest\", "
                                + "\"data\": \"0x1079023a\", "
                                + "\"gas\": " + THROTTLE_GAS_LIMIT + ", "
                                + "\"gasPrice\": null}"))
                .andExpect(status().isOk());

        // Verify that the value field defaults to 0 when explicitly set to null
        verify(service).processCall(argThat(params -> params.getValue() == 0L));
    }

    @Test
    void callWithMalformedJsonBody() throws Exception {
        var request = "{from: 0x00000000000000000000000000000000000004e2\"";
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                "Bad Request",
                                "JSON parse error: Unexpected character ('f' (code 102)): was expecting double-quote to start property name",
                                StringUtils.EMPTY))));
    }

    @Test
    void callWithUnsupportedMediaTypeBody() throws Exception {
        final var request = request();
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(convert(request)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
                                "Content-Type 'text/plain;charset=UTF-8' is not supported"))));
    }

    @Test
    void callRevertMethodAndExpectDetailMessage() throws Exception {
        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";
        final var request = request();
        request.setData("0xa26388bb");

        given(service.processCall(any()))
                .willThrow(new MirrorEvmTransactionException(
                        CONTRACT_REVERT_EXECUTED, detailedErrorMessage, hexDataErrorMessage));

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage, hexDataErrorMessage))));
    }

    @Test
    void callWithInvalidParameter() throws Exception {
        final var error = "No such contract or token";
        final var request = request();

        given(service.processCall(any())).willThrow(new InvalidParametersException(error));
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(convert(new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), error))));
        verify(throttleManager).restore(request.getGas());
    }

    @Test
    void callWithIllegalArgumentExceptionRestoresThrottle() throws Exception {
        final var request = request();

        given(service.processCall(any())).willThrow(new IllegalArgumentException("Invalid hex value"));
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), "Invalid hex value"))));
        verify(throttleManager).restore(request.getGas());
    }

    @Test
    void callWithNotSupportedPrecompile() throws Exception {
        final var request = request();

        given(service.processCall(any())).willThrow(new PrecompileNotSupportedException(StringUtils.EMPTY));
        contractCall(request)
                .andExpect(status().isNotImplemented())
                .andExpect(content()
                        .string(convert(
                                new GenericErrorResponse(NOT_IMPLEMENTED.getReasonPhrase(), StringUtils.EMPTY))));
    }

    @Test
    void callInvalidGasPrice() throws Exception {
        final var errorString = numberErrorString("gasPrice", "greater", 0);
        final var request = request();
        request.setGasPrice(-1L);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), errorString))));
    }

    @Test
    void transferWithoutSender() throws Exception {
        final var errorString = "from field must not be empty";
        final var request = request();
        request.setFrom(null);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), errorString))));
    }

    @Test
    void callValidBlockTypeWithBlockHash() throws Exception {
        final var request = request();
        request.setBlock(new BlockType(HEX_PREFIX + "ef".repeat(48), BlockType.BLOCK_HASH_SENTINEL));

        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callBlockTypeNull() throws Exception {
        final var request = request();
        request.setBlock(null);
        contractCall(request).andExpect(status().isOk());
    }

    @NullAndEmptySource
    @ParameterizedTest
    @ValueSource(strings = {"earliest", "latest", "0", "pending", "safe", "finalized"})
    void callValidBlockType(String value) throws Exception {
        final var request = request();
        request.setBlock(BlockType.of(value));

        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callNegativeBlock() throws Exception {
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"block\": \"-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void callWithBlockNumberNotFoundExceptionTest() throws Exception {
        final var request = request();
        given(service.processCall(any())).willThrow(new BlockNumberNotFoundException());

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(
                                new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), "Unknown block number"))));
    }

    @ParameterizedTest
    @MethodSource("serverResponseCodes")
    void serverErrorStatusesDoNotLeakErrorDetailsToClient(ResponseCodeEnum responseCode) throws Exception {
        final var request = request();
        request.setData("0xa26388bb");

        given(service.processCall(any()))
                .willThrow(new MirrorEvmTransactionException(responseCode, "internal detail", "0xdeadbeef"));

        // On 5xx the detail and data must be redacted so internal server-side state is never leaked to the client.
        contractCall(request)
                .andExpect(status().isInternalServerError())
                .andExpect(content()
                        .string(convert(
                                new GenericErrorResponse(responseCode.name(), StringUtils.EMPTY, StringUtils.EMPTY))));
    }

    @Test
    void callSuccess() throws Exception {
        final var request = request();
        request.setData("0x1079023a0000000000000000000000000000000000000000000000000000000000000156");
        request.setValue(0);

        contractCall(request).andExpect(status().isOk());
    }

    @NullSource
    @ValueSource(strings = {"", "0x"})
    @ParameterizedTest
    void callSuccessWithNullAndEmptyData(String data) throws Exception {
        final var request = request();
        request.setData(data);
        request.setValue(0);

        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callSuccessOnContractCreateWithMissingFrom() throws Exception {
        final var request = request();
        request.setFrom(null);
        request.setData(INIT_CODE);
        request.setValue(0);
        request.setEstimate(false);

        contractCall(request).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1aa"})
    void callBadRequestWithInvalidHexData(String data) throws Exception {
        clearInvocations(throttleManager);
        final var request = request();
        request.setData(data);
        request.setValue(0);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("Odd number of characters")));

        verify(throttleManager, never()).throttle(any());
        verify(throttleManager, never()).restore(anyLong());
        verify(service, never()).processCall(any());
    }

    @Test
    void callFromWithUpperCaseHexPrefixDoesNotThrottle() throws Exception {
        clearInvocations(throttleManager);
        final var request = request();
        request.setFrom("0X00000000000000000000000000000000000004e2");
        request.setValue(0);

        contractCall(request).andExpect(status().isBadRequest());

        verify(throttleManager, never()).throttle(any());
        verify(throttleManager, never()).restore(anyLong());
        verify(service, never()).processCall(any());
    }

    @Test
    void callToWithUpperCaseHexPrefixDoesNotThrottle() throws Exception {
        clearInvocations(throttleManager);
        final var request = request();
        request.setTo("0X00000000000000000000000000000000000004e4");
        request.setValue(0);

        contractCall(request).andExpect(status().isBadRequest());

        verify(throttleManager, never()).throttle(any());
        verify(throttleManager, never()).restore(anyLong());
        verify(service, never()).processCall(any());
    }

    @Test
    void callBadRequestWithInvalidHexData() throws Exception {
        var invalidHexData = "0x12345z";

        var request = request();
        request.setData(invalidHexData);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("data field invalid hexadecimal string")));
    }

    @Test
    void callOkWithMixedCharactersHexData() throws Exception {
        var mixedCharactersData = "0xaBeCd0";

        var request = request();
        request.setData(mixedCharactersData);

        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void transferSuccess() throws Exception {
        final var request = request();
        request.setData(null);

        contractCall(request).andExpect(status().isOk());
    }

    /*
     * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
     * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
     */
    @Test
    void callSuccessCors() throws Exception {
        mockMvc.perform(options(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,HEAD,POST"));
    }

    @Test
    @SneakyThrows
    void handlesQueryTimeoutException(CapturedOutput capturedOutput) {
        final var request = request();
        given(service.processCall(any())).willThrow(new QueryTimeoutException("Query timeout"));

        contractCall(request)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(convert(new GenericErrorResponse("Service Unavailable"))));
        assertThat(capturedOutput.getOut()).contains("503 Query timeout");
    }

    // ── State override tests ──────────────────────────────────────────────────

    @Test
    void callWithStateOverrideDisabled() throws Exception {
        web3Properties.setEnableStateOverrides(false);
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e4");
        override.setCode("0x6080604052");
        final var request = request();
        request.setStateOverrides(List.of(override));
        contractCall(request).andExpect(status().isBadRequest());
    }

    @Test
    void callWithMaxStateOverrides() throws Exception {
        final var overrides = new ArrayList<StateOverride>();
        for (int i = 0; i < 11; i++) {
            final var override = new StateOverride();
            override.setAddress("0x00000000000000000000000000000000000004e4");
            overrides.add(override);
        }
        final var request = request();
        request.setStateOverrides(overrides);
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("stateOverrides field size must be between 0 and 10")));
    }

    @Test
    void callWithStateOverrideBalance() throws Exception {
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e2");
        override.setBalance("0xde0b6b3a7640000"); // 1 HBAR in tinybars hex
        final var request = request();
        request.setStateOverrides(List.of(override));
        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callWithStateOverrideNonce() throws Exception {
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e2");
        override.setNonce("0x2a");
        final var request = request();
        request.setStateOverrides(List.of(override));
        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callWithStateOverrideCode() throws Exception {
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e4");
        override.setCode("0x6080604052");
        final var request = request();
        request.setStateOverrides(List.of(override));
        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callWithStateOverrideCodeExceedsMax() throws Exception {
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e4");
        override.setCode("0x" + RandomStringUtils.secure().next(StateOverride.CODE_MAX_LENGTH + 1, "0123456789abcdef"));
        final var request = request();
        request.setStateOverrides(List.of(override));
        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("code field invalid hexadecimal string")));
    }

    @Test
    void callWithStateOverrideStateDiff() throws Exception {
        final var entry = new StorageEntry();
        entry.setKey("0x0000000000000000000000000000000000000000000000000000000000000001");
        entry.setValue("0x0000000000000000000000000000000000000000000000000000000000000064");
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e4");
        override.setStateDiff(List.of(entry));
        final var request = request();
        request.setStateOverrides(List.of(override));
        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callWithStateOverrideFullState() throws Exception {
        final var entry = new StorageEntry();
        entry.setKey("0x0000000000000000000000000000000000000000000000000000000000000000");
        entry.setValue("0x00000000000000000000000000000000000000000000000000000000deadbeef");
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e4");
        override.setState(List.of(entry));
        final var request = request();
        request.setStateOverrides(List.of(override));
        contractCall(request).andExpect(status().isOk());
    }

    @Test
    void callWithStateOverrideMutuallyExclusiveStateAndStateDiff() throws Exception {
        final var stateEntry = new StorageEntry();
        stateEntry.setKey("0x0000000000000000000000000000000000000000000000000000000000000001");
        stateEntry.setValue("0x0000000000000000000000000000000000000000000000000000000000000001");
        final var diffEntry = new StorageEntry();
        diffEntry.setKey("0x0000000000000000000000000000000000000000000000000000000000000002");
        diffEntry.setValue("0x0000000000000000000000000000000000000000000000000000000000000002");
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e4");
        override.setState(List.of(stateEntry));
        override.setStateDiff(List.of(diffEntry));
        final var request = request();
        request.setStateOverrides(List.of(override));

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("state and state_diff are mutually exclusive")));
    }

    @Test
    void callWithStateOverrideInvalidAddressKey() throws Exception {
        final var override = new StateOverride();
        override.setAddress("0x1234"); // too short (not 40 hex chars)
        override.setBalance("0x1");
        final var request = request();
        request.setStateOverrides(List.of(override));

        contractCall(request).andExpect(status().isBadRequest());
    }

    @Test
    void callWithStateOverrideUpperCaseAddressPrefix() throws Exception {
        final var override = new StateOverride();
        override.setAddress("0X00000000000000000000000000000000000004e4");
        override.setBalance("0x1");
        final var request = request();
        request.setStateOverrides(List.of(override));

        contractCall(request).andExpect(status().isOk());

        web3Properties.setEnableStateOverrides(false);
    }

    @Test
    void callWithStateOverrideInvalidNonce() throws Exception {
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\": \"0x00000000000000000000000000000000000004e4\","
                                + "\"state_overrides\": [{\"address\":"
                                + "\"0x00000000000000000000000000000000000004e2\","
                                + "\"nonce\": \"-1\"}]}"))
                .andExpect(status().isBadRequest());
    }

    private ContractCallRequest request() {
        final var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData("0x1079023a");
        request.setFrom("0x00000000000000000000000000000000000004e2");
        request.setGas(THROTTLE_GAS_LIMIT);
        request.setGasPrice(78282329L);
        request.setTo("0x00000000000000000000000000000000000004e4");
        request.setValue(23);
        return request;
    }

    private String numberErrorString(String field, String direction, long num) {
        return String.format("%s field must be %s than or equal to %d", field, direction, num);
    }

    @AfterEach
    void resetWeb3Properties() {
        enableSimulate(false);
        web3Properties.setEnableStateOverrides(false);
    }

    private void enableSimulate(final boolean enabled) {
        web3Properties
                .getApi()
                .computeIfAbsent(SIMULATE, _ -> new ApiProperties())
                .setEnabled(enabled);
    }

    @SneakyThrows
    private ResultActions simulate(SimulateRequest request) {
        return simulate(convert(request));
    }

    @SneakyThrows
    private ResultActions simulate(String requestBody) {
        return mockMvc.perform(post(SIMULATE_URI)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    private SimulateRequest simulateRequest(int callCount) {
        final var calls = new ArrayList<SimulateCall>();
        for (int i = 0; i < callCount; i++) {
            final var call = new SimulateCall();
            call.setTo("0x00000000000000000000000000000000000004e4");
            calls.add(call);
        }
        final var blockStateCall = new SimulateBlockStateCall();
        blockStateCall.setCalls(calls);
        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(blockStateCall));
        return request;
    }

    @Test
    void simulateSuccess() throws Exception {
        enableSimulate(true);
        final var request = simulateRequest(1);
        final var response = new SimulateResponse(List.of());
        given(contractSimulateService.simulate(request)).willReturn(response);

        simulate(request).andExpect(status().isOk()).andExpect(content().string(convert(response)));

        verify(throttleManager).throttleSimulateRequest(15_000_000L);
    }

    @Test
    void simulateDisabledIsBadRequestWithoutConsumingThrottle() throws Exception {
        final var request = simulateRequest(1);

        simulate(request).andExpect(status().isBadRequest());

        verify(throttleManager, never()).throttleSimulateRequest(anyLong());
    }

    @Test
    void simulateWithStateOverridesDisabledIsBadRequestWithoutConsumingThrottle() throws Exception {
        enableSimulate(true);
        web3Properties.setEnableStateOverrides(false);
        final var request = simulateRequest(1);
        final var override = new StateOverride();
        override.setAddress("0x00000000000000000000000000000000000004e4");
        override.setBalance("0x1");
        request.getBlockStateCalls().getFirst().setStateOverrides(List.of(override));

        simulate(request).andExpect(status().isBadRequest());

        verify(throttleManager, never()).throttleSimulateRequest(anyLong());
    }

    @Test
    void simulateExceedingMaxGasIsBadRequestWithoutConsumingThrottle() throws Exception {
        enableSimulate(true);
        final var request = simulateRequest(1);
        request.getBlockStateCalls().getFirst().getCalls().getFirst().setGas(15_000_001L);

        simulate(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString(numberErrorString("gas", "less", 15_000_000L))));

        verify(throttleManager, never()).throttleSimulateRequest(anyLong());
    }

    @Test
    void simulateBindsHexValuesSnakeCaseGasPriceAndIntegerBalance() throws Exception {
        enableSimulate(true);
        given(contractSimulateService.simulate(any(SimulateRequest.class))).willReturn(new SimulateResponse(List.of()));

        simulate("""
                {
                    "block_state_calls": [
                        {
                            "calls": [
                                {
                                    "from": "0x00000000000000000000000000000000000004e2",
                                    "gas": null,
                                    "gas_price": "0x10",
                                    "to": "0x00000000000000000000000000000000000004e4",
                                    "value": "0x1"
                                }
                            ],
                            "state_overrides": [
                                {
                                    "address": "0x00000000000000000000000000000000000004e4",
                                    "balance": 1208925819
                                }
                            ]
                        }
                    ]
                }
                """).andExpect(status().isOk());

        verify(contractSimulateService).simulate(argThat(request -> {
            final var entry = request.getBlockStateCalls().getFirst();
            final var call = entry.getCalls().getFirst();
            return call.getGas() == 15_000_000L
                    && call.getGasPrice() == 16L
                    && call.getValue() == 1L
                    && "0x480ebe7b".equals(entry.getStateOverrides().getFirst().getBalance());
        }));
    }

    @Test
    void simulateAcceptsAccessAndAuthorizationLists() throws Exception {
        enableSimulate(true);
        given(contractSimulateService.simulate(any(SimulateRequest.class))).willReturn(new SimulateResponse(List.of()));

        simulate("""
                {
                    "block_state_calls": [
                        {
                            "calls": [
                                {
                                    "access_list": [
                                        {
                                            "address": "0xde0b295669a9fd93d5f28d9ec85e40f4cb697bae",
                                            "storage_keys": [
                                                "0x0000000000000000000000000000000000000000000000000000000000000003"
                                            ]
                                        }
                                    ],
                                    "authorization_list": [
                                        {
                                            "address": "0x1111111111111111111111111111111111111111",
                                            "chain_id": "0x127",
                                            "nonce": 5,
                                            "r": "0x2222222222222222222222222222222222222222222222222222222222222222",
                                            "s": "0x3333333333333333333333333333333333333333333333333333333333333333",
                                            "y_parity": 1
                                        }
                                    ],
                                    "from": "0x00000000000000000000000000000000000004e2",
                                    "to": "0x00000000000000000000000000000000000004e4"
                                }
                            ]
                        }
                    ]
                }
                """).andExpect(status().isOk());

        verify(contractSimulateService).simulate(argThat(request -> {
            final var call = request.getBlockStateCalls().getFirst().getCalls().getFirst();
            return call.getAccessList().size() == 1
                    && call.getAccessList().getFirst().getStorageKeys().size() == 1
                    && call.getAuthorizationList().size() == 1
                    && call.getAuthorizationList().getFirst().getNonce() == 5L;
        }));
    }

    @Test
    void simulateResponseIsFlatWithIntegerIndices() throws Exception {
        enableSimulate(true);
        final var log = new SimulateLog(
                "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                "0x5e28f54a56dc9df973a058cd54b3eeef8c67a1a613cb5db1df8a0a434c931d56",
                20784968L,
                "0x01",
                1L,
                false,
                List.of(),
                "0xe7217784e0c3f7b35d39303b1165046e9b7e8af9b9cf80d5d5f96c3163de8f51",
                0L);
        final var response = new SimulateResponse(List.of(
                new SimulateCallResult("0x5208", List.of(), "0x", "0x1"),
                new SimulateCallResult("0x6308", List.of(log), "0x", "0x1")));
        given(contractSimulateService.simulate(any(SimulateRequest.class))).willReturn(response);

        simulate(simulateRequest(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[1].logs[0].block_number").value(20784968L))
                .andExpect(jsonPath("$.result[1].logs[0].log_index").value(1))
                .andExpect(jsonPath("$.result[1].logs[0].transaction_index").value(0));
    }

    @ParameterizedTest
    @MethodSource("invalidSimulateBodies")
    void simulateInvalidBodyIsBadRequestWithoutConsumingThrottle(final String body) throws Exception {
        enableSimulate(true);

        simulate(body).andExpect(status().isBadRequest());

        verify(throttleManager, never()).throttleSimulateRequest(anyLong());
    }

    private static Stream<String> invalidSimulateBodies() {
        final var call = "{\"to\": \"0x00000000000000000000000000000000000004e4\"}";
        final var tooManyCalls = String.join(",", Collections.nCopies(SimulateRequest.MAX_CALLS + 1, call));
        final var tooManyEntries = "{\"calls\": [" + call + "]},"
                + String.join(",", Collections.nCopies(SimulateRequest.MAX_CALLS, "{\"calls\": []}"));
        final var override = "{\"address\": \"0x00000000000000000000000000000000000004e4\"}";
        final var tooManyOverrides = String.join(",", Collections.nCopies(11, override));
        return Stream.of(
                "{\"block_state_calls\": null}",
                "{\"block_state_calls\": []}",
                "{\"block_state_calls\": [{\"calls\": null}]}",
                "{\"block_state_calls\": [{\"calls\": []}]}",
                "{\"block_state_calls\": [{\"calls\": [" + tooManyCalls + "]}]}",
                "{\"block_state_calls\": [" + tooManyEntries + "]}",
                "{\"block_state_calls\": [{\"calls\": [" + call + "], \"state_overrides\": [" + tooManyOverrides
                        + "]}]}",
                "{\"block_state_calls\": [{\"calls\": [" + call
                        + "], \"state_overrides\": [{\"address\": \"0x00000000000000000000000000000000000004e4\", \"balance\": -1}]}]}",
                "{\"block_state_calls\": [{\"calls\": [{\"access_list\": [{\"storage_keys\": []}], \"to\": \"0x00000000000000000000000000000000000004e4\"}]}]}");
    }

    @Test
    void simulateAtMaxCallsIsAccepted() throws Exception {
        enableSimulate(true);
        final var request = simulateRequest(SimulateRequest.MAX_CALLS);
        given(contractSimulateService.simulate(request)).willReturn(new SimulateResponse(List.of()));

        simulate(request).andExpect(status().isOk());
    }

    @TestConfiguration
    public static class TestConfig {

        @Bean
        EvmProperties evmProperties() {
            return new EvmProperties();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        Web3Properties web3Properties() {
            return new Web3Properties();
        }

        @Bean
        ThrottleProperties throttleProperties() {
            return new ThrottleProperties();
        }
    }
}
