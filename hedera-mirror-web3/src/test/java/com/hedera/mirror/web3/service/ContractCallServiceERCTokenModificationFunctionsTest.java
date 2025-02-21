/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.utils.BytecodeUtils;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.mirror.web3.web3j.generated.RedirectTestContract;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import lombok.SneakyThrows;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ContractCallServiceERCTokenModificationFunctionsTest extends AbstractContractCallServiceTest {

    private static final String CALL_URI = "/api/v1/contracts/call";

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
    void approveFungibleToken() {
        // Given
        final var spender = accountEntityPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var amountGranted = 13L;
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = toAddress(spender.toEntityId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall = contract.send_approve(tokenAddress, spenderAddress, BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFT() {
        // Given
        final var spenderEntityId = accountEntityPersist().toEntityId();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersistWithSelfSpenderAndTreasury(contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall = contract.send_approveNFT(tokenAddress, spenderAddress, BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFT() {
        // Given
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersistWithSelfSpenderAndTreasury(contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall = contract.send_approveNFT(tokenAddress, Address.ZERO.toHexString(), BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void contractDeployNonPayableWithoutValue() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
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
    void contractDeployNonPayableWithValue() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData(contract.getContractBinary());
        request.setFrom(Address.ZERO.toHexString());
        request.setValue(10);
        // When
        contractCall(request)
                // Then
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveFungibleTokenWithAlias() {
        // Given
        final var spender = accountEntityWithEvmAddressPersist();
        final var treasury = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        final var amountGranted = 13L;

        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAlias = getAliasFromEntity(spender);
        // When
        final var functionCall = contract.send_approve(tokenAddress, spenderAlias, BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAlias() {
        // Given
        var spender = accountEntityWithEvmAddressPersist();
        final var serialNo = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersistWithSelfSpenderAndTreasury(contractEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAlias = getAliasFromEntity(spender);
        // When
        final var functionCall = contract.send_approveNFT(tokenAddress, spenderAlias, BigInteger.valueOf(serialNo));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transfer() {
        // Given
        final var recipient = accountEntityPersist().toEntityId();
        final var treasury = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var amount = 10L;
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall = contract.send_transfer(tokenAddress, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFrom() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();

        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var amount = 10L;
        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();

        // When
        final var functionCall =
                contract.send_transferFrom(tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccount() {
        // Given
        final var owner = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var hollowAccount = hollowAccountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        tokenAccountPersist(tokenId, hollowAccount.getId());
        tokenAccountPersist(tokenId, owner.getId());

        final var amount = 10L;
        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        // When
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var hollowAccountAlias = getAliasFromEntity(hollowAccount);
        final var functionCall =
                contract.send_transferFrom(tokenAddress, ownerAddress, hollowAccountAlias, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFT() {
        // Given

        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersistWithSelfSpenderAndTreasury(owner);
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId, owner);

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall = contract.send_transferFromNFT(
                tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAlias() {
        // Given
        final var recipient = accountEntityWithEvmAddressPersist();
        final var treasury = accountEntityWithEvmAddressPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());

        final var amount = 10L;
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transfer(tokenAddress, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAlias() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityWithEvmAddressPersist();
        final var recipient = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenId, contractEntityId.getId());

        final var amount = 10L;
        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall =
                contract.send_transferFrom(tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAlias() {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntity = owner.toEntityId();
        final var recipient = accountEntityWithEvmAddressPersist();
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersistWithSelfSpenderAndTreasury(ownerEntity);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, ownerEntity.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId, ownerEntity);

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transferFromNFT(
                tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenRedirect() {
        // Given
        final var spender = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var amountGranted = 13L;
        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = toAddress(spender).toHexString();
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress, spenderAddress, BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTRedirect() {
        // Given
        final var spender = accountEntityPersist().toEntityId();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersistWithSelfSpenderAndTreasury(contractEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = toAddress(spender).toHexString();
        // When
        final var functionCall = contract.send_approveRedirect(tokenAddress, spenderAddress, BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFTRedirect() {
        // Given
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersistWithSelfSpenderAndTreasury(contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress, Address.ZERO.toHexString(), BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAliasRedirect() {
        // Given
        final var spender = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var amountGranted = 13L;
        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAliasFromEntity(spender);
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress, spenderAddress, BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAliasRedirect() {
        // Given
        var spender = accountEntityWithEvmAddressPersist();
        final var serialNo = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersistWithSelfSpenderAndTreasury(contractEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAliasFromEntity(spender);
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress, spenderAddress, BigInteger.valueOf(serialNo));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferRedirect() {
        // Given
        final var recipient = accountEntityPersist().toEntityId();
        final var treasury = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var amount = 10L;
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall =
                contract.send_transferRedirect(tokenAddress, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();

        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var amount = 10L;
        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall = contract.send_transferFromRedirect(
                tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccountRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());

        final var hollowAccount = hollowAccountPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        tokenAccountPersist(tokenId, hollowAccount.getId());
        final var amount = 10L;
        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var hollowAccountAlias = getAliasFromEntity(hollowAccount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                tokenAddress, ownerAddress, hollowAccountAlias, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTRedirect() {
        // Given
        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersistWithSelfSpenderAndTreasury(owner);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId, owner);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithAliasRedirect() {
        // Given
        final var recipient = accountEntityWithEvmAddressPersist();
        final var treasury = accountEntityWithEvmAddressPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());

        final var amount = 10L;
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall =
                contract.send_transferRedirect(tokenAddress, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromWithAliasRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityWithEvmAddressPersist();
        final var recipient = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var amount = 10L;
        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAlias = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                tokenAddress, ownerAlias, recipientAddress, BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAliasRedirect() {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var recipient = accountEntityWithEvmAddressPersist();
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersistWithSelfSpenderAndTreasury(ownerEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, ownerEntityId.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId, ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAlias = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress, ownerAlias, recipientAddress, BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void delegateTransferDoesNotExecuteAndReturnEmpty() throws Exception {
        // Given
        final var recipient = accountEntityPersist().toEntityId();
        final var treasury = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var amount = 10L;
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        contract.send_delegateTransfer(tokenAddress, recipientAddress, BigInteger.valueOf(amount))
                .send();
        final var result = testWeb3jService.getTransactionResult();
        // Then
        assertThat(result).isEqualTo("0x");
    }

    private Entity hollowAccountPersist() {
        return domainBuilder
                .entity()
                .customize(e -> e.key(null).maxAutomaticTokenAssociations(10).receiverSigRequired(false))
                .persist();
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }
}
