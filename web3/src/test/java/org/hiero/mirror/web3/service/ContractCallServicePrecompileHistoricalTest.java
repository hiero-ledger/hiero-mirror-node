// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static org.hiero.mirror.web3.service.AbstractContractCallServiceTest.KeyType.FEE_SCHEDULE_KEY;
import static org.hiero.mirror.web3.service.AbstractContractCallServiceTest.KeyType.FREEZE_KEY;
import static org.hiero.mirror.web3.service.AbstractContractCallServiceTest.KeyType.KYC_KEY;
import static org.hiero.mirror.web3.service.AbstractContractCallServiceTest.KeyType.PAUSE_KEY;
import static org.hiero.mirror.web3.service.AbstractContractCallServiceTest.KeyType.SUPPLY_KEY;
import static org.hiero.mirror.web3.service.AbstractContractCallServiceTest.KeyType.WIPE_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ECDSA_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ED25519_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ECDSA_TYPE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ED_25519_TYPE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.LEDGER_ID;

import com.google.common.collect.Range;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.base.utility.CommonUtils;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContractHistorical;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContractHistorical.FixedFee;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContractHistorical.FungibleTokenInfo;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContractHistorical.KeyValue;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContractHistorical.NonFungibleTokenInfo;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.tx.Contract;

