// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.span;

import static com.hedera.services.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.services.hapi.fees.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoCreateMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.utils.accessors.TxnAccessor;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpandHandleSpanMapAccessorTest {
    private final Map<String, Object> span = new HashMap<>();

    @Mock
    private TxnAccessor accessor;

    private ExpandHandleSpanMapAccessor subject;

    @BeforeEach
    void setUp() {
        subject = new ExpandHandleSpanMapAccessor();

        given(accessor.getSpanMap()).willReturn(span);
    }

    @Test
    void testsForTokenCreateMetaAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getTokenCreateMeta(accessor));
    }

    @Test
    void testsForTokenBurnMetaAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getTokenBurnMeta(accessor));
    }

    @Test
    void testsForTokenWipeMetaAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getTokenWipeMeta(accessor));
    }

    @Test
    void testsForTokenFreezeMetaAsExpected() {
        final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();

        subject.setTokenFreezeMeta(accessor, tokenFreezeMeta);

        assertEquals(48, subject.getTokenFreezeMeta(accessor).getBpt());
    }

    @Test
    void testsForTokenUnfreezeMetaAsExpected() {
        final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();

        subject.setTokenUnfreezeMeta(accessor, tokenUnfreezeMeta);

        assertEquals(48, subject.getTokenUnfreezeMeta(accessor).getBpt());
    }

    @Test
    void testsForTokenPauseMetaAsExpected() {
        final var tokenPauseMeta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();

        subject.setTokenPauseMeta(accessor, tokenPauseMeta);

        assertEquals(24, subject.getTokenPauseMeta(accessor).getBpt());
    }

    @Test
    void testsForTokenUnpauseMetaAsExpected() {
        final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();

        subject.setTokenUnpauseMeta(accessor, tokenUnpauseMeta);

        assertEquals(24, subject.getTokenUnpauseMeta(accessor).getBpt());
    }

    @Test
    void testsForCryptoCreateMetaAsExpected() {
        final var opMeta = new CryptoCreateMeta.Builder()
                .baseSize(1_234)
                .lifeTime(1_234_567L)
                .maxAutomaticAssociations(12)
                .build();

        subject.setCryptoCreateMeta(accessor, opMeta);

        assertEquals(1_234, subject.getCryptoCreateMeta(accessor).getBaseSize());
    }

    @Test
    void testsForCryptoUpdateMetaAsExpected() {
        final var opMeta = new CryptoUpdateMeta.Builder()
                .keyBytesUsed(123)
                .msgBytesUsed(1_234)
                .memoSize(100)
                .effectiveNow(1_234_000L)
                .expiry(1_234_567L)
                .hasProxy(false)
                .maxAutomaticAssociations(3)
                .hasMaxAutomaticAssociations(true)
                .build();

        subject.setCryptoUpdate(accessor, opMeta);

        assertEquals(3, subject.getCryptoUpdateMeta(accessor).getMaxAutomaticAssociations());
    }

    @Test
    void testsForCryptoApproveMetaAsExpected() {
        final var secs = Instant.now().getEpochSecond();
        final var opMeta = CryptoApproveAllowanceMeta.newBuilder()
                .msgBytesUsed(112)
                .effectiveNow(secs)
                .build();

        subject.setCryptoApproveMeta(accessor, opMeta);

        assertEquals(112, subject.getCryptoApproveMeta(accessor).getMsgBytesUsed());
        assertEquals(secs, subject.getCryptoApproveMeta(accessor).getEffectiveNow());
    }

    @Test
    void testsForCryptoDeleteMetaAsExpected() {
        final var now = Instant.now().getEpochSecond();
        final var opMeta = CryptoDeleteAllowanceMeta.newBuilder()
                .msgBytesUsed(112)
                .effectiveNow(now)
                .build();

        subject.setCryptoDeleteAllowanceMeta(accessor, opMeta);
        assertEquals(112, subject.getCryptoDeleteAllowanceMeta(accessor).getMsgBytesUsed());
        assertEquals(now, subject.getCryptoDeleteAllowanceMeta(accessor).getEffectiveNow());
    }
}
