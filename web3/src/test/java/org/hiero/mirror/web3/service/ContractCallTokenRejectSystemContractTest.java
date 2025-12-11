// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.exception.MirrorNodeException;
import org.hiero.mirror.web3.web3j.generated.TokenReject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractCallTokenRejectSystemContractTest extends AbstractContractCallServiceTest {

    public static final int TOKEN_REJECT_FAILURE_THRESHOLD = 11;

    @Test
    @DisplayName("Reject fungible token")
    void tokenRejectSystemContractFungible() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist().toEntityId();

        final var tokenId = fungibleTokenSetup(treasury, sender);

        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), List.of(tokenAddress), List.of());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    @DisplayName("Reject non-fungible token")
    void tokenRejectSystemContractNonFungible() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist().toEntityId();

        final var tokenId = nonFungibleTokenSetup(treasury, sender);

        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), List.of(), List.of(tokenAddress));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    @DisplayName("Reject multiple fungible and non-fungible tokens")
    void tokenRejectSystemContractForMultipleTokens() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist().toEntityId();

        var fungibleTokenAddresses = new ArrayList<String>();
        var nonFungibleTokenAddresses = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {

            final var fungibleTokenId = fungibleTokenSetup(treasury, sender);
            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);

            final var fungibleAddress = toAddress(fungibleTokenId).toHexString();
            final var nonFungibleAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleAddress);
            nonFungibleTokenAddresses.add(nonFungibleAddress);
        }

        // When
        final var functionCall = contract.send_rejectTokens(
                getAddressFromEntity(sender), fungibleTokenAddresses, nonFungibleTokenAddresses);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    @DisplayName("Fails to reject multiple non-fungible tokens if limit exceeds")
    void tokenRejectSystemContractForMultipleNonFungibleTokensExceedsLimit() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist().toEntityId();

        var nonFungibleTokenAddresses = new ArrayList<String>();
        for (int i = 0; i < TOKEN_REJECT_FAILURE_THRESHOLD; i++) {

            final var tokenId = nonFungibleTokenSetup(treasury, sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            nonFungibleTokenAddresses.add(tokenAddress);
        }

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), List.of(), nonFungibleTokenAddresses);

        // Then
        var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    @DisplayName("Fails to reject multiple fungible tokens if limit exceeds")
    void tokenRejectSystemContractForMultipleFungibleTokensExceedsLimit() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist().toEntityId();

        var fungibleTokenAddresses = new ArrayList<String>();
        for (int i = 0; i < TOKEN_REJECT_FAILURE_THRESHOLD; i++) {

            final var tokenId = fungibleTokenSetup(treasury, sender);

            final var tokenAddress = toAddress(tokenId).toHexString();

            fungibleTokenAddresses.add(tokenAddress);
        }

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), fungibleTokenAddresses, List.of());

        // Then
        var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    @DisplayName("Fails to reject token when no association with treasury")
    void tokenRejectSystemContractNoAssociationWithTreasury() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist().toEntityId();

        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());

        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), List.of(tokenAddress), List.of());

        // Then
        var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    @DisplayName("Fails to reject token when no association with sender")
    void tokenRejectSystemContractNoAssociationWithSender() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist().toEntityId();

        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, treasury.getId());

        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), List.of(tokenAddress), List.of());

        // Then
        var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    @DisplayName("Fails to reject token when fungible token is invalid")
    void tokenRejectSystemContractInvalidFungibleToken() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();

        final var senderAddress = getAddressFromEntity(sender);

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), List.of(senderAddress), List.of());

        // Then
        var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    @DisplayName("Fails to reject token when non-fungible token is invalid")
    void tokenRejectSystemContractInvalidNonFungibleToken() {
        // Given
        final var contract = testWeb3jService.deploy(TokenReject::deploy);
        final var sender = accountEntityPersist();

        final var senderAddress = getAddressFromEntity(sender);

        // When
        final var functionCall =
                contract.send_rejectTokens(getAddressFromEntity(sender), List.of(), List.of(senderAddress));

        // Then
        var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    private Long fungibleTokenSetup(final EntityId treasury, final Entity sender) {
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, treasury.getId());
        return tokenId;
    }

    private Long nonFungibleTokenSetup(final EntityId treasury, final Entity sender) {
        final var token = nonFungibleTokenPersistWithTreasury(treasury);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(
                n -> n.tokenId(tokenId).accountId(sender.toEntityId()).spender(sender.getId()));
        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, treasury.getId());
        return tokenId;
    }
}
