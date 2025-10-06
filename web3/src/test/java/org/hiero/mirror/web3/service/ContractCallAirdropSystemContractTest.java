// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.web3.evm.exception.PrecompileNotSupportedException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.web3j.generated.Airdrop;
import org.hiero.mirror.web3.web3j.generated.ClaimAirdrop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith({OutputCaptureExtension.class})
class ContractCallAirdropSystemContractTest extends AbstractContractCallServiceTest {

    private static final BigInteger DEFAULT_DEPLOYED_CONTRACT_BALANCE = BigInteger.valueOf(100_000_000L);
    private static final BigInteger DEPLOYED_BALANCE_1_BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger DEPLOYED_BALANCE_10_BILLION = BigInteger.valueOf(10_000_000_000L);
    private static final int NO_MAX_AUTO_ASSOCIATIONS = 0;
    private static final int UNLIMITED_AUTOMATIC_TOKEN_ASSOCIATIONS = -1;
    private static final String FREE_AUTO_ASSOCIATION_SLOT = "free";
    private static final String NO_AUTO_ASSOCIATION_SLOT = "no";

    private static Stream<Arguments> receiverData() {
        return Stream.of(Arguments.of(ACCOUNT), Arguments.of(CONTRACT));
    }

    private static Stream<Arguments> tokenData() {
        return Stream.of(
                Arguments.of("fungible", FREE_AUTO_ASSOCIATION_SLOT, true, 10),
                Arguments.of("non-fungible", NO_AUTO_ASSOCIATION_SLOT, false, NO_MAX_AUTO_ASSOCIATIONS));
    }

    private static Stream<Arguments> autoAssociationData() {
        return Stream.of(
                Arguments.of(
                        FREE_AUTO_ASSOCIATION_SLOT,
                        "associated to some of them",
                        UNLIMITED_AUTOMATIC_TOKEN_ASSOCIATIONS,
                        true),
                Arguments.of(NO_AUTO_ASSOCIATION_SLOT, "associated to some of them", NO_MAX_AUTO_ASSOCIATIONS, true),
                Arguments.of(
                        FREE_AUTO_ASSOCIATION_SLOT,
                        "not associated to them",
                        UNLIMITED_AUTOMATIC_TOKEN_ASSOCIATIONS,
                        false),
                Arguments.of(NO_AUTO_ASSOCIATION_SLOT, "not associated to them", NO_MAX_AUTO_ASSOCIATIONS, false));
    }

    private static Stream<Arguments> invalidSerialNumberData() {
        return Stream.of(
                Arguments.of(0, ACCOUNT),
                Arguments.of(0, CONTRACT),
                Arguments.of(-1, ACCOUNT),
                Arguments.of(-1, CONTRACT),
                Arguments.of(Long.MAX_VALUE, ACCOUNT),
                Arguments.of(Long.MAX_VALUE, CONTRACT));
    }

    private static void unlimitedMaxAutoAssociations(Entity.EntityBuilder<?, ?> e) {
        e.maxAutomaticTokenAssociations(UNLIMITED_AUTOMATIC_TOKEN_ASSOCIATIONS);
    }

