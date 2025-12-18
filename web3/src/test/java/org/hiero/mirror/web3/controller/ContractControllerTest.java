// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.validation.HexValidator.MESSAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.evm.exception.PrecompileNotSupportedException;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.BlockNumberOutOfRangeException;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.GenericErrorResponse;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls;
import org.hiero.mirror.web3.web3j.generated.ERCTestContractHistorical;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hiero.mirror.web3.web3j.generated.EvmCodesHistorical;
import org.hiero.mirror.web3.web3j.generated.ExchangeRatePrecompileHistorical;
import org.hiero.mirror.web3.web3j.generated.NestedCallsHistorical;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContractHistorical;
import org.hiero.mirror.web3.web3j.generated.TestAddressThis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith({SpringExtension.class, OutputCaptureExtension.class})
@WebMvcTest(controllers = ContractController.class)
class ContractControllerTest {

    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final long THROTTLE_GAS_LIMIT = 10_000_000L;
    private static final String INIT_CODE = "0x6080604052348015600f57600080fd5b5060a38061001c6000396000f3";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContractExecutionService service;

    @MockitoBean
    private ThrottleManager throttleManager;

    @BeforeEach
    void setUp() {
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

    @SneakyThrows
    private ResultActions contractCall(ContractCallRequest request, final Map<String, String> headers) {
        final var requestBuilder = post(CALL_URI)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convert(request));

        // Add headers dynamically
        headers.forEach(requestBuilder::header);

        return mockMvc.perform(requestBuilder);
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
                        .string(convert(
                                new GenericErrorResponse(NOT_FOUND.getReasonPhrase(), "No static resource invalid."))));
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
                                "JSON parse error: Unexpected character ('f' (code 102)): was expecting double-quote to start field name",
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

    @NullAndEmptySource
    @ParameterizedTest
    @ValueSource(strings = {"earliest", "latest", "0", "0x1a", "pending", "safe", "finalized"})
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
    void callWithBlockNumberOutOfRangeExceptionTest() throws Exception {
        final var request = request();
        given(service.processCall(any())).willThrow(new BlockNumberOutOfRangeException("Unknown block number"));

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(
                                new GenericErrorResponse(BAD_REQUEST.getReasonPhrase(), "Unknown block number"))));
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
    void callWithErrorStatusesProducesInternalServerErrorTest(ResponseCodeEnum responseCode) throws Exception {
        final var request = request();
        request.setData("0xa26388bb");

        given(service.processCall(any())).willThrow(new MirrorEvmTransactionException(responseCode, null, null));

        contractCall(request)
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(convert(new GenericErrorResponse(responseCode.name(), null, null))));
    }

    private static java.util.stream.Stream<ResponseCodeEnum> serverResponseCodes() {
        return GenericControllerAdvice.SERVER_RESPONSE_CODES.stream();
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
        final var request = request();
        request.setData(data);
        request.setValue(0);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("contains invalid odd length characters")));
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

    @TestConfiguration
    public static class TestConfig {

        @Bean
        MirrorNodeEvmProperties evmProperties() {
            return new MirrorNodeEvmProperties();
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
