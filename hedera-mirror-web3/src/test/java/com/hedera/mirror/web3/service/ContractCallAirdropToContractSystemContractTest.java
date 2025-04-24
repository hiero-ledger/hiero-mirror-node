// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
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

import com.hedera.mirror.web3.web3j.generated.Empty;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractCallAirdropToContractSystemContractTest extends AbstractContractCallServiceTest {

    public static final BigInteger DEFAULT_WEI_VALUE = BigInteger.ZERO;

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
        final var contractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), contractEntityId, e -> e.maxAutomaticTokenAssociations(1));
        final var tokenId = fungibleTokenSetup(sender);
        tokenAccountPersist(tokenId, contractEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntity = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntity, 100_000_000L);

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
        final var associationContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, _ -> {});
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

            tokenAccountPersist(fungibleTokenId, associationContractEntityId.getId());
            tokenAccountPersist(nonFungibleTokenId, associationContractEntityId.getId());

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }


        final var airdropContract =
                testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));
        final var airdropContractEntity = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntity, 1_000_000_000L);

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
        final var associationContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, e -> e.maxAutomaticTokenAssociations(2));
        final var treasury = accountEntityPersist().toEntityId();

        var fungibleTokenAddresses = new ArrayList<String>();
        var nonFungibleTokenAddresses = new ArrayList<String>();
        var senders = new ArrayList<String>();
        var receivers = new ArrayList<String>();
        var serials = new ArrayList<BigInteger>();
        for (int i = 0; i < 2; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleAddress);

            if(i == 0) {
                //associate to some of the tokens
                tokenAccountPersist(fungibleTokenId, associationContractEntityId.getId());
                tokenAccountPersist(nonFungibleTokenId, associationContractEntityId.getId());
            }

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        final var airdropContract =
                testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));
        final var airdropContractEntity = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntity, 1_000_000_000L);

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
    @DisplayName("Can airdrop two tokens to a contract with no remaining auto association slots and already associated to one of the tokens")
    void airdropToContractWithNoRemainingAssociations() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var associationContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, e -> e.maxAutomaticTokenAssociations(0));
        var fungibleTokenAddresses = new ArrayList<String>();
        var senders = new ArrayList<String>();
        var receivers = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {

            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            fungibleTokenAddresses.add(tokenAddress);

            if(i == 0) {
                //associate to first token
                tokenAccountPersist(tokenId, associationContractEntityId.getId());
            }

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
        }

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

        // When
        final var functionCall = airdropContract.send_tokenNAmountAirdrops(
                fungibleTokenAddresses,
                senders,
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
    @DisplayName("Airdropped token with custom fees to be paid by the contract receiver should be paid by the sender")
    void airdropToContractCustomFeePaidBySender() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var associationContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, _ -> {});

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

        final var treasury = accountEntityPersist().toEntityId();

        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        persistCustomFees(airdropContractEntityId, tokenId, false);

        tokenAccountPersist(tokenId, associationContractEntityId.getId());
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
    @DisplayName("Airdropped token with custom fees (netOfTransfers = true) to be paid by the contract receiver should be paid by the sender")
    void airdropToContractCustomFeePaidBySenderWithNetOfTransfers() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var associationContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, _ -> {});

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

        final var treasury = accountEntityPersist().toEntityId();

        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        persistCustomFees(airdropContractEntityId, tokenId, true);

        tokenAccountPersist(tokenId, associationContractEntityId.getId());
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
    @DisplayName("Airdropped token with custom fees to be paid by the contract receiver that is a fee collector for another fee would not be paid")
    void airdropToContractCustomFeePaidByContractReceiverFeeCollectorNotPaid() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), receiverContractEntityId, _ -> {});

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

        final var treasury = accountEntityPersist().toEntityId();

        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(airdropContractEntityId)
                .denominator(10L)
                .minimumAmount(1L)
                .maximumAmount(100L)
                .numerator(1L)
                .allCollectorsAreExempt(true)
                .build();

        final var fixedFee = FixedFee.builder()
                        .amount(10L)
                                .collectorAccountId(receiverContractEntityId).build();
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
    @DisplayName("Airdropped token with custom fees to be paid by the contract receiver when the collector is contract should not be paid")
    void airdropToContractCustomFeePaidByContractCollectorNotPaid() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), receiverContractEntityId, _ -> {});

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

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

    @Test
    @DisplayName("Can airdrop token to a contract that is not associated to it with free auto association slots")
    void airdropToContractNoAssociations() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(Empty::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(Empty.BINARY), receiverContractEntityId, e -> e.maxAutomaticTokenAssociations(10));

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

        final var fungibleTokenId = fungibleTokenSetup(sender);
        final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

        final var treasury = accountEntityPersist().toEntityId();
        final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
        final var nonFungibleTokenAddress = toAddress(nonFungibleTokenId).toHexString();

        tokenAccountPersist(fungibleTokenId, airdropContractEntityId.getId());
        tokenAccountPersist(nonFungibleTokenId, airdropContractEntityId.getId());

        // When
        final var functionCallFungible = airdropContract.send_tokenAirdrop(
                fungibleTokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        final var functionCallNonFungible = airdropContract.send_nftAirdrop(
                nonFungibleTokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                BigInteger.ONE,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCallFungible, airdropContract);
            verifyEthCallAndEstimateGas(functionCallNonFungible, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCallFungible::send);
        }
    }



    @Test
    @DisplayName("Can airdrop token to a contract that is not associated to it with free auto association slots")
    void airdropToContractWithMaxAutoAssociationsZero() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(Empty::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(Empty.BINARY), receiverContractEntityId, e -> e.maxAutomaticTokenAssociations(0));

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

        final var fungibleTokenId = fungibleTokenSetup(sender);
        final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

        final var treasury = accountEntityPersist().toEntityId();
        final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
        final var nonFungibleTokenAddress = toAddress(nonFungibleTokenId).toHexString();

        tokenAccountPersist(fungibleTokenId, airdropContractEntityId.getId());
        tokenAccountPersist(nonFungibleTokenId, airdropContractEntityId.getId());

        // When
        final var functionCallFungible = airdropContract.send_tokenAirdrop(
                fungibleTokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_WEI_VALUE);

        final var functionCallNonFungible = airdropContract.send_nftAirdrop(
                nonFungibleTokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                BigInteger.ONE,
                DEFAULT_WEI_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCallFungible, airdropContract);
            verifyEthCallAndEstimateGas(functionCallNonFungible, airdropContract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCallFungible::send);
        }
    }

