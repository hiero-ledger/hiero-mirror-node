// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.token.validators;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.txns.validation.TokenListChecks;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateChecksTest {

    @Mock
    private OptionValidator validator;

    @Mock
    private TransactionBody txnBody;

    @Mock
    private TokenCreateTransactionBody op;

    private CreateChecks createChecks;

    @BeforeEach
    void setup() {
        createChecks = new CreateChecks(validator);
    }

    @Test
    void testValidateMemo() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, result);
    }

    @Test
    void testValidateSymbol() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(TOKEN_SYMBOL_TOO_LONG);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(TOKEN_SYMBOL_TOO_LONG, result);
    }

    @Test
    void testValidateName() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(TOKEN_NAME_TOO_LONG);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(TOKEN_NAME_TOO_LONG, result);
    }

    @Test
    void testValidateTypeCheck() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(op.getTokenType()).willReturn(TokenType.UNRECOGNIZED);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(NOT_SUPPORTED, result);
    }

    @Test
    void testValidateSupplyTypeCheck() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(op.getTokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(op.getSupplyType()).willReturn(TokenSupplyType.UNRECOGNIZED);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(NOT_SUPPORTED, result);
    }

    @Test
    void testValidateSuppliesCheck() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(op.getTokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(op.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(op.getInitialSupply()).willReturn(10L);
        given(op.getMaxSupply()).willReturn(5L);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, result);
    }

    @Test
    void testValidateNftSupplyKeyCheck() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(op.getTokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(op.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(op.getInitialSupply()).willReturn(0L);
        given(op.getMaxSupply()).willReturn(10L);
        given(op.hasSupplyKey()).willReturn(false);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, result);
    }

    @Test
    void testValidateHasTreasury() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(op.getTokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(op.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(op.getInitialSupply()).willReturn(0L);
        given(op.getMaxSupply()).willReturn(10L);
        given(op.hasSupplyKey()).willReturn(true);
        given(op.hasTreasury()).willReturn(false);
        var result = createChecks.validate().apply(txnBody);
        assertEquals(INVALID_TREASURY_ACCOUNT_FOR_TOKEN, result);
    }

    @Test
    void testValidateKeys() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        given(op.getTokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(op.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(op.getInitialSupply()).willReturn(0L);
        given(op.getMaxSupply()).willReturn(10L);
        given(op.hasSupplyKey()).willReturn(true);
        given(op.hasTreasury()).willReturn(true);
        given(op.hasKycKey()).willReturn(true);
        given(op.getKycKey()).willReturn(Key.newBuilder().build());
        var result = createChecks.validate().apply(txnBody);
        assertEquals(INVALID_KYC_KEY, result);
    }

    @Test
    @SuppressWarnings("try")
    void testValidateFreezeKey() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);

        try (MockedStatic<TokenListChecks> ignored = mockStatic(TokenListChecks.class)) {
            given(TokenListChecks.typeCheck(any(), anyLong(), anyInt())).willReturn(OK);
            given(TokenListChecks.supplyTypeCheck(any(), anyLong())).willReturn(OK);
            given(TokenListChecks.suppliesCheck(anyLong(), anyLong())).willReturn(OK);
            given(TokenListChecks.nftSupplyKeyCheck(any(), anyBoolean())).willReturn(OK);
            given(TokenListChecks.checkKeys(
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any()))
                    .willReturn(OK);

            given(op.hasTreasury()).willReturn(true);
            given(op.getFreezeDefault()).willReturn(true);
            given(op.hasFreezeKey()).willReturn(false);
            var result = createChecks.validate().apply(txnBody);
            assertEquals(TOKEN_HAS_NO_FREEZE_KEY, result);
        }
    }

    @Test
    @SuppressWarnings("try")
    void testValidateAutorenewAccount() {
        given(txnBody.getTokenCreation()).willReturn(op);
        given(validator.memoCheck(any())).willReturn(OK);
        given(validator.tokenSymbolCheck(any())).willReturn(OK);
        given(validator.tokenNameCheck(any())).willReturn(OK);
        try (MockedStatic<TokenListChecks> ignored = mockStatic(TokenListChecks.class)) {
            given(TokenListChecks.typeCheck(any(), anyLong(), anyInt())).willReturn(OK);
            given(TokenListChecks.supplyTypeCheck(any(), anyLong())).willReturn(OK);
            given(TokenListChecks.suppliesCheck(anyLong(), anyLong())).willReturn(OK);
            given(TokenListChecks.nftSupplyKeyCheck(any(), anyBoolean())).willReturn(OK);
            given(TokenListChecks.checkKeys(
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any()))
                    .willReturn(OK);

            given(op.hasTreasury()).willReturn(true);
            given(op.getFreezeDefault()).willReturn(true);
            given(op.hasFreezeKey()).willReturn(true);
            given(op.hasAutoRenewAccount()).willReturn(true);
            given(validator.isValidAutoRenewPeriod(any())).willReturn(false);
            var result = createChecks.validate().apply(txnBody);
            assertEquals(INVALID_RENEWAL_PERIOD, result);
        }
    }

    @Test
    @SuppressWarnings("try")
    void testValidateHappyPath() {
        try (MockedStatic<TokenListChecks> ignored = mockStatic(TokenListChecks.class)) {
            given(txnBody.getTokenCreation()).willReturn(op);
            given(validator.memoCheck(any())).willReturn(OK);
            given(validator.tokenSymbolCheck(any())).willReturn(OK);
            given(validator.tokenNameCheck(any())).willReturn(OK);
            given(TokenListChecks.typeCheck(any(), anyLong(), anyInt())).willReturn(OK);
            given(TokenListChecks.supplyTypeCheck(any(), anyLong())).willReturn(OK);
            given(TokenListChecks.suppliesCheck(anyLong(), anyLong())).willReturn(OK);
            given(TokenListChecks.nftSupplyKeyCheck(any(), anyBoolean())).willReturn(OK);
            given(TokenListChecks.checkKeys(
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any(),
                            anyBoolean(),
                            any()))
                    .willReturn(OK);

            given(op.hasTreasury()).willReturn(true);
            given(op.getFreezeDefault()).willReturn(true);
            given(op.hasFreezeKey()).willReturn(true);
            given(op.hasAutoRenewAccount()).willReturn(true);
            given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
            var result = createChecks.validate().apply(txnBody);
            assertEquals(OK, result);
        }
    }
}
