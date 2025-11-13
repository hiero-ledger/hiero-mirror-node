// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.CREATE_TOKEN_VALUE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.NEW_ECDSA_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.NEW_ED25519_KEY;

import com.google.protobuf.ByteString;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.web3j.generated.NestedCalls;
import org.hiero.mirror.web3.web3j.generated.NestedCalls.HederaToken;
import org.hiero.mirror.web3.web3j.generated.NestedCalls.KeyValue;
import org.hiero.mirror.web3.web3j.generated.NestedCalls.TokenKey;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContractCallNestedCallsTest extends AbstractContractCallServiceOpcodeTracerTest {
    private static final String EXPECTED_RESULT_NEGATIVE_TESTS = "hardcodedResult";

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            CONTRACT_ID,                ADMIN_KEY,
                            CONTRACT_ID,                KYC_KEY,
                            CONTRACT_ID,                FREEZE_KEY,
                            CONTRACT_ID,                WIPE_KEY,
                            CONTRACT_ID,                SUPPLY_KEY,
                            CONTRACT_ID,                FEE_SCHEDULE_KEY,
                            CONTRACT_ID,                PAUSE_KEY,
                            ED25519,                    ADMIN_KEY,
                            ED25519,                    KYC_KEY,
                            ED25519,                    FREEZE_KEY,
                            ED25519,                    WIPE_KEY,
                            ED25519,                    SUPPLY_KEY,
                            ED25519,                    FEE_SCHEDULE_KEY,
                            ED25519,                    PAUSE_KEY,
                            ECDSA_SECPK256K1,           ADMIN_KEY,
                            ECDSA_SECPK256K1,           KYC_KEY,
                            ECDSA_SECPK256K1,           FREEZE_KEY,
                            ECDSA_SECPK256K1,           WIPE_KEY,
                            ECDSA_SECPK256K1,           SUPPLY_KEY,
                            ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                            ECDSA_SECPK256K1,           PAUSE_KEY,
                            DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                            DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                            DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                            DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                            DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                            DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                            DELEGATABLE_CONTRACT_ID,    PAUSE_KEY,
                            """)
    void updateTokenKeysAndGetUpdatedTokenKeyForFungibleToken(final KeyValueType keyValueType, final KeyType keyType)
            throws Exception {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                domainBuilder.entity().persist().toEntityId());
        final var tokenAddress = toAddress(token.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var contractAddress = contract.getContractAddress();

        final var keyValue = getKeyValueForType(keyValueType, contractAddress);
        final var tokenKey = new TokenKey(keyType.getKeyTypeNumeric(), keyValue);

        // When
        final var result = contract.call_updateTokenKeysAndGetUpdatedTokenKey(
                        tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric())
                .send();

        // Then
        assertThat(result).isEqualTo(keyValue);

        final var functionCall = contract.send_updateTokenKeysAndGetUpdatedTokenKey(
                tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric());

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            CONTRACT_ID,                ADMIN_KEY,
                            CONTRACT_ID,                KYC_KEY,
                            CONTRACT_ID,                FREEZE_KEY,
                            CONTRACT_ID,                WIPE_KEY,
                            CONTRACT_ID,                SUPPLY_KEY,
                            CONTRACT_ID,                FEE_SCHEDULE_KEY,
                            CONTRACT_ID,                PAUSE_KEY,
                            ED25519,                    ADMIN_KEY,
                            ED25519,                    KYC_KEY,
                            ED25519,                    FREEZE_KEY,
                            ED25519,                    WIPE_KEY,
                            ED25519,                    SUPPLY_KEY,
                            ED25519,                    FEE_SCHEDULE_KEY,
                            ED25519,                    PAUSE_KEY,
                            ECDSA_SECPK256K1,           ADMIN_KEY,
                            ECDSA_SECPK256K1,           KYC_KEY,
                            ECDSA_SECPK256K1,           FREEZE_KEY,
                            ECDSA_SECPK256K1,           WIPE_KEY,
                            ECDSA_SECPK256K1,           SUPPLY_KEY,
                            ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                            ECDSA_SECPK256K1,           PAUSE_KEY,
                            DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                            DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                            DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                            DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                            DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                            DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                            DELEGATABLE_CONTRACT_ID,    PAUSE_KEY
                            """)
    void updateTokenKeysAndGetUpdatedTokenKeyForNFT(final KeyValueType keyValueType, final KeyType keyType)
            throws Exception {
        // Given
        final var token = nftPersist();
        final var tokenAddress = toAddress(token.getTokenId()).toHexString();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var contractAddress = contract.getContractAddress();

        final var keyValue = getKeyValueForType(keyValueType, contractAddress);
        final var tokenKey = new TokenKey(keyType.getKeyTypeNumeric(), keyValue);

        // When
        final var result = contract.call_updateTokenKeysAndGetUpdatedTokenKey(
                        tokenAddress, List.of(tokenKey), keyType.getKeyTypeNumeric())
                .send();

        // Then
        assertThat(result).isEqualTo(keyValue);

        final var functionCall = contract.send_updateTokenKeysAndGetUpdatedTokenKey(
                tokenAddress, List.of(tokenKey), keyType.getKeyTypeNumeric());

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenExpiryAndGetUpdatedTokenExpiry(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasury);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenExpiry = new NestedCalls.Expiry(
                BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                toAddress(tokenWithAutoRenewPair.getRight().toEntityId()).toHexString(),
                BigInteger.valueOf(8_000_000));

        // When
        final var result = contract.call_updateTokenExpiryAndGetUpdatedTokenExpiry(
                        tokenAddress.toHexString(), tokenExpiry)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenExpiry);

        final var functionCall =
                contract.send_updateTokenExpiryAndGetUpdatedTokenExpiry(tokenAddress.toHexString(), tokenExpiry);

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoSymbol(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasury);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(), tokenType, treasury.toEntityId(), tokenWithAutoRenewPair.getRight());

        // When
        final var result = contract.call_updateTokenInfoAndGetUpdatedTokenInfoSymbol(
                        tokenAddress.toHexString(), tokenInfo)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenInfo.symbol);

        final var functionCall =
                contract.send_updateTokenInfoAndGetUpdatedTokenInfoSymbol(tokenAddress.toHexString(), tokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoName(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasury);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(), tokenType, treasury.toEntityId(), tokenWithAutoRenewPair.getRight());

        // When
        final var result = contract.call_updateTokenInfoAndGetUpdatedTokenInfoName(
                        tokenAddress.toHexString(), tokenInfo)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenInfo.name);

        final var functionCall =
                contract.send_updateTokenInfoAndGetUpdatedTokenInfoName(tokenAddress.toHexString(), tokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoMemo(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasury);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(), tokenType, treasury.toEntityId(), tokenWithAutoRenewPair.getRight());

        // When
        final var result = contract.call_updateTokenInfoAndGetUpdatedTokenInfoMemo(
                        tokenAddress.toHexString(), tokenInfo)
                .send();

        // Then
        assertThat(result).isEqualTo(tokenInfo.memo);

        final var functionCall =
                contract.send_updateTokenInfoAndGetUpdatedTokenInfoMemo(tokenAddress.toHexString(), tokenInfo);

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void deleteTokenAndGetTokenInfoIsDeleted(final TokenTypeEnum tokenType) throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntityId = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasury)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);

        // When
        final var result = contract.call_deleteTokenAndGetTokenInfoIsDeleted(tokenAddress.toHexString())
                .send();

        // Then
        assertThat(result).isTrue();

        final var functionCall = contract.send_deleteTokenAndGetTokenInfoIsDeleted(tokenAddress.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            true, false, true, true
                            false, false, false, false
                            true, true, true, true
                            """)
    void createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys,
            final boolean inheritKey,
            boolean defaultKycStatus,
            final boolean defaultFreezeStatus)
            throws Exception {
        // Given
        // Modularized code now returns false if the token has no kyc and true when it's created with kyc key
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            defaultKycStatus = !defaultKycStatus;
        }
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.FUNGIBLE_COMMON,
                withKeys,
                inheritKey,
                defaultFreezeStatus,
                treasury);
        testWeb3jService.setValue(CREATE_TOKEN_VALUE);
        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());

        // When
        final var result =
                contract.call_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                                tokenInfo, BigInteger.ONE, BigInteger.ONE)
                        .send();

        // Then
        assertThat(result.component1()).isEqualTo(defaultKycStatus);
        assertThat(result.component2()).isEqualTo(defaultFreezeStatus);
        assertThat(result.component3()).isTrue(); // is a token

        final var functionCall =
                contract.send_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo, BigInteger.ONE, BigInteger.ONE, BigInteger.valueOf(CREATE_TOKEN_VALUE));

        verifyEthCallAndEstimateGasWithValue(functionCall, contract, toAddress(treasury.getId()), CREATE_TOKEN_VALUE);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            true, false, true, true
                            false, false, false, false
                            true, true, true, true
                            """)
    void createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys,
            final boolean inheritKey,
            boolean defaultKycStatus,
            final boolean defaultFreezeStatus)
            throws Exception {
        // Given
        // Modularized code now returns false if the token has no kyc and true when it's created with kyc key
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            defaultKycStatus = !defaultKycStatus;
        }
        final var sender = accountEntityPersist();
        final var treasury = accountEntityPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                withKeys,
                inheritKey,
                defaultFreezeStatus,
                treasury);
        testWeb3jService.setValue(CREATE_TOKEN_VALUE);
        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());

        // When
        final var result = contract.call_createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo)
                .send();

        // Then
        assertThat(result.component1()).isEqualTo(defaultKycStatus);
        assertThat(result.component2()).isEqualTo(defaultFreezeStatus);
        assertThat(result.component3()).isTrue(); // is a token

        final var functionCall = contract.send_createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                tokenInfo, BigInteger.valueOf(CREATE_TOKEN_VALUE));

        verifyEthCallAndEstimateGasWithValue(functionCall, contract, toAddress(treasury.getId()), CREATE_TOKEN_VALUE);
    }

    @Test
    void nestedGetTokenInfoAndHardcodedResult() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);

        // When
        final var function = contract.call_nestedGetTokenInfoAndHardcodedResult(Address.ZERO.toHexString());
        final var result = function.send();

        // Then
        assertThat(result).isEqualTo(EXPECTED_RESULT_NEGATIVE_TESTS);
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    @Test
    void nestedHtsGetApprovedAndHardcodedResult() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);

        // When
        final var function =
                contract.call_nestedHtsGetApprovedAndHardcodedResult(Address.ZERO.toHexString(), DEFAULT_SERIAL_NUMBER);
        final var result = function.send();

        // Then
        assertThat(result).isEqualTo(EXPECTED_RESULT_NEGATIVE_TESTS);
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    @Test
    void nestedMintTokenAndHardcodedResult() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);

        // When
        final var function = contract.call_nestedMintTokenAndHardcodedResult(
                Address.ZERO.toHexString(),
                BigInteger.ZERO,
                List.of(ByteString.copyFromUtf8("firstMeta").toByteArray()));
        final var result = function.send();

        // Then
        assertThat(result).isEqualTo(EXPECTED_RESULT_NEGATIVE_TESTS);
        verifyOpcodeTracerCall(function.encodeFunctionCall(), contract);
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void nestedDeployTwoContracts() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var sender = accountEntityPersist();
        testWeb3jService.setValue(100_000_000_000L);
        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());
        // When
        final var function = contract.call_deployNestedContracts();
        final var result = function.send();
        // Then
        // verify that contract addresses are different
        assertThat(result.getValue1()).isNotEqualTo(result.getValue2());
        // verify that contract balances are different
        assertThat(result.getValue3()).isNotEqualTo(result.getValue4());
    }

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
        return switch (keyValueType) {
            case INHERIT_ACCOUNT_KEY ->
                new KeyValue(
                        Boolean.TRUE, Address.ZERO.toHexString(), new byte[0], new byte[0], Address.ZERO.toHexString());
            case CONTRACT_ID ->
                new KeyValue(Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 ->
                new KeyValue(
                        Boolean.FALSE,
                        Address.ZERO.toHexString(),
                        NEW_ED25519_KEY,
                        new byte[0],
                        Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 ->
                new KeyValue(
                        Boolean.FALSE,
                        Address.ZERO.toHexString(),
                        new byte[0],
                        NEW_ECDSA_KEY,
                        Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID ->
                new KeyValue(Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    private HederaToken getHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final boolean withKeys,
            final boolean inheritAccountKey,
            final boolean freezeDefault,
            final Entity treasuryEntity) {
        final List<TokenKey> tokenKeys = new LinkedList<>();
        final var keyType = inheritAccountKey ? KeyValueType.INHERIT_ACCOUNT_KEY : KeyValueType.ECDSA_SECPK256K1;
        if (withKeys) {
            tokenKeys.add(new TokenKey(KeyType.KYC_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
            tokenKeys.add(new TokenKey(KeyType.FREEZE_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
        }

        return populateHederaToken(contractAddress, tokenType, treasuryEntity.toEntityId(), freezeDefault, tokenKeys);
    }

    private NestedCalls.HederaToken populateHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final EntityId treasuryAccountId,
            Entity autoRenewAccount) {
        // expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new NestedCalls.KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        final var keys = new ArrayList<NestedCalls.TokenKey>();
        keys.add(new NestedCalls.TokenKey(
                AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));
        return new NestedCalls.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                false,
                keys,
                new NestedCalls.Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAddressFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    private NestedCalls.HederaToken populateHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final EntityId treasuryAccountId,
            boolean freezeDefault,
            List<TokenKey> tokenKeys) {
        final var autoRenewAccount =
                accountEntityWithEvmAddressPersist(); // the account that is going to be charged for
        // token renewal upon expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new NestedCalls.KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        tokenKeys.add(new NestedCalls.TokenKey(
                AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));

        return new NestedCalls.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                freezeDefault,
                tokenKeys,
                new NestedCalls.Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAliasFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    private Token nftPersist() {
        final var treasury = domainBuilder.entity().persist().toEntityId();
        final var token = nonFungibleTokenPersistWithTreasury(treasury);
        nftPersistCustomizable(
                n -> n.accountId(treasury).spender(treasury.getId()).tokenId(token.getTokenId()));
        return token;
    }
}