//    @Test
//    @DisplayName("Airdrop to Contract that has filled all its maxAutoAssociation slots")
//    void airdropToContractWithFilledMaxAutoAssociations() throws Exception {
//        // Given
//        final var sender = accountEntityPersist();
//        final var receiverContract = testWeb3jService.deployWithoutPersist(Empty::deploy);
//        final var receiverContractAddress = receiverContract.getContractAddress();
//        final var receiverContractEntityId = getEntityId(receiverContractAddress);
//        associationContractPersist(
//                BytecodeUtils.extractRuntimeBytecode(Empty.BINARY), receiverContractEntityId, e -> e.maxAutomaticTokenAssociations(1));
//
//        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
//        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
//        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);
//
//        final var treasury = accountEntityPersist().toEntityId();
//        final var fungibleTokenIdFillingTheSlot = fungibleTokenSetup(sender);
//        final var fillingTokenAddress = toAddress(fungibleTokenIdFillingTheSlot).toHexString();
//        tokenAccountPersist(fungibleTokenIdFillingTheSlot, airdropContractEntityId.getId());
//
//        final var functionCallFilling = airdropContract.send_tokenAirdrop(
//                fillingTokenAddress,
//                getAddressFromEntity(sender),
//                receiverContractAddress,
//                BigInteger.TWO,
//                DEFAULT_WEI_VALUE);
//
//        final var fungibleTokenId = fungibleTokenSetup(sender);
//        final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();
//        final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
//        final var nonFungibleTokenAddress = toAddress(nonFungibleTokenId).toHexString();
//
//        tokenAccountPersist(fungibleTokenId, airdropContractEntityId.getId());
//        tokenAccountPersist(nonFungibleTokenId, airdropContractEntityId.getId());
//
//        // When
//        final var functionCallFungible = airdropContract.send_tokenAirdrop(
//                fungibleTokenAddress,
//                getAddressFromEntity(sender),
//                receiverContractAddress,
//                DEFAULT_TOKEN_AIRDROP_AMOUNT,
//                DEFAULT_WEI_VALUE);
//
//        final var functionCallNonFungible = airdropContract.send_nftAirdrop(
//                nonFungibleTokenAddress,
//                getAddressFromEntity(sender),
//                receiverContractAddress,
//                BigInteger.ONE,
//                DEFAULT_WEI_VALUE);
//
//        // Then
//        if (mirrorNodeEvmProperties.isModularizedServices()) {
//            verifyEthCallAndEstimateGas(functionCallFilling, airdropContract);
//            verifyEthCallAndEstimateGas(functionCallFungible, airdropContract);
//            verifyEthCallAndEstimateGas(functionCallNonFungible, airdropContract);
//        } else {
//            assertThrows(PrecompileNotSupportedException.class, functionCallFungible::send);
//        }
//    }

    @Test
    @DisplayName("Can airdrop multiple tokens to contract that has free auto association slots")
    void airdropMultipleTokensToContractWithFreeSlots() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var associationContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, _ -> {});
        var fungibleTokenAddresses = new ArrayList<String>();
        var nonFungibleTokenAddresses = new ArrayList<String>();
        var senders = new ArrayList<String>();
        var receivers = new ArrayList<String>();
        var serials = new ArrayList<BigInteger>();
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

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 1_000_000_000L);

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
        final var associationContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, e -> e.maxAutomaticTokenAssociations(0));
        var fungibleTokenAddresses = new ArrayList<String>();
        var nonFungibleTokenAddresses = new ArrayList<String>();
        var senders = new ArrayList<String>();
        var receivers = new ArrayList<String>();
        var serials = new ArrayList<BigInteger>();
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

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 1_000_000_000L);

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
        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 1_000_000_000L);
        final var airdropContractAddress = toAddress(airdropContractEntityId).toHexString();

        final var sender = accountEntityPersist();
        var senders = new ArrayList<String>();
        var fungibleTokenAddresses = new ArrayList<String>();
        var receivers = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(airdropContractAddress);
        }

        // When
        final var functionCall = airdropContract.send_tokenNAmountAirdrops(
                fungibleTokenAddresses,
                senders,
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
    @DisplayName("Airdrop with single sender and multiple contract receivers")
    void airdropSingleSenderMultipleContractReceivers() {
        // Given
        final var sender = accountEntityPersist();
        var receivers = new ArrayList<String>();
        var fungibleTokenAddresses = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
        }
        final var firstReceiverContract = testWeb3jService.deployWithoutPersist(Empty::deploy);
        final var firstReceiverContractAddress = firstReceiverContract.getContractAddress();
        final var emptyContractEntityId = getEntityId(firstReceiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(Empty.BINARY), emptyContractEntityId, _ -> {});

        final var secondReceiverContract = testWeb3jService.deployWithoutPersist(Empty::deploy);
        final var secondReceiverContractAddress = secondReceiverContract.getContractAddress();
        final var associationContractEntityId = getEntityId(secondReceiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), associationContractEntityId, _ -> {});

        receivers.add(firstReceiverContractAddress);
        receivers.add(secondReceiverContractAddress);

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 1_000_000_000L);

        // When
        final var functionCall = airdropContract.send_distributeMultipleTokens(
                fungibleTokenAddresses,
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
    @DisplayName("Airdrop frozen token that is already associated to the receiving contract should result in failed airdrop")
    void airdropFrozenToken() {
        // Given
        final var sender = accountEntityPersist();
        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
        final var receiverContractAddress = receiverContract.getContractAddress();
        final var receiverContractEntityId = getEntityId(receiverContractAddress);
        associationContractPersist(
                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), receiverContractEntityId, e -> e.maxAutomaticTokenAssociations(10));

        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);

        final var tokenId = fungibleTokenSetup(sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        tokenAccountPersist(tokenId, airdropContractEntityId.getId());
        tokenAccount(ta -> ta.tokenId(tokenId).accountId(receiverContractEntityId.getId()).freezeStatus(TokenFreezeStatusEnum.FROZEN));

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

//    @Test
//    @DisplayName("Airdrop token to a contract not associated to it with no available auto association slots should fail")
//    void airdropToContractWithNoFreeSlotsAndNoAssociation() {
//        // Given
//        final var sender = accountEntityPersist();
//        final var receiverContract = testWeb3jService.deployWithoutPersist(AssociateContract::deploy);
//        final var receiverContractAddress = receiverContract.getContractAddress();
//        final var receiverContractEntityId = getEntityId(receiverContractAddress);
//        associationContractPersist(
//                BytecodeUtils.extractRuntimeBytecode(AssociateContract.BINARY), receiverContractEntityId, e -> e.maxAutomaticTokenAssociations(0));
//
//        final var airdropContract = testWeb3jService.deployWithoutPersistWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
//        final var airdropContractEntityId = getEntityId(airdropContract.getContractAddress());
//        airdropContractPersist(BytecodeUtils.extractRuntimeBytecode(Airdrop.BINARY), airdropContractEntityId, 100_000_000L);
//
//        final var treasury = accountEntityPersist().toEntityId();
//        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
//        final var tokenAddress = toAddress(tokenId).toHexString();
//        tokenAccountPersist(tokenId, treasury.getId());
//        tokenAllowancePersist(sender.getId(), treasury.getId(), tokenId);
//        // When
//        final var functionCall = airdropContract.call_transferFrom(
//                tokenAddress,
//                getAddressFromEntity(sender),
//                receiverContractAddress,
//                DEFAULT_TOKEN_AIRDROP_AMOUNT);
//
//        // Then
//        if (mirrorNodeEvmProperties.isModularizedServices()) {
//            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
//            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
//        } else {
//            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
//        }
//    }

    private void persistCustomFees(final EntityId airdropContractEntityId, final Long tokenId, final boolean netOfTransfers) {
        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(airdropContractEntityId)
                .denominator(10L)
                .minimumAmount(1L)
                .maximumAmount(100L)
                .netOfTransfers(netOfTransfers)
                .numerator(1L)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .fractionalFees(List.of(fractionalFee))
                        .fixedFees(List.of())
                        .royaltyFees(List.of()))
                .persist();
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

    private Long fungibleTokenSetupWithTreasuryAccount(final EntityId treasury, final Entity sender) {
        final var token = fungibleTokenCustomizable(t -> t.kycKey(null).treasuryAccountId(treasury));
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());
        return tokenId;
    }

    private void associationContractPersist(final String binary, final EntityId entityId, final Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        final var contractBytes = Hex.decode(binary.replace(HEX_PREFIX, ""));
        final var entity = domainBuilder
                .entity(entityId)
                .customize(e -> {
                    e.type(CONTRACT).alias(null).evmAddress(null)
                            .maxAutomaticTokenAssociations(-1);
                    customizer.accept(e);
                })
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(entity.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.getId()))
                .persist();
    }

    private void airdropContractPersist(final String binary, final EntityId entityId, final Long balance) {
        final var contractBytes = Hex.decode(binary.replace(HEX_PREFIX, ""));
        final var entity = domainBuilder
                .entity(entityId)
                .customize(e -> e.type(CONTRACT).alias(null).evmAddress(null).maxAutomaticTokenAssociations(-1).balance(balance))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(entity.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.getId()))
                .persist();
    }

    private EntityId getEntityId(String address) {
        return  entityIdFromEvmAddress(Address.fromHexString(address));
    }

    private Entity hollowAccountPersist() {
        return domainBuilder
                .entity()
                .customize(e -> e.key(null).maxAutomaticTokenAssociations(10).receiverSigRequired(false))
                .persist();
    }

}
