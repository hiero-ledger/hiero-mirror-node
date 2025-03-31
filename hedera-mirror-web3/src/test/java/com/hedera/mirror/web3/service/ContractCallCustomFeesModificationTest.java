// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ZERO_VALUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FixedFee;
import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FractionalFee;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract;
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
    void updateFungibleTokenFixedFeeInHBAR() throws Exception {
        // 1.CREATE TOKEN WITH FIXED FEE IN HBAR
        final var token = fungibleTokenPersist(accountWithEvmAddressPersist().toEntityId());
        final var collector = accountWithEvmAddressPersist();
        final var fixedFee = fixedFeeInHbarPersist(token, collector, FIXED_FEE_AMOUNT);

        // 2.GET TOKEN FIXED FEES USING PRECOMPILED CONTRACT FUNCTION
        final var precompileTestContract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var getFeesFunctionCall = precompileTestContract.call_getCustomFeesForToken(getTokenAddress(token));
        final var getFeesFunctionCallResult = getFeesFunctionCall.send();

        // 3.VERIFY THE TOKEN FIXED FEES ARE AS EXPECTED
        compareFixedFeesInHBAR(fixedFee, getFeesFunctionCallResult.component1().getFirst());

        verifyEthCallAndEstimateGas(getFeesFunctionCall, precompileTestContract, ZERO_VALUE);

        // 4.UPDATE TOKEN FIXED FEES
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountWithEvmAddressPersist();
        final var newFee = createFixedFeeInHBAR(newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(newFee), List.of(), List.of());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // 5.VERIFY THE TOKEN FIXED FEE IS UPDATED AS EXPECTED
        final var updatedFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(updatedFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(updatedFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(updatedFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(updatedFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(updatedFixedFee.feeCollector)
                .as("Failed to update the fee collector")
                .isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFixedFeeInCustomToken() throws Exception {
        // 1.CREATE TOKEN WITH FIXED FEES IN CUSTOM TOKEN
        final var token = fungibleTokenPersist(accountWithEvmAddressPersist().toEntityId());
        final var collector = accountWithEvmAddressPersist();
        final var fixedFee = fixedFeeInCustomTokenPersist(token, collector, FIXED_FEE_AMOUNT);

        // 2.GET TOKEN FIXED FEES USING PRECOMPILED CONTRACT FUNCTION
        final var precompileTestContract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var getFeesFunctionCall = precompileTestContract.call_getCustomFeesForToken(getTokenAddress(token));
        final var getFeesFunctionCallResult = getFeesFunctionCall.send();

        // 3.VERIFY THE TOKEN FIXED FEES ARE AS EXPECTED
        compareFixedFeesInCustomToken(
                fixedFee, getFeesFunctionCallResult.component1().getFirst());

        verifyEthCallAndEstimateGas(getFeesFunctionCall, precompileTestContract, ZERO_VALUE);

        // 4.UPDATE TOKEN FIXED FEES
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountWithEvmAddressPersist();
        final var newFee = buildFixedFeeInCustomToken(token, newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(newFee), List.of(), List.of());

        tokenAccountPersist(token.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // 5.VERIFY THE TOKEN FIXED FEE IS UPDATED AS EXPECTED
        final var updatedFixedFee = updateFeesFunctionCallResult.component1().getFirst();
        assertThat(updatedFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(updatedFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(updatedFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(updatedFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(updatedFixedFee.feeCollector)
                .as("Failed to update the fee collector")
                .isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFractionalFee() throws Exception {
        // 1.CREATE TOKEN WITH FRACTIONAL FEE
        final var collector = accountWithEvmAddressPersist();
        final var token = fungibleTokenPersist(accountWithEvmAddressPersist().toEntityId());
        final var fractionalFee =
                fractionalFeePersist(token, collector, DENOMINATOR, MAX_AMOUNT, MIN_AMOUNT, NUMERATOR, true);

        // 2.GET CREATED TOKEN FRACTIONAL FEES USING PRECOMPILED CONTRACT FUNCTION
        final var precompileTestContract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var getFeesFunctionCall = precompileTestContract.call_getCustomFeesForToken(getTokenAddress(token));
        final var getFeesFunctionCallResult = getFeesFunctionCall.send();

        // 3.VERIFY THE TOKEN FRACTIONAL FEE IS AS EXPECTED
        compareFractionalFees(
                fractionalFee, getFeesFunctionCallResult.component2().getFirst());

        verifyEthCallAndEstimateGas(getFeesFunctionCall, precompileTestContract, ZERO_VALUE);

        // 4.UPDATE TOKEN FRACTIONAL FEE
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountWithEvmAddressPersist();
        final var newFee = buildFractionalFee(newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(), List.of(newFee), List.of());

        tokenAccountPersist(token.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // 5.VERIFY THE TOKEN FRACTIONAL FEES ARE UPDATED AS EXPECTED
        final var newFractionalFee = updateFeesFunctionCallResult.component2().getFirst();
        assertThat(newFractionalFee.feeCollector)
                .as("Failed to update the fee collector")
                .isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));
        assertThat(newFractionalFee.numerator).isEqualTo(newFee.numerator);
        assertThat(newFractionalFee.denominator).isEqualTo(newFee.denominator);
        assertThat(newFractionalFee.minimumAmount).isEqualTo(newFee.minimumAmount);
        assertThat(newFractionalFee.maximumAmount).isEqualTo(newFee.maximumAmount);

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateFungibleTokenFixedAndFractionalFeeCombination() throws Exception {
        // 1.CREATE TOKEN WITH FRACTIONAL AND FIXED FEES IN CUSTOM TOKEN
        final var token = fungibleTokenPersist(accountWithEvmAddressPersist().toEntityId());
        final var collector = accountWithEvmAddressPersist();
        final var fixedFee = getFixedFeeInCustomToken(token, collector, FIXED_FEE_AMOUNT);
        final var fractionalFee = getFractionalFee(DENOMINATOR, MAX_AMOUNT, MIN_AMOUNT, NUMERATOR, true, collector);
        fractionalAndFixedFeeInCustomTokenPersist(token, fixedFee, fractionalFee);

        // 2.GET TOKEN CUSTOM FEES USING PRECOMPILED CONTRACT FUNCTION
        final var precompileTestContract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var getFeesFunctionCall = precompileTestContract.call_getCustomFeesForToken(getTokenAddress(token));
        final var getFeesFunctionCallResult = getFeesFunctionCall.send();

        // 3.VERIFY THE TOKEN CUSTOM FEES ARE AS EXPECTED
        compareFixedFeesInCustomToken(
                fixedFee, getFeesFunctionCallResult.component1().getFirst());
        compareFractionalFees(
                fractionalFee, getFeesFunctionCallResult.component2().getFirst());

        // 4.UPDATE TOKEN CUSTOM FEES
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountWithEvmAddressPersist();
        final var newFractionalFee = buildFractionalFee(newCollector);
        final var newFixedFee = createFixedFeeInHBAR(newCollector);

        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(token), List.of(newFixedFee), List.of(newFractionalFee), List.of());

        tokenAccountPersist(token.getTokenId(), collector.getId());
        tokenAccountPersist(token.getTokenId(), newCollector.getId());

        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // 5.VERIFY THE TOKEN CUSTOM FEES ARE UPDATED AS EXPECTED
        final var actualFixedFees = updateFeesFunctionCallResult.component1().getFirst();
        assertThat(actualFixedFees.amount).isEqualTo(newFixedFee.amount);
        assertThat(actualFixedFees.useCurrentTokenForPayment).isEqualTo(newFixedFee.useCurrentTokenForPayment);
        assertThat(actualFixedFees.useHbarsForPayment).isEqualTo(newFixedFee.useHbarsForPayment);
        assertThat(actualFixedFees.tokenId).isEqualTo(newFixedFee.tokenId);
        assertThat(actualFixedFees.feeCollector)
                .as("Failed to update the fee collector")
                .isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        final var actualFractionalFees =
                updateFeesFunctionCallResult.component2().getFirst();
        assertThat(actualFractionalFees.feeCollector)
                .as("Failed to update the fee collector")
                .isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));
        assertThat(actualFractionalFees.numerator).isEqualTo(newFractionalFee.numerator);
        assertThat(actualFractionalFees.denominator).isEqualTo(newFractionalFee.denominator);
        assertThat(actualFractionalFees.minimumAmount).isEqualTo(newFractionalFee.minimumAmount);
        assertThat(actualFractionalFees.maximumAmount).isEqualTo(newFractionalFee.maximumAmount);
    }

    @Test
    void updateNonFungibleTokenFixedFeeInHBAR() throws Exception {
        // 1.CREATE NFT WITH FIXED FEE
        final var nft = nftPersist(accountWithEvmAddressPersist().toEntityId());
        final var collector = accountWithEvmAddressPersist();
        final var fixedFee = fixedFeeInHbarPersist(nft, collector, FIXED_FEE_AMOUNT);

        // 2.GET CREATED NFT CUSTOM FEES USING PRECOMPILED CONTRACT FUNCTION
        final var precompileTestContract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var getFeesFunctionCall = precompileTestContract.call_getCustomFeesForToken(getTokenAddress(nft));
        final var getFeesFunctionCallResult = getFeesFunctionCall.send();

        // 3.VERIFY THE NFT FIXED FEE IS AS EXPECTED
        compareFixedFeesInHBAR(fixedFee, getFeesFunctionCallResult.component1().getFirst());

        verifyEthCallAndEstimateGas(getFeesFunctionCall, precompileTestContract, ZERO_VALUE);

        // 4.UPDATE TOKEN FIXED FEE
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountWithEvmAddressPersist();
        final var newFee = createFixedFeeInHBAR(newCollector);
        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(nft), List.of(newFee), List.of(), List.of());

        tokenAccountPersist(nft.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // 5.VERIFY THE TOKEN FIXED FEE IS UPDATED AS EXPECTED
        final var actualFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(newFee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(newFee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(newFee.useHbarsForPayment);
        assertThat(actualFixedFee.tokenId).isEqualTo(newFee.tokenId);
        assertThat(actualFixedFee.feeCollector)
                .as("Failed to update the fee collector")
                .isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    @Test
    void updateNonFungibleTokenFixedFeeInCustomToken() throws Exception {
        // 1.CREATE NFT WITH FIXED FEE
        final var nft = nftPersist(accountWithEvmAddressPersist().toEntityId());
        final var collector = accountWithEvmAddressPersist();
        final var fixedFee = fixedFeeInCustomTokenPersist(nft, collector, FIXED_FEE_AMOUNT);

        // 2.GET CREATED NFT CUSTOM FEES USING PRECOMPILED CONTRACT FUNCTION
        final var precompileTestContract = testWeb3jService.deploy(PrecompileTestContract::deploy);
        final var getFeesFunctionCall = precompileTestContract.call_getCustomFeesForToken(getTokenAddress(nft));
        final var getFeesFunctionCallResult = getFeesFunctionCall.send();

        // 3.VERIFY THE NFT FIXED FEE IS AS EXPECTED
        compareFixedFeesInCustomToken(
                fixedFee, getFeesFunctionCallResult.component1().getFirst());

        verifyEthCallAndEstimateGas(getFeesFunctionCall, precompileTestContract, ZERO_VALUE);

        // 4.UPDATE TOKEN FIXED FEE
        final var modificationPrecompileTestContract =
                testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var newCollector = accountWithEvmAddressPersist();
        final var newFixedFee = createFixedFeeInHBAR(newCollector);

        final var updateFeesFunctionCall =
                modificationPrecompileTestContract.call_updateNonFungibleTokenCustomFeesAndGetExternal(
                        getTokenAddress(nft), List.of(newFixedFee), List.of(), List.of());

        tokenAccountPersist(nft.getTokenId(), newCollector.getId());
        final var updateFeesFunctionCallResult = updateFeesFunctionCall.send();

        // 5.VERIFY THE TOKEN FIXED FEE IS UPDATED AS EXPECTED
        final var actualFixedFee = updateFeesFunctionCallResult.component1().getFirst();

        assertThat(actualFixedFee.amount).isEqualTo(newFixedFee.amount);
        assertThat(actualFixedFee.useCurrentTokenForPayment).isEqualTo(newFixedFee.useCurrentTokenForPayment);
        assertThat(actualFixedFee.useHbarsForPayment).isEqualTo(newFixedFee.useHbarsForPayment);
        assertThat(actualFixedFee.tokenId).isEqualTo(newFixedFee.tokenId);
        assertThat(actualFixedFee.feeCollector)
                .as("Failed to update the fee collector")
                .isEqualTo(getAddressFromEntityId(newCollector.toEntityId()));

        verifyEthCallAndEstimateGas(updateFeesFunctionCall, modificationPrecompileTestContract, ZERO_VALUE);
    }

    private void compareFixedFeesInHBAR(
            com.hedera.mirror.common.domain.token.FixedFee expectedFixedFee,
            PrecompileTestContract.FixedFee actualFixedFee) {
        assertThat(actualFixedFee.feeCollector)
                .isEqualTo(getEvmAddressBytesFromEntity(getEntity(expectedFixedFee.getCollectorAccountId()))
                        .toHexString());
        assertThat(actualFixedFee.tokenId).isEqualTo(Address.ZERO.toHexString());
        assertThat(actualFixedFee.amount).isEqualTo(BigInteger.valueOf(expectedFixedFee.getAmount()));
        assertThat(actualFixedFee.useCurrentTokenForPayment).isFalse();
        assertThat(actualFixedFee.useHbarsForPayment).isTrue();
    }

    private void compareFixedFeesInCustomToken(
            com.hedera.mirror.common.domain.token.FixedFee expectedFixedFee,
            PrecompileTestContract.FixedFee actualFixedFee) {
        assertThat(actualFixedFee.feeCollector)
                .isEqualTo(getEvmAddressBytesFromEntity(getEntity(expectedFixedFee.getCollectorAccountId()))
                        .toHexString());
        assertThat(actualFixedFee.tokenId)
                .isEqualTo(getAddressFromEntity(getEntity(expectedFixedFee.getDenominatingTokenId())));
        assertThat(actualFixedFee.amount).isEqualTo(BigInteger.valueOf(expectedFixedFee.getAmount()));
        assertThat(actualFixedFee.useCurrentTokenForPayment).isFalse();
        assertThat(actualFixedFee.useHbarsForPayment).isFalse();
    }

    private void compareRoyaltyFees(
            com.hedera.mirror.common.domain.token.RoyaltyFee expectedFixedFee,
            PrecompileTestContract.RoyaltyFee actualFixedFee) {
        assertThat(actualFixedFee.feeCollector)
                .isEqualTo(getEvmAddressBytesFromEntity(getEntity(expectedFixedFee.getCollectorAccountId()))
                        .toHexString());
        assertThat(actualFixedFee.amount)
                .isEqualTo(BigInteger.valueOf(expectedFixedFee.getFallbackFee().getAmount()));
        assertThat(actualFixedFee.tokenId)
                .isEqualTo(
                        getAddressFromEntityId(expectedFixedFee.getFallbackFee().getDenominatingTokenId()));
        assertThat(actualFixedFee.numerator).isEqualTo(BigInteger.valueOf(expectedFixedFee.getNumerator()));
        assertThat(actualFixedFee.denominator).isEqualTo(BigInteger.valueOf(expectedFixedFee.getDenominator()));
    }

    private void compareFractionalFees(
            com.hedera.mirror.common.domain.token.FractionalFee expectedFixedFee,
            PrecompileTestContract.FractionalFee actualFixedFee) {
        assertThat(actualFixedFee.feeCollector)
                .isEqualTo(getEvmAddressBytesFromEntity(getEntity(expectedFixedFee.getCollectorAccountId()))
                        .toHexString());
        assertThat(actualFixedFee.numerator).isEqualTo(BigInteger.valueOf(expectedFixedFee.getNumerator()));
        assertThat(actualFixedFee.denominator).isEqualTo(BigInteger.valueOf(expectedFixedFee.getDenominator()));
        assertThat(actualFixedFee.minimumAmount).isEqualTo(BigInteger.valueOf(expectedFixedFee.getMinimumAmount()));
        assertThat(actualFixedFee.maximumAmount).isEqualTo(BigInteger.valueOf(expectedFixedFee.getMaximumAmount()));
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

    private com.hedera.mirror.common.domain.token.FixedFee getFixedFeeInCustomToken(
            Token token, Entity collectorAccount, Long amount) {
        return com.hedera.mirror.common.domain.token.FixedFee.builder()
                .amount(amount)
                .collectorAccountId(collectorAccount.toEntityId())
                .denominatingTokenId(EntityId.of(token.getTokenId()))
                .build();
    }

    private com.hedera.mirror.common.domain.token.FixedFee getFixedFeeInHBAR(Entity collectorAccount, Long amount) {
        return com.hedera.mirror.common.domain.token.FixedFee.builder()
                .amount(amount)
                .collectorAccountId(collectorAccount.toEntityId())
                .build();
    }

    private com.hedera.mirror.common.domain.token.FixedFee fixedFeeInCustomTokenPersist(
            Token token, Entity collectorAccount, Long amount) {
        final var fixedFee = getFixedFeeInCustomToken(token, collectorAccount, amount);

        domainBuilder
                .customFee()
                .customize(f -> f.entityId(token.getTokenId())
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of()))
                .persist();
        return fixedFee;
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

    private com.hedera.mirror.common.domain.token.FractionalFee getFractionalFee(
            Long denominator,
            Long maxAmount,
            Long minAmount,
            Long numerator,
            boolean netOfTransfers,
            Entity collectorAccount) {
        return com.hedera.mirror.common.domain.token.FractionalFee.builder()
                .denominator(denominator)
                .maximumAmount(maxAmount)
                .minimumAmount(minAmount)
                .numerator(numerator)
                .netOfTransfers(netOfTransfers)
                .collectorAccountId(collectorAccount.toEntityId())
                .build();
    }

    private com.hedera.mirror.common.domain.token.FractionalFee fractionalFeePersist(
            Token token,
            Entity collectorAccount,
            Long denominator,
            Long maxAmount,
            Long minAmount,
            Long numerator,
            boolean netOfTransfers) {
        final var fractionalFee =
                getFractionalFee(denominator, maxAmount, minAmount, numerator, netOfTransfers, collectorAccount);

        domainBuilder
                .customFee()
                .customize(f -> f.entityId(token.getTokenId())
                        .fixedFees(List.of())
                        .fractionalFees(List.of(fractionalFee))
                        .royaltyFees(List.of()))
                .persist();
        return fractionalFee;
    }

    private void fractionalAndFixedFeeInCustomTokenPersist(
            Token token,
            com.hedera.mirror.common.domain.token.FixedFee fixedFee,
            com.hedera.mirror.common.domain.token.FractionalFee fractionalFee) {
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(token.getTokenId())
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of(fractionalFee))
                        .royaltyFees(List.of()))
                .persist();
    }

    private ModificationPrecompileTestContract.RoyaltyFee createRoyaltyFee(
            Token token, long amount, long denominator, long numerator, Entity collector, boolean useHbarForPayment) {
        return new ModificationPrecompileTestContract.RoyaltyFee(
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator),
                BigInteger.valueOf(amount),
                token == null ? null : getTokenAddress(token),
                useHbarForPayment,
                getAddressFromEntityId(collector.toEntityId()));
    }

    private com.hedera.mirror.common.domain.token.RoyaltyFee getRoyaltyFee(
            Token token, long amount, long denominator, long numerator, Entity collector) {
        return RoyaltyFee.builder()
                .fallbackFee(FallbackFee.builder()
                        .amount(amount)
                        .denominatingTokenId(EntityId.of(token.getTokenId()))
                        .build())
                .denominator(denominator)
                .collectorAccountId(collector.toEntityId())
                .numerator(numerator)
                .allCollectorsAreExempt(true)
                .build();
    }

    private com.hedera.mirror.common.domain.token.RoyaltyFee getRoyaltyFeeHbar(
            long amount, long denominator, long numerator, Entity collector) {
        return RoyaltyFee.builder()
                .fallbackFee(FallbackFee.builder()
                        .amount(amount)
                        .denominatingTokenId(null)
                        //                        .denominatingTokenId(EntityId.of(token.getTokenId()))
                        .build())
                .denominator(denominator)
                .collectorAccountId(collector.toEntityId())
                .numerator(numerator)
                .allCollectorsAreExempt(true)
                .build();
    }

    private com.hedera.mirror.common.domain.token.RoyaltyFee royaltyFeePersist(
            Token nft,
            Token denominatingToken,
            Long amount,
            Long denominator,
            Long numerator,
            Entity collectorAccount) {
        final var royaltyFee = getRoyaltyFee(denominatingToken, amount, denominator, numerator, collectorAccount);

        domainBuilder
                .customFee()
                .customize(f -> f.entityId(nft.getTokenId())
                        .fixedFees(List.of())
                        .fractionalFees(List.of())
                        .royaltyFees(List.of(royaltyFee)))
                .persist();
        return royaltyFee;
    }

    private com.hedera.mirror.common.domain.token.RoyaltyFee royaltyFeePersistHbar(
            Token nft, Long amount, Long denominator, Long numerator, Entity collectorAccount) {
        final var royaltyFee = getRoyaltyFeeHbar(amount, denominator, numerator, collectorAccount);

        domainBuilder
                .customFee()
                .customize(f -> f.entityId(nft.getTokenId())
                        .fixedFees(List.of())
                        .fractionalFees(List.of())
                        .royaltyFees(List.of(royaltyFee)))
                .persist();
        return royaltyFee;
    }

    private void royaltyAndFixedFeePersist(
            Token token,
            com.hedera.mirror.common.domain.token.RoyaltyFee royaltyFee,
            com.hedera.mirror.common.domain.token.FixedFee fixedFee) {
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(token.getTokenId())
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of(royaltyFee)))
                .persist();
    }
}