    @ParameterizedTest(name = "Airdrop fungible token to a(an) {0} that is already associated to it")
    @MethodSource("receiverData")
    void airdropToken(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver(receiverType, e -> {});

        final var tokenId = fungibleTokenSetup(sender);
        tokenAccountPersist(tokenId, receiver.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall = contract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                toAddress(receiver).toHexString(),
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(name = "Airdrop token with invalid token address: {0}")
    @CsvSource({"0xa7d9ddbe1f17865597fbd27ec712455208b6b76d", "0.0.-1900", "2.1.-1234"})
    void airdropTokenWithInvalidTokenAddress(String invalidTokenId, CapturedOutput output) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver(EntityType.ACCOUNT, e -> {});

        final var tokenId = fungibleTokenSetup(sender);
        tokenAccountPersist(tokenId, receiver.getId());
        final var invalidTokenAddress = parseTokenIdToAddress(invalidTokenId);

        // When
        final var functionCall = contract.send_tokenAirdrop(
                invalidTokenAddress,
                getAddressFromEntity(sender),
                toAddress(receiver).toHexString(),
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_TINYBAR_VALUE);

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
        assertThat(output.getAll()).doesNotContain("InvalidEntityException");
    }

    @ParameterizedTest(name = "Airdrop non-fungible token to a(an) {0} that is already associated to it")
    @MethodSource("receiverData")
    void airdropNFT(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver(receiverType, e -> {});
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var tokenId = nonFungibleTokenSetup(treasuryEntityId, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall = contract.send_nftAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                toAddress(receiver).toHexString(),
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(name = "Airdrop multiple fungible tokens to a(an) {0}")
    @MethodSource("receiverData")
    void airdropTokensMultipleSendersMultipleReceivers(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_1_BILLION);
        final var tokens = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        final var sender = accountEntityPersist();
        for (int i = 0; i < 3; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(toAddress(receiver).toHexString());
        }

        // When
        final var functionCall = contract.send_tokenNAmountAirdrops(
                tokens, senders, receivers, DEFAULT_TOKEN_AIRDROP_AMOUNT, DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(name = "Airdrop multiple non-fungible tokens to a(an) {0}")
    @MethodSource("receiverData")
    void airdropNFTs(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_10_BILLION);
        final var nfts = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 3; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var tokenId = nonFungibleTokenSetup(treasuryEntityId, sender);
            final var nftAddress = toAddress(tokenId).toHexString();

            nfts.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(toAddress(receiver).toHexString());
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        // When
        final var functionCall =
                contract.send_nftNAmountAirdrops(nfts, senders, receivers, serials, DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(name = "Airdrop 5 fungible and 5 non-fungible tokens to a(an) {0}")
    @MethodSource("receiverData")
    void airdrop10TokenAndNFT(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000_000L));
        final var sender = accountEntityPersist();

        final var tokens = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(toAddress(receiver).toHexString());
        }
        final var nfts = new ArrayList<String>();
        final var nftSenders = new ArrayList<String>();
        final var nftReceivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var tokenId = nonFungibleTokenSetup(treasuryEntityId, sender);
            final var nftAddress = toAddress(tokenId).toHexString();

            nfts.add(nftAddress);
            nftSenders.add(getAddressFromEntity(sender));
            nftReceivers.add(toAddress(receiver).toHexString());
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        // When
        final var functionCall = contract.send_mixedAirdrop(
                tokens,
                nfts,
                senders,
                receivers,
                nftSenders,
                nftReceivers,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                serials,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(
            name = "Airdrop 6 fungible tokens and 5 non-fungible tokens fails for {0}, exceeds threshold of 10")
    @MethodSource("receiverData")
    void airdrop11TokenAndNFT(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000_000L));
        final var sender = accountEntityPersist();

        final var tokens = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        for (int i = 0; i < 6; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(toAddress(receiver).toHexString());
        }
        final var nfts = new ArrayList<String>();
        final var nftSenders = new ArrayList<String>();
        final var nftReceivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var tokenId = nonFungibleTokenSetup(treasuryEntityId, sender);
            final var nftAddress = toAddress(tokenId).toHexString();

            nfts.add(nftAddress);
            nftSenders.add(getAddressFromEntity(sender));
            nftReceivers.add(toAddress(receiver).toHexString());
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        // When
        final var functionCall = contract.call_mixedAirdrop(
                tokens, nfts, senders, receivers, nftSenders, nftReceivers, DEFAULT_TOKEN_AIRDROP_AMOUNT, serials);

        // Then
        verifyContractCallWithException(functionCall);
    }

    @ParameterizedTest(name = "Airdrop multiple tokens to contract that has {0} auto association slots and is {1}")
    @MethodSource("autoAssociationData")
    void airdropMultipleTokensToContract(
            final String slotType,
            final String description,
            final int autoAssociationSlots,
            final boolean shouldAssociate) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_1_BILLION);
        final var sender = accountEntityPersist();

        final var receiverContractEntityId =
                persistContractReceiver(e -> e.maxAutomaticTokenAssociations(autoAssociationSlots));
        final var receiverContractAddress = toAddress(receiverContractEntityId).toHexString();

        final var fungibleTokenAddresses = new ArrayList<String>();
        final var nonFungibleTokenAddresses = new ArrayList<String>();
        final var senders = new ArrayList<String>();
        final var receivers = new ArrayList<String>();
        final var serials = new ArrayList<BigInteger>();
        final var treasury = accountEntityPersist().toEntityId();
        for (int i = 0; i < 2; i++) {

            final var fungibleTokenId = fungibleTokenSetup(sender);
            final var fungibleTokenAddress = toAddress(fungibleTokenId).toHexString();

            final var nonFungibleTokenId = nonFungibleTokenSetup(treasury, sender);
            final var nonFungibleTokenAddress = toAddress(nonFungibleTokenId).toHexString();

            fungibleTokenAddresses.add(fungibleTokenAddress);
            nonFungibleTokenAddresses.add(nonFungibleTokenAddress);

            if (shouldAssociate && i == 0) {
                // associate to some of the tokens
                tokenAccountPersist(fungibleTokenId, receiverContractEntityId.getId());
                tokenAccountPersist(nonFungibleTokenId, receiverContractEntityId.getId());
            }

            senders.add(getAddressFromEntity(sender));
            receivers.add(receiverContractAddress);
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        // When
        final var functionCall = contract.send_mixedAirdrop(
                fungibleTokenAddresses,
                nonFungibleTokenAddresses,
                senders,
                receivers,
                senders,
                receivers,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                serials,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(
            name =
                    "Airdropped token with custom fees {0} to be paid by the contract receiver should be paid by the sender")
    @CsvSource({"(netOfTransfers = false), false", "(netOfTransfers = true), true"})
    void airdropToContractCustomFeePaidBySender(final String description, final boolean netOfTransfers) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();

        final var receiverContractEntityId =
                persistContractReceiver(ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var receiverContractAddress = toAddress(receiverContractEntityId).toHexString();

        final var treasury = accountEntityPersist().toEntityId();
        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contractEntityId = getEntityId(contract.getContractAddress());

        persistCustomFees(contractEntityId, tokenId, netOfTransfers);

        tokenAccountPersist(tokenId, receiverContractEntityId.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());

        // When
        final var functionCall = contract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @Test
    @DisplayName(
            "Airdropped token with custom fees to be paid by the contract receiver when the collector is contract should not be paid")
    void airdropToContractCustomFeePaidByContractCollectorNotPaid() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();
        final var receiverContractEntityId =
                persistContractReceiver(ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var receiverContractAddress = toAddress(receiverContractEntityId).toHexString();

        final var tokenId = fungibleTokenSetupWithTreasuryAccount(receiverContractEntityId, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        persistCustomFees(receiverContractEntityId, tokenId, true);

        tokenAccountPersist(tokenId, receiverContractEntityId.getId());

        // When
        final var functionCall = contract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @Test
    @DisplayName(
            "Airdropped token with custom fees to be paid by the contract receiver that is a fee collector for another fee would not be paid")
    void airdropToContractCustomFeePaidByContractReceiverFeeCollectorNotPaid() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();
        final var receiverContractEntityId =
                persistContractReceiver(ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var receiverContractAddress = toAddress(receiverContractEntityId).toHexString();

        final var treasury = accountEntityPersist().toEntityId();
        final var tokenId = fungibleTokenSetupWithTreasuryAccount(treasury, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contractEntityId = getEntityId(contract.getContractAddress());

        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(contractEntityId)
                .denominator(DEFAULT_DENOMINATOR_VALUE.longValue())
                .minimumAmount(DEFAULT_FEE_MIN_VALUE.longValue())
                .maximumAmount(DEFAULT_FEE_MAX_VALUE.longValue())
                .numerator(DEFAULT_NUMERATOR_VALUE.longValue())
                .allCollectorsAreExempt(true)
                .build();

        final var fixedFee = FixedFee.builder()
                .amount(DEFAULT_FEE_AMOUNT.longValue())
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
        tokenAccountPersist(tokenId, contractEntityId.getId());

        // When
        final var functionCall = contract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(
            name = "Airdrop {0} token to a contract that is not associated to it with {1} auto association slots")
    @MethodSource("tokenData")
    void airdropToContractNoAssociations(
            final String tokenType, final String slotType, final boolean isFungible, final int autoAssociationSlots) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();

        final var receiverContractEntityId =
                persistContractReceiver(e -> e.maxAutomaticTokenAssociations(autoAssociationSlots));
        final var receiverContractAddress = toAddress(receiverContractEntityId).toHexString();

        final var contractEntityId = getEntityId(contract.getContractAddress());

        final var tokenId = isFungible
                ? fungibleTokenSetup(sender)
                : nonFungibleTokenSetup(accountEntityPersist().toEntityId(), sender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        tokenAccountPersist(tokenId, contractEntityId.getId());

        // When
        final var functionCall = isFungible
                ? contract.send_tokenAirdrop(
                        tokenAddress,
                        getAddressFromEntity(sender),
                        receiverContractAddress,
                        DEFAULT_TOKEN_AIRDROP_AMOUNT,
                        DEFAULT_TINYBAR_VALUE)
                : contract.send_nftAirdrop(
                        tokenAddress,
                        getAddressFromEntity(sender),
                        receiverContractAddress,
                        BigInteger.ONE,
                        DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(name = "Airdrop with single sender and multiple {0} receivers")
    @MethodSource("receiverData")
    void airdropSingleSenderMultipleContractReceivers(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_1_BILLION);
        final var sender = accountEntityPersist();
        final var receivers = new ArrayList<String>();
        final var tokenAddresses = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {

            final var tokenId = fungibleTokenSetup(sender);
            final var tokenAddress = toAddress(tokenId).toHexString();

            tokenAddresses.add(tokenAddress);
        }

        final var firstReceiver = persistAirdropReceiver(
                receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var firstReceiverContractAddress = toAddress(firstReceiver).toHexString();

        final var secondReceiver = persistAirdropReceiver(
                receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var secondReceiverContractAddress = toAddress(secondReceiver).toHexString();

        receivers.add(firstReceiverContractAddress);
        receivers.add(secondReceiverContractAddress);

        // When
        final var functionCall = contract.send_distributeMultipleTokens(
                tokenAddresses,
                getAddressFromEntity(sender),
                receivers,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(name = "Airdrop to a(an) {0} fails when sender does not have enough balance")
    @MethodSource("receiverData")
    void airdropFailsWhenSenderDoesNotHaveEnoughBalance(final EntityType receiverType) {
        // Given
        // Deploy contract with 0 balance
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(0L));
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver(
                receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        // When
        final var functionCall = contract.call_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                toAddress(receiver).toHexString(),
                DEFAULT_TOKEN_AIRDROP_AMOUNT);
        // Then
        verifyContractCallWithException(functionCall);
    }

    @Test
    void airdropFailsWhenReceiverDoesNotHaveValidAccount() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_10_BILLION);
        final var sender = accountEntityPersist();
        final var receiverInvalid = fungibleTokenPersist();
        final var receiverAddress = toAddress(receiverInvalid.getTokenId()).toHexString();
        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        // When
        final var functionCall = contract.call_tokenAirdrop(
                tokenAddress, getAddressFromEntity(sender), receiverAddress, DEFAULT_TOKEN_AIRDROP_AMOUNT);
        // Then
        verifyContractCallWithException(functionCall);
    }

    @ParameterizedTest(name = "Airdrop to a(an) {0} fails when token does not exist")
    @MethodSource("receiverData")
    void airdropFailsWhenTokenDoesNotExist(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_10_BILLION);
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver(
                receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var token = accountEntityPersist();

        // When
        final var functionCall = contract.call_tokenAirdrop(
                getAddressFromEntity(token),
                getAddressFromEntity(sender),
                toAddress(receiver).toHexString(),
                DEFAULT_TOKEN_AIRDROP_AMOUNT);
        // Then
        verifyContractCallWithException(functionCall);
    }

    @ParameterizedTest(
            name = "Airdrop fails with invalid non-fungible token serial number: {0}, airdrop receiver is {1}")
    @MethodSource("invalidSerialNumberData")
    void airdropFailsWhenAmountIsInvalid(final long serialNumber, final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver(
                receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var tokenId = nonFungibleTokenSetup(treasuryEntityId, sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall = contract.call_nftAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                toAddress(receiver).toHexString(),
                BigInteger.valueOf(serialNumber));

        // Then
        verifyContractCallWithException(functionCall);
    }

    @ParameterizedTest(name = "Distribute non-fungible tokens to a(an) {0}")
    @MethodSource("receiverData")
    void distributeNFTs(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_10_BILLION);

        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var serialNumber = i + 1;
            nftPersistCustomizable(n -> n.accountId(sender.toEntityId())
                    .tokenId(token.getTokenId())
                    .spender(sender.getId())
                    .serialNumber(serialNumber));

            receivers.add(toAddress(receiver).toHexString());
        }

        // When
        final var functionCall = contract.send_nftAirdropDistribute(
                nftAddress, getAddressFromEntity(sender), receivers, DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCall(functionCall, contract);
    }

    @ParameterizedTest(name = "Fails to distribute non-fungible tokens to a(an) {0}, exceeds threshold of 10")
    @MethodSource("receiverData")
    void distributeNFTsOutOfBound(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_10_BILLION);

        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var serialNumber = i + 1;
            nftPersistCustomizable(n -> n.accountId(sender.toEntityId())
                    .tokenId(token.getTokenId())
                    .spender(sender.getId())
                    .serialNumber(serialNumber));

            receivers.add(toAddress(receiver).toHexString());
        }

        // When
        final var functionCall =
                contract.call_nftAirdropDistribute(nftAddress, getAddressFromEntity(sender), receivers);

        // Then
        verifyContractCallWithException(functionCall);
    }

    @ParameterizedTest(name = "Fails to distribute non-fungible tokens with invalid id to a(an) {0} ")
    @MethodSource("receiverData")
    void failToDistributeNFTs(final EntityType receiverType) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEPLOYED_BALANCE_10_BILLION);

        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final var receiver = persistAirdropReceiver(
                    receiverType, ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);

            final var serialNumber = i + 1;
            // Don't create the last 2 nfts
            if (i < 4) {
                nftPersistCustomizable(n -> n.accountId(sender.toEntityId())
                        .tokenId(token.getTokenId())
                        .spender(sender.getId())
                        .serialNumber(serialNumber));
            }
            receivers.add(toAddress(receiver).toHexString());
        }

        // When
        final var functionCall =
                contract.call_nftAirdropDistribute(nftAddress, getAddressFromEntity(sender), receivers);

        // Then
        verifyContractCallWithException(functionCall);
    }

    @Test
    @DisplayName(
            "Airdrop frozen token that is already associated to the receiving contract should result in failed airdrop")
    void airdropFrozenToken() {
        // Given
        final var sender = accountEntityPersist();
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, DEFAULT_DEPLOYED_CONTRACT_BALANCE);
        final var contractEntityId = getEntityId(contract.getContractAddress());

        final var receiverContractEntityId =
                persistContractReceiver(ContractCallAirdropSystemContractTest::unlimitedMaxAutoAssociations);
        final var receiverContractAddress = toAddress(receiverContractEntityId).toHexString();

        final var tokenId = fungibleTokenSetup(sender);
        final var tokenAddress = toAddress(tokenId).toHexString();

        tokenAccountPersist(tokenId, contractEntityId.getId());
        tokenAccount(ta -> ta.tokenId(tokenId)
                .accountId(receiverContractEntityId.getId())
                .freezeStatus(TokenFreezeStatusEnum.FROZEN));

        // When
        final var functionCall = contract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                receiverContractAddress,
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                DEFAULT_TINYBAR_VALUE);

        // Then
        verifyContractCallWithException(functionCall);
    }

    private EntityId persistAirdropReceiver(
            final EntityType receiverType, final Consumer<Entity.EntityBuilder<?, ?>> consumer) {
        EntityId receiverEntityId;
        if (CONTRACT == receiverType) {
            receiverEntityId = persistContractReceiver(consumer);
        } else {
            receiverEntityId = accountEntityPersistCustomizable(
                            e -> e.maxAutomaticTokenAssociations(UNLIMITED_AUTOMATIC_TOKEN_ASSOCIATIONS)
                                    .evmAddress(null)
                                    .alias(null))
                    .toEntityId();
        }
        return receiverEntityId;
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

    private EntityId persistContractReceiver(final Consumer<Entity.EntityBuilder<?, ?>> consumer) {
        final var receiverContract = testWeb3jService.deployWithoutPersist(ClaimAirdrop::deploy);
        return contractPersist(receiverContract.getContractAddress(), consumer);
    }

    private EntityId contractPersist(
            final String receiverContractAddress, final Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        final var receiverContractEntityId = getEntityId(receiverContractAddress);
        contractPersistCustomizable(
                BytecodeUtils.extractRuntimeBytecode(ClaimAirdrop.BINARY), receiverContractEntityId, customizer);
        return receiverContractEntityId;
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

    private Long nonFungibleTokenSetup(final EntityId treasury, final Entity sender) {
        final var nonFungible = nonFungibleTokenCustomizable(t -> t.kycKey(null).treasuryAccountId(treasury));
        final var nonFungibleTokenId = nonFungible.getTokenId();
        nftPersistCustomizable(n ->
                n.tokenId(nonFungibleTokenId).accountId(sender.toEntityId()).spender(sender.getId()));
        tokenAccountPersist(nonFungibleTokenId, sender.getId());
        return nonFungibleTokenId;
    }

    private void verifyContractCall(final RemoteFunctionCall<TransactionReceipt> functionCall, final Airdrop contract) {
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    private void verifyContractCallWithException(final RemoteFunctionCall<?> functionCall) {
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    /**
     * Parses a token ID to string
     *
     * @param tokenId the token ID in format "shard.realm.num" or EVM address
     * @return the token address string
     */
    private String parseTokenIdToAddress(String tokenId) {
        if (tokenId.startsWith("0x")) {
            return tokenId;
        }

        // Parse shard.realm.num format
        String[] parts = tokenId.split("\\.");

        long shard = Long.parseLong(parts[0]);
        long realm = Long.parseLong(parts[1]);
        long num = Long.parseLong(parts[2]);

        // Convert to hex address format
        return String.format("0x%08x%016x%016x", (int) shard, realm, num);
    }
}
