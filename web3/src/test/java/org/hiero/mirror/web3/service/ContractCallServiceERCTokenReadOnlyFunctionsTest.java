// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.ERCTestContract;
import org.hiero.mirror.web3.web3j.generated.RedirectTestContract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
class ContractCallServiceERCTokenReadOnlyFunctionsTest extends AbstractContractCallServiceTest {

    @Test
    void ethCallGetApprovedEmptySpenderStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(treasuryEntityId));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var result =
                contract.call_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER).send();
        final var functionCall = contract.send_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER);

        assertThat(result).isEqualTo((Address.ZERO).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedEmptySpenderNonStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(treasuryEntityId));

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var result = contract.call_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                .send();
        final var functionCall = contract.send_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);

        assertThat(result).isEqualTo((Address.ZERO).toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spenderEntityId.getId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(tokenAddress, ownerAddress, spenderAddress)
                .send();
        final var functionCall = contract.send_isApprovedForAll(tokenAddress, ownerAddress, spenderAddress);
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllNonStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spenderEntityId.getId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(tokenAddress, ownerAddress, spenderAddress)
                .send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(tokenAddress, ownerAddress, spenderAddress);
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllWithAliasStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenId = token.getTokenId();

        nftAllowancePersist(tokenId, spender.getId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = getAddressFromEntity(spender);
        final var ownerAlias = getAliasFromEntity(owner);
        final var spenderAlias = getAliasFromEntity(spender);
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAll(tokenAddress, ownerAlias, spenderAlias)
                .send();
        final var functionCall = contract.send_isApprovedForAll(tokenAddress, ownerAddress, spenderAddress);

        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllWithAliasNonStatic() throws Exception {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenId = token.getTokenId();

        nftAllowancePersist(tokenId, spender.getId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = getAddressFromEntity(spender);
        final var ownerAlias = getAliasFromEntity(owner);
        final var spenderAlias = getAliasFromEntity(spender);

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_isApprovedForAllNonStatic(tokenAddress, ownerAlias, spenderAlias)
                .send();
        final var functionCall = contract.send_isApprovedForAllNonStatic(tokenAddress, ownerAddress, spenderAddress);
        assertThat(result).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var tokenAllowance = tokenAllowancePersist(tokenId, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowance(tokenAddress, ownerAddress, spenderAddress)
                .send();
        final var functionCall = contract.send_allowance(tokenAddress, ownerAddress, spenderAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAllowance.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceNonStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var tokenAllowance = tokenAllowancePersist(tokenId, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_allowanceNonStatic(tokenAddress, ownerAddress, spenderAddress)
                .send();
        final var functionCall = contract.send_allowanceNonStatic(tokenAddress, ownerAddress, spenderAddress);
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
        final var result =
                contract.call_allowance(tokenAddress, senderAlias, spenderAlias).send();
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
        final var functionCall = contract.send_allowanceNonStatic(tokenAddress, senderAlias, spenderAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAllowance.getAmountGranted()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var treasury = accountPersist();
        final var token = nftPersist(treasury, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result =
                contract.call_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER).send();
        final var functionCall = contract.send_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER);
        assertThat(result).isEqualTo(spenderAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedNonStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var treasury = accountPersist();
        final var token = nftPersist(treasury, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                .send();
        final var functionCall = contract.send_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
        assertThat(result).isEqualTo(spenderAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsStatic() throws Exception {
        final var token = fungibleTokenCustomizable(t -> t.decimals(DEFAULT_DECIMALS));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_decimals(tokenAddress).send();
        final var functionCall = contract.send_decimals(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_DECIMALS));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsNonStatic() throws Exception {
        final var token = fungibleTokenCustomizable(t -> t.decimals(DEFAULT_DECIMALS));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_decimalsNonStatic(tokenAddress).send();
        final var functionCall = contract.send_decimalsNonStatic(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_DECIMALS));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyStatic() throws Exception {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupply(tokenAddress).send();
        final var functionCall = contract.send_totalSupply(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(token.getTotalSupply()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyNonStatic() throws Exception {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_totalSupplyNonStatic(tokenAddress).send();
        final var functionCall = contract.send_totalSupplyNonStatic(tokenAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(token.getTotalSupply()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolStatic() throws Exception {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbol(tokenAddress).send();
        final var functionCall = contract.send_symbol(tokenAddress);
        assertThat(result).isEqualTo(token.getSymbol());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolNonStatic() throws Exception {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_symbolNonStatic(tokenAddress).send();
        final var functionCall = contract.send_symbolNonStatic(tokenAddress);
        assertThat(result).isEqualTo(token.getSymbol());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ethCallBalanceOfStatic(boolean overridePayerBalance) throws Exception {
        mirrorNodeEvmProperties.setOverridePayerBalanceValidation(overridePayerBalance);
        final var ownerEntityId = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(ownerEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(tokenId, ownerEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOf(
                        tokenAddress, toAddress(ownerEntityId).toHexString())
                .send();
        final var functionCall = contract.send_balanceOf(tokenAddress, ownerAddress);

        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
        mirrorNodeEvmProperties.setOverridePayerBalanceValidation(false);
    }

    @Test
    void ethCallBalanceOfNonStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(ownerEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(tokenId, ownerEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_balanceOfNonStatic(
                        tokenAddress, toAddress(ownerEntityId).toHexString())
                .send();
        final var functionCall = contract.send_balanceOfNonStatic(tokenAddress, ownerAddress);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasStatic() throws Exception {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(tokenId, owner.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var ownerAlias = getAliasFromEntity(owner);
        final var result = contract.call_balanceOf(tokenAddress, ownerAlias).send();
        final var functionCall = contract.send_balanceOf(tokenAddress, ownerAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasNonStatic() throws Exception {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        final var tokenAccount = tokenAccountPersist(tokenId, owner.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var ownerAlias = getAliasFromEntity(owner);
        final var result =
                contract.call_balanceOfNonStatic(tokenAddress, ownerAlias).send();
        final var functionCall = contract.send_balanceOfNonStatic(tokenAddress, ownerAlias);
        assertThat(result).isEqualTo(BigInteger.valueOf(tokenAccount.getBalance()));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameStatic() throws Exception {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_name(tokenAddress).send();
        final var functionCall = contract.send_name(tokenAddress);
        assertThat(result).isEqualTo(token.getName());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameStaticNullMetadata() throws Exception {
        final var token = fungibleTokenCustomizable(e -> e.metadata(null));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_name(tokenAddress).send();
        final var functionCall = contract.send_name(tokenAddress);
        assertThat(result).isEqualTo(token.getName());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameNonStatic() throws Exception {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_nameNonStatic(tokenAddress).send();
        final var functionCall = contract.send_nameNonStatic(tokenAddress);
        assertThat(result).isEqualTo(token.getName());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameNonStaticNullMetadata() throws Exception {
        final var token = fungibleTokenCustomizable(e -> e.metadata(null));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_nameNonStatic(tokenAddress).send();
        final var functionCall = contract.send_nameNonStatic(tokenAddress);
        assertThat(result).isEqualTo(token.getName());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result =
                contract.call_getOwnerOf(tokenAddress, DEFAULT_SERIAL_NUMBER).send();
        final var functionCall = contract.send_getOwnerOf(tokenAddress, DEFAULT_SERIAL_NUMBER);
        assertThat(result).isEqualTo(ownerAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfNonStatic() throws Exception {
        final var ownerEntityId = accountPersist();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_getOwnerOfNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                .send();
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
        assertThat(result).isEqualTo(ownerAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfStaticEmptyOwner() throws Exception {
        final var token = nftPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.send_getOwnerOf(tokenAddress, DEFAULT_SERIAL_NUMBER);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            final var result = contract.call_getOwnerOf(tokenAddress, DEFAULT_SERIAL_NUMBER)
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
        final var functionCall = contract.send_getOwnerOfNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEstimateGasRevertExecution(
                    functionCall, CONTRACT_REVERT_EXECUTED.name(), MirrorEvmTransactionException.class);
        } else {
            final var result = contract.call_getOwnerOfNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
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
        final var nft = nftPersistCustomizable(n -> n.tokenId(tokenId));
        final var expectedResult = new String(nft.getMetadata());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result =
                contract.call_tokenURI(tokenAddress, DEFAULT_SERIAL_NUMBER).send();
        final var functionCall = contract.send_tokenURI(tokenAddress, DEFAULT_SERIAL_NUMBER);
        assertThat(result).isEqualTo(expectedResult);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallTokenURINonStatic() throws Exception {
        final var treasuryEntityId = accountPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        final var nft = nftPersistCustomizable(n -> n.tokenId(tokenId));
        final var expectedResult = new String(nft.getMetadata());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var result = contract.call_tokenURINonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                .send();
        final var functionCall = contract.send_tokenURINonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
        assertThat(result).isEqualTo(expectedResult);
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
        final var functionCall = contract.send_getApprovedRedirect(tokenAddress, DEFAULT_SERIAL_NUMBER);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllRedirect() {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spenderEntityId.getId(), ownerEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_isApprovedForAllRedirect(tokenAddress, ownerAddress, spenderAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallIsApprovedForAllWithAliasRedirect() {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var ownerEntityId = owner.toEntityId();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spender.getId(), owner.toEntityId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = getAddressFromEntity(spender);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_isApprovedForAllRedirect(tokenAddress, ownerAddress, spenderAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceRedirect() {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        tokenAllowancePersist(tokenId, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_allowanceRedirect(tokenAddress, ownerAddress, spenderAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallAllowanceWithAliasRedirect() {
        final var spender = accountEntityWithEvmAddressPersist();
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        tokenAllowancePersist(tokenId, owner.toEntityId(), spender.toEntityId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAlias = getAliasFromEntity(owner);
        final var spenderAlias = getAliasFromEntity(spender);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_allowanceRedirect(tokenAddress, ownerAlias, spenderAlias);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetApprovedRedirect() {
        final var ownerEntityId = accountPersist();
        final var spenderEntityId = accountPersist();
        final var treasury = accountPersist();
        final var token = nftPersist(treasury, ownerEntityId, spenderEntityId);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getApprovedRedirect(tokenAddress, DEFAULT_SERIAL_NUMBER);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetDecimalsRedirect() {
        final var token = fungibleTokenCustomizable(t -> t.decimals(DEFAULT_DECIMALS));
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_decimalsRedirect(tokenAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetTotalSupplyRedirect() {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_totalSupplyRedirect(tokenAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallSymbolRedirect() {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_symbolRedirect(tokenAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfRedirect() {
        final var ownerEntityId = accountPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(ownerEntityId);
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, ownerEntityId.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_balanceOfRedirect(tokenAddress, ownerAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallBalanceOfWithAliasRedirect() {
        final var owner = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(owner.toEntityId());
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, owner.getId());
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var ownerAlias = getAliasFromEntity(owner);
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_balanceOfRedirect(tokenAddress, ownerAlias);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallNameRedirect() {
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_nameRedirect(tokenAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfRedirect() {
        final var ownerEntityId = accountPersist();
        final var token = nftPersist(ownerEntityId, ownerEntityId, ownerEntityId);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_getOwnerOfRedirect(tokenAddress, DEFAULT_SERIAL_NUMBER);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void ethCallGetOwnerOfEmptyOwnerRedirect() {
        final var token = nftPersist();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var functionCall = contract.send_getOwnerOfRedirect(tokenAddress, DEFAULT_SERIAL_NUMBER);
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
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        nftPersistCustomizable(n -> n.tokenId(tokenId));
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        final var functionCall = contract.send_tokenURIRedirect(tokenAddress, DEFAULT_SERIAL_NUMBER);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void decimalsNegative() {
        // Given
        final var token = nftPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        // When
        final var functionCall = contract.send_decimals(tokenAddress);
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    // Temporary test to increase test coverage
    @Test
    void decimalsNegativeModularizedServices() throws InvocationTargetException, IllegalAccessException {
        // Given
        final var modularizedServicesFlag = mirrorNodeEvmProperties.isModularizedServices();
        final var backupProperties = mirrorNodeEvmProperties.getProperties();

        try {
            activateModularizedFlagAndInitializeState();

            final var token = nftPersist();
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
            // When
            final var functionCall = contract.send_decimals(tokenAddress);
            // Then
            assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
        } finally {
            // Restore changed property values.
            mirrorNodeEvmProperties.setModularizedServices(modularizedServicesFlag);
            mirrorNodeEvmProperties.setProperties(backupProperties);
        }
    }

    @Test
    void ownerOfNegative() {
        // Given
        final var token = fungibleTokenPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        // When
        final var functionCall = contract.send_getOwnerOf(tokenAddress, DEFAULT_SERIAL_NUMBER);
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
        final var functionCall = contract.send_tokenURI(tokenAddress, DEFAULT_SERIAL_NUMBER);
        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void decimalsNegativeRedirect() {
        // Given
        final var token = nftPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_decimalsRedirect(tokenAddress);
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
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_getOwnerOfRedirect(tokenAddress, DEFAULT_SERIAL_NUMBER);
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
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(RedirectTestContract::deploy);
        // When
        final var functionCall = contract.send_tokenURIRedirect(tokenAddress, DEFAULT_SERIAL_NUMBER);
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

    private TokenAllowance tokenAllowancePersist(final long tokenId, final EntityId owner, final EntityId spender) {
        return tokenAllowancePersistCustomizable(
                a -> a.tokenId(tokenId).owner(owner.getId()).spender(spender.getId()));
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
