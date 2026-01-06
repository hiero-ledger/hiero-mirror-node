// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.web3j.generated.TestAddressThis;
import org.hiero.mirror.web3.web3j.generated.TestNestedAddressThis;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

@AutoConfigureMockMvc
class ContractCallAddressThisTest extends AbstractContractCallServiceTest {

    private static final String CALL_URI = "/api/v1/contracts/call";

    @Resource
    protected ContractExecutionService contractCallService;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @SneakyThrows
    private ResultActions contractCall(ContractCallRequest request) {
        return mockMvc.perform(post(CALL_URI)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convert(request)));
    }

    @Test
    void deployAddressThisContract() {
        final var contract = testWeb3jService.deployWithValue(TestAddressThis::deploy, BigInteger.valueOf(1000));
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, Address.ZERO);
        final long actualGas = 120170;
        long expectedGas = longValueOf.applyAsLong(contractCallService.processCall(serviceParameters));
        assertThat(isWithinExpectedGasRange(expectedGas, actualGas))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, expectedGas, actualGas)
                .isTrue();
    }

    @Test
    void addressThisFromFunction() {
        final var contract = testWeb3jService.deployWithValue(TestAddressThis::deploy, BigInteger.valueOf(1000));
        final var functionCall = contract.send_testAddressThisFunction();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void addressThisEthCallWithoutEvmAlias() throws Exception {
        // Given
        final var contract =
                testWeb3jService.deployWithoutPersistWithValue(TestAddressThis::deploy, BigInteger.valueOf(1000));
        addressThisContractPersist(
                testWeb3jService.getContractRuntime(), Address.fromHexString(contract.getContractAddress()));

        // When
        var callFunction = contract.call_getAddressThis();
        final var result = callFunction.send();
        var parameters = getContractExecutionParameters(callFunction, contract);
        var output = contractExecutionService
                .callContract(parameters)
                .functionResult()
                .contractCallResult();

        // Then
        final var successfulResponse = "0x" + StringUtils.leftPad(result.substring(2), 64, '0');
        assertThat(successfulResponse)
                .isEqualTo(Bytes.wrap(output.toByteArray()).toHexString());
    }

    @Test
    void contractDeployWithoutValue() throws Exception {
        // Given
        final var contract = testWeb3jService.deployWithValue(TestAddressThis::deploy, BigInteger.valueOf(1000));
        final var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData(contract.getContractBinary());
        request.setFrom(Address.ZERO.toHexString());
        // When
        contractCall(request)
                // Then
                .andExpect(status().isOk())
                .andExpect(result -> {
                    final var response = result.getResponse().getContentAsString();
                    assertThat(response).contains(BytecodeUtils.extractRuntimeBytecode(contract.getContractBinary()));
                });
    }

    @Test
    void contractDeployWithValue() throws Exception {
        // Given
        final var contract = testWeb3jService.deployWithValue(TestAddressThis::deploy, BigInteger.valueOf(1000));
        final var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData(contract.getContractBinary());
        request.setFrom(treasuryAddress);
        request.setValue(1000);
        // When
        contractCall(request)
                // Then
                .andExpect(status().isOk())
                .andExpect(result -> {
                    final var response = result.getResponse().getContentAsString();
                    assertThat(response).contains(BytecodeUtils.extractRuntimeBytecode(contract.getContractBinary()));
                });
    }

    @Test
    void deployNestedAddressThisContract() {
        final var contract = testWeb3jService.deploy(TestNestedAddressThis::deploy);
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, Address.ZERO);
        final long actualGas = 170000L;
        long expectedGas = longValueOf.applyAsLong(contractCallService.processCall(serviceParameters));
        assertThat(isWithinExpectedGasRange(expectedGas, actualGas))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, expectedGas, actualGas)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getBalance(boolean validatePayerBalance) throws Exception {
        mirrorNodeEvmProperties.setValidatePayerBalance(validatePayerBalance);
        final var contract = testWeb3jService.deployWithValue(TestAddressThis::deploy, BigInteger.valueOf(1000));
        final var entity1 = accountEntityPersist();
        final var functionCall = contract.call_getBalance(getAddressFromEntity(entity1));
        final var result = functionCall.send();
        assertThat(result).isEqualTo(BigInteger.valueOf(entity1.getBalance()));
        mirrorNodeEvmProperties.setValidatePayerBalance(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getAddressThisBalance(boolean validatePayerBalance) throws Exception {
        mirrorNodeEvmProperties.setValidatePayerBalance(validatePayerBalance);
        final long contractBalance = DEFAULT_SMALL_ACCOUNT_BALANCE - 1;
        final var contract =
                testWeb3jService.deployWithValue(TestAddressThis::deploy, BigInteger.valueOf(contractBalance));
        final var functionCall = contract.call_getAddressThisBalance();
        final var result = functionCall.send();
        assertThat(result).isEqualTo(BigInteger.valueOf(contractBalance));
        mirrorNodeEvmProperties.setValidatePayerBalance(true);
    }

    private void addressThisContractPersist(byte[] runtimeBytecode, Address contractAddress) {
        final var addressThisContractEntityId = entityIdFromEvmAddress(contractAddress);
        final var addressThisEvmAddress = toEvmAddress(addressThisContractEntityId);
        domainBuilder
                .entity()
                .customize(e -> e.id(addressThisContractEntityId.getId())
                        .num(addressThisContractEntityId.getNum())
                        .evmAddress(addressThisEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(addressThisContractEntityId.getId()).runtimeBytecode(runtimeBytecode))
                .persist();
        domainBuilder.recordFile().customize(f -> f.bytes(runtimeBytecode)).persist();
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }
}
