// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.OpcodeTracerUtil.options;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.config.data.ContractsConfig;
import java.math.BigInteger;
import org.hiero.mirror.common.exception.MirrorNodeException;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.utils.ContractFunctionProviderRecord;
import org.hiero.mirror.web3.web3j.generated.TraceDataSize;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class ContractCallTraceDataSizeTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final int PAYLOAD_SIZE = 32 * 1024;

    @Test
    void traceDataWithinLimit() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(TraceDataSize::deploy);
        // When
        final var result =
                contract.call_callEcho(new byte[PAYLOAD_SIZE], BigInteger.ONE).send();
        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(PAYLOAD_SIZE));
    }

    @Test
    void traceDataExceedsLimit() {
        // Given
        final var contract = testWeb3jService.deploy(TraceDataSize::deploy);
        // When
        final var functionCall = contract.call_callEcho(new byte[PAYLOAD_SIZE], exceedingIterations());
        // Then
        final var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(INSUFFICIENT_GAS.protoName());
    }

    @Test
    void opcodesTraceDataWithinLimit() {
        // Given
        final var contract = testWeb3jService.deploy(TraceDataSize::deploy);
        // When
        final var result = processOpcodeCall(contract, BigInteger.ONE);
        // Then
        assertThat(result.transactionProcessingResult().responseCodeEnum()).isEqualTo(SUCCESS);
        assertThat(result.opcodes()).isNotEmpty();
    }

    @Test
    void opcodesTraceDataExceedsLimit() {
        // Given
        final var contract = testWeb3jService.deploy(TraceDataSize::deploy);
        // When
        final var result = processOpcodeCall(contract, exceedingIterations());
        // Then
        assertThat(result.transactionProcessingResult().responseCodeEnum()).isEqualTo(INSUFFICIENT_GAS);
        assertThat(result.opcodes()).isNotEmpty();
    }

    // Each iteration records the payload in both the input and the output of the nested call action
    private BigInteger exceedingIterations() {
        final var maxSerializedTraceDataBytes = evmProperties
                .getVersionedConfiguration()
                .getConfigData(ContractsConfig.class)
                .maxSerializedTraceDataBytes();
        return BigInteger.valueOf(maxSerializedTraceDataBytes / PAYLOAD_SIZE);
    }

    private OpcodesProcessingResult processOpcodeCall(final TraceDataSize contract, final BigInteger iterations) {
        setOpcodeEndpoint();
        final var callData =
                contract.call_callEcho(new byte[PAYLOAD_SIZE], iterations).encodeFunctionCall();
        final var functionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .build();
        final var params = getDebugParameters(functionProvider, callData);
        return ContractCallContext.run(context -> contractDebugService.processOpcodeCall(params, options()));
    }
}
