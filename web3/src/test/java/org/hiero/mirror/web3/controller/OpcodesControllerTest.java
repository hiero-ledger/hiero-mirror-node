// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.CommonUtils.instant;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.web3.utils.Constants.OPCODES_URI;
import static org.hiero.mirror.web3.utils.TransactionProviderEnum.entityAddress;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSortedMap;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.Key;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.Opcode;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.OpcodeService;
import org.hiero.mirror.web3.service.OpcodeServiceImpl;
import org.hiero.mirror.web3.service.RecordFileService;
import org.hiero.mirror.web3.service.RecordFileServiceImpl;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.utils.TransactionProviderEnum;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.GenericErrorResponse;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = OpcodesController.class)
class OpcodesControllerTest {

    private static final DomainBuilder DOMAIN_BUILDER = new DomainBuilder();
    private final AtomicReference<OpcodesProcessingResult> opcodesResultCaptor = new AtomicReference<>();
    private final AtomicReference<ContractDebugParameters> expectedCallServiceParameters = new AtomicReference<>();

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContractDebugService contractDebugService;

    @MockitoBean(name = "rateLimitBucket")
    private Bucket rateLimitBucket;

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

    @Captor
    private ArgumentCaptor<ContractDebugParameters> callServiceParametersCaptor;

    @Captor
    private ArgumentCaptor<OpcodeTracerOptions> tracerOptionsCaptor;

    static Stream<Arguments> transactionsWithDifferentTracerOptions() {
        final List<OpcodeTracerOptions> tracerOptions = List.of(
                new OpcodeTracerOptions(true, true, true),
                new OpcodeTracerOptions(false, true, true),
                new OpcodeTracerOptions(true, false, true),
                new OpcodeTracerOptions(true, true, false),
                new OpcodeTracerOptions(false, false, true),
                new OpcodeTracerOptions(false, true, false),
                new OpcodeTracerOptions(true, false, false),
                new OpcodeTracerOptions(false, false, false));
        return Arrays.stream(TransactionProviderEnum.values())
                .flatMap(providerEnum -> tracerOptions.stream().map(options -> Arguments.of(providerEnum, options)));
    }

    static Stream<Arguments> transactionsWithDifferentSenderAddresses() {
        return Arrays.stream(TransactionProviderEnum.values()).flatMap(providerEnum -> entityAddressCombinations(
                        providerEnum.getPayerAccountId())
                .map(pair -> Arguments.of(Named.of(
                        "%s(payerAccountId=%s, evmAddress=%s, alias=%s)"
                                .formatted(
                                        providerEnum.name(),
                                        pair.getLeft() != null ? pair.getLeft().toString() : null,
                                        pair.getMiddle() != null ? Bytes.of(pair.getMiddle()) : null,
                                        pair.getRight() != null ? Bytes.of(pair.getRight()) : null),
                        providerEnum.customize(p -> {
                            p.setPayerAccountId(pair.getLeft());
                            p.setPayerEvmAddress(pair.getMiddle());
                            p.setPayerAlias(pair.getRight());
                        })))));
    }

    static Stream<Arguments> transactionsWithDifferentReceiverAddresses() {
        return Arrays.stream(TransactionProviderEnum.values()).flatMap(providerEnum -> entityAddressCombinations(
                        providerEnum.getContractId())
                .map(pair -> Arguments.of(Named.of(
                        "%s(contractId=%s, evmAddress=%s, alias=%s)"
                                .formatted(
                                        providerEnum.name(),
                                        pair.getLeft() != null ? pair.getLeft().toString() : null,
                                        pair.getMiddle() != null ? Bytes.of(pair.getMiddle()) : null,
                                        pair.getRight() != null ? Bytes.of(pair.getRight()) : null),
                        providerEnum.customize(p -> {
                            p.setContractId(pair.getLeft());
                            p.setContractEvmAddress(pair.getMiddle());
                            p.setContractAlias(pair.getRight());
                        })))));
    }

    static Stream<Triple<EntityId, byte[], byte[]>> entityAddressCombinations(EntityId entityId) {
        long entityIdNum = entityId == null ? 0L : entityId.getNum();
        // spotless:off
        Supplier<byte[]> validAlias = () -> new byte[]{
                0, 0, 0, 0, // shard
                0, 0, 0, 0, 0, 0, 0, 0, // realm
                0, 0, 0, 0, 0, 0, 0, (byte) entityIdNum, // num
        };
        // spotless:on
        Supplier<byte[]> invalidAlias = () -> DOMAIN_BUILDER.key(Key.KeyCase.ED25519);
        return Stream.of(
                Triple.of(DOMAIN_BUILDER.entityId(), DOMAIN_BUILDER.evmAddress(), validAlias.get()),
                Triple.of(DOMAIN_BUILDER.entityId(), null, validAlias.get()),
                Triple.of(DOMAIN_BUILDER.entityId(), null, invalidAlias.get()),
                Triple.of(DOMAIN_BUILDER.entityId(), null, null),
                Triple.of(EntityId.EMPTY, new byte[0], new byte[0]),
                Triple.of(null, null, null));
    }

