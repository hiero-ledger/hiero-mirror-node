// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.evm.exception.PrecompileNotSupportedException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.CancelAirdrop;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractCallCancelAirdropSystemContractTest extends AbstractContractCallServiceTest {

    @Test
    void cancelAirdrop() {
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        persistAirdropForFungibleToken(token, sender, receiver);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var functionCall =
                contract.send_cancelAirdrop(getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancelNFTAirdrop() {
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        persistAirdropForNft(token, sender, receiver);

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall = contract.send_cancelNFTAirdrop(
                getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress, DEFAULT_SERIAL_NUMBER);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancel10Airdrops() {
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
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();
            persistAirdropForNft(token, sender, receiver);

            tokens.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var functionCall = contract.send_cancelAirdrops(senders, receivers, tokens, serials);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancel11AirdropsFails() {
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
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();
            persistAirdropForNft(token, sender, receiver);

            tokens.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var functionCall = contract.send_cancelAirdrops(senders, receivers, tokens, serials);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancelAirdropFailsWithMissingAirdropRecord() {
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall =
                contract.send_cancelAirdrop(getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancelAirdropFailsWithInvalidTokenId() {
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var token = accountEntityPersist();

        final var functionCall = contract.send_cancelAirdrop(
                getAddressFromEntity(sender), getAddressFromEntity(receiver), getAddressFromEntity(token));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancelAirdropFailsWithInvalidSender() {
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

        final var functionCall =
                contract.send_cancelAirdrop(tokenAddress, getAddressFromEntity(receiver), tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancelAirdropFailsWithInvalidReceiver() {
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

        final var functionCall = contract.send_cancelAirdrop(getAddressFromEntity(sender), tokenAddress, tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancelAirdropFailsWithInvalidNft() {
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

        final var functionCall = contract.send_cancelNFTAirdrop(
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                getAddressFromEntity(receiver),
                DEFAULT_SERIAL_NUMBER);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void cancelNFTAirdropFailsWithInvalidSerialNumber() {
        final var contract = testWeb3jService.deploy(CancelAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistCancelAirdropReceiver();

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        persistAirdropForNft(token, sender, receiver);

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall = contract.send_cancelNFTAirdrop(
                getAddressFromEntity(sender),
                getAddressFromEntity(receiver),
                tokenAddress,
                BigInteger.valueOf(DEFAULT_SERIAL_NUMBER.longValue() + 1));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    private Entity persistCancelAirdropReceiver() {
        return accountEntityPersistCustomizable(
                e -> e.maxAutomaticTokenAssociations(0).evmAddress(null).alias(null));
    }
}
