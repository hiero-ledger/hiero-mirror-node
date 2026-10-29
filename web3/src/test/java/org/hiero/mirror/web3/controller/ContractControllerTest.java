// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.ApiEndpointName.CALL;
import static org.hiero.mirror.web3.utils.Constants.ACTIONS_CALL_URI;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.hiero.mirror.web3.validation.HexValidator.MESSAGE;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ACCESS_LIST_MAX_SIZE;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.AUTHORIZATION_LIST_MAX_SIZE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.ApiProperties;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.evm.exception.PrecompileNotSupportedException;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.exception.TraceTimeoutException;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.RecordFileService;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.throttle.EndpointThrottle;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.AccessListEntry;
import org.hiero.mirror.web3.viewmodel.ActionTraceBundle;
import org.hiero.mirror.web3.viewmodel.ActionTraceRequest;
import org.hiero.mirror.web3.viewmodel.AuthorizationListEntry;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.GenericErrorResponse;
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
import org.jspecify.annotations.Nullable;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith({MockitoExtension.class, SpringExtension.class, OutputCaptureExtension.class})
@WebMvcTest(controllers = ContractController.class)
final class ContractControllerTest {

    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final long THROTTLE_GAS_LIMIT = 10_000_000L;
    private static final String INIT_CODE = "0x6080604052348015600f57600080fd5b5060a38061001c6000396000f3";
    private static final ActionResponse TRACE_RESPONSE =
            new ActionResponse().from("0x01").to("0x02");
    private static final List<ActionResponse> TRACE_RESPONSES = List.of(TRACE_RESPONSE);

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private Web3Properties web3Properties;

    @Resource
    private ActionsProperties actionsProperties;

    @MockitoBean
    private ContractExecutionService service;

    @MockitoBean
    private ContractDebugService contractDebugService;

    @MockitoBean
    private EndpointThrottle endpointThrottle;

    @MockitoBean
    private RecordFileService recordFileService;

    @MockitoBean
    private ThrottleManager throttleManager;

    @MockitoBean
    private TransactionRepository transactionRepository;

    private static java.util.stream.Stream<ResponseCodeEnum> serverResponseCodes() {
        return GenericControllerAdvice.SERVER_RESPONSE_CODES.stream();
    }

