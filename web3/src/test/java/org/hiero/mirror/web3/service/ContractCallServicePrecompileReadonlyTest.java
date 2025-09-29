// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.services.utils.EntityIdUtils.asHexedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ECDSA_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ED25519_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ECDSA_TYPE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ED_25519_TYPE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.LEDGER_ID;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ZERO_VALUE;
import static org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.Expiry;
import static org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.HederaToken;
import static org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.TokenKey;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.evm.exception.PrecompileNotSupportedException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContract;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.FixedFee;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.FungibleTokenInfo;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.KeyValue;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.NonFungibleTokenInfo;
import org.hiero.mirror.web3.web3j.generated.PrecompileTestContract.TokenInfo;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

class ContractCallServicePrecompileReadonlyTest extends AbstractContractCallServiceOpcodeTracerTest {

    @Test
    void unsupportedPrecompileFails() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_callMissingPrecompile();

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var result = functionCall.send();
            assertThat(result.component1()).isTrue();
            assertThat(result.component2()).isEmpty();
        } else {
            assertThatThrownBy(functionCall::send).isInstanceOf(PrecompileNotSupportedException.class);
        }
    }

    // Temporary test until we start supporting this precompile
    @Test
    void hrcIsAssociatedFails() throws Exception {
        // Given
        final var token = fungibleTokenPersist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_hrcIsAssociated(asHexedEvmAddress(token.getTokenId()));

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(functionCall.send()).isFalse();
        } else {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(PrecompileNotSupportedException.class)
                    .hasMessage("HRC isAssociated() precompile is not supported.");
        }
    }

    @Test
    void isTokenFrozen() throws Exception {
        // Given
        final var account = accountEntityPersist();
        final var token = fungibleTokenCustomizable(t -> t.freezeDefault(true));
        final var tokenId = token.getTokenId();
        tokenAccount(t -> t.tokenId(tokenId).accountId(account.getId()).freezeStatus(TokenFreezeStatusEnum.FROZEN));

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isTokenFrozen(toAddress(tokenId).toHexString(), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isTokenFrozenWithAlias() throws Exception {
        // Given
        final var account = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenCustomizable(t -> t.freezeDefault(true));
        final var tokenId = token.getTokenId();
        tokenAccount(t -> t.tokenId(tokenId).accountId(account.getId()).freezeStatus(TokenFreezeStatusEnum.FROZEN));

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isTokenFrozen(toAddress(tokenId).toHexString(), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isKycGranted() throws Exception {
        // Given
        final var account = accountEntityPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, account.getId());

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isKycGrantedWithAlias() throws Exception {
        // Given
        final var account = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, account.getId());

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isKycGrantedForNFT() throws Exception {
        // Given
        final var account = accountEntityPersist();
        final var token = nftPersist();
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, account.getId());

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isKycGrantedForNFTWithAlias() throws Exception {
        // Given
        final var account = accountEntityWithEvmAddressPersist();
        final var token = nftPersist();
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, account.getId());

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(toAddress(tokenId).toHexString(), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isTokenAddress() throws Exception {
        // Given
        final var token = fungibleTokenPersist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_isTokenAddress(asHexedEvmAddress(token.getTokenId()));

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isTokenAddressNFT() throws Exception {
        // Given
        final var token = nftPersist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_isTokenAddress(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getDefaultKycToken() throws Exception {
        // Given
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        final var token = fungibleTokenCustomizable(t -> t.kycStatus(TokenKycStatusEnum.GRANTED));
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getTokenDefaultKyc(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getDefaultKycNFT() throws Exception {
        // Given
        final var token = nonFungibleTokenCustomizable(t -> t.kycStatus(TokenKycStatusEnum.GRANTED));
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId));
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getTokenDefaultKyc(toAddress(tokenId).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getTokenType() throws Exception {
        // Given
        final var token = fungibleTokenPersist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getType(asHexedEvmAddress(token.getTokenId()));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ZERO);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getTokenTypeNFT() throws Exception {
        // Given
        final var token = nftPersist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getType(toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ONE);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getTokenDefaultFreeze() throws Exception {
        // Given
        final var token = fungibleTokenCustomizable(t -> t.freezeDefault(true));

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultFreeze(
                toAddress(token.getTokenId()).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getNFTDefaultFreeze() throws Exception {
        // Given
        final var token = nonFungibleTokenCustomizable(t -> t.freezeDefault(true));
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId));

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getTokenDefaultFreeze(toAddress(tokenId).toHexString());

        // Then
        assertThat(functionCall.send()).isTrue();

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, ADMIN_KEY
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, KYC_KEY
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FREEZE_KEY
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, WIPE_KEY
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, SUPPLY_KEY
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, PAUSE_KEY
                                FUNGIBLE_COMMON, ED25519, ADMIN_KEY
                                FUNGIBLE_COMMON, ED25519, FREEZE_KEY
                                FUNGIBLE_COMMON, ED25519, WIPE_KEY
                                FUNGIBLE_COMMON, ED25519, SUPPLY_KEY
                                FUNGIBLE_COMMON, ED25519, FEE_SCHEDULE_KEY
                                FUNGIBLE_COMMON, ED25519, PAUSE_KEY
                                FUNGIBLE_COMMON, CONTRACT_ID, ADMIN_KEY
                                FUNGIBLE_COMMON, CONTRACT_ID, FREEZE_KEY
                                FUNGIBLE_COMMON, CONTRACT_ID, WIPE_KEY
                                FUNGIBLE_COMMON, CONTRACT_ID, SUPPLY_KEY
                                FUNGIBLE_COMMON, CONTRACT_ID, FEE_SCHEDULE_KEY
                                FUNGIBLE_COMMON, CONTRACT_ID, PAUSE_KEY
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, ADMIN_KEY
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FREEZE_KEY
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, WIPE_KEY
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, PAUSE_KEY
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, ADMIN_KEY
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, KYC_KEY
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FREEZE_KEY
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, WIPE_KEY
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, SUPPLY_KEY
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, PAUSE_KEY
                                NON_FUNGIBLE_UNIQUE, ED25519, ADMIN_KEY
                                NON_FUNGIBLE_UNIQUE, ED25519, FREEZE_KEY
                                NON_FUNGIBLE_UNIQUE, ED25519, WIPE_KEY
                                NON_FUNGIBLE_UNIQUE, ED25519, SUPPLY_KEY
                                NON_FUNGIBLE_UNIQUE, ED25519, FEE_SCHEDULE_KEY
                                NON_FUNGIBLE_UNIQUE, ED25519, PAUSE_KEY
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, ADMIN_KEY
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FREEZE_KEY
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, WIPE_KEY
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, SUPPLY_KEY
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FEE_SCHEDULE_KEY
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, PAUSE_KEY
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, ADMIN_KEY
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FREEZE_KEY
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, WIPE_KEY
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, PAUSE_KEY
                            """)
    void getTokenKey(final TokenTypeEnum tokenType, final KeyValueType keyValueType, final KeyType keyType)
            throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        final var tokenEntity = getTokenWithKey(tokenType, keyValueType, keyType, contract);

        // When
        final var functionCall =
                contract.call_getTokenKeyPublic(getAddressFromEntity(tokenEntity), keyType.getKeyTypeNumeric());

        final var expectedKey = getKeyValueForType(keyValueType, contract.getContractAddress());

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedKey);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getCustomFeesForTokenWithFixedFee() throws Exception {
        // Given
        final var collectorAccount = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var entityId = EntityId.of(tokenId);
        final var fixedFee = org.hiero.mirror.common.domain.token.FixedFee.builder()
                .amount(DEFAULT_FEE_AMOUNT.longValue())
                .collectorAccountId(collectorAccount.toEntityId())
                .denominatingTokenId(entityId)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of()))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getCustomFeesForToken(toAddress(tokenId).toHexString());

        final var expectedFee = new FixedFee(
                DEFAULT_FEE_AMOUNT,
                toAddress(tokenId).toHexString(),
                false,
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component1().getFirst()).isEqualTo(expectedFee);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getCustomFeesForTokenWithFractionalFee() throws Exception {
        // Given
        final var collectorAccount = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
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
                        .royaltyFees(List.of()))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getCustomFeesForToken(toAddress(tokenId).toHexString());

        final var expectedFee = new PrecompileTestContract.FractionalFee(
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

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getCustomFeesForTokenWithRoyaltyFee() throws Exception {
        // Given
        final var collectorAccount = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var entityId = EntityId.of(tokenId);

        final var royaltyFee = RoyaltyFee.builder()
                .collectorAccountId(collectorAccount.toEntityId())
                .denominator(DEFAULT_DENOMINATOR_VALUE.longValue())
                .fallbackFee(FallbackFee.builder()
                        .amount(DEFAULT_FEE_AMOUNT.longValue())
                        .denominatingTokenId(entityId)
                        .build())
                .numerator(DEFAULT_NUMERATOR_VALUE.longValue())
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.entityId(tokenId)
                        .royaltyFees(List.of(royaltyFee))
                        .fixedFees(List.of())
                        .fractionalFees(List.of()))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_getCustomFeesForToken(toAddress(tokenId).toHexString());

        final var expectedFee = new PrecompileTestContract.RoyaltyFee(
                DEFAULT_NUMERATOR_VALUE,
                DEFAULT_DENOMINATOR_VALUE,
                DEFAULT_FEE_AMOUNT,
                EntityIdUtils.asHexedEvmAddress(new Id(entityId.getShard(), entityId.getRealm(), entityId.getNum())),
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component3().getFirst()).isEqualTo(expectedFee);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getExpiryForToken() throws Exception {
        // Given
        final var expiryPeriod = 9999999999999L;
        final var autoRenewExpiry = 100000000L;
        final var autoRenewAccount = accountEntityWithEvmAddressPersist();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN)
                        .autoRenewAccountId(autoRenewAccount.getId())
                        .expirationTimestamp(expiryPeriod)
                        .autoRenewPeriod(autoRenewExpiry))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getExpiryInfoForToken(getAddressFromEntity(tokenEntity));

        final var expectedExpiry = new Expiry(
                BigInteger.valueOf(expiryPeriod).divide(BigInteger.valueOf(1_000_000_000L)),
                mirrorNodeEvmProperties.isModularizedServices()
                        ? getAddressFromEntity(autoRenewAccount)
                        : Address.fromHexString(Bytes.wrap(autoRenewAccount.getEvmAddress())
                                        .toHexString())
                                .toHexString(),
                BigInteger.valueOf(autoRenewExpiry));

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedExpiry);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getAllowanceForToken() throws Exception {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var spender = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();

        tokenAllowancePersist(spender.getId(), owner.getId(), tokenId);
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_htsAllowance(
                toAddress(tokenId).toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void isApprovedForAllNFT() throws Exception {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var spender = accountEntityWithEvmAddressPersist();
        final var token = nftPersist();
        final var tokenId = token.getTokenId();
        nftAllowancePersist(tokenId, spender.getId(), owner.toEntityId());

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_htsIsApprovedForAll(
                toAddress(tokenId).toHexString(), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(Boolean.TRUE);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getFungibleTokenInfo() throws Exception {
        // Given
        final var treasury = accountEntityWithEvmAddressPersist();
        final var feeCollector = accountEntityWithEvmAddressPersist();
        final var autoRenewAccount = accountEntityPersist();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();

        final var customFees =
                persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, TokenTypeEnum.FUNGIBLE_COMMON);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForFungibleToken(getAddressFromEntity(tokenEntity));

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                getAddressFromEntity(autoRenewAccount),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new HederaToken(
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

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();
        fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
        final var expectedFungibleTokenInfo =
                new FungibleTokenInfo(expectedTokenInfo, BigInteger.valueOf(token.getDecimals()));

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedFungibleTokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getFungibleTokenInfoWithEmptyAutoRenew() throws Exception {
        // Given
        final var treasury = accountEntityWithEvmAddressPersist();
        final var feeCollector = accountEntityWithEvmAddressPersist();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(null))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();

        final var customFees =
                persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, TokenTypeEnum.FUNGIBLE_COMMON);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForFungibleToken(getAddressFromEntity(tokenEntity));

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.ZERO.toHexString(),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new HederaToken(
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

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();
        fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
        final var expectedFungibleTokenInfo =
                new FungibleTokenInfo(expectedTokenInfo, BigInteger.valueOf(token.getDecimals()));

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedFungibleTokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getNonFungibleTokenInfo() throws Exception {
        // Given
        final var owner = accountEntityPersist();
        final var treasury = accountEntityWithEvmAddressPersist();
        final var feeCollector = accountEntityWithEvmAddressPersist();
        final var autoRenewAccount = accountEntityPersist();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        final var nft = domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .spender(null)
                        .accountId(owner.toEntityId()))
                .persist();
        final var customFees =
                persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, TokenTypeEnum.NON_FUNGIBLE_UNIQUE);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForNonFungibleToken(
                getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER);

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                getAddressFromEntity(autoRenewAccount),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new HederaToken(
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

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();
        royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
        final var expectedNonFungibleTokenInfo = new NonFungibleTokenInfo(
                expectedTokenInfo,
                BigInteger.valueOf(nft.getSerialNumber()),
                getAddressFromEntity(owner),
                BigInteger.valueOf(token.getCreatedTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                nft.getMetadata(),
                Address.ZERO.toHexString());

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedNonFungibleTokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void getNonFungibleTokenInfoWithEmptyAutoRenew() throws Exception {
        // Given
        final var owner = accountEntityPersist();
        final var treasury = accountEntityWithEvmAddressPersist();
        final var feeCollector = accountEntityWithEvmAddressPersist();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(null))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId()))
                .persist();
        final var nft = domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .spender(null)
                        .accountId(owner.toEntityId()))
                .persist();
        final var customFees =
                persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, TokenTypeEnum.NON_FUNGIBLE_UNIQUE);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForNonFungibleToken(
                getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER);

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.ZERO.toHexString(),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new HederaToken(
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

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();
        royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
        final var expectedNonFungibleTokenInfo = new NonFungibleTokenInfo(
                expectedTokenInfo,
                BigInteger.valueOf(nft.getSerialNumber()),
                getAddressFromEntity(owner),
                BigInteger.valueOf(token.getCreatedTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                nft.getMetadata(),
                Address.ZERO.toHexString());

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedNonFungibleTokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void getTokenInfo(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasury = accountEntityWithEvmAddressPersist();
        final var feeCollector = accountEntityWithEvmAddressPersist();
        final var autoRenewAccount = accountEntityPersist();
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasury.toEntityId()))
                .persist();

        final var customFees = persistCustomFeesWithFeeCollector(feeCollector, tokenEntity, tokenType);

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity));

        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = new Expiry(
                BigInteger.valueOf(tokenEntity.getExpirationTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                getAddressFromEntity(autoRenewAccount),
                BigInteger.valueOf(tokenEntity.getAutoRenewPeriod()));
        final var expectedHederaToken = new HederaToken(
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

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContract.FractionalFee>();
        if (TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)) {
            fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));
        }

        final var royaltyFees = new ArrayList<PrecompileTestContract.RoyaltyFee>();
        if (TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)) {
            royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));
        }

        final var expectedTokenInfo = new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedTokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
    }

    @Test
    void nftInfoForInvalidSerialNo() {
        // Given
        final var token = nftPersist();
        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForNonFungibleToken(
                toAddress(token.getTokenId()).toHexString(), INVALID_SERIAL_NUMBER);

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void tokenInfoForNonTokenAccount() {
        // Given
        final var account = accountEntityPersist();

        final var contract = testWeb3jService.deploy(PrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_getInformationForToken(getAddressFromEntity(account));

        // Then
        assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall, final Contract contract, final Long value) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallServiceParameters.CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isModularized(mirrorNodeEvmProperties.isModularizedServices())
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(testWeb3jService.getSender())
                .value(value)
                .build();
    }

    private Entity getTokenWithKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final KeyType keyType,
            final Contract contract) {
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
                .customize(e -> e.type(EntityType.TOKEN).key(key.toByteArray()))
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

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
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

    private Token nftPersist() {
        final var token = nonFungibleTokenPersist();
        nftPersistCustomizable(n -> n.tokenId(token.getTokenId()));
        return token;
    }

    private CustomFee persistCustomFeesWithFeeCollector(
            final Entity feeCollector, final Entity tokenEntity, final TokenTypeEnum tokenType) {
        final var fixedFee = org.hiero.mirror.common.domain.token.FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(domainBuilder.number())
                .collectorAccountId(feeCollector.toEntityId())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var fractionalFee = TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)
                ? FractionalFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .maximumAmount(domainBuilder.number())
                        .minimumAmount(DEFAULT_FEE_MIN_VALUE.longValue())
                        .numerator(domainBuilder.number())
                        .netOfTransfers(true)
                        .build()
                : null;

        final var fallbackFee = FallbackFee.builder()
                .amount(domainBuilder.number())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var royaltyFee = TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)
                ? RoyaltyFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .fallbackFee(fallbackFee)
                        .numerator(domainBuilder.number())
                        .build()
                : null;

        if (TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)) {
            return domainBuilder
                    .customFee()
                    .customize(f -> f.entityId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .fractionalFees(List.of(fractionalFee))
                            .royaltyFees(new ArrayList<>()))
                    .persist();
        } else if (TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)) {
            return domainBuilder
                    .customFee()
                    .customize(f -> f.entityId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .royaltyFees(List.of(royaltyFee))
                            .fractionalFees(new ArrayList<>()))
                    .persist();
        }

        return CustomFee.builder().build();
    }

    private List<TokenKey> getExpectedTokenKeys(final Entity tokenEntity, final Token token) {
        final var expectedTokenKeys = new ArrayList<TokenKey>();
        expectedTokenKeys.add(new TokenKey(KeyType.ADMIN_KEY.getKeyTypeNumeric(), getKeyValue(tokenEntity.getKey())));
        expectedTokenKeys.add(new TokenKey(KeyType.KYC_KEY.getKeyTypeNumeric(), getKeyValue(token.getKycKey())));
        expectedTokenKeys.add(new TokenKey(KeyType.FREEZE_KEY.getKeyTypeNumeric(), getKeyValue(token.getFreezeKey())));
        expectedTokenKeys.add(new TokenKey(KeyType.WIPE_KEY.getKeyTypeNumeric(), getKeyValue(token.getWipeKey())));
        expectedTokenKeys.add(new TokenKey(KeyType.SUPPLY_KEY.getKeyTypeNumeric(), getKeyValue(token.getSupplyKey())));
        expectedTokenKeys.add(
                new TokenKey(KeyType.FEE_SCHEDULE_KEY.getKeyTypeNumeric(), getKeyValue(token.getFeeScheduleKey())));
        expectedTokenKeys.add(new TokenKey(KeyType.PAUSE_KEY.getKeyTypeNumeric(), getKeyValue(token.getPauseKey())));

        return expectedTokenKeys;
    }

    private KeyValue getKeyValue(byte[] serializedKey) {
        try {
            final var key = Key.parseFrom(serializedKey);
            return new KeyValue(
                    false,
                    key.getContractID().hasContractNum()
                            ? asTypedEvmAddress(key.getContractID()).toHexString()
                            : Address.ZERO.toHexString(),
                    key.getEd25519().toByteArray(),
                    key.getECDSASecp256K1().toByteArray(),
                    key.getDelegatableContractId().hasContractNum()
                            ? asTypedEvmAddress(key.getDelegatableContractId()).toHexString()
                            : Address.ZERO.toHexString());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse key", e);
        }
    }

    private FixedFee getFixedFee(
            final org.hiero.mirror.common.domain.token.FixedFee fixedFee, final Entity feeCollector) {
        return new FixedFee(
                BigInteger.valueOf(fixedFee.getAmount()),
                getAddressFromEntityId(fixedFee.getDenominatingTokenId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContract.FractionalFee getFractionalFee(
            final FractionalFee fractionalFee, final Entity feeCollector) {
        return new PrecompileTestContract.FractionalFee(
                BigInteger.valueOf(fractionalFee.getNumerator()),
                BigInteger.valueOf(fractionalFee.getDenominator()),
                BigInteger.valueOf(fractionalFee.getMinimumAmount()),
                BigInteger.valueOf(fractionalFee.getMaximumAmount()),
                true,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContract.RoyaltyFee getRoyaltyFee(final RoyaltyFee royaltyFee, final Entity feeCollector) {
        return new PrecompileTestContract.RoyaltyFee(
                BigInteger.valueOf(royaltyFee.getNumerator()),
                BigInteger.valueOf(royaltyFee.getDenominator()),
                BigInteger.valueOf(royaltyFee.getFallbackFee().getAmount()),
                getAddressFromEntityId(royaltyFee.getFallbackFee().getDenominatingTokenId()),
                false,
                getAddressFromEvmAddress(feeCollector.getEvmAddress()));
    }
}
