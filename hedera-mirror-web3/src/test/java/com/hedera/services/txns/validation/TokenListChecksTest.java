// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenListChecksTest {

    @Mock
    Predicate<Key> adminKeyRemoval;

    @Test
    void permitsAdminKeyRemoval() {
        TokenListChecks.adminKeyRemoval = adminKeyRemoval;
        given(adminKeyRemoval.test(any())).willReturn(true);

        final var validity = TokenListChecks.checkKeys(
                true, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance());

        assertEquals(OK, validity);

        TokenListChecks.adminKeyRemoval = ImmutableKeyUtils::signalsKeyRemoval;
    }

    @Test
    void checksInvalidFeeScheduleKey() {
        final var invalidKeyList1 = KeyList.newBuilder().build();
        final var invalidFeeScheduleKey =
                Key.newBuilder().setKeyList(invalidKeyList1).build();

        final var validity = TokenListChecks.checkKeys(
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                true, invalidFeeScheduleKey,
                false, Key.getDefaultInstance());

        assertEquals(INVALID_CUSTOM_FEE_SCHEDULE_KEY, validity);
    }

    @Test
    void checksInvalidPauseKey() {
        final var invalidKeyList1 = KeyList.newBuilder().build();
        final var invalidPauseKey = Key.newBuilder().setKeyList(invalidKeyList1).build();

        final var validity = TokenListChecks.checkKeys(
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                true,
                invalidPauseKey);

        assertEquals(INVALID_PAUSE_KEY, validity);
    }

    @Test
    void typeChecks() {
        var validity = TokenListChecks.typeCheck(TokenType.FUNGIBLE_COMMON, 10, 5);
        assertEquals(OK, validity);

        validity = TokenListChecks.typeCheck(TokenType.NON_FUNGIBLE_UNIQUE, 0, 0);
        assertEquals(OK, validity);

        validity = TokenListChecks.typeCheck(TokenType.FUNGIBLE_COMMON, 10, -1);
        assertEquals(INVALID_TOKEN_DECIMALS, validity);
        validity = TokenListChecks.typeCheck(TokenType.FUNGIBLE_COMMON, -1, 100);
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, validity);

        validity = TokenListChecks.typeCheck(TokenType.NON_FUNGIBLE_UNIQUE, 1, 0);
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, validity);
        validity = TokenListChecks.typeCheck(TokenType.NON_FUNGIBLE_UNIQUE, 0, 1);
        assertEquals(INVALID_TOKEN_DECIMALS, validity);

        validity = TokenListChecks.typeCheck(TokenType.UNRECOGNIZED, 0, 0);
        assertEquals(NOT_SUPPORTED, validity);
    }

    @Test
    void suppliesChecks() {
        var validity = TokenListChecks.suppliesCheck(10, 100);
        assertEquals(OK, validity);

        validity = TokenListChecks.suppliesCheck(101, 100);
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, validity);
    }

    @Test
    void supplyTypeChecks() {
        var validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.FINITE, 10);
        assertEquals(OK, validity);
        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.INFINITE, 0);
        assertEquals(OK, validity);

        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.FINITE, 0);
        assertEquals(INVALID_TOKEN_MAX_SUPPLY, validity);
        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.INFINITE, 10);
        assertEquals(INVALID_TOKEN_MAX_SUPPLY, validity);

        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.UNRECOGNIZED, 10);
        assertEquals(NOT_SUPPORTED, validity);
    }

    @Test
    void checksSupplyKey() {
        var validity = TokenListChecks.nftSupplyKeyCheck(TokenType.NON_FUNGIBLE_UNIQUE, false);
        assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, validity);

        validity = TokenListChecks.nftSupplyKeyCheck(TokenType.NON_FUNGIBLE_UNIQUE, true);
        assertEquals(OK, validity);

        validity = TokenListChecks.nftSupplyKeyCheck(TokenType.FUNGIBLE_COMMON, false);
        assertEquals(OK, validity);

        validity = TokenListChecks.nftSupplyKeyCheck(TokenType.FUNGIBLE_COMMON, true);
        assertEquals(OK, validity);
    }
}
