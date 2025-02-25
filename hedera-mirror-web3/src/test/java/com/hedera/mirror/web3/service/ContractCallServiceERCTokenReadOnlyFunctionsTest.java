// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.mirror.web3.web3j.generated.RedirectTestContract;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractCallServiceERCTokenReadOnlyFunctionsTest extends AbstractContractCallServiceTest {

    @Test
    void ethCallGetApprovedEmptySpenderStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(treasuryEntityId))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result =
                contract.call_getApproved(tokenAddress, BigInteger.valueOf(1)).send();
        final var functionCall = contract.send_getApproved(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo((Address.ZERO).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedEmptySpenderNonStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(treasuryEntityId))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApprovedNonStatic(tokenAddress, BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getApprovedNonStatic(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo((Address.ZERO).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(owner)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(
                        tokenAddress.toHexString(),
                        toAddress(owner).toHexString(),
                        toAddress(spender).toHexString())
                .send();
        final var functionCall = contract.send_isApprovedForAll(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(spender).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(owner)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(
                        tokenAddress.toHexString(),
                        toAddress(owner).toHexString(),
                        toAddress(spender).toHexString())
                .send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(spender).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllWithAliasStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var tokenEntity = nftPersist(ownerEntityId);

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(ownerEntityId)
                        .approvedForAll(true))
                .persist();

        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(
                        tokenAddress.toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender))
                .send();
        final var functionCall = contract.send_isApprovedForAll(
                tokenAddress.toHexString(),
                toAddress(ownerEntityId).toHexString(),
                toAddress(spender.toEntityId()).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllWithAliasNonStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var tokenEntity = nftPersist(ownerEntityId);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(ownerEntityId)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(
                        tokenAddress.toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender))
                .send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(
                tokenAddress.toHexString(),
                toAddress(ownerEntityId).toHexString(),
                toAddress(spender.toEntityId()).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenId).owner(owner.getNum()).spender(spender.getNum()))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowance(
                        tokenAddress.toHexString(),
                        toAddress(owner).toHexString(),
                        toAddress(spender).toHexString())
                .send();
        final var functionCall = contract.send_allowance(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(spender).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenId).owner(owner.getNum()).spender(spender.getNum()))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowanceNonStatic(
                        tokenAddress.toHexString(),
                        toAddress(owner).toHexString(),
                        toAddress(spender).toHexString())
                .send();
        final var functionCall = contract.send_allowanceNonStatic(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(spender).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenId).owner(owner.getNum()).spender(spender.getNum()))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var senderAlias = getAliasFromEntity(owner);
        final var spenderAlias = getAliasFromEntity(spender);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowance(tokenAddress.toHexString(), senderAlias, spenderAlias)
                .send();
        final var functionCall = contract.send_allowance(tokenAddress.toHexString(), senderAlias, spenderAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasNonStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenId).owner(owner.getNum()).spender(spender.getNum()))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var senderAlias = getAliasFromEntity(owner);
        final var spenderAlias = getAliasFromEntity(spender);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowanceNonStatic(tokenAddress.toHexString(), senderAlias, spenderAlias)
                .send();
        final var functionCall =
                contract.send_allowanceNonStatic(tokenAddress.toHexString(), senderAlias, spenderAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner, owner, spender);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getApproved(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(spender).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner, owner, spender);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getApprovedNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(spender).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsStatic() throws Exception {
        final var decimals = 12;
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(ta -> ta.tokenId(tokenEntity.getId()).decimals(decimals))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_decimals(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_decimals(tokenAddress.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(decimals));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsNonStatic() throws Exception {
        final var decimals = 12;
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(ta -> ta.tokenId(tokenEntity.getId()).decimals(decimals))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result =
                contract.call_decimalsNonStatic(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_decimalsNonStatic(tokenAddress.toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(decimals));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyStatic() throws Exception {
        final var totalSupply = 12345L;
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .totalSupply(totalSupply))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupply(tokenAddress).send();
        final var functionCall = contract.send_totalSupply(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyNonStatic() throws Exception {
        final var totalSupply = 12345L;
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .totalSupply(totalSupply))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupplyNonStatic(tokenAddress).send();
        final var functionCall = contract.send_totalSupplyNonStatic(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolStatic() throws Exception {
        final var symbol = "HBAR";
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .symbol(symbol))
                .persist();
        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbol(tokenAddress).send();
        final var functionCall = contract.send_symbol(tokenAddress);
        assertThat(result).isEqualTo(symbol);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolNonStatic() throws Exception {
        final var symbol = "HBAR";
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .symbol(symbol))
                .persist();
        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbolNonStatic(tokenAddress).send();
        final var functionCall = contract.send_symbolNonStatic(tokenAddress);
        assertThat(result).isEqualTo(symbol);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfStatic() throws Exception {
        final var owner = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner);
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId()).tokenId(tokenId))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOf(
                        tokenAddress.toHexString(), toAddress(owner).toHexString())
                .send();
        final var functionCall = contract.send_balanceOf(
                tokenAddress.toHexString(), toAddress(owner).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfNonStatic() throws Exception {
        final var owner = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner);
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId()).tokenId(tokenId))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOfNonStatic(
                        tokenAddress.toHexString(), toAddress(owner).toHexString())
                .send();
        final var functionCall = contract.send_balanceOfNonStatic(
                tokenAddress.toHexString(), toAddress(owner).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasStatic() throws Exception {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId()).tokenId(tokenId))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var ownerAlias = getAliasFromEntity(owner);
        final var result =
                contract.call_balanceOf(tokenAddress.toHexString(), ownerAlias).send();
        final var functionCall = contract.send_balanceOf(tokenAddress.toHexString(), ownerAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasNonStatic() throws Exception {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        var entity = domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId()).tokenId(tokenId))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var ownerAlias = getAliasFromEntity(owner);
        final var result = contract.call_balanceOfNonStatic(tokenAddress.toHexString(), ownerAlias)
                .send();
        final var functionCall = contract.send_balanceOfNonStatic(tokenAddress.toHexString(), ownerAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(entity.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameStatic() throws Exception {
        final var tokenName = "Hbars";
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .name(tokenName))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_name(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_name(tokenAddress.toHexString());
        assertThat(result).isEqualTo(tokenName);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameNonStatic() throws Exception {
        final var tokenName = "Hbars";
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .name(tokenName))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result =
                contract.call_nameNonStatic(tokenAddress.toHexString()).send();
        final var functionCall = contract.send_nameNonStatic(tokenAddress.toHexString());
        assertThat(result).isEqualTo(tokenName);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStatic() throws Exception {
        final var owner = accountPersist();
        final var tokenEntity = nftPersist(owner);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfNonStatic() throws Exception {
        final var owner = accountPersist();
        final var tokenEntity = nftPersist(owner);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStaticEmptyOwner() throws Exception {
        final var tokenEntity = nftPersist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.send_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            final var result = contract.call_getOwnerOf(tokenAddress.toHexString(), BigInteger.valueOf(1))
                    .send();
            assertThat(result).isEqualTo(Address.ZERO.toHexString());
            verifyEthCallAndEstimateGas(functionCall, contract);
        }
    }

    @Test
    void ethCallGetOwnerOfStaticEmptyOwnerNonStatic() throws Exception {
        final var tokenEntity = nftPersist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            final var result = contract.call_getOwnerOfNonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1))
                    .send();
            assertThat(result).isEqualTo(Address.ZERO.toHexString());
            verifyEthCallAndEstimateGas(functionCall, contract);
        }
    }

    @Test
    void ethCallTokenURIStatic() throws Exception {
        final var ownerEntity = accountPersist();
        final byte[] kycKey = domainBuilder.key();
        final var metadata = "NFT_METADATA_URI";
        final var tokenEntity = tokenEntityPersist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(ownerEntity)
                        .kycKey(kycKey))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .metadata(metadata.getBytes())
                        .serialNumber(1))
                .persist();

        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_tokenURI(tokenAddress.toHexString(), BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_tokenURI(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(metadata);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallTokenURINonStatic() throws Exception {
        final var ownerEntity = accountPersist();
        final byte[] kycKey = domainBuilder.key();
        final var metadata = "NFT_METADATA_URI";
        final var tokenEntity = tokenEntityPersist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(ownerEntity)
                        .kycKey(kycKey))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .metadata(metadata.getBytes())
                        .serialNumber(1))
                .persist();

        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_tokenURINonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_tokenURINonStatic(tokenAddress.toHexString(), BigInteger.valueOf(1));
        assertThat(result).isEqualTo(metadata);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedEmptySpenderRedirect() {
        final var treasuryEntityId = accountPersist();
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).accountId(treasuryEntityId))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getApprovedRedirect(tokenAddress, BigInteger.valueOf(1));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllRedirect() {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner);
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getTokenId())
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(owner)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_isApprovedForAllRedirect(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(spender).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllWithAliasRedirect() {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var token = nftPersist(ownerEntityId);
        final var tokenId = token.getTokenId();
        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenId)
                        .spender(spender.getId())
                        .owner(owner.getId())
                        .payerAccountId(ownerEntityId)
                        .approvedForAll(true))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_isApprovedForAllRedirect(
                tokenAddress.toHexString(),
                toAddress(ownerEntityId).toHexString(),
                toAddress(spender.toEntityId()).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceRedirect() {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenId).owner(owner.getNum()).spender(spender.getNum()))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_allowanceRedirect(
                tokenAddress.toHexString(),
                toAddress(owner).toHexString(),
                toAddress(spender).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasRedirect() {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenId).owner(owner.getNum()).spender(spender.getNum()))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_allowanceRedirect(
                tokenAddress.toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedRedirect() {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var tokenEntity = nftPersist(owner, owner, spender);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getApprovedRedirect(tokenAddress.toHexString(), BigInteger.valueOf(1));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsRedirect() {
        final var decimals = 12;
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(ta -> ta.tokenId(tokenEntity.getId()).decimals(decimals))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_decimalsRedirect(tokenAddress.toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyRedirect() {
        final var totalSupply = 12345L;
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .totalSupply(totalSupply))
                .persist();

        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_totalSupplyRedirect(tokenAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolRedirect() {
        final var symbol = "HBAR";
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .symbol(symbol))
                .persist();
        final var tokenAddress = getAddressFromEntity(tokenEntity);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_symbolRedirect(tokenAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfRedirect() {
        final var owner = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner);
        final var tokenId = token.getTokenId();
        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId()).tokenId(tokenId))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_balanceOfRedirect(
                tokenAddress.toHexString(), toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasRedirect() {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();

        domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId()).tokenId(tokenId))
                .persist();
        final var tokenAddress = toAddress(tokenId);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_balanceOfRedirect(tokenAddress.toHexString(), getAliasFromEntity(owner));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameRedirect() {
        final var tokenName = "Hbars";
        final var tokenEntity = tokenEntityPersist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .name(tokenName))
                .persist();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_nameRedirect(tokenAddress.toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfRedirect() {
        final var owner = accountPersist();
        final var tokenEntity = nftPersist(owner);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getOwnerOfRedirect(tokenAddress.toHexString(), BigInteger.valueOf(1));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfEmptyOwnerRedirect() {
        final var tokenEntity = nftPersist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getOwnerOfRedirect(tokenAddress.toHexString(), BigInteger.valueOf(1));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            verifyEthCallAndEstimateGas(functionCall, contract);
        }
    }

    @Test
    void ethCallTokenURIRedirect() {
        final var ownerEntity = accountPersist();
        final byte[] kycKey = domainBuilder.key();
        final var metadata = "NFT_METADATA_URI";
        final var tokenEntity = tokenEntityPersist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(ownerEntity)
                        .kycKey(kycKey))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .metadata(metadata.getBytes())
                        .serialNumber(1))
                .persist();

        final var tokenAddress = toAddress(tokenEntity.getId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_tokenURIRedirect(tokenAddress.toHexString(), BigInteger.valueOf(1));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void decimalsNegative() {
        // Given
        final var tokenEntity = nftPersist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_decimals(tokenAddress.toHexString());
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    // Temporary test to increase test coverage
    @Test
    void decimalsNegativeModularizedServices() throws InvocationTargetException, IllegalAccessException {
        // Given
        final var modularizedServicesFlag = mirrorNodeEvmProperties.isModularizedServices();
        mirrorNodeEvmProperties.setModularizedServices(true);

        final var backupProperties = mirrorNodeEvmProperties.getProperties();
        final Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put("contracts.maxRefundPercentOfGasLimit", "100");
        propertiesMap.put("contracts.maxGasPerSec", "15000000");
        mirrorNodeEvmProperties.setProperties(propertiesMap);

        Method postConstructMethod = Arrays.stream(MirrorNodeState.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostConstruct.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("@PostConstruct method not found"));

        postConstructMethod.setAccessible(true); // Make the method accessible
        postConstructMethod.invoke(state);

        final var tokenEntity = nftPersist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_decimals(tokenAddress.toHexString());
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);

        // Restore changed property values.
        mirrorNodeEvmProperties.setModularizedServices(modularizedServicesFlag);
        mirrorNodeEvmProperties.setProperties(backupProperties);
    }

    @Test
    void ownerOfNegative() {
        // Given
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_getOwnerOf(tokenAddress.toHexString(), BigInteger.ONE);
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void tokenURINegative() {
        // Given
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId());
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_tokenURI(tokenAddress.toHexString(), BigInteger.ONE);
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void decimalsNegativeRedirect() {
        // Given
        final var tokenEntity = nftPersist();
        final var tokenAddress = toAddress(tokenEntity.getTokenId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_decimalsRedirect(tokenAddress.toHexString());
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        } else {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(INVALID_TOKEN_ID.name());
        }
    }

    @Test
    void ownerOfNegativeRedirect() {
        // Given
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_getOwnerOfRedirect(tokenAddress.toHexString(), BigInteger.ONE);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        } else {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(INVALID_TOKEN_ID.name());
        }
    }

    @Test
    void tokenURINegativeRedirect() {
        // Given
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_tokenURIRedirect(tokenAddress.toHexString(), BigInteger.ONE);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        } else {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(INVALID_TOKEN_ID.name());
        }
    }

    private EntityId accountPersist() {
        return accountEntityPersist().toEntityId();
    }

    private Token nftPersist() {
        final var tokenEntity = tokenEntityPersist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L))
                .persist();
        return token;
    }

    private Token nftPersist(final EntityId treasuryEntityId) {
        return nftPersist(treasuryEntityId, treasuryEntityId);
    }

    private Token nftPersist(final EntityId treasuryEntityId, final EntityId ownerEntityId) {
        return nftPersist(treasuryEntityId, ownerEntityId, ownerEntityId);
    }

    private Token nftPersist(
            final EntityId treasuryEntityId, final EntityId ownerEntityId, final EntityId spenderEntityId) {
        return nftPersist(treasuryEntityId, ownerEntityId, spenderEntityId, domainBuilder.key());
    }

    private Token nftPersist(
            final EntityId treasuryEntityId,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final byte[] kycKey) {
        final var nftEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(nftEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryEntityId)
                        .kycKey(kycKey))
                .persist();

        domainBuilder
                .nft()
                .customize(n -> n.accountId(treasuryEntityId)
                        .spender(spenderEntityId)
                        .accountId(ownerEntityId)
                        .tokenId(nftEntity.getId())
                        .metadata("NFT_METADATA_URI".getBytes())
                        .serialNumber(1))
                .persist();
        return token;
    }
}
