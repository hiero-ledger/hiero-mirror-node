// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.span;

import com.hedera.services.fees.usage.token.meta.TokenBurnMeta;
import com.hedera.services.fees.usage.token.meta.TokenCreateMeta;
import com.hedera.services.fees.usage.token.meta.TokenFreezeMeta;
import com.hedera.services.fees.usage.token.meta.TokenPauseMeta;
import com.hedera.services.fees.usage.token.meta.TokenUnfreezeMeta;
import com.hedera.services.fees.usage.token.meta.TokenUnpauseMeta;
import com.hedera.services.fees.usage.token.meta.TokenWipeMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoCreateMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.utils.accessors.TxnAccessor;

/** Minimal helper class for getting/setting entries in a span map. */

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Remove FeeSchedule, UtilPrng, File logic
 */
public class ExpandHandleSpanMapAccessor {
    private static final String TOKEN_CREATE_META_KEY = "tokenCreateMeta";
    private static final String TOKEN_BURN_META_KEY = "tokenBurnMeta";
    private static final String TOKEN_WIPE_META_KEY = "tokenWipeMeta";
    private static final String TOKEN_FREEZE_META_KEY = "tokenFreezeMeta";
    private static final String TOKEN_UNFREEZE_META_KEY = "tokenUnfreezeMeta";
    private static final String TOKEN_PAUSE_META_KEY = "tokenPauseMeta";
    private static final String TOKEN_UNPAUSE_META_KEY = "tokenUnpauseMeta";
    private static final String CRYPTO_CREATE_META_KEY = "cryptoCreateMeta";
    private static final String CRYPTO_UPDATE_META_KEY = "cryptoUpdateMeta";
    private static final String CRYPTO_APPROVE_META_KEY = "cryptoApproveMeta";
    private static final String CRYPTO_DELETE_ALLOWANCE_META_KEY = "cryptoDeleteAllowanceMeta";

    public void setTokenCreateMeta(final TxnAccessor accessor, final TokenCreateMeta tokenCreateMeta) {
        accessor.getSpanMap().put(TOKEN_CREATE_META_KEY, tokenCreateMeta);
    }

    public TokenCreateMeta getTokenCreateMeta(final TxnAccessor accessor) {
        return (TokenCreateMeta) accessor.getSpanMap().get(TOKEN_CREATE_META_KEY);
    }

    public void setTokenBurnMeta(final TxnAccessor accessor, final TokenBurnMeta tokenBurnMeta) {
        accessor.getSpanMap().put(TOKEN_BURN_META_KEY, tokenBurnMeta);
    }

    public TokenBurnMeta getTokenBurnMeta(final TxnAccessor accessor) {
        return (TokenBurnMeta) accessor.getSpanMap().get(TOKEN_BURN_META_KEY);
    }

    public void setTokenWipeMeta(final TxnAccessor accessor, final TokenWipeMeta tokenWipeMeta) {
        accessor.getSpanMap().put(TOKEN_WIPE_META_KEY, tokenWipeMeta);
    }

    public TokenWipeMeta getTokenWipeMeta(final TxnAccessor accessor) {
        return (TokenWipeMeta) accessor.getSpanMap().get(TOKEN_WIPE_META_KEY);
    }

    public void setTokenFreezeMeta(final TxnAccessor accessor, final TokenFreezeMeta tokenFreezeMeta) {
        accessor.getSpanMap().put(TOKEN_FREEZE_META_KEY, tokenFreezeMeta);
    }

    public TokenFreezeMeta getTokenFreezeMeta(final TxnAccessor accessor) {
        return (TokenFreezeMeta) accessor.getSpanMap().get(TOKEN_FREEZE_META_KEY);
    }

    public void setTokenUnfreezeMeta(final TxnAccessor accessor, final TokenUnfreezeMeta tokenUnfreezeMeta) {
        accessor.getSpanMap().put(TOKEN_UNFREEZE_META_KEY, tokenUnfreezeMeta);
    }

    public TokenUnfreezeMeta getTokenUnfreezeMeta(final TxnAccessor accessor) {
        return (TokenUnfreezeMeta) accessor.getSpanMap().get(TOKEN_UNFREEZE_META_KEY);
    }

    public void setTokenPauseMeta(final TxnAccessor accessor, final TokenPauseMeta tokenPauseMeta) {
        accessor.getSpanMap().put(TOKEN_PAUSE_META_KEY, tokenPauseMeta);
    }

    public TokenPauseMeta getTokenPauseMeta(final TxnAccessor accessor) {
        return (TokenPauseMeta) accessor.getSpanMap().get(TOKEN_PAUSE_META_KEY);
    }

    public void setTokenUnpauseMeta(final TxnAccessor accessor, final TokenUnpauseMeta tokenUnpauseMeta) {
        accessor.getSpanMap().put(TOKEN_UNPAUSE_META_KEY, tokenUnpauseMeta);
    }

    public TokenUnpauseMeta getTokenUnpauseMeta(final TxnAccessor accessor) {
        return (TokenUnpauseMeta) accessor.getSpanMap().get(TOKEN_UNPAUSE_META_KEY);
    }

    public void setCryptoCreateMeta(final TxnAccessor accessor, final CryptoCreateMeta cryptoCreateMeta) {
        accessor.getSpanMap().put(CRYPTO_CREATE_META_KEY, cryptoCreateMeta);
    }

    public CryptoCreateMeta getCryptoCreateMeta(final TxnAccessor accessor) {
        return (CryptoCreateMeta) accessor.getSpanMap().get(CRYPTO_CREATE_META_KEY);
    }

    public void setCryptoUpdate(final TxnAccessor accessor, final CryptoUpdateMeta cryptoUpdateMeta) {
        accessor.getSpanMap().put(CRYPTO_UPDATE_META_KEY, cryptoUpdateMeta);
    }

    public CryptoUpdateMeta getCryptoUpdateMeta(final TxnAccessor accessor) {
        return (CryptoUpdateMeta) accessor.getSpanMap().get(CRYPTO_UPDATE_META_KEY);
    }

    public void setCryptoApproveMeta(final TxnAccessor accessor, final CryptoApproveAllowanceMeta cryptoApproveMeta) {
        accessor.getSpanMap().put(CRYPTO_APPROVE_META_KEY, cryptoApproveMeta);
    }

    public CryptoApproveAllowanceMeta getCryptoApproveMeta(final TxnAccessor accessor) {
        return (CryptoApproveAllowanceMeta) accessor.getSpanMap().get(CRYPTO_APPROVE_META_KEY);
    }

    public void setCryptoDeleteAllowanceMeta(
            final TxnAccessor accessor, final CryptoDeleteAllowanceMeta cryptoDeleteAllowanceMeta) {
        accessor.getSpanMap().put(CRYPTO_DELETE_ALLOWANCE_META_KEY, cryptoDeleteAllowanceMeta);
    }

    public CryptoDeleteAllowanceMeta getCryptoDeleteAllowanceMeta(final TxnAccessor accessor) {
        return (CryptoDeleteAllowanceMeta) accessor.getSpanMap().get(CRYPTO_DELETE_ALLOWANCE_META_KEY);
    }
}
