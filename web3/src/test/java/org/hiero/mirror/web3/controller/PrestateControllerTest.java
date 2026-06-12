// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.CommonUtils.instant;
import static org.hiero.mirror.web3.utils.Constants.PRESTATE_URI;
import static org.hiero.mirror.web3.utils.TransactionProviderEnum.entityAddress;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.PrestateContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.PrestateService;
import org.hiero.mirror.web3.service.PrestateServiceImpl;
import org.hiero.mirror.web3.service.RecordFileService;
import org.hiero.mirror.web3.service.RecordFileServiceImpl;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.utils.TransactionProviderEnum;
import org.hiero.mirror.web3.viewmodel.GenericErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@WebMvcTest(controllers = PrestateController.class)
class PrestateControllerTest {

    private static final DomainBuilder DOMAIN_BUILDER = new DomainBuilder();

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContractDebugService contractDebugService;

    @MockitoBean
    private ThrottleManager throttleManager;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private EthereumTransactionRepository ethereumTransactionRepository;

    @MockitoBean
    private ContractTransactionHashRepository contractTransactionHashRepository;

    @MockitoBean
    private ContractResultRepository contractResultRepository;

    @MockitoBean
    private RecordFileRepository recordFileRepository;

    @MockitoBean
    private CommonEntityAccessor commonEntityAccessor;

    @MockitoBean
    private Web3Properties web3Properties;

    @Resource
    private TracerProperties tracerProperties;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> callServiceParametersCaptor;

    @Captor
    private ArgumentCaptor<PrestateContext> prestateContextCaptor;

    @BeforeEach
    void setUp() {
        tracerProperties.setEnabled(true);
        doNothing()
                .when(contractDebugService)
                .processPrestateCall(callServiceParametersCaptor.capture(), prestateContextCaptor.capture());
    }

