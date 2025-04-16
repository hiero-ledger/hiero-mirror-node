// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.web3.utils.BytecodeUtils;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.web3j.generated.TestAddressThis;
import com.hedera.mirror.web3.web3j.generated.TestNestedAddressThis;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import lombok.SneakyThrows;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    private double modularizedTrafficPercent;

    @SneakyThrows
    private ResultActions contractCall(ContractCallRequest request) {
        return mockMvc.perform(post(CALL_URI)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(convert(request)));
    }

    @BeforeEach
    void before() {
        modularizedTrafficPercent = mirrorNodeEvmProperties.getModularizedTrafficPercent();
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            mirrorNodeEvmProperties.setModularizedTrafficPercent(1.0);
        }
    }

    @AfterEach
    void after() {
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            mirrorNodeEvmProperties.setModularizedTrafficPercent(modularizedTrafficPercent);
        }
    }

    @Test
    void deployAddressThisContract() {
        final var contract = testWeb3jService.deployWithValue(TestAddressThis::deploy, BigInteger.valueOf(1000));
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, Address.ZERO);
        final long actualGas = 57764L;
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
        var output = contractExecutionService.callContract(parameters).getOutput();

        // Then
        final var successfulResponse = "0x" + StringUtils.leftPad(result.substring(2), 64, '0');
        assertThat(successfulResponse).isEqualTo(output.toHexString());
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
        final long actualGas = 95401L;
        long expectedGas = longValueOf.applyAsLong(contractCallService.processCall(serviceParameters));
        assertThat(isWithinExpectedGasRange(expectedGas, actualGas))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, expectedGas, actualGas)
                .isTrue();
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
