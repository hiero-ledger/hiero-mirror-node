// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
import static com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.ED25519_BYTE_LENGTH;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.jproto.JContractIDKey;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;

class TokenCreateWrapperTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final byte[] ecdsaSecpk256k1 = "123456789012345678901234567890123".getBytes();
    private static final byte[] ed25519 = "12345678901234567890123456789012".getBytes();
    private final TokenKeyWrapper tokenKeyWrapper =
            new TokenKeyWrapper(1, new KeyValueWrapper(true, null, new byte[] {}, new byte[] {}, null));
    private final ContractID contractID = contractIdFromEvmAddress(contractAddress);

    @Test
    void getAdminKeyReturnsPresentAdminKeyAsExpected() {
        // given
        final var wrapper = createTokenCreateWrapperWithKeys(List.of(tokenKeyWrapper));

        // when
        final var result = wrapper.getAdminKey();

        // then
        assertTrue(result.isPresent());
        assertEquals(tokenKeyWrapper, result.get());
        assertEquals(true, wrapper.isFungible());
        assertEquals("symbol", wrapper.getSymbol());
        assertEquals("token", wrapper.getName());
        assertEquals("memo", wrapper.getMemo());
        assertEquals(5054L, wrapper.getMaxSupply());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), wrapper.getInitSupply());
        assertEquals(false, wrapper.isFreezeDefault());
        assertEquals(false, wrapper.isSupplyTypeFinite());
        assertEquals(BigInteger.valueOf(Integer.MAX_VALUE), wrapper.getDecimals());
        assertEquals(account, wrapper.getTreasury());
        assertEquals(List.of(tokenKeyWrapper), wrapper.getTokenKeys());
        assertEquals(List.of(), wrapper.getFixedFees());
    }

    @Test
    void getAdminKeyReturnsEmptyOptionalWhenNoAdminkeyIsPresent() {
        // given
        final TokenCreateWrapper wrapper = createTokenCreateWrapperWithKeys(Collections.emptyList());

        // when
        final var result = wrapper.getAdminKey();

        // then
        assertFalse(result.isPresent());
    }

    @Test
    void translatesKeyValueWrapperWithInheritedKeyAsExpected() {
        // given
        final var wrapper = new KeyValueWrapper(true, null, new byte[] {}, new byte[] {}, null);

        // when
        final Key key = wrapper.asGrpc();

        // then
        assertEquals(KeyValueWrapper.KeyValueType.INHERIT_ACCOUNT_KEY, wrapper.getKeyValueType());
        assertNull(key);
    }

    @Test
    void translatesKeyValueWrapperWithContractIdAsExpected() {
        // given
        final var wrapper = new KeyValueWrapper(false, contractID, new byte[] {}, new byte[] {}, null);

        // when
        final var key = wrapper.asGrpc();

        // then
        assertEquals(KeyValueWrapper.KeyValueType.CONTRACT_ID, wrapper.getKeyValueType());
        assertEquals(contractID, key.getContractID());
    }

    @Test
    void translatesKeyValueWrapperWithDelegatableContractIdAsExpected() {
        // given
        final var wrapper = new KeyValueWrapper(false, null, new byte[] {}, new byte[] {}, contractID);

        // when
        final var key = wrapper.asGrpc();

        // then
        assertEquals(KeyValueWrapper.KeyValueType.DELEGATABLE_CONTRACT_ID, wrapper.getKeyValueType());
        assertEquals(contractID, key.getDelegatableContractId());
    }

    @Test
    void translatesKeyValueWrapperWithEd25519AsExpected() {
        // given
        final var wrapper = new KeyValueWrapper(false, null, ed25519, new byte[] {}, null);

        // when
        final var key = wrapper.asGrpc();

        // then
        assertEquals(KeyValueWrapper.KeyValueType.ED25519, wrapper.getKeyValueType());
        assertArrayEquals(ed25519, key.getEd25519().toByteArray());
    }

    @Test
    void translatesKeyValueWrapperWithEcdsaSecpk256k1AsExpected() {
        // given
        final var wrapper = new KeyValueWrapper(false, null, new byte[] {}, ecdsaSecpk256k1, null);

        // when
        final var key = wrapper.asGrpc();

        // then
        assertEquals(KeyValueWrapper.KeyValueType.ECDSA_SECPK256K1, wrapper.getKeyValueType());
        assertArrayEquals(ecdsaSecpk256k1, key.getECDSASecp256K1().toByteArray());
    }

    @Test
    void keyValueWrapperWithNoKeyValueSpecifiedHasInvalidKeyType() {
        final var wrapper = new KeyValueWrapper(false, null, new byte[] {}, new byte[] {}, null);

        assertEquals(KeyValueWrapper.KeyValueType.INVALID_KEY, wrapper.getKeyValueType());
        assertThrows(InvalidTransactionException.class, wrapper::asGrpc);
    }

    @Test
    void keyValueWrapperWithInheritAccountAndOneMoreValueHasInvalidKeyType() {
        final var wrapper = new KeyValueWrapper(true, contractID, new byte[] {}, new byte[] {}, null);

        assertEquals(KeyValueWrapper.KeyValueType.INVALID_KEY, wrapper.getKeyValueType());
    }

    @Test
    void keyValueWrapperWithContractIdAndOneMoreKeyValueHasInvalidKeyType() {
        final var wrapper = new KeyValueWrapper(false, contractID, new byte[] {}, new byte[] {}, contractID);

        assertEquals(KeyValueWrapper.KeyValueType.INVALID_KEY, wrapper.getKeyValueType());
    }

    @Test
    void keyValueWrapperWithEd25519AndOneMoreKeyValueHasInvalidKeyType() {
        final var wrapper = new KeyValueWrapper(false, null, ed25519, new byte[] {}, contractID);

        assertEquals(KeyValueWrapper.KeyValueType.INVALID_KEY, wrapper.getKeyValueType());
    }

    @Test
    void keyValueWrapperWithEcdsaSecpk256k1AndOneMoreKeyValueHasInvalidKeyType() {
        final var wrapper = new KeyValueWrapper(false, null, new byte[] {}, ecdsaSecpk256k1, contractID);

        assertEquals(KeyValueWrapper.KeyValueType.INVALID_KEY, wrapper.getKeyValueType());
    }

    @Test
    void keyValueWrapperWithEd25519KeyWithByteArrayWithSizeDifferentFromRequiredHasInvalidKeyType() {
        final var wrapper = new KeyValueWrapper(false, null, new byte[ED25519_BYTE_LENGTH - 1], new byte[] {}, null);

        assertEquals(KeyValueWrapper.KeyValueType.INVALID_KEY, wrapper.getKeyValueType());
    }

    @Test
    void keyValueWrapperWithEcdsaSecpk256k1KeyWithByteArrayWithSizeDifferentFromRequiredHasInvalidKeyType() {
        final var wrapper = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH - 1], null);

        assertEquals(KeyValueWrapper.KeyValueType.INVALID_KEY, wrapper.getKeyValueType());
    }

    @Test
    void translatesFixedFeeWithNoCollectorAsExpected() {
        // given
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, token, false, false, null);

        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertTrue(result.hasFixedFee());
        assertEquals(5, result.getFixedFee().getAmount());
        assertTrue(result.getFixedFee().hasDenominatingTokenId());
        assertEquals(token, result.getFixedFee().getDenominatingTokenId());
        assertFalse(result.hasFeeCollectorAccountId());
    }

    @Test
    void translatesFixedFeeWithDenominatedTokenAsExpected() {
        // given
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, token, false, false, receiver);

        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertTrue(result.hasFixedFee());
        assertEquals(5, result.getFixedFee().getAmount());
        assertTrue(result.getFixedFee().hasDenominatingTokenId());
        assertEquals(token, result.getFixedFee().getDenominatingTokenId());
        assertEquals(receiver, result.getFeeCollectorAccountId());
    }

    @Test
    void translatesFixedFeeWithHbarPaymentAsExpected() {
        // given
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, null, true, false, receiver);

        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertTrue(result.hasFixedFee());
        assertEquals(5, result.getFixedFee().getAmount());
        assertFalse(result.getFixedFee().hasDenominatingTokenId());
        assertEquals(receiver, result.getFeeCollectorAccountId());
    }

    @Test
    void translatesFixedFeeWithCreatedTokenAsExpected() {
        // given
        final var commonProperties = CommonProperties.getInstance();
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, null, false, true, receiver);
        final var defaultInstance = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), 0)
                .toTokenID();
        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertTrue(result.hasFixedFee());
        final var fixedFee = result.getFixedFee();
        assertEquals(5, fixedFee.getAmount());
        assertTrue(fixedFee.hasDenominatingTokenId());
        assertEquals(defaultInstance, fixedFee.getDenominatingTokenId());
        assertEquals(receiver, result.getFeeCollectorAccountId());
    }

    @Test
    void fixedFeeWithMultipleFormsOfPaymentIsInvalid() {
        // given
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, null, true, true, receiver);

        // when
        assertEquals(
                TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT, feeWrapper.getFixedFeePayment());
    }

    @Test
    void fixedFeeWithNoFormOfPaymentIsInvalid() {
        // given
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, null, false, false, receiver);

        // when
        assertEquals(
                TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT, feeWrapper.getFixedFeePayment());
    }

    @Test
    void invalidFixedFeeWithHbarsAndNewTokenAsPaymentTranslationThrows() {
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, null, true, true, receiver);

        assertThrows(InvalidTransactionException.class, feeWrapper::asGrpc);
    }

    @Test
    void invalidFixedFeeWithTokenIdAndHbarsAsPaymentTranslationThrows() {
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, token, true, false, receiver);

        assertThrows(InvalidTransactionException.class, feeWrapper::asGrpc);
    }

    @Test
    void invalidFixedFeeWithNoPaymentTypeTranslationThrows() {
        final var feeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, null, false, false, receiver);

        assertThrows(InvalidTransactionException.class, feeWrapper::asGrpc);
    }

    @Test
    void translatesFractionalFeesAsExpected() {
        // given
        final var feeWrapper = new TokenCreateWrapper.FractionalFeeWrapper(4, 5, 10, 20, true, receiver);

        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertEquals(4, result.getFractionalFee().getFractionalAmount().getNumerator());
        assertEquals(5, result.getFractionalFee().getFractionalAmount().getDenominator());
        assertEquals(10, result.getFractionalFee().getMinimumAmount());
        assertEquals(20, result.getFractionalFee().getMaximumAmount());
        assertTrue(result.getFractionalFee().getNetOfTransfers());
        assertEquals(receiver, result.getFeeCollectorAccountId());
    }

    @Test
    void translatesFractionalFeesWithoutCollectorAsExpected() {
        // given
        final var feeWrapper = new TokenCreateWrapper.FractionalFeeWrapper(4, 5, 10, 20, true, null);

        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertEquals(4, result.getFractionalFee().getFractionalAmount().getNumerator());
        assertEquals(5, result.getFractionalFee().getFractionalAmount().getDenominator());
        assertEquals(10, result.getFractionalFee().getMinimumAmount());
        assertEquals(20, result.getFractionalFee().getMaximumAmount());
        assertTrue(result.getFractionalFee().getNetOfTransfers());
        assertFalse(result.hasFeeCollectorAccountId());
    }

    @Test
    void translatesRoyaltyFeesAsExpected() {
        // given
        final var fallbackFeeWrapper = new TokenCreateWrapper.FixedFeeWrapper(5, token, false, false, receiver);
        final var feeWrapper = new TokenCreateWrapper.RoyaltyFeeWrapper(4, 5, fallbackFeeWrapper, receiver);

        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertEquals(4, result.getRoyaltyFee().getExchangeValueFraction().getNumerator());
        assertEquals(5, result.getRoyaltyFee().getExchangeValueFraction().getDenominator());
        assertEquals(
                fallbackFeeWrapper.asGrpc().getFixedFee(),
                result.getRoyaltyFee().getFallbackFee());
        assertEquals(receiver, result.getFeeCollectorAccountId());
    }

    @Test
    void translatesRoyaltyFeeWithoutCollectorAndFallbackFeeAsExpected() {
        // given
        final var feeWrapper = new TokenCreateWrapper.RoyaltyFeeWrapper(4, 5, null, null);

        // when
        final var result = feeWrapper.asGrpc();

        // then
        assertEquals(4, result.getRoyaltyFee().getExchangeValueFraction().getNumerator());
        assertEquals(5, result.getRoyaltyFee().getExchangeValueFraction().getDenominator());
        assertFalse(result.getRoyaltyFee().hasFallbackFee());
        assertFalse(result.hasFeeCollectorAccountId());
    }

    @Test
    void royaltyFeeWithInvalidFallbackFeeTranslationThrows() {
        // given
        final var feeWrapper = new TokenCreateWrapper.RoyaltyFeeWrapper(
                4, 5, new TokenCreateWrapper.FixedFeeWrapper(5, null, true, true, null), null);

        assertThrows(InvalidTransactionException.class, feeWrapper::asGrpc);
    }

    @Test
    void autoRenewAccountIsCheckedAsExpected() {
        final var renewAccount = domainBuilder.entityId().toAccountID();
        final var wrapper = createTokenCreateWrapperWithKeys(Collections.emptyList());
        assertTrue(wrapper.hasAutoRenewAccount());
        assertEquals(payer, wrapper.getExpiry().autoRenewAccount());
        wrapper.inheritAutoRenewAccount(renewAccount);
        assertEquals(renewAccount, wrapper.getExpiry().autoRenewAccount());
    }

    @Test
    void setInheritedKeysToSpecificKeyWorksAsExpected() throws InvalidKeyException {
        // given
        final var key = new JContractIDKey(contractID);
        final var wrapper = createTokenCreateWrapperWithKeys(List.of(
                tokenKeyWrapper,
                new TokenKeyWrapper(4, new KeyValueWrapper(true, null, new byte[] {}, new byte[] {}, null))));

        // when
        wrapper.setAllInheritedKeysTo(key);

        // then
        assertEquals(JKey.mapJKey(key), wrapper.getTokenKeys().get(0).key().asGrpc());
        assertEquals(JKey.mapJKey(key), wrapper.getTokenKeys().get(1).key().asGrpc());
    }
}
