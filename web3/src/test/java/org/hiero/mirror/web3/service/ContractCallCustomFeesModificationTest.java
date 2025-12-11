// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ZERO_VALUE;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FixedFee;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FractionalFee;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.RoyaltyFee;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests system contact functions for custom token fees(fixed, fractional, royalty) modification.
 * HTS functions are exposed to smart contract calls via IHederaTokenService.sol
 * Target functions are updateFungibleTokenCustomFees and updateNonFungibleTokenCustomFees
 * Fixed fee - a flat fee charged regardless of the transferred amount
 * Fractional fee - a percentage of the transferred amount
 * Royalty fee - a custom fee you can attach to a nft, allowing the original creator to earn from future
 *      transfers. Each time a nft is transferred a royalty fee is payed to the creator. The royalty fee is a
 *      fraction of the value exchanged for the nft or fixed amount in case the nft is exchanged for free.
 *      The fee is payed in HBARs or in a custom fungible token.The royalty fee is not applied when the nft is
 *      transferred from or to the treasury account.
 */
class ContractCallCustomFeesModificationTest extends AbstractContractCallServiceOpcodeTracerTest {
    private Map<String, String> evmProperties;

    @BeforeEach
    void beforeEach() throws InvocationTargetException, IllegalAccessException {
        evmProperties = mirrorNodeEvmProperties.getProperties();
        initializeState();
    }

    @AfterEach
    void afterEach() {
        mirrorNodeEvmProperties.setProperties(evmProperties);
    }

    /**
     * Verifies already existing fixed fee of a fungible token can be updated
     * @throws Exception
     */
    @Test
    void updateFungibleTokenFixedFeeInHbarAlreadyExists() throws Exception {
        // Given I create token with fixed fee in HBAR
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());
        final var collector = accountEntityWithEvmAddressPersist();
        fixedFeeInHbarPersist(token, collector, DEFAULT_FEE_AMOUNT.longValue());