    TransactionIdOrHashParameter setUp(final TransactionProviderEnum provider) {
        provider.init(DOMAIN_BUILDER);

        final var transaction = provider.getTransaction().get();
        final var ethTransaction = provider.getEthTransaction().get();
        final var recordFile = provider.getRecordFile().get();
        final var contractTransactionHash =
                provider.getContractTransactionHash().get();
        final var contractResult = provider.getContractResult().get();
        final var contractEntity = provider.getContractEntity().get();
        final var senderEntity = provider.getSenderEntity().get();

        final var hash = provider.hasEthTransaction() ? ethTransaction.getHash() : transaction.getTransactionHash();
        final var consensusTimestamp = transaction.getConsensusTimestamp();
        final var payerAccountId = transaction.getPayerAccountId();
        final var validStartNs = transaction.getValidStartNs();
        final var senderId = contractResult.getSenderId();
        final var senderAddress = entityAddress(senderEntity);
        final var contractId = EntityId.of(contractResult.getContractId());
        final var contractAddress = entityAddress(contractEntity);

        when(contractTransactionHashRepository.findByHash(hash)).thenReturn(Optional.of(contractTransactionHash));
        when(transactionRepository.findByPayerAccountIdAndValidStartNs(
                        eq(payerAccountId.getId()), eq(validStartNs), anyLong(), anyLong()))
                .thenReturn(List.of(transaction));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        contractTransactionHash.getConsensusTimestamp(),
                        EntityId.of(contractTransactionHash.getPayerAccountId())))
                .thenReturn(Optional.ofNullable(ethTransaction));
        when(contractResultRepository.findById(consensusTimestamp)).thenReturn(Optional.of(contractResult));
        when(recordFileRepository.findByTimestamp(consensusTimestamp)).thenReturn(Optional.of(recordFile));
        when(commonEntityAccessor.evmAddressFromId(contractId, Optional.empty()))
                .thenReturn(contractAddress);
        when(commonEntityAccessor.evmAddressFromId(senderId, Optional.empty())).thenReturn(senderAddress);
        when(commonEntityAccessor.get(contractAddress, Optional.empty()))
                .thenReturn(Optional.ofNullable(contractEntity));
        when(commonEntityAccessor.get(senderAddress, Optional.empty())).thenReturn(Optional.of(senderEntity));

        if (ethTransaction != null) {
            return new TransactionHashParameter(Bytes.of(hash));
        } else {
            return new TransactionIdParameter(payerAccountId, instant(validStartNs));
        }
    }

    // --- Positive tests ---

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void successfulCallReturnsOk(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isOk());
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
        final var transactionIdOrHash = setUp(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash)
                        .queryParam("diff", String.valueOf(diff))
                        .queryParam("code", String.valueOf(code))
                        .queryParam("storage", String.valueOf(storage)))
                .andExpect(status().isOk());

        final var captured = prestateContextCaptor.getValue();
        assertThat(captured.isDiff()).isEqualTo(diff);
        assertThat(captured.isCode()).isEqualTo(code);
        assertThat(captured.isStorage()).isEqualTo(storage);
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithDefaultQueryParams(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isOk());

        final var captured = prestateContextCaptor.getValue();
        assertThat(captured.isDiff()).isFalse();
        assertThat(captured.isCode()).isFalse();
        assertThat(captured.isStorage()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callSuccessCors(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

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
        final var transactionIdOrHash = setUp(providerEnum);

        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";

        reset(contractDebugService);
        doThrow(new MirrorEvmTransactionException(
                        CONTRACT_EXECUTION_EXCEPTION, detailedErrorMessage, hexDataErrorMessage))
                .when(contractDebugService)
                .processPrestateCall(any(), any());

        mockMvc.perform(prestateRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(responseBody(new GenericErrorResponse(
                        CONTRACT_EXECUTION_EXCEPTION.name(), detailedErrorMessage, hexDataErrorMessage)));
    }

    @Test
    void callWhenTracerDisabled() throws Exception {
        tracerProperties.setEnabled(false);
        final var transactionIdOrHash = setUp(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void exceedingRateLimit(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

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
        final var transactionIdOrHash = setUp(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("diff", "true"))
                .andExpect(status().isOk());

        final var captured = prestateContextCaptor.getValue();
        assertThat(captured.isDiff()).isTrue();
        assertThat(captured.isCode()).isFalse();
        assertThat(captured.isStorage()).isFalse();
    }

    @Test
    void callWithOnlyCodeParamSet() throws Exception {
        final var transactionIdOrHash = setUp(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("code", "true"))
                .andExpect(status().isOk());

        final var captured = prestateContextCaptor.getValue();
        assertThat(captured.isDiff()).isFalse();
        assertThat(captured.isCode()).isTrue();
        assertThat(captured.isStorage()).isFalse();
    }

    @Test
    void callWithOnlyStorageParamSet() throws Exception {
        final var transactionIdOrHash = setUp(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("storage", "true"))
                .andExpect(status().isOk());

        final var captured = prestateContextCaptor.getValue();
        assertThat(captured.isDiff()).isFalse();
        assertThat(captured.isCode()).isFalse();
        assertThat(captured.isStorage()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithAllFlagsEnabled(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash)
                        .queryParam("diff", "true")
                        .queryParam("code", "true")
                        .queryParam("storage", "true"))
                .andExpect(status().isOk());

        final var captured = prestateContextCaptor.getValue();
        assertThat(captured.isDiff()).isTrue();
        assertThat(captured.isCode()).isTrue();
        assertThat(captured.isStorage()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithServiceThrowingRuntimeException(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

        reset(contractDebugService);
        doThrow(new RuntimeException("Unexpected error"))
                .when(contractDebugService)
                .processPrestateCall(any(), any());

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
        EntityManager entityManager() {
            return mock(EntityManager.class);
        }

        @Bean
        TransactionOperations transactionOperations() {
            return mock(TransactionOperations.class);
        }

        @Bean
        RecordFileService recordFileService(final RecordFileRepository recordFileRepository) {
            return new RecordFileServiceImpl(recordFileRepository);
        }

        @Bean
        PrestateService prestateService(
                final RecordFileService recordFileService,
                final ContractDebugService contractDebugService,
                final EthereumTransactionRepository ethereumTransactionRepository,
                final ContractResultRepository contractResultRepository,
                final CommonEntityAccessor commonEntityAccessor,
                final ContractTransactionHashRepository contractTransactionHashRepository,
                final TransactionRepository transactionRepository,
                final CommonProperties commonProperties) {
            return new PrestateServiceImpl(
                    contractDebugService,
                    commonEntityAccessor,
                    commonProperties,
                    recordFileService,
                    ethereumTransactionRepository,
                    contractResultRepository,
                    contractTransactionHashRepository,
                    transactionRepository);
        }

        @Bean
        TracerProperties tracerProperties() {
            final var tracerProperties = new TracerProperties();
            tracerProperties.setEnabled(true);

            return tracerProperties;
        }
    }
}
