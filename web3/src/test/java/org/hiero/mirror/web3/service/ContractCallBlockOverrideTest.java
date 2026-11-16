// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.getUnixSeconds;

import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.model.CallServiceParameters.CallType;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.TraceBundle;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.web3j.protocol.core.RemoteFunctionCall;

/** Verifies that every {@code block_override} field reaches the opcode that reads it. */
class ContractCallBlockOverrideTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final long BASE_FEE = 10_000_000_000L;
    private static final long BLOB_BASE_FEE = 7L;
    private static final long BLOCK_NUMBER = 80L;
    private static final String FEE_RECIPIENT = "0x00000000000000000000000000000000000004d2";
    private static final long GAS_LIMIT = 15_000_000L;
    private static final long PREV_RANDAO = 123_456_789L;
    private static final String SECOND_FEE_RECIPIENT = "0x00000000000000000000000000000000000004d3";
    private static final long TIME = 1_793_619_195L;

    private EvmCodes contract;
    private RecordFile recordFile;

    @Resource
    private TransactionExecutorFactory transactionExecutorFactory;

    @BeforeEach
    void setUpContract() {
        // Executors are built asynchronously at startup. Wait for them here so the first call in a fresh context does
        // not spend its request timeout on that.
        ContractCallContext.run(_ -> transactionExecutorFactory.get());
        final long consensusStart = DomainUtils.now();
        recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(consensusStart).consensusEnd(consensusStart + 2 * NANOS_PER_SECOND))
                .persist();
        contract = testWeb3jService.deploy(EvmCodes::deploy);
    }

    @Test
    void withoutOverrideReturnsFrameDefaults() {
        assertThat(call(contract.call_getBlockBaseFee(), null)).isEqualTo(BigInteger.ZERO);
        assertThat(call(contract.call_getBlobBaseFee(), null)).isEqualTo(BigInteger.ONE);
        assertThat(call(contract.call_getBlockGasLimit(), null)).isEqualTo(BigInteger.valueOf(TRANSACTION_GAS_LIMIT));
        assertThat(call(contract.call_getBlockNumber(), null)).isEqualTo(BigInteger.valueOf(recordFile.getIndex()));
        assertThat(call(contract.call_getBlockTimestamp(), null))
                .isEqualTo(BigInteger.valueOf(getUnixSeconds(recordFile.getConsensusStart())));
        assertThat(address(call(contract.call_getBlockCoinbase(), null)))
                .isEqualTo(toAddress(systemEntity.networkAdminFeeAccount()));
        assertThat(call(contract.call_getBlockPrevrandao(), null)).isNotEqualTo(BigInteger.valueOf(PREV_RANDAO));
    }

    @Test
    void baseFeePerGas() {
        final var override = new BlockOverride();
        override.setBaseFeePerGas(BASE_FEE);

        assertThat(call(contract.call_getBlockBaseFee(), override)).isEqualTo(BigInteger.valueOf(BASE_FEE));
    }

    @Test
    void blobBaseFee() {
        final var override = new BlockOverride();
        override.setBlobBaseFee(BLOB_BASE_FEE);

        assertThat(call(contract.call_getBlobBaseFee(), override)).isEqualTo(BigInteger.valueOf(BLOB_BASE_FEE));
    }

    @Test
    void blockNumber() {
        final var override = new BlockOverride();
        override.setBlockNumber(BLOCK_NUMBER);

        assertThat(call(contract.call_getBlockNumber(), override)).isEqualTo(BigInteger.valueOf(BLOCK_NUMBER));
    }

    @Test
    void feeRecipient() {
        final var override = new BlockOverride();
        override.setFeeRecipient(FEE_RECIPIENT);

        assertThat(address(call(contract.call_getBlockCoinbase(), override)))
                .isEqualTo(Address.fromHexString(FEE_RECIPIENT));
    }

    @Test
    void gasLimit() {
        final var override = new BlockOverride();
        override.setGasLimit(1L);

        assertThat(call(contract.call_getBlockGasLimit(), override)).isEqualTo(BigInteger.ONE);
    }

    @Test
    void prevRandao() {
        final var override = new BlockOverride();
        override.setPrevRandao(PREV_RANDAO);

        assertThat(call(contract.call_getBlockPrevrandao(), override)).isEqualTo(BigInteger.valueOf(PREV_RANDAO));
    }

    @Test
    void prevRandaoMaxValue() {
        final var override = new BlockOverride();
        override.setPrevRandao(Long.MAX_VALUE);

        assertThat(call(contract.call_getBlockPrevrandao(), override)).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE));
    }

    @Test
    void time() {
        final var override = new BlockOverride();
        override.setTime(TIME);

        assertThat(call(contract.call_getBlockTimestamp(), override)).isEqualTo(BigInteger.valueOf(TIME));
    }

    @Test
    void everyFieldTogether() {
        final var override = fullOverride();

        assertThat(call(contract.call_getBlockBaseFee(), override)).isEqualTo(BigInteger.valueOf(BASE_FEE));
        assertThat(call(contract.call_getBlobBaseFee(), override)).isEqualTo(BigInteger.valueOf(BLOB_BASE_FEE));
        assertThat(call(contract.call_getBlockNumber(), override)).isEqualTo(BigInteger.valueOf(BLOCK_NUMBER));
        assertThat(address(call(contract.call_getBlockCoinbase(), override)))
                .isEqualTo(Address.fromHexString(FEE_RECIPIENT));
        assertThat(call(contract.call_getBlockGasLimit(), override)).isEqualTo(BigInteger.valueOf(GAS_LIMIT));
        assertThat(call(contract.call_getBlockPrevrandao(), override)).isEqualTo(BigInteger.valueOf(PREV_RANDAO));
        assertThat(call(contract.call_getBlockTimestamp(), override)).isEqualTo(BigInteger.valueOf(TIME));
    }

    @Test
    void overrideDoesNotLeakIntoNextCall() {
        call(contract.call_getBlockCoinbase(), fullOverride());

        assertThat(call(contract.call_getBlockBaseFee(), null)).isEqualTo(BigInteger.ZERO);
        assertThat(address(call(contract.call_getBlockCoinbase(), null)))
                .isEqualTo(toAddress(systemEntity.networkAdminFeeAccount()));
    }

    @ParameterizedTest
    @EnumSource(
            value = CallType.class,
            names = {"ETH_CALL", "ETH_ESTIMATE_GAS"})
    void estimateGasAcceptsEveryField(final CallType callType) {
        final var params = parameters(contract.call_getBlockCoinbase().encodeFunctionCall(), fullOverride(), callType);

        final var result = contractExecutionService.processCallWithGas(params);

        assertThat(result.gasUsed()).isPositive();
        if (callType == ETH_CALL) {
            assertThat(Address.wrap(Bytes.fromHexString(result.result()).slice(12)))
                    .isEqualTo(Address.fromHexString(FEE_RECIPIENT));
        }
    }

    @Test
    void traceAppliesEachBundleOverrideToItsTransactions() {
        final var second = new BlockOverride();
        second.setFeeRecipient(SECOND_FEE_RECIPIENT);
        second.setBaseFeePerGas(1L);
        final var block =
                BlockType.of(domainBuilder.recordFile().persist().getIndex().toString());
        final var headerCalls = List.of(
                contract.call_getBlockBaseFee().encodeFunctionCall(),
                contract.call_getBlockNumber().encodeFunctionCall(),
                contract.call_getBlockCoinbase().encodeFunctionCall(),
                contract.call_getBlockTimestamp().encodeFunctionCall());

        final var actions = contractDebugService.processTraceCall(TraceCall.builder()
                .block(block)
                .bundles(List.of(
                        bundle(fullOverride(), block, everyBlockValueCall()),
                        bundle(second, block, headerCalls),
                        bundle(null, block, headerCalls)))
                .onlyTopCall(true)
                .timeout(Duration.ofSeconds(10))
                .build());

        final var outputs = actions.stream()
                .map(action -> new BigInteger(
                        1, Bytes.fromHexString(action.getOutput()).toArrayUnsafe()))
                .toList();
        assertThat(outputs).hasSize(15);
        // First bundle: every field comes from its override.
        assertThat(outputs.subList(0, 7))
                .containsExactly(
                        BigInteger.valueOf(BASE_FEE),
                        BigInteger.valueOf(BLOB_BASE_FEE),
                        BigInteger.valueOf(BLOCK_NUMBER),
                        addressValue(FEE_RECIPIENT),
                        BigInteger.valueOf(GAS_LIMIT),
                        BigInteger.valueOf(PREV_RANDAO),
                        BigInteger.valueOf(TIME));
        // Second bundle: its own fields, and block number and time advance from the first bundle.
        assertThat(outputs.subList(7, 11))
                .containsExactly(
                        BigInteger.ONE,
                        BigInteger.valueOf(BLOCK_NUMBER).add(BigInteger.ONE),
                        addressValue(SECOND_FEE_RECIPIENT),
                        BigInteger.valueOf(TIME).add(BigInteger.TWO));
        // Third bundle: no override, so the other fields return to their defaults and only the header advances.
        assertThat(outputs.subList(11, 15))
                .containsExactly(
                        BigInteger.ZERO,
                        BigInteger.valueOf(BLOCK_NUMBER).add(BigInteger.TWO),
                        addressValue(
                                toAddress(systemEntity.networkAdminFeeAccount()).toHexString()),
                        BigInteger.valueOf(TIME).add(BigInteger.valueOf(4L)));
    }

    private BigInteger call(final RemoteFunctionCall<?> functionCall, final @Nullable BlockOverride override) {
        final var params = parameters(functionCall.encodeFunctionCall(), override, ETH_CALL);
        final var result = contractExecutionService.processCall(params);
        return new BigInteger(1, Bytes.fromHexString(result).toArrayUnsafe());
    }

    private ContractExecutionParameters parameters(
            final String data, final @Nullable BlockOverride override, final CallType callType) {
        return parameters(data, override, callType, testWeb3jService.getBlockType());
    }

    private ContractExecutionParameters parameters(
            final String data, final @Nullable BlockOverride override, final CallType callType, final BlockType block) {
        return ContractExecutionParameters.builder()
                .block(block)
                .blockOverride(override)
                .callData(Bytes.fromHexString(data).toArrayUnsafe())
                .callType(callType)
                .gas(TRANSACTION_GAS_LIMIT)
                .gasPrice(0L)
                .isEstimate(callType == ETH_ESTIMATE_GAS)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(Address.ZERO)
                .value(0L)
                .build();
    }

    private TraceBundle bundle(
            final @Nullable BlockOverride override, final BlockType block, final List<String> calls) {
        return TraceBundle.builder()
                .blockOverride(override)
                .transactions(calls.stream()
                        .map(data -> parameters(data, null, ETH_CALL, block))
                        .toList())
                .build();
    }

    /** One call per block-value getter, in the order the trace test asserts them. */
    private List<String> everyBlockValueCall() {
        return List.of(
                contract.call_getBlockBaseFee().encodeFunctionCall(),
                contract.call_getBlobBaseFee().encodeFunctionCall(),
                contract.call_getBlockNumber().encodeFunctionCall(),
                contract.call_getBlockCoinbase().encodeFunctionCall(),
                contract.call_getBlockGasLimit().encodeFunctionCall(),
                contract.call_getBlockPrevrandao().encodeFunctionCall(),
                contract.call_getBlockTimestamp().encodeFunctionCall());
    }

    private static BlockOverride fullOverride() {
        final var override = new BlockOverride();
        override.setBaseFeePerGas(BASE_FEE);
        override.setBlobBaseFee(BLOB_BASE_FEE);
        override.setBlockNumber(BLOCK_NUMBER);
        override.setFeeRecipient(FEE_RECIPIENT);
        override.setGasLimit(GAS_LIMIT);
        override.setPrevRandao(PREV_RANDAO);
        override.setTime(TIME);
        return override;
    }

    private static Address address(final BigInteger value) {
        return Address.wrap(Bytes32.leftPad(Bytes.wrap(value.toByteArray()).trimLeadingZeros())
                .slice(12));
    }

    private static BigInteger addressValue(final String hex) {
        return new BigInteger(hex.substring(2), 16);
    }
}