class ContractCallServicePrecompileHistoricalTest extends AbstractContractCallServiceHistoricalTest {

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenFrozen(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = accountEntityPersistHistorical(historicalRange);
        final var token = fungibleTokenPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        tokenAccountFrozenRelationshipPersistHistorical(tokenId, account.getId(), historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isTokenFrozen(toAddress(tokenId).toHexString(), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenFrozenWithAlias(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var accountAndToken = accountTokenAndFrozenRelationshipPersistHistorical(historicalRange);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_isTokenFrozen(
                getAddressFromEntity(accountAndToken.getRight()), getAliasFromEntity(accountAndToken.getLeft()));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGranted(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = accountEntityPersistHistorical(historicalRange);
        final var token = fungibleTokenPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        tokenAccountFrozenRelationshipPersistHistorical(tokenId, account.getId(), historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGrantedWithAlias(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var token = fungibleTokenPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        tokenAccountFrozenRelationshipPersistHistorical(tokenId, account.getId(), historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGrantedForNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = accountEntityPersistHistorical(historicalRange);
        final var token = nftPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        tokenAccountFrozenRelationshipPersistHistorical(tokenId, account.getId(), historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGrantedForNFTWithAlias(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var token = nftPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        tokenAccountFrozenRelationshipPersistHistorical(tokenId, account.getId(), historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenAddress(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var token = fungibleTokenPersistHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isTokenAddress(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenAddressNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var token = nftPersistHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isTokenAddress(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getDefaultKycToken(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
        fungibleTokenCustomizable(t -> t.tokenId(tokenEntity.getId())
                .kycStatus(TokenKycStatusEnum.GRANTED)
                .timestampRange(historicalRange));

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultKyc(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getDefaultKycNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var token = nftPersistHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_getTokenDefaultKyc(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenType(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var token = fungibleTokenPersistHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_getType(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ZERO);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenTypeNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var token = nftPersistHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_getType(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ONE);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenDefaultFreeze(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
        fungibleTokenCustomizable(
                t -> t.tokenId(tokenEntity.getId()).freezeDefault(true).timestampRange(historicalRange));

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getNFTDefaultFreeze(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
        final var tokenId = tokenEntity.getId();
        nonFungibleTokenCustomizable(t -> t.tokenId(tokenId).freezeDefault(true).timestampRange(historicalRange));

        nftPersistCustomizable(n -> n.tokenId(tokenId).timestampRange(historicalRange));

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getCustomFeesForTokenWithFixedFee(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var token = fungibleTokenPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        final var collectorAccount = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var fixedFee = org.hiero.mirror.common.domain.token.FixedFee.builder()
                .amount(DEFAULT_FEE_AMOUNT.longValue())
                .collectorAccountId(collectorAccount.toEntityId())
                .denominatingTokenId(EntityId.of(tokenId))
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of())
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);
        final var tokenAddress = toAddress(tokenId).toHexString();

        // When
        final var functionCall = contract.call_getCustomFeesForToken(tokenAddress);

        final var expectedFee = new PrecompileTestContractHistorical.FixedFee(
                DEFAULT_FEE_AMOUNT,
                tokenAddress,
                false,
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component1().getFirst()).isEqualTo(expectedFee);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getCustomFeesForTokenWithFractionalFee(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var collectorAccount = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var token = fungibleTokenPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(collectorAccount.toEntityId())
                .denominator(DEFAULT_DENOMINATOR_VALUE.longValue())
                .minimumAmount(DEFAULT_FEE_MIN_VALUE.longValue())
                .maximumAmount(DEFAULT_FEE_MAX_VALUE.longValue())
                .netOfTransfers(true)
                .numerator(DEFAULT_NUMERATOR_VALUE.longValue())
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .fractionalFees(List.of(fractionalFee))
                        .fixedFees(List.of())
                        .royaltyFees(List.of())
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_getCustomFeesForToken(toAddress(tokenId).toHexString());

        final var expectedFee = new PrecompileTestContractHistorical.FractionalFee(
                DEFAULT_NUMERATOR_VALUE,
                DEFAULT_DENOMINATOR_VALUE,
                DEFAULT_FEE_MIN_VALUE,
                DEFAULT_FEE_MAX_VALUE,
                true,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component2().getFirst()).isEqualTo(expectedFee);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getCustomFeesForTokenWithRoyaltyFee(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var collectorAccount = accountEntityPersistWithEvmAddressHistorical(historicalRange);

        final var token = fungibleTokenPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();
        final var tokenEntityId = EntityId.of(tokenId);

        final var royaltyFee = RoyaltyFee.builder()
                .collectorAccountId(collectorAccount.toEntityId())
                .denominator(DEFAULT_DENOMINATOR_VALUE.longValue())
                .fallbackFee(FallbackFee.builder()
                        .amount(DEFAULT_FEE_AMOUNT.longValue())
                        .denominatingTokenId(tokenEntityId)
                        .build())
                .numerator(DEFAULT_NUMERATOR_VALUE.longValue())
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .royaltyFees(List.of(royaltyFee))
                        .fixedFees(List.of())
                        .fractionalFees(List.of())
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_getCustomFeesForToken(toAddress(tokenId).toHexString());

        final var expectedFee = new PrecompileTestContractHistorical.RoyaltyFee(
                DEFAULT_NUMERATOR_VALUE,
                DEFAULT_DENOMINATOR_VALUE,
                DEFAULT_FEE_AMOUNT,
                CommonUtils.hex(toEvmAddress(tokenEntityId)),
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component3().getFirst()).isEqualTo(expectedFee);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getExpiryForToken(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var expiryPeriod = 9999999999999L;
        final var autoRenewExpiry = 100000000L;
        final var autoRenewAccount = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN)
                        .autoRenewAccountId(autoRenewAccount.getId())
                        .expirationTimestamp(expiryPeriod)
                        .timestampRange(historicalRange)
                        .autoRenewPeriod(autoRenewExpiry))
                .persist();
        fungibleTokenPersistHistoricalCustomizable(historicalRange, t -> t.tokenId(tokenEntity.getId()));
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getExpiryInfoForToken(getAddressFromEntity(tokenEntity));

        final var expectedExpiry = new PrecompileTestContractHistorical.Expiry(
                BigInteger.valueOf(expiryPeriod).divide(BigInteger.valueOf(1_000_000_000L)),
                mirrorNodeEvmProperties.isModularizedServices()
                        ? getAddressFromEntity(autoRenewAccount)
                        : Address.fromHexString(Bytes.wrap(autoRenewAccount.getEvmAddress())
                                        .toHexString())
                                .toHexString(),
                BigInteger.valueOf(autoRenewExpiry));

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedExpiry);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getApproved(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var approvedAccount = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
        final var tokenId = tokenEntity.getId();
        nonFungibleTokenCustomizable(t -> t.tokenId(tokenId).freezeDefault(true).timestampRange(historicalRange));
        nftPersistCustomizable(
                n -> n.tokenId(tokenId).timestampRange(historicalRange).spender(approvedAccount.getId()));

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsGetApproved(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER);
        final var result = functionCall.send();

        // Then
        assertThat(result).isEqualTo(getAliasFromEntity(approvedAccount));
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getAllowanceForToken(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var token = fungibleTokenPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();

        tokenAllowancePersistCustomizable(a -> a.tokenId(tokenId)
                .owner(owner.getId())
                .spender(spender.getId())
                .amount(DEFAULT_AMOUNT_GRANTED)
                .amountGranted(DEFAULT_AMOUNT_GRANTED)
                .timestampRange(historicalRange));

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsAllowance(
                toAddress(tokenId).toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isApprovedForAllNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var token = nftPersistHistorical(historicalRange);
        final var tokenId = token.getTokenId();

        nftAllowancePersistCustomizable(a -> a.tokenId(tokenId)
                .owner(owner.getId())
                .spender(spender.getId())
                .timestampRange(historicalRange)
                .approvedForAll(true));

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsIsApprovedForAll(
                toAddress(tokenId).toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(Boolean.TRUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getFungibleTokenInfo(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);

        final var autoRenewAccount = accountEntityPersistHistorical(historicalRange);
        final var tokenEntity = tokenEntityPersistHistoricalCustomizable(
                historicalRange, e -> e.autoRenewAccountId(autoRenewAccount.getId()));
        final var treasury =
                accountPersistWithBalanceHistorical(DEFAULT_TOKEN_SUPPLY, tokenEntity.toEntityId(), historicalRange);
        final var feeCollector = accountEntityPersistWithEvmAddressHistorical(historicalRange);

        final var token = fungibleTokenCustomizable(t -> t.tokenId(tokenEntity.getId())
                .treasuryAccountId(treasury.toEntityId())
                .timestampRange(historicalRange)
                .totalSupply(DEFAULT_TOKEN_SUPPLY));

        final var customFees = customFeesWithFeeCollectorPersistHistorical(
                feeCollector.toEntityId(), tokenEntity.toEntityId(), TokenTypeEnum.FUNGIBLE_COMMON, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForFungibleToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury, autoRenewAccount);

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContractHistorical.FractionalFee>();
        fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));

        final var royaltyFees = new ArrayList<PrecompileTestContractHistorical.RoyaltyFee>();

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, fractionalFees, royaltyFees);

        final var expectedFungibleTokenInfo =
                new FungibleTokenInfo(expectedTokenInfo, BigInteger.valueOf(token.getDecimals()));

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedFungibleTokenInfo);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getNonFungibleTokenInfo(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);

        final var owner = accountEntityPersistHistorical(historicalRange);
        final var treasury = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var feeCollector = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var autoRenewAccount = accountEntityPersistHistorical(historicalRange);
        final var tokenEntity = tokenEntityPersistHistoricalCustomizable(
                historicalRange, e -> e.autoRenewAccountId(autoRenewAccount.getId()));
        final var token = nonFungibleTokenCustomizable(t -> t.tokenId(tokenEntity.getId())
                .treasuryAccountId(treasury.toEntityId())
                .timestampRange(historicalRange)
                .createdTimestamp(historicalRange.lowerEndpoint())
                .totalSupply(1L));

        final var nft = nftPersistCustomizable(n -> n.tokenId(tokenEntity.getId())
                .accountId(owner.toEntityId())
                .timestampRange(historicalRange)
                .createdTimestamp(historicalRange.lowerEndpoint()));

        final var customFees = customFeesWithFeeCollectorPersistHistorical(
                feeCollector.toEntityId(),
                tokenEntity.toEntityId(),
                TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForNonFungibleToken(
                        getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER)
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury, autoRenewAccount);

        final var fixedFees = new ArrayList<PrecompileTestContractHistorical.FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContractHistorical.FractionalFee>();

        final var royaltyFees = new ArrayList<PrecompileTestContractHistorical.RoyaltyFee>();
        royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, fractionalFees, royaltyFees);

        final var expectedNonFungibleTokenInfo = new NonFungibleTokenInfo(
                expectedTokenInfo,
                BigInteger.valueOf(nft.getSerialNumber()),
                getAddressFromEntityId(owner.toEntityId()),
                BigInteger.valueOf(token.getCreatedTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                nft.getMetadata(),
                Address.ZERO.toHexString());

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedNonFungibleTokenInfo);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenInfoFungible(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);

        final var autoRenewAccount = accountEntityPersistHistorical(historicalRange);
        final var tokenEntity = tokenEntityPersistHistoricalCustomizable(
                historicalRange, e -> e.autoRenewAccountId(autoRenewAccount.getId()));
        final var treasury =
                accountPersistWithBalanceHistorical(DEFAULT_TOKEN_SUPPLY, tokenEntity.toEntityId(), historicalRange);
        final var feeCollector = accountEntityPersistWithEvmAddressHistorical(historicalRange);

        final var token = fungibleTokenCustomizable(t -> t.tokenId(tokenEntity.getId())
                .treasuryAccountId(treasury.toEntityId())
                .timestampRange(historicalRange)
                .totalSupply(DEFAULT_TOKEN_SUPPLY));

        final var customFees = customFeesWithFeeCollectorPersistHistorical(
                feeCollector.toEntityId(), tokenEntity.toEntityId(), TokenTypeEnum.FUNGIBLE_COMMON, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury, autoRenewAccount);

        final var fixedFees = new ArrayList<PrecompileTestContractHistorical.FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContractHistorical.FractionalFee>();
        fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, fractionalFees, Collections.emptyList());

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedTokenInfo);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenInfoNonFungible(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var treasury = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var feeCollector = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var autoRenewAccount = accountEntityPersistHistorical(historicalRange);
        final var tokenEntity = tokenEntityPersistHistoricalCustomizable(
                historicalRange, e -> e.autoRenewAccountId(autoRenewAccount.getId()));
        final var token = nonFungibleTokenCustomizable(t -> t.tokenId(tokenEntity.getId())
                .treasuryAccountId(treasury.toEntityId())
                .timestampRange(historicalRange)
                .createdTimestamp(historicalRange.lowerEndpoint())
                .totalSupply(1L));

        nftPersistCustomizable(n -> n.tokenId(tokenEntity.getId())
                .timestampRange(historicalRange)
                .createdTimestamp(historicalRange.lowerEndpoint()));

        final var customFees = customFeesWithFeeCollectorPersistHistorical(
                feeCollector.toEntityId(),
                tokenEntity.toEntityId(),
                TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury, autoRenewAccount);

        final var fixedFees = new ArrayList<PrecompileTestContractHistorical.FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var royaltyFees = new ArrayList<PrecompileTestContractHistorical.RoyaltyFee>();
        royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, Collections.emptyList(), royaltyFees);

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedTokenInfo);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, KYC_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, KYC_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, WIPE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, WIPE_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, PAUSE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, ED25519, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, ED25519, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, WIPE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, WIPE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, ED25519, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, ED25519, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, PAUSE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, WIPE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, WIPE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, PAUSE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, KYC_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, KYC_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 49
                            """)
    void getTokenKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final AbstractContractCallServiceTest.KeyType keyType,
            final long blockNumber)
            throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        final var tokenEntity = getTokenWithKey(tokenType, keyValueType, keyType, contract, historicalRange);

        // When
        final var functionCall =
                contract.call_getTokenKeyPublic(getAddressFromEntity(tokenEntity), keyType.getKeyTypeNumeric());

        final var expectedKey = getKeyValueForType(keyValueType, contract.getContractAddress());

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedKey);
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void evmPrecompileReadOnlyTokenFunctionsEthCallHistoricalNotExistingBlockTest(final long blockNumber) {
        testWeb3jService.setUseContractCallDeploy(true);
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        assertThatThrownBy(() -> testWeb3jService.deploy(PrecompileTestContractHistorical::deploy))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(UNKNOWN_BLOCK_NUMBER);
    }

    private Entity getTokenWithKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final AbstractContractCallServiceTest.KeyType keyType,
            final Contract contract,
            final Range<Long> historicalRange) {
        final Key key =
                switch (keyValueType) {
                    case ECDSA_SECPK256K1 -> KEY_WITH_ECDSA_TYPE;
                    case ED25519 -> KEY_WITH_ED_25519_TYPE;
                    case CONTRACT_ID -> getKeyWithContractId(contract);
                    case DELEGATABLE_CONTRACT_ID -> getKeyWithDelegatableContractId(contract);
                    default -> throw new IllegalArgumentException("Invalid key type");
                };

        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).key(key.toByteArray()).timestampRange(historicalRange))
                .persist();
        final var tokenBuilder = domainBuilder.token().customize(t -> t.tokenId(tokenEntity.getId())
                .type(tokenType));

        switch (keyType) {
            case ADMIN_KEY:
                break;
            case KYC_KEY:
                tokenBuilder.customize(t -> t.kycKey(key.toByteArray()));
                break;
            case FREEZE_KEY:
                tokenBuilder.customize(t -> t.freezeKey(key.toByteArray()));
                break;
            case WIPE_KEY:
                tokenBuilder.customize(t -> t.wipeKey(key.toByteArray()));
                break;
            case SUPPLY_KEY:
                tokenBuilder.customize(t -> t.supplyKey(key.toByteArray()));
                break;
            case FEE_SCHEDULE_KEY:
                tokenBuilder.customize(t -> t.feeScheduleKey(key.toByteArray()));
                break;
            case PAUSE_KEY:
                tokenBuilder.customize(t -> t.pauseKey(key.toByteArray()));
                break;
            default:
                throw new IllegalArgumentException("Invalid key type");
        }

        tokenBuilder.persist();
        return tokenEntity;
    }

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, final String contractAddress) {
        return switch (keyValueType) {
            case CONTRACT_ID ->
                new KeyValue(Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 ->
                new KeyValue(
                        Boolean.FALSE,
                        Address.ZERO.toHexString(),
                        ED25519_KEY,
                        new byte[0],
                        Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 ->
                new KeyValue(
                        Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], ECDSA_KEY, Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID ->
                new KeyValue(Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    private List<PrecompileTestContractHistorical.TokenKey> getExpectedTokenKeys(
            final Entity tokenEntity, final Token token) {
        final var expectedTokenKeys = new ArrayList<PrecompileTestContractHistorical.TokenKey>();
        expectedTokenKeys.add(
                new PrecompileTestContractHistorical.TokenKey(BigInteger.ONE, getKeyValue(tokenEntity.getKey())));
        expectedTokenKeys.add(
                new PrecompileTestContractHistorical.TokenKey(KYC_KEY.keyTypeNumeric, getKeyValue(token.getKycKey())));
        expectedTokenKeys.add(new PrecompileTestContractHistorical.TokenKey(
                FREEZE_KEY.keyTypeNumeric, getKeyValue(token.getFreezeKey())));
        expectedTokenKeys.add(new PrecompileTestContractHistorical.TokenKey(
                WIPE_KEY.keyTypeNumeric, getKeyValue(token.getWipeKey())));
        expectedTokenKeys.add(new PrecompileTestContractHistorical.TokenKey(
                SUPPLY_KEY.keyTypeNumeric, getKeyValue(token.getSupplyKey())));
        expectedTokenKeys.add(new PrecompileTestContractHistorical.TokenKey(
                FEE_SCHEDULE_KEY.keyTypeNumeric, getKeyValue(token.getFeeScheduleKey())));
        expectedTokenKeys.add(new PrecompileTestContractHistorical.TokenKey(
                PAUSE_KEY.keyTypeNumeric, getKeyValue(token.getPauseKey())));

        return expectedTokenKeys;
    }

    private PrecompileTestContractHistorical.FixedFee getFixedFee(
            final org.hiero.mirror.common.domain.token.FixedFee fixedFee, final Entity feeCollector) {
        return new PrecompileTestContractHistorical.FixedFee(
                BigInteger.valueOf(fixedFee.getAmount()),
                getAddressFromEntityId(fixedFee.getDenominatingTokenId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContractHistorical.FractionalFee getFractionalFee(
            final FractionalFee fractionalFee, final Entity feeCollector) {
        return new PrecompileTestContractHistorical.FractionalFee(
                BigInteger.valueOf(fractionalFee.getNumerator()),
                BigInteger.valueOf(fractionalFee.getDenominator()),
                BigInteger.valueOf(fractionalFee.getMinimumAmount()),
                BigInteger.valueOf(fractionalFee.getMaximumAmount()),
                true,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContractHistorical.RoyaltyFee getRoyaltyFee(
            final RoyaltyFee royaltyFee, final Entity feeCollector) {
        return new PrecompileTestContractHistorical.RoyaltyFee(
                BigInteger.valueOf(royaltyFee.getNumerator()),
                BigInteger.valueOf(royaltyFee.getDenominator()),
                BigInteger.valueOf(royaltyFee.getFallbackFee().getAmount()),
                getAddressFromEntityId(royaltyFee.getFallbackFee().getDenominatingTokenId()),
                false,
                getAddressFromEvmAddress(feeCollector.getEvmAddress()));
    }

    private PrecompileTestContractHistorical.KeyValue getKeyValue(final byte[] serializedKey) {
        try {
            final var key = Key.parseFrom(serializedKey);
            return new PrecompileTestContractHistorical.KeyValue(
                    false,
                    key.getContractID().hasContractNum()
                            ? EntityIdUtils.asTypedEvmAddress(key.getContractID())
                                    .toHexString()
                            : Address.ZERO.toHexString(),
                    key.getEd25519().toByteArray(),
                    key.getECDSASecp256K1().toByteArray(),
                    key.getDelegatableContractId().hasContractNum()
                            ? EntityIdUtils.asTypedEvmAddress(key.getDelegatableContractId())
                                    .toHexString()
                            : Address.ZERO.toHexString());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse key", e);
        }
    }

    private Entity accountPersistWithBalanceHistorical(
            final long balance, final EntityId token, final Range<Long> timestampRange) {
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.balance(balance)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), treasuryEntity.toEntityId()))
                        .balance(treasuryEntity.getBalance()))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), entity.toEntityId()))
                        .balance(balance))
                .persist();

        domainBuilder
                .tokenBalance()
                .customize(ab -> ab.id(new TokenBalance.Id(entity.getCreatedTimestamp(), entity.toEntityId(), token))
                        .balance(balance))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(ab -> ab.id(new TokenTransfer.Id(entity.getCreatedTimestamp(), entity.toEntityId(), token))
                        .amount(100))
                .persist();
        return entity;
    }

    private PrecompileTestContractHistorical.HederaToken createExpectedHederaToken(
            final Entity tokenEntity, final Token token, final Entity treasury, final Entity autoRenewAccount) {
        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new PrecompileTestContractHistorical.Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                getAddressFromEntity(autoRenewAccount),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        return new PrecompileTestContractHistorical.HederaToken(
                token.getName(),
                token.getSymbol(),
                mirrorNodeEvmProperties.isModularizedServices()
                        ? getAddressFromEntityId(treasury.toEntityId())
                        : getAddressFromEvmAddress(treasury.getEvmAddress()),
                tokenEntity.getMemo(),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                expectedTokenKeys,
                expectedExpiry);
    }

    private PrecompileTestContractHistorical.TokenInfo createExpectedTokenInfo(
            PrecompileTestContractHistorical.HederaToken expectedHederaToken,
            Token token,
            Entity tokenEntity,
            List<PrecompileTestContractHistorical.FixedFee> fixedFees,
            List<PrecompileTestContractHistorical.FractionalFee> fractionalFees,
            List<PrecompileTestContractHistorical.RoyaltyFee> royaltyFees) {
        return new PrecompileTestContractHistorical.TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
    }
}
