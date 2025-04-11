// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.evm.exception.PrecompileNotSupportedException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.Airdrop;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallAirdropSystemContractTest extends AbstractContractCallServiceTest {

    private static final BigInteger ZERO_VALUE = BigInteger.ZERO;

    @BeforeEach
    void setUp() {
        persistRewardAccounts();
    }

    @Test
    void airdropToken() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        // When
        final var functionCall = contract.send_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                DEFAULT_TOKEN_AIRDROP_AMOUNT,
                ZERO_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdropNFT() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        // When
        final var functionCall = contract.send_nftAirdrop(
                nftAddress,
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                DEFAULT_SERIAL_NUMBER,
                ZERO_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdropTokens() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000L));
        List<String> tokens = new ArrayList<>();
        List<String> senders = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        final var sender = accountEntityPersist();
        for (int i = 0; i < 3; i++) {
            final var receiver = persistAirdropReceiver();

            final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        // When
        final var functionCall = contract.send_tokenNAmountAirdrops(
                tokens, senders, receivers, DEFAULT_TOKEN_AIRDROP_AMOUNT, ZERO_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdropNFTs() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000L));
        List<String> nfts = new ArrayList<>();
        List<String> senders = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        List<BigInteger> serials = new ArrayList<>();
        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 3; i++) {
            final var receiver = persistAirdropReceiver();

            final var token = nonFungibleTokenCustomizable(
                    e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
            nftPersistCustomizable(n ->
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();

            nfts.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        // When
        final var functionCall = contract.send_nftNAmountAirdrops(nfts, senders, receivers, serials, ZERO_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdrop10TokenAndNFT() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(1_000_000_000_000L));
        final var sender = accountEntityPersist();

        List<String> tokens = new ArrayList<>();
        List<String> senders = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistAirdropReceiver();

            final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }
        List<String> nfts = new ArrayList<>();
        List<String> nftSenders = new ArrayList<>();
        List<String> nftReceivers = new ArrayList<>();
        List<BigInteger> serials = new ArrayList<>();
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistAirdropReceiver();

            final var token = nonFungibleTokenCustomizable(
                    e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
            nftPersistCustomizable(n ->
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();

            nfts.add(nftAddress);
            nftSenders.add(getAddressFromEntity(sender));
            nftReceivers.add(getAddressFromEntity(receiver));
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
                ZERO_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdrop11TokenAndNFT() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000_000L));
        final var sender = accountEntityPersist();

        List<String> tokens = new ArrayList<>();
        List<String> senders = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final var receiver = persistAirdropReceiver();

            final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }
        List<String> nfts = new ArrayList<>();
        List<String> nftSenders = new ArrayList<>();
        List<String> nftReceivers = new ArrayList<>();
        List<BigInteger> serials = new ArrayList<>();
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistAirdropReceiver();

            final var token = nonFungibleTokenCustomizable(
                    e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
            nftPersistCustomizable(n ->
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();

            nfts.add(nftAddress);
            nftSenders.add(getAddressFromEntity(sender));
            nftReceivers.add(getAddressFromEntity(receiver));
            serials.add(DEFAULT_SERIAL_NUMBER);
        }

        // When
        final var functionCall = contract.call_mixedAirdrop(
                tokens, nfts, senders, receivers, nftSenders, nftReceivers, DEFAULT_TOKEN_AIRDROP_AMOUNT, serials);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdropFailsWhenSenderDoesNotHaveEnoughBalance() {
        // Given
        // Deploy contract with 0 balance
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(0L));
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        // When
        final var functionCall = contract.call_tokenAirdrop(
                tokenAddress,
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                DEFAULT_TOKEN_AIRDROP_AMOUNT);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdropFailsWhenReceiverDoesNotHaveValidAccount() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000L));
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
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void airdropFailsWhenTokenDoesNotExist() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000L));
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver();
        final var token = accountEntityPersist();

        // When
        final var functionCall = contract.call_tokenAirdrop(
                getAddressFromEntity(token),
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                DEFAULT_TOKEN_AIRDROP_AMOUNT);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MAX_VALUE})
    void airdropFailsWhenAmountIsInvalid(long serialNumber) {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(100_000_000L));
        final var sender = accountEntityPersist();
        final var receiver = persistAirdropReceiver();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        // When
        final var functionCall = contract.call_nftAirdrop(
                nftAddress,
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                BigInteger.valueOf(serialNumber));

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void distributeNFTs() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000L));

        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final var receiver = persistAirdropReceiver();

            final var serialNumber = i + 1;
            nftPersistCustomizable(n -> n.accountId(sender.toEntityId())
                    .tokenId(token.getTokenId())
                    .spender(sender.toEntityId())
                    .serialNumber(serialNumber));

            receivers.add(getAddressFromEntity(receiver));
        }

        // When
        final var functionCall =
                contract.send_nftAirdropDistribute(nftAddress, getAddressFromEntity(sender), receivers, ZERO_VALUE);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void distributeNFTsOutOfBound() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000L));

        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            final var receiver = persistAirdropReceiver();

            final var serialNumber = i + 1;
            nftPersistCustomizable(n -> n.accountId(sender.toEntityId())
                    .tokenId(token.getTokenId())
                    .spender(sender.toEntityId())
                    .serialNumber(serialNumber));

            receivers.add(getAddressFromEntity(receiver));
        }

        // When
        final var functionCall =
                contract.call_nftAirdropDistribute(nftAddress, getAddressFromEntity(sender), receivers);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void failToDistributeNFTs() {
        // Given
        final var contract = testWeb3jService.deployWithValue(Airdrop::deploy, BigInteger.valueOf(10_000_000_000L));

        final var sender = accountEntityPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();

        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var nftAddress = toAddress(token.getTokenId()).toHexString();

        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final var receiver = persistAirdropReceiver();

            final var serialNumber = i + 1;
            // Don't create the last 2 nfts
            if (i < 4) {
                nftPersistCustomizable(n -> n.accountId(sender.toEntityId())
                        .tokenId(token.getTokenId())
                        .spender(sender.toEntityId())
                        .serialNumber(serialNumber));
            }
            receivers.add(getAddressFromEntity(receiver));
        }

        // When
        final var functionCall =
                contract.call_nftAirdropDistribute(nftAddress, getAddressFromEntity(sender), receivers);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    private Entity persistAirdropReceiver() {
        return accountEntityPersistCustomizable(
                e -> e.maxAutomaticTokenAssociations(-1).evmAddress(null).alias(null));
    }
}
