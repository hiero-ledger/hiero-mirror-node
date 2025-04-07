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
import com.hedera.mirror.web3.web3j.generated.ClaimAirdrop;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractCallClaimAirdropSystemContractTest extends AbstractContractCallServiceTest {

    @Test
    void claimAirdrop() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistClaimAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(t -> t.amount(DEFAULT_TOKEN_AIRDROP_AMOUNT.longValue())
                        .tokenId(token.getTokenId())
                        .receiverAccountId(receiver.getId())
                        .senderAccountId(sender.getId()))
                .persist();

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall =
                contract.send_claim(getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claimNFTAirdrop() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistClaimAirdropReceiver();

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(t -> t.serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .tokenId(token.getTokenId())
                        .receiverAccountId(receiver.getId())
                        .senderAccountId(sender.getId()))
                .persist();

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall = contract.send_claimNFTAirdrop(
                getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress, DEFAULT_SERIAL_NUMBER);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claim10Airdrops() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
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
            final var receiver = persistClaimAirdropReceiver();

            final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();

            domainBuilder
                    .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                    .customize(t -> t.amount(DEFAULT_TOKEN_AIRDROP_AMOUNT.longValue())
                            .tokenId(token.getTokenId())
                            .receiverAccountId(receiver.getId())
                            .senderAccountId(sender.getId()))
                    .persist();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistClaimAirdropReceiver();

            final var token = nonFungibleTokenCustomizable(
                    e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
            nftPersistCustomizable(n ->
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();
            domainBuilder
                    .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                    .customize(t -> t.serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                            .tokenId(token.getTokenId())
                            .receiverAccountId(receiver.getId())
                            .senderAccountId(sender.getId()))
                    .persist();

            tokens.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var functionCall = contract.send_claimAirdrops(senders, receivers, tokens, serials);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claim11AirdropsFails() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
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
            final var receiver = persistClaimAirdropReceiver();

            final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();

            domainBuilder
                    .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                    .customize(t -> t.amount(DEFAULT_TOKEN_AIRDROP_AMOUNT.longValue())
                            .tokenId(token.getTokenId())
                            .receiverAccountId(receiver.getId())
                            .senderAccountId(sender.getId()))
                    .persist();

            tokens.add(tokenAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        for (int i = 0; i < 5; i++) {
            final var receiver = persistClaimAirdropReceiver();

            final var token = nonFungibleTokenCustomizable(
                    e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
            nftPersistCustomizable(n ->
                    n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
            tokenAccountPersist(token.getTokenId(), sender.getId());
            final var nftAddress = toAddress(token.getTokenId()).toHexString();
            domainBuilder
                    .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                    .customize(t -> t.serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                            .tokenId(token.getTokenId())
                            .receiverAccountId(receiver.getId())
                            .senderAccountId(sender.getId()))
                    .persist();

            tokens.add(nftAddress);
            senders.add(getAddressFromEntity(sender));
            receivers.add(getAddressFromEntity(receiver));
        }

        final var functionCall = contract.send_claimAirdrops(senders, receivers, tokens, serials);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claimAirdropFailsWithMissingAirdropRecord() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistClaimAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));
        tokenAccountPersist(token.getTokenId(), sender.getId());
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall =
                contract.send_claim(getAddressFromEntity(sender), getAddressFromEntity(receiver), tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claimAirdropFailsWithInvalidTokenId() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistClaimAirdropReceiver();

        final var token = accountEntityPersist();

        final var functionCall = contract.send_claim(
                getAddressFromEntity(sender), getAddressFromEntity(receiver), getAddressFromEntity(token));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claimAirdropFailsWithInvalidSender() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
        final var receiver = persistClaimAirdropReceiver();

        final var token = fungibleTokenCustomizable(e -> e.kycKey(null));

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(t -> t.amount(DEFAULT_TOKEN_AIRDROP_AMOUNT.longValue())
                        .tokenId(token.getTokenId())
                        .receiverAccountId(receiver.getId())
                        .senderAccountId(token.getTokenId()))
                .persist();

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall = contract.send_claim(tokenAddress, getAddressFromEntity(receiver), tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claimAirdropFailsWithInvalidReceiver() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
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

        final var functionCall = contract.send_claim(getAddressFromEntity(sender), tokenAddress, tokenAddress);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claimAirdropFailsWithInvalidNft() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistClaimAirdropReceiver();

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(t -> t.amount(DEFAULT_TOKEN_AIRDROP_AMOUNT.longValue())
                        .tokenId(receiver.getId())
                        .receiverAccountId(receiver.getId())
                        .senderAccountId(sender.getId()))
                .persist();

        final var functionCall = contract.send_claim(
                getAddressFromEntity(sender), getAddressFromEntity(receiver), getAddressFromEntity(receiver));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void claimNFTAirdropFailsWithInvalidSerialNumber() {
        final var contract = testWeb3jService.deploy(ClaimAirdrop::deploy);
        final var sender = accountEntityPersist();
        final var receiver = persistClaimAirdropReceiver();

        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenCustomizable(
                e -> e.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(n ->
                n.accountId(sender.toEntityId()).tokenId(token.getTokenId()).spender(sender.toEntityId()));
        tokenAccountPersist(token.getTokenId(), sender.getId());

        domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(t -> t.serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .tokenId(token.getTokenId())
                        .receiverAccountId(receiver.getId())
                        .senderAccountId(sender.getId()))
                .persist();

        final var tokenAddress = toAddress(token.getTokenId()).toHexString();

        final var functionCall = contract.send_claimNFTAirdrop(
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

    private Entity persistClaimAirdropReceiver() {
        return accountEntityPersistCustomizable(
                e -> e.maxAutomaticTokenAssociations(0).evmAddress(null).alias(null));
    }
}
