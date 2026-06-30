// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.common.util.CommonUtils.instant;
import static org.hiero.mirror.web3.utils.Constants.PRESTATE_URI;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractStateChangeRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.PrestateService;
import org.hiero.mirror.web3.service.PrestateServiceImpl;
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
    private ThrottleManager throttleManager;

    @MockitoBean
    private AccountBalanceRepository accountBalanceRepository;

    @MockitoBean
    private ContractActionRepository contractActionRepository;

    @MockitoBean
    private ContractRepository contractRepository;

    @MockitoBean
    private ContractStateChangeRepository contractStateChangeRepository;

    @MockitoBean
    private EntityRepository entityRepository;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private EthereumTransactionRepository ethereumTransactionRepository;

    @MockitoBean
    private ContractTransactionHashRepository contractTransactionHashRepository;

    @MockitoBean
    private ContractResultRepository contractResultRepository;

    @MockitoBean
    private Web3Properties web3Properties;

    @Resource
    private PrestateProperties prestateProperties;

    @BeforeEach
    void setUp() {
        prestateProperties.setEnabled(true);
    }

    TransactionIdOrHashParameter setUp(final TransactionProviderEnum provider) {
        provider.init(DOMAIN_BUILDER);

        final var transaction = provider.getTransaction().get();
        final var ethTransaction = provider.getEthTransaction().get();
        final var contractTransactionHash =
                provider.getContractTransactionHash().get();
        final var contractResult = provider.getContractResult().get();

        final var hash = provider.hasEthTransaction() ? ethTransaction.getHash() : transaction.getTransactionHash();
        final var consensusTimestamp = transaction.getConsensusTimestamp();
        final var payerAccountId = transaction.getPayerAccountId();
        final var validStartNs = transaction.getValidStartNs();

        when(contractTransactionHashRepository.findByHash(hash)).thenReturn(Optional.of(contractTransactionHash));
        when(transactionRepository.findByPayerAccountIdAndValidStartNs(
                        eq(payerAccountId.getId()), eq(validStartNs), anyLong(), anyLong()))
                .thenReturn(List.of(transaction));
        when(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        contractTransactionHash.getConsensusTimestamp(),
                        EntityId.of(contractTransactionHash.getPayerAccountId())))
                .thenReturn(Optional.ofNullable(ethTransaction));
        when(contractResultRepository.findById(consensusTimestamp)).thenReturn(Optional.of(contractResult));
        when(contractActionRepository.findByConsensusTimestamp(consensusTimestamp))
                .thenReturn(Collections.emptyList());
        when(contractStateChangeRepository.findByConsensusTimestamp(consensusTimestamp))
                .thenReturn(Collections.emptyList());
        when(contractRepository.findByConsensusTimestamp(consensusTimestamp)).thenReturn(Collections.emptyList());
        when(entityRepository.findActiveSnapshotsByIdsAndTimestamp(anyCollection(), anyLong()))
                .thenReturn(Collections.emptyList());
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

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
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithDefaultQueryParams(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isOk());
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

        doThrow(new EntityNotFoundException("Contract result not found"))
                .when(contractResultRepository)
                .findById(anyLong());

        mockMvc.perform(prestateRequest(transactionIdOrHash)).andExpect(status().isNotFound());
    }

    @Test
    void callWhenTracerDisabled() throws Exception {
        prestateProperties.setEnabled(false);
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
    }

    @Test
    void callWithOnlyCodeParamSet() throws Exception {
        final var transactionIdOrHash = setUp(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("code", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void callWithOnlyStorageParamSet() throws Exception {
        final var transactionIdOrHash = setUp(TransactionProviderEnum.CONTRACT_CALL);

        mockMvc.perform(prestateRequest(transactionIdOrHash).queryParam("storage", "true"))
                .andExpect(status().isOk());
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
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithServiceThrowingRuntimeException(final TransactionProviderEnum providerEnum) throws Exception {
        final var transactionIdOrHash = setUp(providerEnum);

        doThrow(new RuntimeException("Unexpected error"))
                .when(contractActionRepository)
                .findByConsensusTimestamp(anyLong());

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
        PrestateService prestateService(
                final AccountBalanceRepository accountBalanceRepository,
                final ContractActionRepository contractActionRepository,
                final ContractRepository contractRepository,
                final ContractResultRepository contractResultRepository,
                final ContractStateChangeRepository contractStateChangeRepository,
                final ContractTransactionHashRepository contractTransactionHashRepository,
                final EntityRepository entityRepository,
                final CommonProperties commonProperties,
                final TransactionRepository transactionRepository) {
            return new PrestateServiceImpl(
                    accountBalanceRepository,
                    contractActionRepository,
                    contractRepository,
                    contractResultRepository,
                    contractStateChangeRepository,
                    contractTransactionHashRepository,
                    entityRepository,
                    new SystemEntity(commonProperties),
                    transactionRepository);
        }

        @Bean
        PrestateProperties tracerProperties() {
            final var tracerProperties = new PrestateProperties();
            tracerProperties.setEnabled(true);

            return tracerProperties;
        }
    }
}
