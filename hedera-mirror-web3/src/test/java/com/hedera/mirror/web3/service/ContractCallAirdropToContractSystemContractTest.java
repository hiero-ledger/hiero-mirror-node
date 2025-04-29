// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.web3.evm.exception.PrecompileNotSupportedException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.utils.BytecodeUtils;
import com.hedera.mirror.web3.web3j.generated.Airdrop;
import com.hedera.mirror.web3.web3j.generated.AssociateContract;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContractCallAirdropToContractSystemContractTest extends AbstractContractCallServiceTest {

    public static final BigInteger DEFAULT_WEI_VALUE = BigInteger.ZERO;
    public static final long DEFAULT_BALANCE = 100_000_000L;
    public static final BigInteger DEFAULT_DEPLOYED_CONTRACT_BALANCE = BigInteger.valueOf(DEFAULT_BALANCE);
    public static final long MAX_BALANCE = 1_000_000_000L;
    public static final BigInteger MAX_DEPLOYED_CONTRACT_BALANCE = BigInteger.valueOf(MAX_BALANCE);

    private static Stream<Arguments> tokenType() {
        return Stream.of(Arguments.of("fungible", true), Arguments.of("non-fungible", false));
    }

    @BeforeEach
    void setUp() {
        persistRewardAccounts();
    }

    @Test
    @DisplayName("Can airdrop fungible token to a contract that is already associated to it")
    void airdropToContract() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = contractPersist(
                receiverContractAddress, AssociateContract.BINARY, e -> e.maxAutomaticTokenAssociations(1));

        final var tokenId = fungibleTokenSetup(sender);
        tokenAccountPersist(tokenId, receiverContractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        // When
        final var functionCall = airdropContract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

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
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId =
                contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> {});
        final var treasury = accountEntityPersist().toEntityId();

        final var fungibleTokenAddresses = new ArrayList<String>();
        final var nonFungibleTokenAddresses = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        for (int i = 0; i < 3; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleAddress);

            tokenAccountPersist(fungibleTokenId, receiverContractEntityId.getId());
            tokenAccountPersist(nonFungibleTokenId, receiverContractEntityId.getId());

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, MAX_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(MAX_BALANCE));

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
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Can airdrop multiple tokens to a contract that is already associated to some of them")
    void airdropMultipleToContractWithSomeAssociations() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = contractPersist(
                receiverContractAddress, AssociateContract.BINARY, e -> e.maxAutomaticTokenAssociations(2));

        final var treasury = accountEntityPersist().toEntityId();

        final var fungibleTokenAddresses = new ArrayList<String>();
        final var nonFungibleTokenAddresses = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        for (int i = 0; i < 2; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleAddress);

            if (i == 0) {
                // associate to some of the tokens
                tokenAccountPersist(fungibleTokenId, receiverContractEntityId.getId());
                tokenAccountPersist(nonFungibleTokenId, receiverContractEntityId.getId());
            }

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        final var airdropContract = testWeb3jService.deployWithValue(Airdrop::deploy, MAX_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(MAX_BALANCE));

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
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName(
            "Can airdrop two tokens to a contract with no remaining auto association slots and already associated to one of the tokens")
    void airdropToContractWithNoRemainingAssociations() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = contractPersist(
                receiverContractAddress, AssociateContract.BINARY, e -> e.maxAutomaticTokenAssociations(0));

        final var tokenAddresses = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {

            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            tokenAddresses.add(tokenAddress);

            if (i == 0) {
                // associate to first token
                tokenAccountPersist(tokenId, receiverContractEntityId.getId());
            }

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
        }

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        // When
        final var functionCall = airdropContract.send_tokenNAmountAirdrops(
                tokenAddresses, senders, receivers, DEFAULT_TOKEN_AIRDROP_AMOUNT, DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Airdropped token with custom fees to be paid by the contract receiver should be paid by the sender")
    void airdropToContractCustomFeePaidBySender() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId =
                contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> {});

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var airdropContractEntityId =
                contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        final var treasury = accountEntityPersist().toEntityId();
        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        persistCustomFees(airdropContractEntityId, tokenId, false);

        tokenAccountPersist(tokenId, receiverContractEntityId.getId());
        tokenAccountPersist(tokenId, airdropContractEntityId.getId());

        // When
        final var functionCall = airdropContract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName(
            "Airdropped token with custom fees (netOfTransfers = true) to be paid by the contract receiver should be paid by the sender")
    void airdropToContractCustomFeePaidBySenderWithNetOfTransfers() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId =
                contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> {});

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var airdropContractEntityId =
                contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        final var treasury = accountEntityPersist().toEntityId();

        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        persistCustomFees(airdropContractEntityId, tokenId, true);

        tokenAccountPersist(tokenId, receiverContractEntityId.getId());
        tokenAccountPersist(tokenId, airdropContractEntityId.getId());

        // When
        final var functionCall = airdropContract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName(
            "Airdropped token with custom fees to be paid by the contract receiver that is a fee collector for another fee would not be paid")
    void airdropToContractCustomFeePaidByContractReceiverFeeCollectorNotPaid() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId =
                contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> {});

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var airdropContractEntityId =
                contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        final var treasury = accountEntityPersist().toEntityId();
        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(airdropContractEntityId)
                .denominator(DEFAULT_DENOMINATOR_VALUE.longValue())
                .minimumAmount(DEFAULT_FEE_MIN_VALUE.longValue())
                .maximumAmount(DEFAULT_FEE_MAX_VALUE.longValue())
                .numerator(DEFAULT_NUMERATOR_VALUE.longValue())
                .allCollectorsAreExempt(true)
                .build();

        final var fixedFee = FixedFee.builder()
                .amount(10L)
                .collectorAccountId(receiverContractEntityId)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .fractionalFees(List.of(fractionalFee))
                        .fixedFees(List.of(fixedFee))
                        .royaltyFees(List.of()))
                .persist();

        tokenAccountPersist(tokenId, receiverContractEntityId.getId());
        tokenAccountPersist(tokenId, airdropContractEntityId.getId());

        // When
        final var functionCall = airdropContract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName(
            "Airdropped token with custom fees to be paid by the contract receiver when the collector is contract should not be paid")
    void airdropToContractCustomFeePaidByContractCollectorNotPaid() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId =
                contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> {});

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        final var tokenId = fungibleTokenSetupWithTreasuryAccount(receiverContractEntityId, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        persistCustomFees(receiverContractEntityId, tokenId, true);

        tokenAccountPersist(tokenId, receiverContractEntityId.getId());

        // When
        final var functionCall = airdropContract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @ParameterizedTest(
            name = "Can airdrop {0} token to a contract that is not associated to it with free auto association slots")
    @MethodSource("tokenType")
    void airdropToContractNoAssociations(String tokenType, boolean isFungible) {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> e.maxAutomaticTokenAssociations(10));

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var airdropContractEntityId =
                contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        final var tokenId = isFungible
                ? fungibleTokenSetup(sender)
                : nonFungibleTokenSetup(accountEntityPersist().toEntityId(), sender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        tokenAccountPersist(tokenId, airdropContractEntityId.getId());

        // When
        final var functionCall = isFungible
                ? airdropContract.send_tokenAirdrop(
                        tokenAddress,
                        getAddressFromEntity(sender),
                        receiverContractAddress,
                        DEFAULT_TOKEN_AIRDROP_AMOUNT,
                        DEFAULT_WEI_VALUE)
                : airdropContract.send_nftAirdrop(
                        tokenAddress,
                        getAddressFromEntity(sender),
                        receiverContractAddress,
                        BigInteger.ONE,
                        DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @ParameterizedTest(
            name =
                    "Can airdrop {0} token to a contract that is not associated to it with no free auto association slots")
    @MethodSource("tokenType")
    void airdropToContractWithMaxAutoAssociationsZero(String tokenType, boolean isFungible) {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> e.maxAutomaticTokenAssociations(0));

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var airdropContractEntityId =
                contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        final var tokenId = isFungible
                ? fungibleTokenSetup(sender)
                : nonFungibleTokenSetup(accountEntityPersist().toEntityId(), sender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        tokenAccountPersist(tokenId, airdropContractEntityId.getId());

        // When
        final var functionCall = isFungible
                ? airdropContract.send_tokenAirdrop(
                        tokenAddress,
                        getAddressFromEntity(sender),
                        receiverContractAddress,
                        DEFAULT_TOKEN_AIRDROP_AMOUNT,
                        DEFAULT_WEI_VALUE)
                : airdropContract.send_nftAirdrop(
                        tokenAddress,
                        getAddressFromEntity(sender),
                        receiverContractAddress,
                        BigInteger.ONE,
                        DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Can airdrop multiple tokens to contract that has free auto association slots")
    void airdropMultipleTokensToContractWithFreeSlots() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> {});

        final var fungibleTokenAddresses = new ArrayList<String>();
        final var nonFungibleTokenAddresses = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        final var treasury = accountEntityPersist().toEntityId();
        for (int i = 0; i < 3; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleTokenAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleTokenAddress);

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, MAX_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(MAX_BALANCE));

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
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Can airdrop multiple tokens to contract that has no free auto association slots")
    void airdropMultipleTokensToContractWithNoFreeSlots() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> e.maxAutomaticTokenAssociations(0));

        final var fungibleTokenAddresses = new ArrayList<String>();
        final var nonFungibleTokenAddresses = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        final var treasury = accountEntityPersist().toEntityId();
        for (int i = 0; i < 3; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleTokenAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleTokenAddress);

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, MAX_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(MAX_BALANCE));

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
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Airdrop with multiple senders and multiple contract receivers")
    void airdropMultipleSenderMultipleContractReceivers() {
        // Given
        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, MAX_DEPLOYED_CONTRACT_BALANCE);
        final var airdropContractAddress = airdropContract.getContractAddress();
        contractPersist(airdropContractAddress, Airdrop.BINARY, e -> e.balance(MAX_BALANCE));

        final var sender = accountEntityPersist();
        final var senders = new ArrayList<String>();
        final var tokenAdresses = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            tokenAdresses.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(airdropContractAddress);
        }

        // When
        final var functionCall = airdropContract.send_tokenNAmountAirdrops(
                tokenAdresses, senders, receivers, DEFAULT_TOKEN_AIRDROP_AMOUNT, DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName("Airdrop with single sender and multiple contract receivers")
    void airdropSingleSenderMultipleContractReceivers() {
        // Given
        final var sender = accountEntityPersist();
        final var receivers = new ArrayList<String>();
        final var tokenAddresses = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {

            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            tokenAddresses.add(tokenAddress);
        }

        final var firstReceiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var firstReceiverContractAddress = firstReceiverContract.getContractAddress();
        contractPersist(firstReceiverContractAddress, AssociateContract.BINARY, e -> {});

        final var secondReceiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var secondReceiverContractAddress = secondReceiverContract.getContractAddress();
        contractPersist(secondReceiverContractAddress, AssociateContract.BINARY, e -> {});

        receivers.add(firstReceiverContractAddress);
        receivers.add(secondReceiverContractAddress);

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, MAX_DEPLOYED_CONTRACT_BALANCE);
        contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(MAX_BALANCE));

        // When
        final var functionCall = airdropContract.send_distributeMultipleTokens(
                tokenAddresses,
                getAddressFromEntity(sender),
                receivers,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    @DisplayName(
            "Airdrop frozen token that is already associated to the receiving contract should result in failed airdrop")
    void airdropFrozenToken() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId =
                contractPersist(receiverContractAddress, AssociateContract.BINARY, e -> {});

        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var airdropContractEntityId =
                contractPersist(airdropContract.getContractAddress(), Airdrop.BINARY, e -> e.balance(DEFAULT_BALANCE));

        final var tokenId = fungibleTokenSetup(sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        tokenAccountPersist(tokenId, airdropContractEntityId.getId());
        tokenAccount(ta -> ta.tokenId(tokenId)
                .accountId(receiverContractEntityId.getId())
                .freezeStatus(TokenFreezeStatusEnum.FROZEN));

        // When
        final var functionCall = airdropContract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    private void persistCustomFees(final EntityId entityId, final Long tokenId, final boolean netOfTransfers) {
        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(entityId)
                .denominator(DEFAULT_DENOMINATOR_VALUE.longValue())
                .minimumAmount(DEFAULT_FEE_MIN_VALUE.longValue())
                .maximumAmount(DEFAULT_FEE_MAX_VALUE.longValue())
                .netOfTransfers(netOfTransfers)
                .numerator(DEFAULT_NUMERATOR_VALUE.longValue())
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .fractionalFees(List.of(fractionalFee))
                        .fixedFees(List.of())
                        .royaltyFees(List.of()))
                .persist();
    }

    private Long nonFungibleTokenSetup(final EntityId treasury, final Entity sender) {
        final var nonFungible = nonFungibleTokenCustomizable(t -> t.kycKey(null).treasuryAccountId(treasury));
        final var nonFungibleTokenId = nonFungible.getTokenId();
        nftPersistCustomizable(n ->
                n.tokenId(nonFungibleTokenId).accountId(sender.toEntityId()).spender(sender.toEntityId()));
        tokenAccountPersist(nonFungibleTokenId, sender.getId());

        return nonFungibleTokenId;
    }

    private Long fungibleTokenSetup(final Entity sender) {
        final var token = fungibleTokenCustomizable(t -> t.kycKey(null));
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());
        return tokenId;
    }

    private Long fungibleTokenSetupWithTreasuryAccount(final EntityId treasury, final Entity sender) {
        final var token = fungibleTokenCustomizable(t -> t.kycKey(null).treasuryAccountId(treasury));
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());
        return tokenId;
    }

    private EntityId getEntityId(String address) {
        return entityIdFromEvmAddress(Address.fromHexString(address));
    }

    private EntityId contractPersist(
            String receiverContractAddress, String binary, Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        final var receiverContractEntityId = getEntityId(receiverContractAddress);
        contractPersistCustomizable(BytecodeUtils.extractRuntimeBytecode(binary), receiverContractEntityId, customizer);
        return receiverContractEntityId;
    }
}
