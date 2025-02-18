/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
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

    public static final String NFT_METADATA_URI = "NFT_METADATA_URI";
    public static final String HBAR = "HBAR";

    @Test
    void ethCallGetApprovedEmptySpenderStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(treasuryEntityId));
        final var tokenAddress = toAddress(tokenId).toHexString();

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
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(treasuryEntityId));
        final var tokenAddress = toAddress(tokenId).toHexString();

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
        final var token = nftPersist(owner);
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spender, owner);
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(
                        tokenAddress,
                        toAddress(owner).toHexString(),
                        toAddress(spender).toHexString())
                .send();
        final var functionCall = contract.send_isApprovedForAll(
                tokenAddress,
                toAddress(owner).toHexString(),
                toAddress(spender).toHexString());
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = nftPersist(owner);
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spender, owner);
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(
                        tokenAddress,
                        toAddress(owner).toHexString(),
                        toAddress(spender).toHexString())
                .send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(
                tokenAddress,
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
        final var token = nftPersist(ownerEntityId);
        final var tokenId = token.getTokenId();

        nftAllowancePersist(tokenId, spender.toEntityId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(
                        tokenAddress, getAliasFromEntity(owner), getAliasFromEntity(spender))
                .send();
        final var functionCall = contract.send_isApprovedForAll(
                tokenAddress,
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
        final var token = nftPersist(ownerEntityId);
        final var tokenId = token.getTokenId();

        nftAllowancePersist(tokenId, spender.toEntityId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(
                        tokenAddress, getAliasFromEntity(owner), getAliasFromEntity(spender))
                .send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(
                tokenAddress,
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
        final var tokenAllowance = tokenAllowancePersist(tokenId, owner, spender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var spenderAddress = toAddress(spender).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowance(
                        tokenAddress,
                        ownerAddress,
                        spenderAddress)
                .send();
        final var functionCall = contract.send_allowance(
                tokenAddress,
                ownerAddress,
                spenderAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAllowance.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }



    @Test
    void ethCallAllowanceNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var tokenAllowance = tokenAllowancePersist(tokenId, owner, spender);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(owner).toHexString();
        final var spenderAddress = toAddress(spender).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowanceNonStatic(
                        tokenAddress,
                        ownerAddress,
                        spenderAddress)
                .send();
        final var functionCall = contract.send_allowanceNonStatic(
                tokenAddress,
                ownerAddress,
                spenderAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAllowance.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var tokenAllowance = tokenAllowancePersist(tokenId, owner.toEntityId(), spender.toEntityId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var senderAlias = getAliasFromEntity(owner);
        final var spenderAlias = getAliasFromEntity(spender);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowance(tokenAddress, senderAlias, spenderAlias)
                .send();
        final var functionCall = contract.send_allowance(tokenAddress, senderAlias, spenderAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAllowance.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasNonStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var tokenAllowance = tokenAllowancePersist(tokenId, owner.toEntityId(), spender.toEntityId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var senderAlias = getAliasFromEntity(owner);
        final var spenderAlias = getAliasFromEntity(spender);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowanceNonStatic(tokenAddress, senderAlias, spenderAlias)
                .send();
        final var functionCall =
                contract.send_allowanceNonStatic(tokenAddress, senderAlias, spenderAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAllowance.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = nftPersistWithSpenderAndTreasury(owner, spender);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApproved(tokenAddress, BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getApproved(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(spender).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedNonStatic() throws Exception {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = nftPersistWithSpenderAndTreasury(owner, spender);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApprovedNonStatic(tokenAddress, BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getApprovedNonStatic(tokenAddress, BigInteger.valueOf(1));
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
        final var tokenAddress = toAddress(tokenEntity.getId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_decimals(tokenAddress).send();
        final var functionCall = contract.send_decimals(tokenAddress);
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
        final var token = fungibleTokenCustomizable(t -> t.totalSupply(totalSupply));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupply(tokenAddress).send();
        final var functionCall = contract.send_totalSupply(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyNonStatic() throws Exception {
        final var totalSupply = 12345L;
        final var token = fungibleTokenCustomizable(t -> t.totalSupply(totalSupply));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupplyNonStatic(tokenAddress).send();
        final var functionCall = contract.send_totalSupplyNonStatic(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolStatic() throws Exception {
        final var token = fungibleTokenCustomizable(t -> t.symbol(HBAR));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbol(tokenAddress).send();
        final var functionCall = contract.send_symbol(tokenAddress);
        assertThat(result).isEqualTo(HBAR);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolNonStatic() throws Exception {
        final var token = fungibleTokenCustomizable(t -> t.symbol(HBAR));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbolNonStatic(tokenAddress).send();
        final var functionCall = contract.send_symbolNonStatic(tokenAddress);
        assertThat(result).isEqualTo(HBAR);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfStatic() throws Exception {
        final var owner = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner);
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(owner, tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOf(
                        tokenAddress, toAddress(owner).toHexString())
                .send();
        final var functionCall = contract.send_balanceOf(
                tokenAddress, toAddress(owner).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }



    @Test
    void ethCallBalanceOfNonStatic() throws Exception {
        final var owner = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner);
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(owner, tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOfNonStatic(
                        tokenAddress, toAddress(owner).toHexString())
                .send();
        final var functionCall = contract.send_balanceOfNonStatic(
                tokenAddress, toAddress(owner).toHexString());
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasStatic() throws Exception {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(owner.toEntityId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var ownerAlias = getAliasFromEntity(owner);
        final var result =
                contract.call_balanceOf(tokenAddress, ownerAlias).send();
        final var functionCall = contract.send_balanceOf(tokenAddress, ownerAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasNonStatic() throws Exception {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(owner.toEntityId(), tokenId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var ownerAlias = getAliasFromEntity(owner);
        final var result = contract.call_balanceOfNonStatic(tokenAddress, ownerAlias)
                .send();
        final var functionCall = contract.send_balanceOfNonStatic(tokenAddress, ownerAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameStatic() throws Exception {
        final var tokenName = "Hbars";
        final var token = fungibleTokenCustomizable(t -> t.name(tokenName));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_name(tokenAddress).send();
        final var functionCall = contract.send_name(tokenAddress);
        assertThat(result).isEqualTo(tokenName);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameNonStatic() throws Exception {
        final var tokenName = "Hbars";
        final var token = fungibleTokenCustomizable(t -> t.name(tokenName));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result =
                contract.call_nameNonStatic(tokenAddress).send();
        final var functionCall = contract.send_nameNonStatic(tokenAddress);
        assertThat(result).isEqualTo(tokenName);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStatic() throws Exception {
        final var owner = accountPersist();
        final var token = nftPersist(owner);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOf(tokenAddress, BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getOwnerOf(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfNonStatic() throws Exception {
        final var owner = accountPersist();
        final var token = nftPersist(owner);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOfNonStatic(tokenAddress, BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo(toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStaticEmptyOwner() throws Exception {
        final var token = nftPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.send_getOwnerOf(tokenAddress, BigInteger.valueOf(1));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            final var result = contract.call_getOwnerOf(tokenAddress, BigInteger.valueOf(1))
                    .send();
            assertThat(result).isEqualTo(Address.ZERO.toHexString());
            verifyEthCallAndEstimateGas(functionCall, contract);
        }
    }

    @Test
    void ethCallGetOwnerOfStaticEmptyOwnerNonStatic() throws Exception {
        final var token = nftPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress, BigInteger.valueOf(1));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            final var result = contract.call_getOwnerOfNonStatic(tokenAddress, BigInteger.valueOf(1))
                    .send();
            assertThat(result).isEqualTo(Address.ZERO.toHexString());
            verifyEthCallAndEstimateGas(functionCall, contract);
        }
    }

    @Test
    void ethCallTokenURIStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).metadata(NFT_METADATA_URI.getBytes()));
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_tokenURI(tokenAddress, BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_tokenURI(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo(NFT_METADATA_URI);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallTokenURINonStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).metadata(NFT_METADATA_URI.getBytes()));

        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_tokenURINonStatic(tokenAddress, BigInteger.valueOf(1))
                .send();
        final var functionCall = contract.send_tokenURINonStatic(tokenAddress, BigInteger.valueOf(1));
        assertThat(result).isEqualTo(NFT_METADATA_URI);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedEmptySpenderRedirect() {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(treasuryEntityId));
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getApprovedRedirect(tokenAddress, BigInteger.valueOf(1));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllRedirect() {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = nftPersist(owner);
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spender, owner);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_isApprovedForAllRedirect(
                toAddress(tokenId).toHexString(),
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
        nftAllowancePersist(tokenId, spender.toEntityId(), owner.toEntityId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_isApprovedForAllRedirect(
                toAddress(tokenId).toHexString(),
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
        tokenAllowancePersist(tokenId, owner, spender); //TODO check order

        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_allowanceRedirect(
                toAddress(tokenId).toHexString(),
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
        tokenAllowancePersist(tokenId, owner.toEntityId(), spender.toEntityId());
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_allowanceRedirect(
                toAddress(tokenId).toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedRedirect() {
        final var owner = accountPersist();
        final var spender = accountPersist();
        final var token = nftPersistWithSpenderAndTreasury(owner, spender); //treasury, owner, spender
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getApprovedRedirect(toAddress(token.getTokenId()).toHexString(), BigInteger.valueOf(1));
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
        final var token = fungibleTokenCustomizable(t -> t.totalSupply(totalSupply));
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_totalSupplyRedirect(toAddress(token.getTokenId()).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolRedirect() {
        final var token = fungibleTokenCustomizable(t -> t.symbol(HBAR));
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_symbolRedirect(toAddress(token.getTokenId()).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfRedirect() {
        final var owner = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(owner, tokenId);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_balanceOfRedirect(
                toAddress(tokenId).toHexString(), toAddress(owner).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasRedirect() {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        tokenAccountPersist(owner.toEntityId(), tokenId); //TODO: to parent class
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_balanceOfRedirect(toAddress(tokenId).toHexString(), getAliasFromEntity(owner));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameRedirect() {
        final var tokenName = "Hbars";
        final var token = fungibleTokenCustomizable(t -> t.name(tokenName));
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_nameRedirect(toAddress(token.getTokenId()).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfRedirect() {
        final var owner = accountPersist();
        final var token = nftPersist(owner);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getOwnerOfRedirect(toAddress(token.getTokenId()).toHexString(), BigInteger.valueOf(1));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }
    @Test
    void ethCallGetOwnerOfEmptyOwnerRedirect() {
        final var token = nftPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getOwnerOfRedirect(toAddress(token.getTokenId()).toHexString(), BigInteger.valueOf(1));
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            verifyEthCallAndEstimateGas(functionCall, contract);
        }
    }

    @Test
    void ethCallTokenURIRedirect() {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).metadata(NFT_METADATA_URI.getBytes()));
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_tokenURIRedirect(toAddress(tokenId).toHexString(), BigInteger.valueOf(1));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void decimalsNegative() {
        // Given
        final var token = nftPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_decimals(toAddress(token.getTokenId()).toHexString());
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

        final var token = nftPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_decimals(toAddress(token.getTokenId()).toHexString());
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
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_getOwnerOf(toAddress(token.getTokenId()).toHexString(), BigInteger.ONE);
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void tokenURINegative() {
        // Given
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_tokenURI(toAddress(token.getTokenId()).toHexString(), BigInteger.ONE);
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void decimalsNegativeRedirect() {
        // Given
        final var token = nftPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_decimalsRedirect(toAddress(token.getTokenId()).toHexString());
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
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_getOwnerOfRedirect(toAddress(token.getTokenId()).toHexString(), BigInteger.ONE);
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
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_tokenURIRedirect(toAddress(token.getTokenId()).toHexString(), BigInteger.ONE);
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

    private TokenAccount tokenAccountPersist(EntityId owner, Long tokenId) { //TODO: parent class
        return domainBuilder
                .tokenAccount()
                .customize(e -> e.accountId(owner.getId()).tokenId(tokenId))
                .persist();
    }

    private TokenAllowance tokenAllowancePersist(Long tokenId, EntityId owner, EntityId spender) {
        return tokenAllowancePersistCustomizable(a -> a.tokenId(tokenId).owner(owner.getNum()).spender(spender.getNum()));
    }

    private EntityId accountPersist() {
        return accountEntityPersist().toEntityId();
    }

    private Token nftPersist() {
        final var token = nonFungibleTokenPersist();
        nftPersistCustomizable(n -> n.tokenId(token.getTokenId()));
        return token;
    }
}
