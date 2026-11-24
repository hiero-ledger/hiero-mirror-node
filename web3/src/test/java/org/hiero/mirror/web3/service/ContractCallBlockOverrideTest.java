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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.model.CallServiceParameters.CallType;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.web3j.protocol.core.RemoteFunctionCall;

/** Verifies that every {@code block_override} field reaches the opcode that reads it. */
class ContractCallBlockOverrideTest extends AbstractContractCallServiceTest {

    private static final long BASE_FEE = 10_000_000_000L;
    private static final long BLOB_BASE_FEE = 7L;
    private static final long BLOCK_NUMBER = 80L;
    private static final String FEE_RECIPIENT = "0x00000000000000000000000000000000000004d2";
    private static final long GAS_LIMIT = 15_000_000L;
    private static final long PREV_RANDAO = 123_456_789L;
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
        assertThat(call(contract.call_getBlockBaseFee(), BlockOverride.EMPTY)).isEqualTo(BigInteger.ZERO);
        assertThat(call(contract.call_getBlobBaseFee(), BlockOverride.EMPTY)).isEqualTo(BigInteger.ONE);
        assertThat(call(contract.call_getBlockGasLimit(), BlockOverride.EMPTY))
                .isEqualTo(BigInteger.valueOf(TRANSACTION_GAS_LIMIT));
        assertThat(call(contract.call_getBlockNumber(), BlockOverride.EMPTY))
                .isEqualTo(BigInteger.valueOf(recordFile.getIndex()));
        assertThat(call(contract.call_getBlockTimestamp(), BlockOverride.EMPTY))
                .isEqualTo(BigInteger.valueOf(getUnixSeconds(recordFile.getConsensusStart())));
        assertThat(address(call(contract.call_getBlockCoinbase(), BlockOverride.EMPTY)))
                .isEqualTo(toAddress(systemEntity.networkAdminFeeAccount()));
        assertThat(call(contract.call_getBlockPrevrandao(), BlockOverride.EMPTY))
                .isNotEqualTo(BigInteger.valueOf(PREV_RANDAO));
    }

    @Test
    void baseFeePerGas() {
        final var override = BlockOverride.builder().baseFeePerGas(BASE_FEE).build();

        assertThat(call(contract.call_getBlockBaseFee(), override)).isEqualTo(BigInteger.valueOf(BASE_FEE));
    }

    @Test
    void blobBaseFee() {
        final var override = BlockOverride.builder().blobBaseFee(BLOB_BASE_FEE).build();

        assertThat(call(contract.call_getBlobBaseFee(), override)).isEqualTo(BigInteger.valueOf(BLOB_BASE_FEE));
    }

    @Test
    void blockNumber() {
        final var override = BlockOverride.builder().blockNumber(BLOCK_NUMBER).build();

        assertThat(call(contract.call_getBlockNumber(), override)).isEqualTo(BigInteger.valueOf(BLOCK_NUMBER));
    }

    @Test
    void feeRecipient() {
        final var override = BlockOverride.builder().feeRecipient(FEE_RECIPIENT).build();

        assertThat(address(call(contract.call_getBlockCoinbase(), override)))
                .isEqualTo(Address.fromHexString(FEE_RECIPIENT));
    }

    @Test
    void gasLimit() {
        final var override = BlockOverride.builder().gasLimit(1L).build();

        assertThat(call(contract.call_getBlockGasLimit(), override)).isEqualTo(BigInteger.ONE);
    }

    @Test
    void prevRandao() {
        final var override = BlockOverride.builder().prevRandao(PREV_RANDAO).build();

        assertThat(call(contract.call_getBlockPrevrandao(), override)).isEqualTo(BigInteger.valueOf(PREV_RANDAO));
    }

    @Test
    void prevRandaoMaxValue() {
        final var override = BlockOverride.builder().prevRandao(Long.MAX_VALUE).build();

        assertThat(call(contract.call_getBlockPrevrandao(), override)).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE));
    }

    @Test
    void time() {
        final var override = BlockOverride.builder().time(TIME).build();

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

        assertThat(call(contract.call_getBlockBaseFee(), BlockOverride.EMPTY)).isEqualTo(BigInteger.ZERO);
        assertThat(address(call(contract.call_getBlockCoinbase(), BlockOverride.EMPTY)))
                .isEqualTo(toAddress(systemEntity.networkAdminFeeAccount()));
    }

    @ParameterizedTest
    @EnumSource(
            value = CallType.class,
            names = {"ETH_CALL", "ETH_ESTIMATE_GAS"})
    void estimateGasAcceptsEveryField(final CallType callType) {
        final var params = parameters(contract.call_getBlockCoinbase().encodeFunctionCall(), fullOverride(), callType);

        final var result = ContractCallContext.run(ctx -> contractExecutionService.processCallWithGas(params, ctx));

        assertThat(result.gasUsed()).isPositive();
        if (callType == ETH_CALL) {
            assertThat(Address.wrap(Bytes.fromHexString(result.result()).slice(12)))
                    .isEqualTo(Address.fromHexString(FEE_RECIPIENT));
        }
    }

    private BigInteger call(final RemoteFunctionCall<?> functionCall, final BlockOverride override) {
        final var params = parameters(functionCall.encodeFunctionCall(), override, ETH_CALL);
        final var result = ContractCallContext.run(ctx -> contractExecutionService.processCall(params, ctx));
        return new BigInteger(1, Bytes.fromHexString(result).toArrayUnsafe());
    }

    private ContractExecutionParameters parameters(
            final String data, final BlockOverride override, final CallType callType) {
        return ContractExecutionParameters.builder()
                .block(testWeb3jService.getBlockType())
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

    private static BlockOverride fullOverride() {
        return BlockOverride.builder()
                .baseFeePerGas(BASE_FEE)
                .blobBaseFee(BLOB_BASE_FEE)
                .blockNumber(BLOCK_NUMBER)
                .feeRecipient(FEE_RECIPIENT)
                .gasLimit(GAS_LIMIT)
                .prevRandao(PREV_RANDAO)
                .time(TIME)
                .build();
    }

    private static Address address(final BigInteger value) {
        return Address.wrap(Bytes32.leftPad(Bytes.wrap(value.toByteArray()).trimLeadingZeros())
                .slice(12));
    }
}
