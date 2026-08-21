// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.CommonUtils.instant;
import static org.hiero.mirror.web3.controller.OpcodesController.MISSING_GZIP_HEADER_MESSAGE;
import static org.hiero.mirror.web3.utils.Constants.OPCODES_URI;
import static org.hiero.mirror.web3.utils.TransactionProviderEnum.entityAddress;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;
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
import com.google.common.collect.Range;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tuweni.bytes.Bytes;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.utils.TransactionProviderEnum;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.GenericErrorResponse;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;

@AutoConfigureMockMvc
@ExtendWith(MockitoExtension.class)
class OpcodesControllerTest extends Web3IntegrationTest {

    private static final DomainBuilder METHOD_SOURCE_DOMAIN_BUILDER = new DomainBuilder();

    private final AtomicReference<OpcodesProcessingResult> opcodesResultCaptor = new AtomicReference<>();
    private final AtomicReference<ContractDebugParameters> expectedCallServiceParameters = new AtomicReference<>();

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private CommonEntityAccessor commonEntityAccessor;

    @MockitoBean
    private ContractDebugService contractDebugService;

    @MockitoBean
    private ThrottleManager throttleManager;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> callServiceParametersCaptor;

    @Captor
    private ArgumentCaptor<OpcodeContext> tracerOptionsCaptor;