    @BeforeEach
    void setUp() {
        web3Properties.setEnableBlockOverrides(true);
        web3Properties.setEnableStateOverrides(true);
        throttleManager.throttle(any(ContractCallRequest.class));
        web3Properties.getApi(CALL).setEnabled(true);
        final var recordFile = RecordFile.builder()
                .consensusStart(1_000_000_000L)
                .consensusEnd(2_000_000_000L)
                .index(1L)
                .build();
        lenient().when(recordFileService.findByBlockType(any())).thenReturn(Optional.of(recordFile));
        lenient()
                .when(transactionRepository.findConsensusTimestampAtPosition(anyLong(), anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.of(1_500_000_000L));
    }

    @AfterEach
    void tearDown() {
        setApiEnabled(ACTIONS, false);
        setApiEnabled(CALL, true);
    }

    private void enableActionsApi() {
        setApiEnabled(ACTIONS, true);
    }

    private void setApiEnabled(final ApiEndpointName name, final boolean enabled) {
        web3Properties.getApi().computeIfAbsent(name, _ -> new ApiProperties()).setEnabled(enabled);
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
    private ResultActions contractActionsCall(ActionTraceRequest request) {
        return contractActionsCall(request, null, null);
    }

    @SneakyThrows
    private ResultActions contractActionsCall(ActionTraceRequest request, final Boolean onlyTopCall) {
        return contractActionsCall(request, onlyTopCall, null);
    }

    @SneakyThrows
    private ResultActions contractActionsCall(
            ActionTraceRequest request, final Boolean onlyTopCall, final String timeout) {
        if (onlyTopCall != null) {
            request.setOnlyTopCall(onlyTopCall);
        }
        if (timeout != null) {
            request.setTimeout(timeout);
        }
        return mockMvc.perform(post(ACTIONS_CALL_URI)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
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
        doThrow(new ThrottleException("")).when(throttleManager).throttle(any(ContractCallRequest.class));
        contractCall(request).andExpect(status().isTooManyRequests());
    }

    @Test
    void actionsCallSuccess() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request)
                .andExpect(status().isOk())
                .andExpect(content().string(convert(TRACE_RESPONSES)));

        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> isSingleTrace(call, false, Duration.ofSeconds(4))));
        verify(endpointThrottle).throttle(ACTIONS);
        verify(throttleManager).throttle(any(ContractCallRequest.class));
    }

    @Test
    void actionsCallManySuccess() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var second = transaction();
        second.setValue(0);
        second.setData("0x1079023b");
        request.getBundles().add(bundleOf(second));
        final var secondResponse = new ActionResponse().from("0x03").to("0x04");
        given(contractDebugService.processTraceCall(any())).willReturn(List.of(TRACE_RESPONSE, secondResponse));

        contractActionsCall(request)
                .andExpect(status().isOk())
                .andExpect(content().string(convert(List.of(TRACE_RESPONSE, secondResponse))));

        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> call.getBundles().size() == 2));
        verify(endpointThrottle).throttle(ACTIONS);
        verify(throttleManager, times(2)).throttle(any(ContractCallRequest.class));
    }

    @Test
    void actionsCallExceedingEndpointRateLimit() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        doThrow(new ThrottleException("")).when(endpointThrottle).throttle(ACTIONS);

        contractActionsCall(request).andExpect(status().isTooManyRequests());

        verify(contractDebugService, never()).processTraceCall(any());
        verify(throttleManager, never()).throttle(any());
        verify(throttleManager, never()).restoreRequest();
        verify(throttleManager, never()).restore(anyLong());
    }

    @Test
    void actionsCallExceedingRateLimit() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        doThrow(new ThrottleException("")).when(throttleManager).throttle(any(ContractCallRequest.class));

        contractActionsCall(request).andExpect(status().isTooManyRequests());

        verify(contractDebugService, never()).processTraceCall(any());
        verify(endpointThrottle).throttle(ACTIONS);
        verify(throttleManager).throttle(any(ContractCallRequest.class));
        verify(throttleManager, never()).restoreRequest();
        verify(throttleManager, never()).restore(anyLong());
    }

    @Test
    void actionsCallReleasesEarlierReservationWhenALaterTransactionIsThrottled() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var second = transaction();
        second.setValue(0);
        second.setData("0x1079023b");
        request.getBundles().getFirst().getTransactions().add(second);
        doThrow(new ThrottleException(""))
                .when(throttleManager)
                .throttle(argThat((ContractCallRequest call) -> "0x1079023b".equals(call.getData())));

        contractActionsCall(request).andExpect(status().isTooManyRequests());

        verify(contractDebugService, never()).processTraceCall(any());
        verify(endpointThrottle).throttle(ACTIONS);
        verify(throttleManager).restoreRequest();
        verify(throttleManager).restore(THROTTLE_GAS_LIMIT);
    }

    @Test
    void actionsCallRejectsEmptyList() throws Exception {
        enableActionsApi();
        contractActionsCall(new ActionTraceRequest()).andExpect(status().isBadRequest());
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallWithOnlyTopCall() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request, true).andExpect(status().isOk());

        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> isSingleTrace(call, true, Duration.ofSeconds(4))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void actionsCallOmitsTimeoutWhenBlank(final String timeout) throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request, null, timeout).andExpect(status().isOk());

        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> isSingleTrace(call, false, Duration.ofSeconds(4))));
    }

    @Test
    void actionsCallWithTimeout() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request, null, "1s").andExpect(status().isOk());

        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> isSingleTrace(call, false, Duration.ofSeconds(1))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"30s", "PT30S"})
    void actionsCallCapsTimeoutAtMax(final String timeout) throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request, null, timeout).andExpect(status().isOk());

        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> isSingleTrace(call, false, Duration.ofSeconds(4))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s", "not-a-duration"})
    void actionsCallRejectsInvalidTimeout(final String timeout) throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);

        contractActionsCall(request, null, timeout).andExpect(status().isBadRequest());
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRestoresThrottleOnInvalidParameters() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any())).willThrow(new InvalidParametersException("invalid"));

        contractActionsCall(request).andExpect(status().isBadRequest());
        verify(contractDebugService).processTraceCall(any());
        verify(endpointThrottle).throttle(ACTIONS);
        verify(throttleManager).throttle(argThat((ContractCallRequest call) -> call.getGas() == THROTTLE_GAS_LIMIT));
        // The service returns gas for transactions that never ran. This mock does not, and the controller must not
        // return it a second time.
        verify(throttleManager, never()).restore(anyLong());
        verify(throttleManager, never()).restoreRequest();
    }

    @Test
    void actionsCallRequiresGzip() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);

        mockMvc.perform(post(ACTIONS_CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(convert(request)))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(new StringContains(NOT_ACCEPTABLE.getReasonPhrase())))
                .andExpect(content().string(new StringContains(GzipEncoding.MISSING_GZIP_HEADER_MESSAGE)));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallTimeoutReturns408() throws Exception {
        enableActionsApi();
        clearInvocations(throttleManager);
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any())).willThrow(new TraceTimeoutException(TRACE_RESPONSE));

        contractActionsCall(request)
                .andExpect(status().isRequestTimeout())
                .andExpect(content().string(convert(TRACE_RESPONSES)));
    }

    @Test
    void actionsCallTimeoutManyReturnsPartialList() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        request.getBundles().add(bundleOf(transaction()));
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any()))
                .willThrow(new TraceTimeoutException(List.of(TRACE_RESPONSE, TRACE_RESPONSE)));

        contractActionsCall(request)
                .andExpect(status().isRequestTimeout())
                .andExpect(content().string(convert(List.of(TRACE_RESPONSE, TRACE_RESPONSE))));
    }

    @Test
    void actionsCallRejectsStateOverridesWhenDisabled() throws Exception {
        enableActionsApi();
        web3Properties.setEnableStateOverrides(false);
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var override = new StateOverride();
        override.setAddress("00000000000000000000000000000000000004e4");
        request.setStateOverrides(List.of(override));

        contractActionsCall(request).andExpect(status().isBadRequest());
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsTransactionStateOverridesWhenDisabled() throws Exception {
        enableActionsApi();
        web3Properties.setEnableStateOverrides(false);
        final var request = traceRequest();
        transaction(request).setValue(0);
        transaction(request)
                .setStateOverrides(List.of(balanceOverride("00000000000000000000000000000000000004e4", "0x1")));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("State overrides are not supported.")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallForwardsSingleStateOverride() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var override = new StateOverride();
        override.setAddress("00000000000000000000000000000000000004e4");
        override.setBalance("0x1");
        request.setStateOverrides(List.of(override));
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> isSingleTrace(call, false, Duration.ofSeconds(4))
                        && List.of(override).equals(call.getStateOverrides())));
    }

    @Test
    void actionsCallForwardsMultipleStateOverrides() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var first = balanceOverride("00000000000000000000000000000000000004e4", "0x64");
        final var second = balanceOverride("00000000000000000000000000000000000004e5", "0xc8");
        request.setStateOverrides(List.of(first, second));
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> isSingleTrace(call, false, Duration.ofSeconds(4))
                        && List.of(first, second).equals(call.getStateOverrides())));
    }

    @Test
    void actionsCallManyForwardsMultipleStateOverrides() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var secondTransaction = transaction();
        secondTransaction.setValue(0);
        request.getBundles().add(bundleOf(secondTransaction));
        final var first = balanceOverride("00000000000000000000000000000000000004e4", "0x64");
        final var second = balanceOverride("00000000000000000000000000000000000004e5", "0xc8");
        request.setStateOverrides(List.of(first, second));
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> call.getBundles().size() == 2
                        && call.getBundles().getFirst().getTransactions().size() == 1
                        && call.getBundles().get(1).getTransactions().size() == 1
                        && List.of(first, second).equals(call.getStateOverrides())));
    }

    @Test
    void actionsCallNotImplementedWhenDisabled() throws Exception {
        final var request = traceRequest();

        contractActionsCall(request).andExpect(status().isNotImplemented());
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallForwardsOnlyTopCallForTraceCallMany() throws Exception {
        enableActionsApi();
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);
        final var manyBundles = traceRequest();
        transaction(manyBundles).setValue(0);
        manyBundles.getBundles().add(bundleOf(transaction()));
        manyBundles.setOnlyTopCall(true);

        contractActionsCall(manyBundles).andExpect(status().isOk());

        final var manyTransactions = traceRequest();
        transaction(manyTransactions).setValue(0);
        manyTransactions.getBundles().getFirst().getTransactions().add(transaction());
        manyTransactions.setOnlyTopCall(true);

        contractActionsCall(manyTransactions).andExpect(status().isOk());

        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) ->
                        call.isOnlyTopCall() && call.getBundles().size() == 2));
        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> call.isOnlyTopCall()
                        && call.getBundles().size() == 1
                        && call.getBundles().getFirst().getTransactions().size() == 2));
    }

    @Test
    void actionsCallPassesTransactionIndexForTraceCallMany() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var second = transaction();
        second.setValue(0);
        request.getBundles().add(bundleOf(second));
        request.setBlock(BlockType.of("15"));
        request.setTransactionIndex(5);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());

        verify(contractDebugService)
                .processTraceCall(
                        argThat((TraceCall call) -> call.getBundles().size() == 2 && call.getTransactionIndex() == 5));
        verify(endpointThrottle).throttle(ACTIONS);
        verify(throttleManager, times(2)).throttle(any(ContractCallRequest.class));
    }

    @Test
    void actionsCallPassesMinusOneTransactionIndex() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        request.setTransactionIndex(-1);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());

        verify(contractDebugService).processTraceCall(argThat((TraceCall call) -> call.getTransactionIndex() == -1));
        verify(endpointThrottle).throttle(ACTIONS);
        verify(throttleManager).throttle(any(ContractCallRequest.class));
    }

    @Test
    void actionsCallRejectsEstimate() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        transaction(request).setEstimate(true);

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("Estimate is not supported for tracing.")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsBlockOverrideWhenDisabled() throws Exception {
        enableActionsApi();
        web3Properties.setEnableBlockOverrides(false);
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var override = new BlockOverride();
        override.setNumber("0x100");
        bundle(request).setBlockOverride(override);

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("Block overrides are not supported.")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallUsesRequestBlockForEveryTransaction() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        transaction(request).setBlock(BlockType.of("16"));
        request.setBlock(BlockType.of("32"));
        final var second = transaction();
        second.setValue(0);
        second.setBlock(BlockType.EARLIEST);
        request.getBundles().add(bundleOf(second));
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService).processTraceCall(argThat((TraceCall call) -> {
            if (!BlockType.of("32").equals(call.getBlock())
                    || call.getBundles().size() != 2
                    || call.getTransactionIndex() != -1) {
                return false;
            }
            for (final var bundle : call.getBundles()) {
                for (final var transaction : bundle.getTransactions()) {
                    if (!BlockType.of("32").equals(transaction.getBlock())) {
                        return false;
                    }
                }
            }
            return true;
        }));
    }

    @Test
    void actionsCallEvmRevertReturnsOkWithRevertPayload() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var reverted =
                new ActionResponse().from("0x01").to("0x02").error("REVERT").revertReason("0x08c379a0");
        given(contractDebugService.processTraceCall(any())).willReturn(List.of(reverted));

        contractActionsCall(request)
                .andExpect(status().isOk())
                .andExpect(content().string(convert(List.of(reverted))));
    }

    @Test
    void actionsCallTransactionFailureReturnsBadRequest() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        given(contractDebugService.processTraceCall(any()))
                .willThrow(new MirrorEvmTransactionException(
                        ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND, "sender not found", "0x"));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(convert(new GenericErrorResponse(
                                ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND.name(), "sender not found", "0x"))));
    }

    @Test
    void actionsCallAcceptsBlockOverrideNumberAndTime() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var override = new BlockOverride();
        override.setNumber("0x100");
        override.setTime("0x65f9e0c0");
        bundle(request).setBlockOverride(override);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService).processTraceCall(argThat((TraceCall call) -> {
            final var blockOverride = call.getBundles().getFirst().getBlockOverride();
            return blockOverride != null
                    && "0x100".equals(blockOverride.getNumber())
                    && "0x65f9e0c0".equals(blockOverride.getTime());
        }));
    }

    @Test
    void actionsCallAcceptsBlockOverrideNumber() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var override = new BlockOverride();
        override.setNumber("0x100");
        bundle(request).setBlockOverride(override);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService).processTraceCall(argThat((TraceCall call) -> {
            if (call.getBundles().size() != 1) {
                return false;
            }
            final var blockOverride = call.getBundles().getFirst().getBlockOverride();
            return blockOverride != null
                    && "0x100".equals(blockOverride.getNumber())
                    && blockOverride.getTime() == null;
        }));
    }

    @Test
    void actionsCallAcceptsBlockOverrideTime() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var override = new BlockOverride();
        override.setTime("0x65f9e0c0");
        bundle(request).setBlockOverride(override);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService).processTraceCall(argThat((TraceCall call) -> {
            if (call.getBundles().size() != 1) {
                return false;
            }
            final var blockOverride = call.getBundles().getFirst().getBlockOverride();
            return blockOverride != null
                    && "0x65f9e0c0".equals(blockOverride.getTime())
                    && blockOverride.getNumber() == null;
        }));
    }

    @Test
    void actionsCallRejectsOlderBlockNumber() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        bundle(request).setBlockOverride(blockNumber("0x100"));
        request.getBundles().add(tracedBundle(blockNumber("0xff")));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains("block_override.number on bundle 1 is older than bundle 0")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsOlderBlockTime() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        bundle(request).setBlockOverride(blockTime("0x65f9e0c0"));
        request.getBundles().add(tracedBundle(blockTime("0x65f9e0bf")));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().string(new StringContains("block_override.time on bundle 1 is older than bundle 0")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsBlockNumberOlderThanAdvancedPreviousBundle() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        bundle(request).setBlockOverride(blockNumber("0x100"));
        request.getBundles().add(tracedBundle(null));
        request.getBundles().add(tracedBundle(blockNumber("0x100")));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains("block_override.number on bundle 2 is older than bundle 1")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsBlockTimeOlderThanAdvancedPreviousBundle() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        bundle(request).setBlockOverride(blockTime("0x65f9e0c0"));
        request.getBundles().add(tracedBundle(null));
        request.getBundles().add(tracedBundle(blockTime("0x65f9e0c1")));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().string(new StringContains("block_override.time on bundle 2 is older than bundle 1")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallAcceptsNonDecreasingBlockOverrides() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        bundle(request).setBlockOverride(blockNumber("256"));
        request.getBundles().add(tracedBundle(blockNumber("0x100")));
        request.getBundles().add(tracedBundle(null));
        request.getBundles().add(tracedBundle(blockNumber("0x102")));
        request.getBundles().add(tracedBundle(blockTime("0x65f9e0c0")));
        request.getBundles().add(tracedBundle(null));
        request.getBundles().add(tracedBundle(blockTime("0x65f9e0c2")));
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());
        verify(contractDebugService)
                .processTraceCall(argThat((TraceCall call) -> call.getBundles().size() == 7));
    }

    @Test
    void actionsCallSerializesDeeplyNestedCalls() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        var nested = new ActionResponse().from("0x1");
        for (int i = 0; i < 120; i++) {
            nested = new ActionResponse().from("0x1").calls(List.of(nested));
        }
        given(contractDebugService.processTraceCall(any())).willReturn(List.of(nested));

        contractActionsCall(request).andExpect(status().isOk());
    }

    @Test
    void callAcceptsGasPriceSnakeCaseAlias() throws Exception {
        given(service.processCall(any())).willReturn("0x0");
        mockMvc.perform(post(CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"0x00000000000000000000000000000000000004e4","gas_price":100}
                                """))
                .andExpect(status().isOk());
        verify(service).processCall(argThat(params -> params.getGasPrice() == 100L));
    }

    @Test
    void callRejectsBlockOverrideWhenDisabled() throws Exception {
        web3Properties.setEnableBlockOverrides(false);
        final var request = request();
        final var override = new BlockOverride();
        override.setNumber("0x100");
        request.setBlockOverride(override);

        contractCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("Block overrides are not supported.")));
        verify(service, never()).processCall(any());
    }

    @Test
    void callWithoutBlockOverrideSucceedsWhenBlockOverridesDisabled() throws Exception {
        web3Properties.setEnableBlockOverrides(false);
        given(service.processCall(any())).willReturn("0x0");

        contractCall(request()).andExpect(status().isOk());
        verify(service).processCall(argThat(params -> params.getBlockOverride() == null));
    }

    @Test
    void callAcceptsBlockOverrideNumber() throws Exception {
        given(service.processCall(any())).willReturn("0x0");
        final var request = request();
        final var override = new BlockOverride();
        override.setNumber("0x100");
        request.setBlockOverride(override);

        contractCall(request).andExpect(status().isOk());
        verify(service).processCall(argThat(params -> {
            final var blockOverride = params.getBlockOverride();
            return blockOverride != null
                    && "0x100".equals(blockOverride.getNumber())
                    && blockOverride.getTime() == null;
        }));
    }

    @Test
    void callAcceptsBlockOverrideTime() throws Exception {
        given(service.processCall(any())).willReturn("0x0");
        final var request = request();
        final var override = new BlockOverride();
        override.setTime("0x65f9e0c0");
        request.setBlockOverride(override);

        contractCall(request).andExpect(status().isOk());
        verify(service).processCall(argThat(params -> {
            final var blockOverride = params.getBlockOverride();
            return blockOverride != null
                    && "0x65f9e0c0".equals(blockOverride.getTime())
                    && blockOverride.getNumber() == null;
        }));
    }

    @Test
    void callAcceptsBlockOverrideNumberAndTime() throws Exception {
        given(service.processCall(any())).willReturn("0x0");
        final var request = request();
        final var override = new BlockOverride();
        override.setNumber("0x100");
        override.setTime("0x65f9e0c0");
        request.setBlockOverride(override);

        contractCall(request).andExpect(status().isOk());
        verify(service).processCall(argThat(params -> {
            final var blockOverride = params.getBlockOverride();
            return blockOverride != null
                    && "0x100".equals(blockOverride.getNumber())
                    && "0x65f9e0c0".equals(blockOverride.getTime());
        }));
    }

    @Test
    void actionsCallRejectsAccessListEntryWithoutAddress() throws Exception {
        enableActionsApi();
        mockMvc.perform(post(ACTIONS_CALL_URI)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bundles":[{"transactions":[{"to":"0x00000000000000000000000000000000000004e4",\
                                "value":0,"access_list":[{"storage_keys":[]}]}]}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("address")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsOversizedAccessList() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var entry = new AccessListEntry();
        entry.setAddress("0x00000000000000000000000000000000000004e4");
        transaction(request).setAccessList(Collections.nCopies(ACCESS_LIST_MAX_SIZE + 1, entry));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains(
                                "accessList field size must be between 0 and %d".formatted(ACCESS_LIST_MAX_SIZE))));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsOversizedAuthorizationList() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var entry = new AuthorizationListEntry();
        entry.setAddress("0x00000000000000000000000000000000000004e4");
        transaction(request).setAuthorizationList(Collections.nCopies(AUTHORIZATION_LIST_MAX_SIZE + 1, entry));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains("authorizationList field size must be between 0 and %d"
                                .formatted(AUTHORIZATION_LIST_MAX_SIZE))));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsOversizedStorageKeys() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        final var entry = new AccessListEntry();
        entry.setAddress("0x00000000000000000000000000000000000004e4");
        entry.setStorageKeys(
                Collections.nCopies(10_001, "0x0000000000000000000000000000000000000000000000000000000000000001"));
        transaction(request).setAccessList(List.of(entry));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("storageKeys field size must be between 0 and 10000")));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallAcceptsNullableAccessAndAuthorizationLists() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        transaction(request).setValue(0);
        transaction(request).setAccessList(null);
        transaction(request).setAuthorizationList(null);
        given(contractDebugService.processTraceCall(any())).willReturn(TRACE_RESPONSES);

        contractActionsCall(request).andExpect(status().isOk());

        transaction(request).setAccessList(List.of());
        transaction(request).setAuthorizationList(List.of());
        contractActionsCall(request).andExpect(status().isOk());
    }

    @Test
    void actionsCallRejectsTooManyBundles() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        final var bundles = new ArrayList<ActionTraceBundle>();
        for (int i = 0; i < ContractCallRequest.MAX_TRACE_BUNDLES + 1; i++) {
            bundles.add(bundleOf(transaction()));
        }
        request.setBundles(bundles);

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains("bundles field size must be between 0 and %d"
                                .formatted(ContractCallRequest.MAX_TRACE_BUNDLES))));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsTooManyTransactions() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        final var transactions = new ArrayList<ContractCallRequest>();
        for (int i = 0; i < ContractCallRequest.MAX_TRACE_TRANSACTIONS + 1; i++) {
            transactions.add(transaction());
        }
        bundle(request).setTransactions(transactions);

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains("transactions field size must be between 0 and %d"
                                .formatted(ContractCallRequest.MAX_TRACE_TRANSACTIONS))));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"bundles\":[null]}",
                "{\"bundles\":[{\"transactions\":[null]}]}",
                "{\"bundles\":[{\"transactions\":[{}]}],\"state_overrides\":[null]}",
                "{\"bundles\":[{\"transactions\":[{}]}],\"state_overrides\":[{\"address\":\"0x0000000000000000000000000000000000000001\",\"state\":[null]}]}"
            })
    void actionsCallRejectsNullElements(final String body) throws Exception {
        enableActionsApi();
        mockMvc.perform(post(ACTIONS_CALL_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                        .content(body))
                .andExpect(status().isBadRequest());
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsTooManyTransactionsAcrossBundles() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        final var bundles = new ArrayList<ActionTraceBundle>();
        for (int i = 0; i < 8; i++) {
            final var transactions = new ArrayList<ContractCallRequest>();
            for (int j = 0; j < 3; j++) {
                transactions.add(transaction());
            }
            final var bundle = new ActionTraceBundle();
            bundle.setTransactions(transactions);
            bundles.add(bundle);
        }
        request.setBundles(bundles);

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains("transactions field size must be between 1 and %d"
                                .formatted(ContractCallRequest.MAX_TRACE_TRANSACTIONS))));
        verify(contractDebugService, never()).processTraceCall(any());
    }

    @Test
    void actionsCallRejectsOversizedBlockOverride() throws Exception {
        enableActionsApi();
        final var request = traceRequest();
        bundle(request).setBlockOverride(blockNumber("1".repeat(BlockOverride.MAX_LENGTH + 1)));

        contractActionsCall(request)
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string(new StringContains(
                                "number field size must be between 0 and %d".formatted(BlockOverride.MAX_LENGTH))));
        verify(contractDebugService, never()).processTraceCall(any());
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
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,HEAD,POST"))
                .andExpect(header().string("Access-Control-Expose-Headers", CommonProperties.CLUSTER_ID_HEADER));
    }

    @Test
    void callSuccessReturnsClusterIdHeader() throws Exception {
        contractCall(request())
                .andExpect(status().isOk())
                .andExpect(header().string(
                                CommonProperties.CLUSTER_ID_HEADER,
                                CommonProperties.getInstance().getClusterId()));
    }

    @Test
    @SneakyThrows
    void handlesQueryTimeoutException(CapturedOutput capturedOutput) {
        final var request = request();
        given(service.processCall(any())).willThrow(new QueryTimeoutException("Query timeout"));

        contractCall(request)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(
                                CommonProperties.CLUSTER_ID_HEADER,
                                CommonProperties.getInstance().getClusterId()))
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

    private static StateOverride balanceOverride(final String address, final String balance) {
        final var override = new StateOverride();
        override.setAddress(address);
        override.setBalance(balance);
        return override;
    }

    private static boolean isSingleTrace(final TraceCall call, final boolean onlyTopCall, final Duration timeout) {
        return call.getBlock() == BlockType.LATEST
                && call.isOnlyTopCall() == onlyTopCall
                && timeout.equals(call.getTimeout())
                && call.getBundles().size() == 1
                && call.getBundles().getFirst().getTransactions().size() == 1
                && call.getBundles().getFirst().getBlockOverride() == null;
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

    private ActionTraceRequest traceRequest() {
        final var request = new ActionTraceRequest();
        request.setBlock(BlockType.LATEST);
        request.setBundles(new ArrayList<>(List.of(bundleOf(transaction()))));
        return request;
    }

    private ContractCallRequest transaction() {
        return request();
    }

    private static ContractCallRequest transaction(final ActionTraceRequest request) {
        return request.getBundles().getFirst().getTransactions().getFirst();
    }

    private static ActionTraceBundle bundle(final ActionTraceRequest request) {
        return request.getBundles().getFirst();
    }

    private static BlockOverride blockNumber(final String number) {
        final var override = new BlockOverride();
        override.setNumber(number);
        return override;
    }

    private static BlockOverride blockTime(final String time) {
        final var override = new BlockOverride();
        override.setTime(time);
        return override;
    }

    private ActionTraceBundle tracedBundle(final @Nullable BlockOverride override) {
        final var bundle = bundleOf(transaction());
        bundle.getTransactions().getFirst().setValue(0);
        bundle.setBlockOverride(override);
        return bundle;
    }

    private static ActionTraceBundle bundleOf(final ContractCallRequest transaction) {
        final var bundle = new ActionTraceBundle();
        bundle.setTransactions(new ArrayList<>(List.of(transaction)));
        return bundle;
    }

    private String numberErrorString(String field, String direction, long num) {
        return String.format("%s field must be %s than or equal to %d", field, direction, num);
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
        ActionsProperties actionsProperties() {
            return new ActionsProperties();
        }

        @Bean
        ThrottleProperties throttleProperties() {
            return new ThrottleProperties();
        }
    }
}
