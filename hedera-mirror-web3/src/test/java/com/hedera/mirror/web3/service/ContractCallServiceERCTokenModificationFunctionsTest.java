// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
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
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(),
                toAddress(spender.toEntityId()).toHexString(),
                BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFT() {
        // Given
        final var spender = accountEntityPersist().toEntityId();
        final var treasury = accountEntityPersist().toEntityId();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, contractEntityId);
        tokenAssociateAccountPersist(contractEntityId.getId(), token.getTokenId());
        // When
        final var functionCall = contract.send_approveNFT(
                toAddress(token.getTokenId()).toHexString(), toAddress(spender).toHexString(), BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFT() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, contractEntityId);
        final var tokenAddress = toAddress(token.getTokenId());
        tokenAssociateAccountPersist(contractEntityId.getId(), token.getTokenId());
        // When
        final var functionCall =
                contract.send_approveNFT(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
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
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(spender.getId(), tokenId);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall = contract.send_approve(
                tokenAddress.toHexString(), getAliasFromEntity(spender), BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAlias() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();
        final var serialNo = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var contractId = contractEntityId.getId();
        final var token = nftPersist(treasury, contractEntityId);
        final var tokenId = token.getTokenId();

        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(contractId, tokenId);
        // When
        final var functionCall = contract.send_approveNFT(
                tokenAddress.toHexString(), getAliasFromEntity(spender), BigInteger.valueOf(serialNo));
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
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        final var amount = 10L;
        // When
        final var functionCall = contract.send_transfer(
                toAddress(tokenId).toHexString(), toAddress(recipient).toHexString(), BigInteger.valueOf(amount));
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
        tokenAssociateAccountPersist(owner.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, EntityId.of(tokenId), amount);
        // When
        final var functionCall = contract.send_transferFrom(
                toAddress(tokenId).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccount() {
        // Given
        final var owner = accountEntityPersist().toEntityId();
        final var token = domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(token.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .kycKey(new byte[0]))
                .persist();

        final var hollowAccount = hollowAccountPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var tokenId = token.getId();
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        tokenAssociateAccountPersist(hollowAccount.getId(), tokenId);
        tokenAssociateAccountPersist(owner.getId(), tokenId);

        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, token.toEntityId(), amount);
        // When
        final var functionCall = contract.send_transferFrom(
                toAddress(token.getId()).toHexString(),
                toAddress(owner).toHexString(),
                getAliasFromEntity(hollowAccount),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFT() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury, owner);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAssociateAccountPersist(owner.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        nftTokenAllowancePersist(contractEntityId, owner, entityIdFromEvmAddress(tokenAddress));
        // When
        final var functionCall = contract.send_transferFromNFT(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(serialNumber));
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
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);

        final var amount = 10L;
        // When
        final var functionCall = contract.send_transfer(
                toAddress(tokenId).toHexString(), getAliasFromEntity(recipient), BigInteger.valueOf(amount));
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
        final var tokenAddress = toAddress(token.getTokenId());
        final var tokenEntity = entityIdFromEvmAddress(tokenAddress);
        tokenAssociateAccountPersist(owner.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);

        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner.toEntityId(), tokenEntity, amount);
        // When
        final var functionCall = contract.send_transferFrom(
                tokenAddress.toHexString(),
                getAliasFromEntity(owner),
                getAliasFromEntity(recipient),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAlias() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntity = owner.toEntityId();
        final var recipient = accountEntityWithEvmAddressPersist();
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, ownerEntity);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);
        final var tokenEntity = entityIdFromEvmAddress(tokenAddress);
        tokenAssociateAccountPersist(owner.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        nftTokenAllowancePersist(contractEntityId, ownerEntity, tokenEntity);
        // When
        final var functionCall = contract.send_transferFromNFT(
                tokenAddress.toHexString(),
                getAliasFromEntity(owner),
                getAliasFromEntity(recipient),
                BigInteger.valueOf(serialNumber));
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
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(spender.getId(), tokenId);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTRedirect() {
        // Given
        final var spender = accountEntityPersist().toEntityId();
        final var treasury = accountEntityPersist().toEntityId();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), toAddress(spender).toHexString(), BigInteger.ONE);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFTRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress.toHexString(), Address.ZERO.toHexString(), BigInteger.ONE);
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
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(spender.getId(), tokenId);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), getAliasFromEntity(spender), BigInteger.valueOf(amountGranted));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAliasRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();
        final var serialNo = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury, contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall = contract.send_approveRedirect(
                tokenAddress.toHexString(), getAliasFromEntity(spender), BigInteger.valueOf(serialNo));
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
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        // When
        final var functionCall = contract.send_transferRedirect(
                tokenAddress.toHexString(), toAddress(recipient).toHexString(), BigInteger.valueOf(amount));
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
        final var tokenEntity = EntityId.of(tokenId);
        tokenAssociateAccountPersist(owner.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, tokenEntity, amount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenId).toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromToHollowAccountRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityPersist().toEntityId();
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury)
                        .kycKey(new byte[0]))
                .persist();
        final var tokenId = tokenEntity.getId();
        tokenAssociateAccountPersist(owner.getId(), tokenId);

        final var hollowAccount = hollowAccountPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        tokenAssociateAccountPersist(hollowAccount.getId(), tokenId);
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner, tokenEntity.toEntityId(), amount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenEntity.getId()).toHexString(),
                toAddress(owner).toHexString(),
                getAliasFromEntity(hollowAccount),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();
        final var serialNumber = 1L;

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(treasury, owner);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAssociateAccountPersist(owner.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        nftTokenAllowancePersist(contractEntityId, owner, EntityId.of(tokenId));
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(recipient).toHexString(),
                BigInteger.valueOf(serialNumber));
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
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);

        final var amount = 10L;
        // When
        final var functionCall = contract.send_transferRedirect(
                tokenAddress.toHexString(), getAliasFromEntity(recipient), BigInteger.valueOf(amount));
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
        tokenAssociateAccountPersist(owner.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        final var amount = 10L;
        fungibleTokenAllowancePersist(contractEntityId, owner.toEntityId(), EntityId.of(tokenId), amount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                toAddress(tokenId).toHexString(),
                getAliasFromEntity(owner),
                getAliasFromEntity(recipient),
                BigInteger.valueOf(amount));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAliasRedirect() {
        // Given
        final var treasury = accountEntityPersist().toEntityId();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var recipient = accountEntityWithEvmAddressPersist();
        final var serialNumber = 1L;
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(treasury, ownerEntityId);
        final var tokenId = token.getTokenId();
        final var tokenEntityId = EntityId.of(token.getTokenId());
        tokenAssociateAccountPersist(ownerEntityId.getId(), tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);
        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        nftTokenAllowancePersist(contractEntityId, ownerEntityId, tokenEntityId);
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                toAddress(token.getTokenId()).toHexString(),
                getAliasFromEntity(owner),
                getAliasFromEntity(recipient),
                BigInteger.valueOf(serialNumber));
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
        final var tokenAddress = toAddress(tokenId);
        tokenAssociateAccountPersist(recipient.getId(), tokenId);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAssociateAccountPersist(contractEntityId.getId(), tokenId);
        final var amount = 10L;
        // When
        contract.send_delegateTransfer(
                        tokenAddress.toHexString(), toAddress(recipient).toHexString(), BigInteger.valueOf(amount))
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

    private Token nftPersist(final EntityId treasuryEntityId, final EntityId ownerEntityId) {
        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId)
                        .kycKey(domainBuilder.key()))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(ownerEntityId)
                        .tokenId(nftEntity.getId())
                        .spender(ownerEntityId)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .serialNumber(1))
                .persist();
        return token;
    }

    protected void tokenAssociateAccountPersist(final long accountId, final long tokenId) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(accountId)
                        .tokenId(tokenId)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
    }

    protected void nftTokenAllowancePersist(
            final EntityId spender, final EntityId owner, final EntityId tokenEntityId) {
        domainBuilder
                .nftAllowance()
                .customize(ta -> ta.tokenId(tokenEntityId.getId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .approvedForAll(true)
                        .payerAccountId(owner))
                .persist();
    }

    protected void fungibleTokenAllowancePersist(
            final EntityId spender, final EntityId owner, final EntityId tokenEntityId, final Long amount) {
        domainBuilder
                .tokenAllowance()
                .customize(ta -> ta.tokenId(tokenEntityId.getId())
                        .spender(spender.getId())
                        .amount(amount)
                        .owner(owner.getId()))
                .persist();
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }
}
