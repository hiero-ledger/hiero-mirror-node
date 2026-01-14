// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.CancelAirdrop;
import org.junit.jupiter.api.Test;

class ContractCallCancelAirdropSystemContractTest extends AbstractContractCallServiceTest {

    @Test
    void cancelAirdrop() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        persistAirdropForFungibleToken(token, sender, receiver);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        // When
        final var functionCall =
                contract.send_cancelAirdrop(getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void cancelNFTAirdrop() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.getId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        persistAirdropForNft(token, sender, receiver);

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        // When
        final var functionCall = contract.send_cancelNFTAirdrop(
                getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress, DEFAULT_SERIAL_NUMBER);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void cancel10Airdrops() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();

        List<String> tokens = new ArrayList<>();
        List<String> senders = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        List<BigInteger> serials = List.of(
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER);
        for (int i = 0; i < 5; i++) {
            final var receiver = persistCancelAirdropReceiver();

            final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();

            persistAirdropForFungibleToken(token, sender, receiver);

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistCancelAirdropReceiver();

            final var token = nonFungibleTokenCustomizable(
                    e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
            nftPersistCustomizable(n ->
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.getId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();
            persistAirdropForNft(token, sender, receiver);

            tokens.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }
        // When
        final var functionCall = contract.send_cancelAirdrops(senders, receivers, tokens, serials);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void cancel11AirdropsFails() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();

        List<String> tokens = new ArrayList<>();
        List<String> senders = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        List<BigInteger> serials = List.of(
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER,
                DEFAULT_SERIAL_NUMBER);
        for (int i = 0; i < 6; i++) {
            final var receiver = persistCancelAirdropReceiver();

            final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();

            persistAirdropForFungibleToken(token, sender, receiver);

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistCancelAirdropReceiver();

            final var token = nonFungibleTokenCustomizable(
                    e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
            nftPersistCustomizable(n ->
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.getId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();
            persistAirdropForNft(token, sender, receiver);

            tokens.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }
        // When
        final var functionCall = contract.send_cancelAirdrops(senders, receivers, tokens, serials);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void cancelAirdropFailsWithMissingAirdropRecord() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        // When
        final var functionCall =
                contract.send_cancelAirdrop(getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void cancelAirdropFailsWithInvalidTokenId() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var token = accountEntityPersist();
        // When
        final var functionCall = contract.send_cancelAirdrop(
                getAddressFromEntity(sender), getAddressFromEntity(receiver), getAddressFromEntity(token));
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void cancelAirdropFailsWithInvalidSender() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var receiver = persistCancelAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(t -> t.amount(DEFAULT_TOKEN_AIRDROP_AMOUNT.longValue())
                        .tokenId(token.getTokenId())
                        .receiverAccountId(receiver.getId())
                        .senderAccountId(token.getTokenId()))
                .persist();

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        // When
        final var functionCall =
                contract.send_cancelAirdrop(tokenAddress, getAddressFromEntity(receiver), tokenAddress);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void cancelAirdropFailsWithInvalidReceiver() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(t -> t.amount(DEFAULT_TOKEN_AIRDROP_AMOUNT.longValue())
                        .tokenId(token.getTokenId())
                        .receiverAccountId(token.getTokenId())
                        .senderAccountId(sender.getId()))
                .persist();

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        // When
        final var functionCall = contract.send_cancelAirdrop(getAddressFromEntity(sender), tokenAddress, tokenAddress);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void cancelAirdropFailsWithInvalidNft() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(t -> t.serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .tokenId(receiver.getId())
                        .receiverAccountId(receiver.getId())
                        .senderAccountId(sender.getId()))
                .persist();
        // When
        final var functionCall = contract.send_cancelNFTAirdrop(
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                getAddressFromEntity(receiver),
                DEFAULT_SERIAL_NUMBER);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void cancelNFTAirdropFailsWithInvalidSerialNumber() {
        // Given
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.getId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        persistAirdropForNft(token, sender, receiver);

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        // When
        final var functionCall = contract.send_cancelNFTAirdrop(
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                tokenAddress,
                BigInteger.valueOf(DEFAULT_SERIAL_NUMBER.longValue() + 1));
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    private Entity persistCancelAirdropReceiver() {
        return accountEntityPersistCustomizable(
                e -> e.maxAutomaticTokenAssociations(0).evmAddress(null).alias(null));
    }
}