    private MockHttpServletRequestBuilder opcodesRequest(final TransactionIdOrHashParameter parameter) {
        return opcodesRequest(parameter, new OpcodeTracerOptions());
    }

    private MockHttpServletRequestBuilder opcodesRequest(
            final TransactionIdOrHashParameter parameter, final OpcodeTracerOptions options) {
        final String transactionIdOrHash =
                switch (parameter) {
                    case TransactionHashParameter hashParameter ->
                        hashParameter.hash().toHexString();
                    case TransactionIdParameter transactionIdParameter ->
                        Builder.transactionIdString(
                                transactionIdParameter.payerAccountId(), transactionIdParameter.validStart());
                };

        return opcodesRequest(transactionIdOrHash)
                .queryParam("stack", String.valueOf(options.isStack()))
                .queryParam("memory", String.valueOf(options.isMemory()))
                .queryParam("storage", String.valueOf(options.isStorage()));
    }

    private MockHttpServletRequestBuilder opcodesRequest(final String transactionIdOrHash) {
        return get(OPCODES_URI, transactionIdOrHash)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResultMatcher responseBody(final Object expectedBody) throws JsonProcessingException {
        return content().string(objectMapper.writeValueAsString(expectedBody));
    }

    @BeforeEach
    void setUp() {
        when(rateLimitBucket.tryConsume(anyLong())).thenReturn(true);
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenAnswer(invocation -> {
                    final ContractDebugParameters params = invocation.getArgument(0);
                    final OpcodeTracerOptions options = invocation.getArgument(1);
                    final var result = Builder.successfulOpcodesProcessingResult(params, options);
                    opcodesResultCaptor.set(result);
                    return result;
                });
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

        expectedCallServiceParameters.set(ContractDebugParameters.builder()
                .consensusTimestamp(consensusTimestamp)
                .sender(senderAddress)
                .receiver(contractAddress)
                .gas(provider.hasEthTransaction() ? ethTransaction.getGasLimit() : contractResult.getGasLimit())
                .value(
                        provider.hasEthTransaction()
                                ? new BigInteger(ethTransaction.getValue()).longValue()
                                : contractResult.getAmount())
                .callData(
                        provider.hasEthTransaction()
                                ? Bytes.of(ethTransaction.getCallData())
                                : Bytes.of(contractResult.getFunctionParameters()))
                .block(BlockType.of(recordFile.getIndex().toString()))
                .build());

        when(contractTransactionHashRepository.findByHash(hash)).thenReturn(Optional.of(contractTransactionHash));
        when(transactionRepository.findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
                        payerAccountId, validStartNs))
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

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callThrowsExceptionAndExpectDetailMessage(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";

        reset(contractDebugService);
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenThrow(new MirrorEvmTransactionException(
                        CONTRACT_EXECUTION_EXCEPTION, detailedErrorMessage, hexDataErrorMessage));

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(responseBody(new GenericErrorResponse(
                        CONTRACT_EXECUTION_EXCEPTION.name(), detailedErrorMessage, hexDataErrorMessage)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void unsuccessfulCall(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        reset(contractDebugService);
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenAnswer(context -> {
                    final OpcodeTracerOptions options = context.getArgument(1);
                    opcodesResultCaptor.set(Builder.unsuccessfulOpcodesProcessingResult(
                            options,
                            entityAddress(providerEnum.getContractEntity().get())));
                    return opcodesResultCaptor.get();
                });

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

        expectedCallServiceParameters.set(
                expectedCallServiceParameters.get().toBuilder().build());

        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentTracerOptions")
    void callWithDifferentCombinationsOfTracerOptions(
            final TransactionProviderEnum providerEnum, OpcodeTracerOptions options) throws Exception {

        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        mockMvc.perform(opcodesRequest(transactionIdOrHash, options))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

        assertThat(tracerOptionsCaptor.getValue()).isEqualTo(options);
        assertThat(callServiceParametersCaptor.getValue())
                .isEqualTo(expectedCallServiceParameters.get().toBuilder().build());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithContractResultNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);
        final var id = providerEnum.getContractResult().get().getConsensusTimestamp();

        when(contractResultRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(
                        new GenericErrorResponse(NOT_FOUND.getReasonPhrase(), "Contract result not found: " + id)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithTransactionNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);
        final var message = NOT_FOUND.getReasonPhrase();
        final GenericErrorResponse expectedError =
                switch (transactionIdOrHash) {
                    case TransactionHashParameter parameter -> {
                        reset(contractTransactionHashRepository);
                        when(contractTransactionHashRepository.findByHash(
                                        parameter.hash().toArray()))
                                .thenReturn(Optional.empty());
                        yield new GenericErrorResponse(message, "Contract transaction hash not found: " + parameter);
                    }
                    case TransactionIdParameter parameter -> {
                        reset(transactionRepository);
                        when(transactionRepository.findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
                                        parameter.payerAccountId(), convertToNanosMax(parameter.validStart())))
                                .thenReturn(Collections.emptyList());
                        yield new GenericErrorResponse(message, "Transaction not found: " + parameter);
                    }
                };

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(expectedError));
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentSenderAddresses")
    void callWithDifferentSenderAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum)
            throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        if (transactionIdOrHash instanceof TransactionIdParameter id && id.payerAccountId() == null) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash))
                    .andExpect(status().isBadRequest())
                    .andExpect(responseBody(new GenericErrorResponse(
                            BAD_REQUEST.getReasonPhrase(),
                            "Unsupported ID format: 'null-%d-%d'"
                                    .formatted(
                                            id.validStart().getEpochSecond(),
                                            id.validStart().getNano()))));
            return;
        }

        expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                .sender(entityAddress(providerEnum.getSenderEntity().get()))
                .build());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentReceiverAddresses")
    void callWithDifferentReceiverAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum)
            throws Exception {

        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                .receiver(entityAddress(providerEnum.getContractEntity().get()))
                .build());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "00000000001239847e",
                "0.0.1234-1234567890", // missing nanos
                "0.0.1234-0-1234567890", // nanos overflow
                "0.0.1234-1-123456789-", // dash after nanos
            })
    void callInvalidTransactionIdOrHash(final String transactionIdOrHash) throws Exception {
        when(rateLimitBucket.tryConsume(1)).thenReturn(true);

        final var expectedMessage = StringUtils.hasText(transactionIdOrHash)
                ? "Unsupported ID format: '%s'".formatted(transactionIdOrHash)
                : "Missing transaction ID or hash";

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains(expectedMessage)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void exceedingRateLimit(final TransactionProviderEnum providerEnum) throws Exception {

        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);
        expectedCallServiceParameters.set(
                expectedCallServiceParameters.get().toBuilder().build());

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

            assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
        }

        when(rateLimitBucket.tryConsume(1)).thenReturn(false);
        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isTooManyRequests())
                .andExpect(responseBody(new GenericErrorResponse(
                        TOO_MANY_REQUESTS.getReasonPhrase(), "Requests per second rate limit exceeded.")));
    }

    /*
     * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
     * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
     */
    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callSuccessCors(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = setUp(providerEnum);

        final String param =
                switch (transactionIdOrHash) {
                    case TransactionHashParameter hashParameter ->
                        hashParameter.hash().toHexString();
                    case TransactionIdParameter transactionIdParameter ->
                        Builder.transactionIdString(
                                transactionIdParameter.payerAccountId(), transactionIdParameter.validStart());
                };

        mockMvc.perform(options(OPCODES_URI, param)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,HEAD,POST"));
    }

    /**
     * Utility class with helper methods for building different objects in the tests
     */
    @UtilityClass
    private static class Builder {

        private static String transactionIdString(final EntityId payerAccountId, final Instant validStart) {
            return "%s-%d-%d".formatted(payerAccountId, validStart.getEpochSecond(), validStart.getNano());
        }

        private static OpcodesResponse opcodesResponse(
                final OpcodesProcessingResult result, final CommonEntityAccessor commonEntityAccessor) {
            return new OpcodesResponse()
                    .address(
                            result.recipient().equals(Address.ZERO)
                                    ? Address.ZERO.toHexString()
                                    : commonEntityAccessor
                                            .get(result.recipient(), Optional.empty())
                                            .map(TransactionProviderEnum::entityAddress)
                                            .map(Address::toHexString)
                                            .orElse(null))
                    .contractId(
                            result.recipient().equals(Address.ZERO)
                                    ? null
                                    : commonEntityAccessor
                                            .get(result.recipient(), Optional.empty())
                                            .map(Entity::toEntityId)
                                            .map(EntityId::toString)
                                            .orElse(null))
                    .failed(!result.transactionProcessingResult().isSuccessful())
                    .gas(result.transactionProcessingResult().gasUsed())
                    .opcodes(result.opcodes().stream()
                            .map(opcode -> new org.hiero.mirror.rest.model.Opcode()
                                    .depth(opcode.depth())
                                    .gas(opcode.gas())
                                    .gasCost(opcode.gasCost())
                                    .op(opcode.op())
                                    .pc(opcode.pc())
                                    .reason(opcode.reason())
                                    .stack(opcode.stack().stream()
                                            .map(Bytes::toHexString)
                                            .toList())
                                    .memory(opcode.memory().stream()
                                            .map(Bytes::toHexString)
                                            .toList())
                                    .storage(opcode.storage().entrySet().stream()
                                            .collect(Collectors.toMap(
                                                    entry -> entry.getKey().toHexString(),
                                                    entry -> entry.getValue().toHexString()))))
                            .toList())
                    .returnValue(Optional.ofNullable(Bytes.wrap(result.transactionProcessingResult()
                                    .functionResult()
                                    .contractCallResult()
                                    .toByteArray()))
                            .map(Bytes::toHexString)
                            .orElse(Bytes.EMPTY.toHexString()));
        }

        private static OpcodesProcessingResult successfulOpcodesProcessingResult(
                final ContractDebugParameters params, final OpcodeTracerOptions options) {
            final Address recipient = params != null ? params.getReceiver() : Address.ZERO;
            final List<Opcode> opcodes = opcodes(options);
            final long gasUsed =
                    opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L);
            return new OpcodesProcessingResult(
                    new EvmTransactionResult(
                            ResponseCodeEnum.SUCCESS,
                            ContractFunctionResult.newBuilder().gasUsed(gasUsed).build()),
                    Address.ZERO,
                    opcodes);
        }

        private static OpcodesProcessingResult unsuccessfulOpcodesProcessingResult(
                final OpcodeTracerOptions options, final Address recipient) {
            final List<Opcode> opcodes = opcodes(options);
            final long gasUsed =
                    opcodes.stream().map(Opcode::gas).reduce(Long::sum).orElse(0L);
            return new OpcodesProcessingResult(
                    new EvmTransactionResult(
                            ResponseCodeEnum.CONTRACT_REVERT_EXECUTED,
                            ContractFunctionResult.newBuilder().gasUsed(gasUsed).build()),
                    recipient,
                    opcodes);
        }

        private static List<Opcode> opcodes(final OpcodeTracerOptions options) {
            return Arrays.asList(
                    new Opcode(
                            1273,
                            "PUSH1",
                            2731,
                            3,
                            2,
                            options.isStack()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "000000000000000000000000000000000000000000000000000000004700d305"),
                                            Bytes.fromHexString(
                                                    "00000000000000000000000000000000000000000000000000000000000000a7"))
                                    : Collections.emptyList(),
                            options.isMemory()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "4e487b7100000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000001200000000000000000000000000000000000000000000000000000000"))
                                    : Collections.emptyList(),
                            Collections.emptySortedMap(),
                            null),
                    new Opcode(
                            1275,
                            "REVERT",
                            2728,
                            0,
                            2,
                            options.isStack()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "000000000000000000000000000000000000000000000000000000004700d305"),
                                            Bytes.fromHexString(
                                                    "00000000000000000000000000000000000000000000000000000000000000a7"))
                                    : Collections.emptyList(),
                            options.isMemory()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "4e487b7100000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000001200000000000000000000000000000000000000000000000000000000"))
                                    : Collections.emptyList(),
                            Collections.emptySortedMap(),
                            "0x4e487b710000000000000000000000000000000000000000000000000000000000000012"),
                    new Opcode(
                            682,
                            "SWAP2",
                            2776,
                            3,
                            1,
                            options.isStack()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "000000000000000000000000000000000000000000000000000000000135b7d0"),
                                            Bytes.fromHexString(
                                                    "00000000000000000000000000000000000000000000000000000000000000a0"))
                                    : Collections.emptyList(),
                            options.isMemory()
                                    ? List.of(
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000000"))
                                    : Collections.emptyList(),
                            options.isStorage()
                                    ? ImmutableSortedMap.of(
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000000"),
                                            Bytes.fromHexString(
                                                    "0000000000000000000000000000000000000000000000000000000000000014"))
                                    : Collections.emptySortedMap(),
                            null));
        }
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
        OpcodeService opcodeService(
                final RecordFileService recordFileService,
                final ContractDebugService contractDebugService,
                final ContractTransactionHashRepository contractTransactionHashRepository,
                final EthereumTransactionRepository ethereumTransactionRepository,
                final TransactionRepository transactionRepository,
                final ContractResultRepository contractResultRepository,
                final CommonEntityAccessor commonEntityAccessor) {
            return new OpcodeServiceImpl(
                    recordFileService,
                    contractDebugService,
                    contractTransactionHashRepository,
                    ethereumTransactionRepository,
                    transactionRepository,
                    contractResultRepository,
                    commonEntityAccessor);
        }
    }
}
