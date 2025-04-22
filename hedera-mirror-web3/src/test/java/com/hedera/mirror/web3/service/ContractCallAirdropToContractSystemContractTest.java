// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.exception.PrecompileNotSupportedException;
import com.hedera.mirror.web3.web3j.generated.Airdrop;
import com.hedera.mirror.web3.web3j.generated.AssociateContract;
import java.math.BigInteger;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractCallAirdropToContractSystemContractTest extends AbstractContractCallServiceTest {

    @BeforeEach
    void setUp() {
        persistRewardAccounts();
    }

    @Test
    @DisplayName("Can airdrop fungible token to a contract that is already associated to it")
    void airdropToContract() throws Exception {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deploy(AssociateContract::deploy);
        final var tokenId = fungibleTokenSetup(sender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        receiverContract.send_associateTokenToThisContract(tokenAddress).send();

        final var airdropContract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));

        final var receiverContractAddress = receiverContract.getContractAddress();
        // When
        final var functionCall = airdropContract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                BigInteger.ZERO);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Can airdrop multiple tokens to a contract that is already associated to them")
    void airdropMultipleToContract() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithMaxAutoTokenAssociations(AssociateContract::deploy, -1);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var treasury = accountEntityPersist().toEntityId();

        var fungibleTokenAddresses = new ArrayList<String>();
        var nonFungibleTokenAddresses = new ArrayList<String>();
        var senders = new ArrayList<String>();
        var receivers = new ArrayList<String>();
        var serials = new ArrayList<BigInteger>();
        for (int i = 0; i < 3; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleAddress);

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        final var allTokenAddresses = new ArrayList<String>();
        allTokenAddresses.addAll(fungibleTokenAddresses);
        allTokenAddresses.addAll(nonFungibleTokenAddresses);

        receiverContract.send_associateTokensToThisContract(allTokenAddresses);

        final var airdropContract =
                testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));

        // When
        final var functionCall = airdropContract.send_mixedAirdrop(
                fungibleTokenAddresses,
                nonFungibleTokenAddresses,
                senders,
                receivers,
                senders,
                receivers,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                serials,
                BigInteger.ZERO);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Can airdrop multiple tokens to a contract that is already associated to some of them")
    void airdropMultipleToContractWithSomeAssociations() throws Exception {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithMaxAutoTokenAssociations(AssociateContract::deploy, 2);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var treasury = accountEntityPersist().toEntityId();

        var fungibleTokenAddresses = new ArrayList<String>();
        var nonFungibleTokenAddresses = new ArrayList<String>();
        var senders = new ArrayList<String>();
        var receivers = new ArrayList<String>();
        var serials = new ArrayList<BigInteger>();
        var associatedTokenAddresses = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {

            final var fungibleTokenId = fungibleTokenSetup2(treasury, sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleAddress);

            if(i == 0) {
                associatedTokenAddresses.add(fungibleTokenAddress);
                associatedTokenAddresses.add(nonFungibleAddress);
            }

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        final var test = receiverContract.send_associateTokensToThisContract(associatedTokenAddresses).send();

        final var airdropContract =
                testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));

        // When
        final var functionCall = airdropContract.send_mixedAirdrop(
                fungibleTokenAddresses,
                nonFungibleTokenAddresses,
                senders,
                receivers,
                senders,
                receivers,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                serials,
                BigInteger.ZERO);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    private Long nonFungibleTokenSetup(EntityId treasury, Entity sender) {
        final var nonFungible =
                nonFungibleTokenCustomizable(t -> t.kycKey(null).treasuryAccountId(treasury));
        final var nonFungibleTokenId = nonFungible.getTokenId();
        nftPersistCustomizable(n ->
                n.tokenId(nonFungibleTokenId).accountId(sender.toEntityId()).spender(sender.toEntityId()));
        tokenAccountPersist(nonFungibleTokenId, sender.getId());

        return nonFungibleTokenId;
    }

    private Long fungibleTokenSetup(Entity sender) {
        final var token = fungibleTokenCustomizable(t -> t.kycKey(null));
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());
        return tokenId;
    }

    private Long fungibleTokenSetup2(EntityId treasury, Entity sender) {
        final var token = fungibleTokenCustomizable(t -> t.kycKey(null).treasuryAccountId(treasury));
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());
        return tokenId;
    }
}
