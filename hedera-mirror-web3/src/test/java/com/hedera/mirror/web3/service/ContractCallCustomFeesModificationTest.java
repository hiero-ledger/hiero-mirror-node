// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ZERO_VALUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FixedFee;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FractionalFee;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests system contact functions for custom token fees(fixed, fractional, royalty) modification.
 * HTS functions are exposed to smart contract calls via IHederaTokenService.sol
 * Target functions are updateFungibleTokenCustomFees and updateNonFungibleTokenCustomFees
 */
class ContractCallCustomFeesModificationTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final long FIXED_FEE_AMOUNT = 10L;
    private static final long MIN_AMOUNT = 1L;
    private static final long MAX_AMOUNT = 1000L;
    private static final long NUMERATOR = 2L;
    private static final long DENOMINATOR = 100L;

    private boolean isModularized;
    private Map<String, String> evmProperties;

    @BeforeEach
    void beforeEach() throws InvocationTargetException, IllegalAccessException {
        isModularized = mirrorNodeEvmProperties.isModularizedServices();
        evmProperties = mirrorNodeEvmProperties.getProperties();
        activateModularizedFlagAndInitializeState();
    }

    @AfterEach
    void afterEach() {
        mirrorNodeEvmProperties.setModularizedServices(isModularized);
        mirrorNodeEvmProperties.setProperties(evmProperties);
    }

    @Test
    void updateFungibleTokenFixedFeeInHbarAlreadyExists() throws Exception {
        // Given I create token with fixed fee in HBAR
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());
        final var collector = accountEntityWithEvmAddressPersist();
        fixedFeeInHbarPersist(token, collector, FIXED_FEE_AMOUNT);

        // When I update the token fees
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = createFixedFeeInHBAR(newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(newFee), List.of(), List.of());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then I verify the token fixed fees are updated as expected
        final var updatedFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(updatedFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(updatedFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(updatedFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(updatedFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(updatedFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFixedFeeNotExisting() throws Exception {
        // Given I create token with no fixed fee set
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When I update the token fees
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = createFixedFeeInHBAR(newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(newFee), List.of(), List.of());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then I verify the token fixed fees are updated as expected
        final var updatedFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(updatedFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(updatedFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(updatedFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(updatedFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(updatedFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFixedFeeInCustomToken() throws Exception {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = buildFixedFeeInCustomToken(token, newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(newFee), List.of(), List.of());

        tokenAccountPersist(token.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var updatedFixedFee = updateFeesFunctionCallResult.component1().getFirst();
        assertThat(updatedFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(updatedFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(updatedFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(updatedFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(updatedFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFractionalFee() throws Exception {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = buildFractionalFee(newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(), List.of(newFee), List.of());

        tokenAccountPersist(token.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var newFractionalFee = updateFeesFunctionCallResult.component2().getFirst();
        assertThat(newFractionalFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));
        assertThat(newFractionalFee.numerator).isEqualTo(newFee.numerator);
        assertThat(newFractionalFee.denominator).isEqualTo(newFee.denominator);
        assertThat(newFractionalFee.minimumAmount).isEqualTo(newFee.minimumAmount);
        assertThat(newFractionalFee.maximumAmount).isEqualTo(newFee.maximumAmount);

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFixedAndFractionalFeeCombination() throws Exception {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFractionalFee = buildFractionalFee(newCollector);
        final var newFixedFee = createFixedFeeInHBAR(newCollector);

        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(newFixedFee), List.of(newFractionalFee), List.of());

        tokenAccountPersist(token.getTokenId(), newCollector.getId());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualFixedFees = updateFeesFunctionCallResult.component1().getFirst();
        assertThat(actualFixedFees.amount).isEqualTo(newFixedFee.amount);
        assertThat(actualFixedFees.useCurrentTokenForPayment).isEqualTo(newFixedFee.useCurrentTokenForPayment);
        assertThat(actualFixedFees.useHbarsForPayment).isEqualTo(newFixedFee.useHbarsForPayment);
        assertThat(actualFixedFees.tokenId).isEqualTo(newFixedFee.tokenId);
        assertThat(actualFixedFees.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        final var actualFractionalFees =
                updateFeesFunctionCallResult.component2().getFirst();
        assertThat(actualFractionalFees.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));
        assertThat(actualFractionalFees.numerator).isEqualTo(newFractionalFee.numerator);
        assertThat(actualFractionalFees.denominator).isEqualTo(newFractionalFee.denominator);
        assertThat(actualFractionalFees.minimumAmount).isEqualTo(newFractionalFee.minimumAmount);
        assertThat(actualFractionalFees.maximumAmount).isEqualTo(newFractionalFee.maximumAmount);
    }

    @Test
    void updateNonFungibleTokenFixedFeeInHBAR() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = createFixedFeeInHBAR(newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(nft), List.of(newFee), List.of(), List.of());

        tokenAccountPersist(nft.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(actualFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(actualFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateNonFungibleTokenFixedFeeInCustomToken() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFixedFee = createFixedFeeInHBAR(newCollector);

        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(nft), List.of(newFixedFee), List.of(), List.of());

        tokenAccountPersist(nft.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(newFixedFee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(newFixedFee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(newFixedFee.useHbarsForPayment);
        assertThat(actualFixedFee.tokenId).isEqualTo(newFixedFee.tokenId);
        assertThat(actualFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    private FixedFee createFixedFeeInHBAR(Entity collectorAccount) {
        return new FixedFee(
                BigInteger.valueOf(FIXED_FEE_AMOUNT + 10),
                Address.ZERO.toHexString(),
                true,
                false,
                getAccountEvmAddress(collectorAccount));
    }

    private FixedFee buildFixedFeeInCustomToken(Token token, Entity collectorAccount) {
        return new FixedFee(
                BigInteger.valueOf(FIXED_FEE_AMOUNT + 10),
                getTokenAddress(token),
                false,
                false,
                getAccountEvmAddress(collectorAccount));
    }

    private FractionalFee buildFractionalFee(Entity collectorAccount) {
        return new FractionalFee(
                BigInteger.valueOf(NUMERATOR + 1),
                BigInteger.valueOf(DENOMINATOR + 1),
                BigInteger.valueOf(MIN_AMOUNT + 1),
                BigInteger.valueOf(MAX_AMOUNT + 1),
                false,
                getAccountEvmAddress(collectorAccount));
    }

    private com.hedera.mirror.common.domain.token.FixedFee getFixedFeeInHBAR(Entity collectorAccount, Long amount) {
        return com.hedera.mirror.common.domain.token.FixedFee.builder()
                .amount(amount)
                .collectorAccountId(collectorAccount.toEntityId())
                .build();
    }

    private com.hedera.mirror.common.domain.token.FixedFee fixedFeeInHbarPersist(
            Token token, Entity collectorAccount, Long amount) {
        final var fixedFee = getFixedFeeInHBAR(collectorAccount, amount);

        domainBuilder
                .customFee()
                .customize(f -> f.entityId(token.getTokenId())
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of()))
                .persist();
        return fixedFee;
    }
}