    private static final TransactionIdOrHashParameter DUMMY_TRANSACTION_ID =
            new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH);

    static Stream<Arguments> transactionsWithDifferentTracerOptions() {
        final List<OpcodeContext> tracerOptions = List.of(
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, true, true, true), 0, new OpcodesProperties()),
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, false, true, true), 0, new OpcodesProperties()),
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, true, false, true), 0, new OpcodesProperties()),
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, true, true, false), 0, new OpcodesProperties()),
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, false, false, true), 0, new OpcodesProperties()),
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, false, true, false), 0, new OpcodesProperties()),
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, true, false, false), 0, new OpcodesProperties()),
                new OpcodeContext(
                        new OpcodeRequest(DUMMY_TRANSACTION_ID, false, false, false), 0, new OpcodesProperties()));
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
        Supplier<byte[]> invalidAlias = () -> METHOD_SOURCE_DOMAIN_BUILDER.key(Key.KeyCase.ED25519);
        return Stream.of(
                Triple.of(
                        METHOD_SOURCE_DOMAIN_BUILDER.entityId(),
                        METHOD_SOURCE_DOMAIN_BUILDER.evmAddress(),
                        validAlias.get()),
                Triple.of(METHOD_SOURCE_DOMAIN_BUILDER.entityId(), null, validAlias.get()),
                Triple.of(METHOD_SOURCE_DOMAIN_BUILDER.entityId(), null, invalidAlias.get()),
                Triple.of(METHOD_SOURCE_DOMAIN_BUILDER.entityId(), null, null));
    }

    private MockHttpServletRequestBuilder opcodesRequest(final TransactionIdOrHashParameter parameter) {
        return opcodesRequest(
                parameter,
                new OpcodeContext(new OpcodeRequest(parameter, true, false, false), 0, new OpcodesProperties()));
    }

    private MockHttpServletRequestBuilder opcodesRequest(
            final TransactionIdOrHashParameter parameter, final OpcodeContext options) {
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
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResultMatcher responseBody(final Object expectedBody) throws JsonProcessingException {
        return content().string(objectMapper.writeValueAsString(expectedBody));
    }

    @BeforeEach
    void setUp() {
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenAnswer(invocation -> {
                    final ContractDebugParameters params = invocation.getArgument(0);
                    final OpcodeContext options = invocation.getArgument(1);
                    final var result = Builder.successfulOpcodesProcessingResult(params, options);
                    opcodesResultCaptor.set(result);
                    return result;
                });
    }

    TransactionIdOrHashParameter persistTransaction(final TransactionProviderEnum provider) {
        provider.init(domainBuilder);

        final var transactionWrapper = provider.getTransaction();
        final var ethTransactionWrapper = provider.getEthTransaction();
        final var transaction = transactionWrapper.get();
        final var ethTransaction = ethTransactionWrapper.get();

        final var consensusTimestamp = transaction.getConsensusTimestamp();
        final var recordFile = provider.getRecordFile().persist();
        transactionWrapper.persist();
        if (provider.hasEthTransaction()) {
            ethTransactionWrapper.persist();
        }
        provider.getContractTransactionHash().persist();
        final var contractResult = provider.getContractResult().persist();

        final var contractEntity = provider.getContractEntity().get();
        if (contractEntity != null) {
            provider.getContractEntity()
                    .customize(e ->
                            e.createdTimestamp(consensusTimestamp).timestampRange(Range.atLeast(consensusTimestamp)))
                    .persist();
        }
        final var senderEntity = provider.getSenderEntity()
                .customize(
                        e -> e.createdTimestamp(consensusTimestamp).timestampRange(Range.atLeast(consensusTimestamp)))
                .persist();

        final var hash = provider.hasEthTransaction() ? ethTransaction.getHash() : transaction.getTransactionHash();
        final var senderAddress = entityAddress(senderEntity);
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
                                ? ethTransaction.getCallData()
                                : contractResult.getFunctionParameters())
                .ethereumData(provider.hasEthTransaction() ? ethTransaction.getData() : new byte[0])
                .block(BlockType.of(recordFile.getIndex().toString()))
                .build());

        if (provider.hasEthTransaction()) {
            return new TransactionHashParameter(Bytes.of(hash));
        }
        return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callThrowsExceptionAndExpectDetailMessage(final TransactionProviderEnum providerEnum) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = persistTransaction(providerEnum);

        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";

        org.mockito.Mockito.reset(contractDebugService);
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
        final TransactionIdOrHashParameter transactionIdOrHash = persistTransaction(providerEnum);

        org.mockito.Mockito.reset(contractDebugService);
        when(contractDebugService.processOpcodeCall(
                        callServiceParametersCaptor.capture(), tracerOptionsCaptor.capture()))
                .thenAnswer(context -> {
                    final OpcodeContext options = context.getArgument(1);
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
    void callWithDifferentCombinationsOfTracerOptions(final TransactionProviderEnum providerEnum, OpcodeContext options)
            throws Exception {

        final TransactionIdOrHashParameter transactionIdOrHash = persistTransaction(providerEnum);

        mockMvc.perform(opcodesRequest(transactionIdOrHash, options))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

        assertThat(tracerOptionsCaptor.getValue())
                .usingRecursiveComparison()
                .ignoringFields("opcodes")
                .isEqualTo(options);
        assertThat(callServiceParametersCaptor.getValue())
                .isEqualTo(expectedCallServiceParameters.get().toBuilder().build());
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithContractResultNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        providerEnum.init(domainBuilder);

        final var transaction = providerEnum.getTransaction().persist();
        if (providerEnum.hasEthTransaction()) {
            providerEnum.getEthTransaction().persist();
        }
        providerEnum.getContractTransactionHash().persist();
        final var consensusTimestamp = transaction.getConsensusTimestamp();

        final TransactionIdOrHashParameter transactionIdOrHash = providerEnum.hasEthTransaction()
                ? new TransactionHashParameter(Bytes.of(providerEnum.getHash()))
                : new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(new GenericErrorResponse(
                        NOT_FOUND.getReasonPhrase(), "Contract result not found: " + consensusTimestamp)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void callWithTransactionNotFoundExceptionTest(final TransactionProviderEnum providerEnum) throws Exception {
        providerEnum.init(domainBuilder);

        final TransactionIdOrHashParameter transactionIdOrHash;
        final GenericErrorResponse expectedError;
        final var message = NOT_FOUND.getReasonPhrase();

        if (providerEnum.hasEthTransaction()) {
            transactionIdOrHash = new TransactionHashParameter(Bytes.of(providerEnum.getHash()));
            expectedError =
                    new GenericErrorResponse(message, "Contract transaction hash not found: " + transactionIdOrHash);
        } else {
            final var transaction = providerEnum.getTransaction().get();
            transactionIdOrHash =
                    new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
            expectedError = new GenericErrorResponse(message, "Transaction not found: " + transactionIdOrHash);
        }

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isNotFound())
                .andExpect(responseBody(expectedError));
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentSenderAddresses")
    void callWithDifferentSenderAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum)
            throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash = persistTransaction(providerEnum);

        expectedCallServiceParameters.set(expectedCallServiceParameters.get().toBuilder()
                .sender(entityAddress(providerEnum.getSenderEntity().get()))
                .build());

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isOk())
                .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

        assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
    }

    @Test
    void callWithNullPayerAccountIdReturnsBadRequest() throws Exception {
        final var validStart = Instant.ofEpochSecond(2, 2000);

        mockMvc.perform(opcodesRequest("null-%d-%d".formatted(validStart.getEpochSecond(), validStart.getNano())))
                .andExpect(status().isBadRequest())
                .andExpect(responseBody(new GenericErrorResponse(
                        BAD_REQUEST.getReasonPhrase(),
                        "Unsupported ID format: 'null-%d-%d'"
                                .formatted(validStart.getEpochSecond(), validStart.getNano()))));
    }

    @ParameterizedTest
    @MethodSource("transactionsWithDifferentReceiverAddresses")
    void callWithDifferentReceiverAddressShouldUseEvmAddressWhenPossible(final TransactionProviderEnum providerEnum)
            throws Exception {

        final TransactionIdOrHashParameter transactionIdOrHash = persistTransaction(providerEnum);

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
        final var expectedMessage = StringUtils.hasText(transactionIdOrHash)
                ? "Unsupported ID format: '%s'".formatted(transactionIdOrHash)
                : "Missing transaction ID or hash";

        mockMvc.perform(opcodesRequest(transactionIdOrHash))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains(expectedMessage)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"deflate", "identity", "br", ""})
    void rejectsWhenAcceptEncodingIsNotGzip(final String acceptEncoding) throws Exception {
        final TransactionIdOrHashParameter transactionIdOrHash =
                persistTransaction(TransactionProviderEnum.CONTRACT_CALL);
        final String param = transactionIdOrHash instanceof TransactionHashParameter(Bytes hash)
                ? hash.toHexString()
                : Builder.transactionIdString(
                        ((TransactionIdParameter) transactionIdOrHash).payerAccountId(),
                        ((TransactionIdParameter) transactionIdOrHash).validStart());

        mockMvc.perform(get(OPCODES_URI, param)
                        .header(HttpHeaders.ACCEPT_ENCODING, acceptEncoding)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable())
                .andExpect(responseBody(
                        new GenericErrorResponse(NOT_ACCEPTABLE.getReasonPhrase(), MISSING_GZIP_HEADER_MESSAGE)));
    }

    @ParameterizedTest
    @EnumSource(TransactionProviderEnum.class)
    void exceedingRateLimit(final TransactionProviderEnum providerEnum) throws Exception {

        final TransactionIdOrHashParameter transactionIdOrHash = persistTransaction(providerEnum);
        expectedCallServiceParameters.set(
                expectedCallServiceParameters.get().toBuilder().build());

        for (var i = 0; i < 3; i++) {
            mockMvc.perform(opcodesRequest(transactionIdOrHash))
                    .andExpect(status().isOk())
                    .andExpect(responseBody(Builder.opcodesResponse(opcodesResultCaptor.get(), commonEntityAccessor)));

            assertThat(callServiceParametersCaptor.getValue()).isEqualTo(expectedCallServiceParameters.get());
        }

        doThrow(new ThrottleException("Requests per second rate limit exceeded."))
                .when(throttleManager)
                .throttleOpcodeRequest();
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
        final TransactionIdOrHashParameter transactionIdOrHash = persistTransaction(providerEnum);

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
                        .header("Access-Control-Request-Method", "GET")
                        .header(HttpHeaders.ACCEPT_ENCODING, "gzip"))
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
            final var address = result.recipient().equals(Address.ZERO)
                    ? Address.ZERO.toHexString()
                    : commonEntityAccessor
                            .get(result.recipient(), Optional.empty())
                            .map(TransactionProviderEnum::entityAddress)
                            .map(Address::toHexString)
                            .orElse(Address.ZERO.toHexString());
            final var contractId = result.recipient().equals(Address.ZERO)
                    ? null
                    : commonEntityAccessor
                            .get(result.recipient(), Optional.empty())
                            .map(Entity::toEntityId)
                            .map(EntityId::toString)
                            .orElse(null);
            final var opcodes = result.opcodes();

            return new OpcodesResponse()
                    .address(address)
                    .contractId(contractId)
                    .failed(!result.transactionProcessingResult().isSuccessful())
                    .gas(result.transactionProcessingResult().gasUsed())
                    .opcodes(opcodes)
                    .returnValue(result.transactionProcessingResult().contractCallResult());
        }

        private static OpcodesProcessingResult successfulOpcodesProcessingResult(
                final ContractDebugParameters params, final OpcodeContext options) {
            final List<Opcode> opcodes = opcodes(options);
            final long gasUsed =
                    opcodes.stream().map(Opcode::getGas).reduce(Long::sum).orElse(0L);
            return new OpcodesProcessingResult(
                    new EvmTransactionResult(
                            ResponseCodeEnum.SUCCESS,
                            ContractFunctionResult.newBuilder().gasUsed(gasUsed).build()),
                    Address.ZERO,
                    opcodes);
        }

        private static OpcodesProcessingResult unsuccessfulOpcodesProcessingResult(
                final OpcodeContext options, final Address recipient) {
            final List<Opcode> opcodes = opcodes(options);
            final long gasUsed =
                    opcodes.stream().map(Opcode::getGas).reduce(Long::sum).orElse(0L);
            return new OpcodesProcessingResult(
                    new EvmTransactionResult(
                            ResponseCodeEnum.CONTRACT_REVERT_EXECUTED,
                            ContractFunctionResult.newBuilder().gasUsed(gasUsed).build()),
                    recipient,
                    opcodes);
        }

        private static List<Opcode> opcodes(final OpcodeContext options) {
            return Arrays.asList(
                    new Opcode()
                            .pc(1273)
                            .op("PUSH1")
                            .gas(2731L)
                            .gasCost(3L)
                            .depth(2)
                            .stack(
                                    options.isStack()
                                            ? List.of(
                                                    "0x000000000000000000000000000000000000000000000000000000004700d305",
                                                    "0x00000000000000000000000000000000000000000000000000000000000000a7")
                                            : Collections.emptyList())
                            .memory(
                                    options.isMemory()
                                            ? List.of(
                                                    "0x4e487b7100000000000000000000000000000000000000000000000000000000",
                                                    "0x0000001200000000000000000000000000000000000000000000000000000000")
                                            : Collections.emptyList())
                            .storage(Collections.emptyMap())
                            .reason(null),
                    new Opcode()
                            .pc(1275)
                            .op("REVERT")
                            .gas(2728L)
                            .gasCost(0L)
                            .depth(2)
                            .stack(
                                    options.isStack()
                                            ? List.of(
                                                    "0x000000000000000000000000000000000000000000000000000000004700d305",
                                                    "0x00000000000000000000000000000000000000000000000000000000000000a7")
                                            : Collections.emptyList())
                            .memory(
                                    options.isMemory()
                                            ? List.of(
                                                    "0x4e487b7100000000000000000000000000000000000000000000000000000000",
                                                    "0x0000001200000000000000000000000000000000000000000000000000000000")
                                            : Collections.emptyList())
                            .storage(Collections.emptyMap())
                            .reason("0x4e487b710000000000000000000000000000000000000000000000000000000000000012"),
                    new Opcode()
                            .pc(682)
                            .op("SWAP2")
                            .gas(2776L)
                            .gasCost(3L)
                            .depth(1)
                            .stack(
                                    options.isStack()
                                            ? List.of(
                                                    "0x000000000000000000000000000000000000000000000000000000000135b7d0",
                                                    "0x00000000000000000000000000000000000000000000000000000000000000a0")
                                            : Collections.emptyList())
                            .memory(
                                    options.isMemory()
                                            ? List.of(
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000",
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000")
                                            : Collections.emptyList())
                            .storage(
                                    options.isStorage()
                                            ? ImmutableSortedMap.of(
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000",
                                                    "0x0000000000000000000000000000000000000000000000000000000000000014")
                                            : Collections.emptyMap())
                            .reason(null));
        }
    }
}
