// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.utils.Constants.CALL_URI;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.List;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.web3j.generated.ERCTestContract;
import org.hiero.mirror.web3.web3j.generated.RedirectTestContract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ContractCallServiceERCTokenModificationFunctionsTest extends AbstractContractCallServiceTest {

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
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAddressFromEntity(spender);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall =
                contract.send_approve(tokenAddress, spenderAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithInvalidAmount() {
        // Given
        final var spender = accountEntityPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAddressFromEntity(spender);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());

        final var invalidAmount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));

        // When
        final var functionCall = contract.send_approve(tokenAddress, spenderAddress, invalidAmount);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void approveNFT() {
        // Given
        final var spender = accountEntityPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(contractEntityId, contractEntityId, contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAddressFromEntity(spender);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall = contract.send_approveNFT(tokenAddress, spenderAddress, DEFAULT_SERIAL_NUMBER);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTInvalidAmount() {
        // Given
        final var spender = accountEntityPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(contractEntityId, contractEntityId, contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAddressFromEntity(spender);
        tokenAccountPersist(tokenId, contractEntityId.getId());

        final var invalidSerialNumber =
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));

        // When
        final var functionCall = contract.send_approveNFT(tokenAddress, spenderAddress, invalidSerialNumber);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void deleteAllowanceNFT() {
        // Given
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(contractEntityId, contractEntityId, contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall =
                contract.send_approveNFT(tokenAddress, Address.ZERO.toHexString(), DEFAULT_SERIAL_NUMBER);
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

        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAlias = getAliasFromEntity(spender);
        // When
        final var functionCall =
                contract.send_approve(tokenAddress, spenderAlias, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAlias() {
        // Given
        var spender = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(contractEntityId, contractEntityId, contractEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAlias = getAliasFromEntity(spender);
        // When
        final var functionCall = contract.send_approveNFT(tokenAddress, spenderAlias, DEFAULT_SERIAL_NUMBER);
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
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall =
                contract.send_transfer(tokenAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferWithFees() {
        // Given
        final var recipient = accountEntityPersist().toEntityId();
        final var treasury = accountEntityPersist().toEntityId();

        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, recipient.getId());
        final var tokenEntityId = EntityId.of(tokenId);
        final var fractionalFee =
                FractionalFee.builder().collectorAccountId(recipient).build();
        final var fixedFee = FixedFee.builder()
                .denominatingTokenId(tokenEntityId)
                .collectorAccountId(recipient)
                .build();
        final var fallbackFee =
                FallbackFee.builder().denominatingTokenId(tokenEntityId).build();
        final var royaltyFee = RoyaltyFee.builder()
                .fallbackFee(fallbackFee)
                .collectorAccountId(recipient)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of(fractionalFee))
                        .royaltyFees(List.of(royaltyFee)))
                .persist();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall =
                contract.send_transfer(tokenAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
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

        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();

        // When
        final var functionCall = contract.send_transferFrom(
                tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
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

        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        // When
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var hollowAccountAlias = getAliasFromEntity(hollowAccount);
        final var functionCall = contract.send_transferFrom(
                tokenAddress, ownerAddress, hollowAccountAlias, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFT() {
        // Given

        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(owner, owner, owner);
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId.getId(), owner);

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall =
                contract.send_transferFromNFT(tokenAddress, ownerAddress, recipientAddress, DEFAULT_SERIAL_NUMBER);
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

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall =
                contract.send_transfer(tokenAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
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

        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transferFrom(
                tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAlias() {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntity = owner.toEntityId();
        final var recipient = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nftPersist(treasuryEntityId, ownerEntity, ownerEntity);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, ownerEntity.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId.getId(), ownerEntity);

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall =
                contract.send_transferFromNFT(tokenAddress, ownerAddress, recipientAddress, DEFAULT_SERIAL_NUMBER);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenRedirect() {
        // Given
        final var spender = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = toAddress(spender).toHexString();
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress, spenderAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
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
        final var token = nftPersist(contractEntityId, contractEntityId, contractEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = toAddress(spender).toHexString();
        // When
        final var functionCall = contract.send_approveRedirect(tokenAddress, spenderAddress, DEFAULT_SERIAL_NUMBER);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void deleteAllowanceNFTRedirect() {
        // Given
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var token = nftPersist(contractEntityId, contractEntityId, contractEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId).toHexString();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress, Address.ZERO.toHexString(), DEFAULT_SERIAL_NUMBER);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveFungibleTokenWithAliasRedirect() {
        // Given
        final var spender = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAliasFromEntity(spender);
        // When
        final var functionCall =
                contract.send_approveRedirect(tokenAddress, spenderAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void approveNFTWithAliasRedirect() {
        // Given
        var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(contractEntityId, contractEntityId, contractEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var spenderAddress = getAliasFromEntity(spender);
        // When
        final var functionCall = contract.send_approveRedirect(tokenAddress, spenderAddress, DEFAULT_SERIAL_NUMBER);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferRedirect() {
        // Given
        final var recipient = accountEntityPersist().toEntityId();
        final var treasury = accountEntityPersist().toEntityId();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);

        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall = contract.send_transferRedirect(
                tokenAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
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

        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall = contract.send_transferFromRedirect(
                tokenAddress, ownerAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
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

        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var hollowAccountAlias = getAliasFromEntity(hollowAccount);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                tokenAddress, ownerAddress, hollowAccountAlias, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTRedirect() {
        // Given
        final var owner = accountEntityPersist().toEntityId();
        final var recipient = accountEntityPersist().toEntityId();

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = nftPersist(owner, owner, owner);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId.getId(), owner);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress, ownerAddress, recipientAddress, DEFAULT_SERIAL_NUMBER);
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

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transferRedirect(
                tokenAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
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

        tokenAllowancePersist(contractEntityId.getId(), owner.getId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAlias = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transferFromRedirect(
                tokenAddress, ownerAlias, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void transferFromNFTWithAliasRedirect() {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var recipient = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nftPersist(treasuryEntityId, ownerEntityId, ownerEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, ownerEntityId.getId());
        tokenAccountPersist(tokenId, recipient.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId.getId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAlias = getAliasFromEntity(owner);
        final var recipientAddress = getAliasFromEntity(recipient);
        // When
        final var functionCall = contract.send_transferFromNFTRedirect(
                tokenAddress, ownerAlias, recipientAddress, DEFAULT_SERIAL_NUMBER);
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

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var recipientAddress = toAddress(recipient).toHexString();
        // When
        contract.send_delegateTransfer(tokenAddress, recipientAddress, BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED))
                .send();
        final var result = testWeb3jService.getTransactionResult();
        // Then
        assertThat(result).isEqualTo("0x");
    }

    @SneakyThrows
    private String convert(Object object) {
        return objectMapper.writeValueAsString(object);
    }
}