        // When I update the token fees
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = createFixedFeeInHBAR(newCollector);
        final var updateFeesFunctionCall = contract.call_updateFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(token), List.of(newFee), List.of(), List.of());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then I verify the token fixed fees are updated as expected
        final var updatedFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(updatedFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(updatedFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(updatedFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(updatedFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(updatedFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    /**
     * Verifies fixed fee can be applied to a fungible token that does not have a previously set fixed fee
     * @throws Exception
     */
    @Test
    void updateFungibleTokenFixedFeeInHbarNotExisting() throws Exception {
        // Given I create token with no fixed fee set
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When I update the token fees
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var collector = accountEntityWithEvmAddressPersist();
        final var fixedFee = createFixedFeeInHBAR(collector);
        final var updateFeesFunctionCall = contract.call_updateFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(token), List.of(fixedFee), List.of(), List.of());

        final var functionCallResult = updateFeesFunctionCall.send();

        // Then I verify the token fixed fees are updated as expected
        final var actualFixedFee = functionCallResult.component1().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(fixedFee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(fixedFee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(fixedFee.useHbarsForPayment);
        assertThat(actualFixedFee.tokenId).isEqualTo(fixedFee.tokenId);
        assertThat(actualFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(collector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFixedFeeInCustomToken() throws Exception {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = buildFixedFeeInCustomToken(token, newCollector);
        final var updateFeesFunctionCall = contract.call_updateFungibleTokenCustomFeesAndGetExternal(
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

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFractionalFee() throws Exception {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var collector = accountEntityWithEvmAddressPersist();
        final var fractionalFee = buildFractionalFee(collector);
        final var updateFeesFunctionCall = contract.call_updateFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(token), List.of(), List.of(fractionalFee), List.of());

        tokenAccountPersist(token.getTokenId(), collector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var newFractionalFee = updateFeesFunctionCallResult.component2().getFirst();
        assertThat(newFractionalFee.feeCollector).isEqualTo(getAddressFromEntityId(collector.toEntityId()));
        assertThat(newFractionalFee.numerator).isEqualTo(fractionalFee.numerator);
        assertThat(newFractionalFee.denominator).isEqualTo(fractionalFee.denominator);
        assertThat(newFractionalFee.minimumAmount).isEqualTo(fractionalFee.minimumAmount);
        assertThat(newFractionalFee.maximumAmount).isEqualTo(fractionalFee.maximumAmount);

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFixedAndFractionalFee() throws Exception {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFractionalFee = buildFractionalFee(newCollector);
        final var newFixedFee = createFixedFeeInHBAR(newCollector);

        final var updateFeesFunctionCall = contract.call_updateFungibleTokenCustomFeesAndGetExternal(
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

    /**
     * Verifies fixed fee can be applied to a nft that has fixed fee already set
     * @throws Exception
     */
    @Test
    void updateNonFungibleTokenFixedFeeInHbarAlreadyExisting() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());

        final var collector = accountEntityWithEvmAddressPersist();
        fixedFeeInHbarPersist(nft, collector, DEFAULT_FEE_AMOUNT.longValue());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFee = createFixedFeeInHBAR(newCollector);
        final var updateFeesFunctionCall = contract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(nft), List.of(newFee), List.of(), List.of());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(actualFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(actualFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    /**
     * Verifies fixed fee can be applied to a nft that does not have a previously set fixed fee
     * @throws Exception
     */
    @Test
    void updateNonFungibleTokenFixedFeeInHBAR() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var collector = accountEntityWithEvmAddressPersist();
        final var fee = createFixedFeeInHBAR(collector);
        final var updateFeesFunctionCall = contract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(nft), List.of(fee), List.of(), List.of());

        tokenAccountPersist(nft.getTokenId(), collector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(fee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(fee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(fee.useHbarsForPayment);
        assertThat(actualFixedFee.tokenId).isEqualTo(fee.tokenId);
        assertThat(actualFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(collector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    @Test
    void updateNonFungibleTokenFixedFeeInCustomToken() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountEntityWithEvmAddressPersist();
        final var newFixedFee = createFixedFeeInHBAR(newCollector);

        final var updateFeesFunctionCall = contract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
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

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    @Test
    void updateNonFungibleTokenRoyaltyFeeInCustomToken() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());
        final var tokenForPayment = fungibleTokenPersistWithTreasuryAccount(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var collector = accountEntityWithEvmAddressPersist();
        final var royaltyFee = createRoyaltyFeeInCustomToken(tokenForPayment, collector);

        final var updateFeesFunctionCall = contract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(nft), List.of(), List.of(), List.of(royaltyFee));

        tokenAccountPersist(tokenForPayment.getTokenId(), collector.getId());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualRoyaltyFee = updateFeesFunctionCallResult.component3().getFirst();

        assertThat(actualRoyaltyFee.amount).isEqualTo(royaltyFee.amount);
        assertThat(actualRoyaltyFee.useHbarsForPayment).isEqualTo(false);
        assertThat(actualRoyaltyFee.tokenId).isEqualTo(getTokenAddress(tokenForPayment));
        assertThat(actualRoyaltyFee.feeCollector).isEqualTo(getAddressFromEntityId(collector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    @Test
    void updateNonFungibleTokenRoyaltyFeeInHBAR() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var collector = accountEntityWithEvmAddressPersist();
        final var royaltyFee = createRoyaltyFeeInHBAR(collector);

        final var updateFeesFunctionCall = contract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(nft), List.of(), List.of(), List.of(royaltyFee));

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualRoyaltyFee = updateFeesFunctionCallResult.component3().getFirst();

        assertThat(actualRoyaltyFee.amount).isEqualTo(royaltyFee.amount);
        assertThat(actualRoyaltyFee.useHbarsForPayment).isEqualTo(true);
        assertThat(actualRoyaltyFee.tokenId).isEqualTo(Address.ZERO.toHexString());
        assertThat(actualRoyaltyFee.feeCollector).isEqualTo(getAddressFromEntityId(collector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    @Test
    void updateNonFungibleTokenFixedFeeAndRoyaltyFeeInHBAR() throws Exception {
        // Given
        final var nft = nonFungibleTokenPersistWithTreasury(
                accountEntityWithEvmAddressPersist().toEntityId());

        // When
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var collector = accountEntityWithEvmAddressPersist();
        final var fixedFee = createFixedFeeInHBAR(collector);
        final var royaltyFee = createRoyaltyFeeInHBAR(collector);

        final var updateFeesFunctionCall = contract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                getTokenAddress(nft), List.of(fixedFee), List.of(), List.of(royaltyFee));

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // Then
        final var actualFixedFee = updateFeesFunctionCallResult.component1().getFirst();
        final var actualRoyaltyFee = updateFeesFunctionCallResult.component3().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(fixedFee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(fixedFee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(true);
        assertThat(actualFixedFee.tokenId).isEqualTo(fixedFee.tokenId);
        assertThat(actualFixedFee.feeCollector).isEqualTo(getAddressFromEntityId(collector.toEntityId()));

        assertThat(actualRoyaltyFee.amount).isEqualTo(royaltyFee.amount);
        assertThat(actualRoyaltyFee.useHbarsForPayment).isEqualTo(true);
        assertThat(actualRoyaltyFee.tokenId).isEqualTo(Address.ZERO.toHexString());
        assertThat(actualRoyaltyFee.feeCollector).isEqualTo(getAddressFromEntityId(collector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, contract, ZERO_VALUE);
    }

    private FixedFee createFixedFeeInHBAR(Entity collectorAccount) {
        return new FixedFee(
                DEFAULT_FEE_AMOUNT.add(BigInteger.TEN),
                Address.ZERO.toHexString(),
                true,
                false,
                getAccountEvmAddress(collectorAccount));
    }

    /**
     * When the royalty fee is set to be payed in HBARs, the ZERO address should be set for the denominating token.
     * @param collectorAccount
     * @return
     */
    private RoyaltyFee createRoyaltyFeeInHBAR(Entity collectorAccount) {
        return new RoyaltyFee(
                DEFAULT_NUMERATOR_VALUE,
                DEFAULT_DENOMINATOR_VALUE,
                DEFAULT_FEE_AMOUNT,
                Address.ZERO.toHexString(),
                true,
                getAccountEvmAddress(collectorAccount));
    }

    private RoyaltyFee createRoyaltyFeeInCustomToken(Token token, Entity collectorAccount) {
        return new RoyaltyFee(
                DEFAULT_NUMERATOR_VALUE,
                DEFAULT_DENOMINATOR_VALUE,
                DEFAULT_FEE_AMOUNT,
                getTokenAddress(token),
                false,
                getAccountEvmAddress(collectorAccount));
    }

    private FixedFee buildFixedFeeInCustomToken(Token token, Entity collectorAccount) {
        return new FixedFee(
                DEFAULT_FEE_AMOUNT, getTokenAddress(token), false, false, getAccountEvmAddress(collectorAccount));
    }

    private FractionalFee buildFractionalFee(Entity collectorAccount) {
        return new FractionalFee(
                DEFAULT_NUMERATOR_VALUE,
                DEFAULT_DENOMINATOR_VALUE,
                DEFAULT_FEE_MIN_VALUE,
                DEFAULT_FEE_MIN_VALUE,
                false,
                getAccountEvmAddress(collectorAccount));
    }

    private org.hiero.mirror.common.domain.token.FixedFee getFixedFeeInHBAR(Entity collectorAccount, Long amount) {
        return org.hiero.mirror.common.domain.token.FixedFee.builder()
                .amount(amount)
                .collectorAccountId(collectorAccount.toEntityId())
                .build();
    }

    private org.hiero.mirror.common.domain.token.FixedFee fixedFeeInHbarPersist(
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
