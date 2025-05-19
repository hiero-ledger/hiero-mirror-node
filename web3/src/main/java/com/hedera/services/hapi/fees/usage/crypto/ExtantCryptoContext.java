// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.fees.usage.crypto;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.INT_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getAccountKeyStorageSize;

import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Key;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 *  Exact copy from hedera-services
 */
public class ExtantCryptoContext {
    private final int currentNumTokenRels;
    private final Key currentKey;
    private final long currentExpiry;
    private final String currentMemo;
    private final boolean currentlyHasProxy;
    private final int currentMaxAutomaticAssociations;
    private final Map<EntityNum, Long> currentCryptoAllowances;
    private final Map<AllowanceId, Long> currentTokenAllowances;
    private final Set<AllowanceId> currentApproveForAllNftAllowances;

    private ExtantCryptoContext(final Builder builder) {
        currentNumTokenRels = builder.currentNumTokenRels;
        currentMemo = builder.currentMemo;
        currentExpiry = builder.currentExpiry;
        currentKey = builder.currentKey;
        currentlyHasProxy = builder.currentlyHasProxy;
        currentMaxAutomaticAssociations = builder.currentMaxAutomaticAssociations;
        this.currentCryptoAllowances = builder.currentCryptoAllowances;
        this.currentTokenAllowances = builder.currentTokenAllowances;
        this.currentApproveForAllNftAllowances = builder.currentApproveForAllNftAllowances;
    }

    public long currentNonBaseRb() {
        return (long) (currentlyHasProxy ? BASIC_ENTITY_ID_SIZE : 0)
                + currentMemo.getBytes(StandardCharsets.UTF_8).length
                + getAccountKeyStorageSize(currentKey)
                + (currentMaxAutomaticAssociations == 0 ? 0 : INT_SIZE);
    }

    public Key currentKey() {
        return currentKey;
    }

    public int currentNumTokenRels() {
        return currentNumTokenRels;
    }

    public long currentExpiry() {
        return currentExpiry;
    }

    public String currentMemo() {
        return currentMemo;
    }

    public boolean currentlyHasProxy() {
        return currentlyHasProxy;
    }

    public int currentMaxAutomaticAssociations() {
        return currentMaxAutomaticAssociations;
    }

    public Map<EntityNum, Long> currentCryptoAllowances() {
        return currentCryptoAllowances;
    }

    public Map<AllowanceId, Long> currentTokenAllowances() {
        return currentTokenAllowances;
    }

    public Set<AllowanceId> currentNftAllowances() {
        return currentApproveForAllNftAllowances;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private static final int HAS_PROXY_MASK = 1 << 0;
        private static final int EXPIRY_MASK = 1 << 1;
        private static final int MEMO_MASK = 1 << 2;
        private static final int KEY_MASK = 1 << 3;
        private static final int TOKEN_RELS_MASK = 1 << 4;
        private static final int MAX_AUTO_ASSOCIATIONS_MASK = 1 << 5;
        private static final int CRYPTO_ALLOWANCES_MASK = 1 << 6;
        private static final int TOKEN_ALLOWANCES_MASK = 1 << 7;
        private static final int NFT_ALLOWANCES_MASK = 1 << 8;

        private static final int ALL_FIELDS_MASK = TOKEN_RELS_MASK
                | EXPIRY_MASK
                | MEMO_MASK
                | KEY_MASK
                | HAS_PROXY_MASK
                | MAX_AUTO_ASSOCIATIONS_MASK
                | CRYPTO_ALLOWANCES_MASK
                | TOKEN_ALLOWANCES_MASK
                | NFT_ALLOWANCES_MASK;

        private int mask = 0;

        private int currentNumTokenRels;
        private Key currentKey;
        private String currentMemo;
        private boolean currentlyHasProxy;
        private long currentExpiry;
        private int currentMaxAutomaticAssociations;
        private Map<EntityNum, Long> currentCryptoAllowances;
        private Map<AllowanceId, Long> currentTokenAllowances;
        private Set<AllowanceId> currentApproveForAllNftAllowances;

        private Builder() {}

        public ExtantCryptoContext build() {
            if (mask != ALL_FIELDS_MASK) {
                throw new IllegalStateException(String.format("Field mask is %d, not %d!", mask, ALL_FIELDS_MASK));
            }
            return new ExtantCryptoContext(this);
        }

        public Builder setCurrentNumTokenRels(final int currentNumTokenRels) {
            this.currentNumTokenRels = currentNumTokenRels;
            mask |= TOKEN_RELS_MASK;
            return this;
        }

        public Builder setCurrentExpiry(final long currentExpiry) {
            this.currentExpiry = currentExpiry;
            mask |= EXPIRY_MASK;
            return this;
        }

        public Builder setCurrentMemo(final String currentMemo) {
            this.currentMemo = currentMemo;
            mask |= MEMO_MASK;
            return this;
        }

        public Builder setCurrentKey(final Key currentKey) {
            this.currentKey = currentKey;
            mask |= KEY_MASK;
            return this;
        }

        public Builder setCurrentlyHasProxy(final boolean currentlyHasProxy) {
            this.currentlyHasProxy = currentlyHasProxy;
            mask |= HAS_PROXY_MASK;
            return this;
        }

        public Builder setCurrentMaxAutomaticAssociations(final int currentMaxAutomaticAssociations) {
            this.currentMaxAutomaticAssociations = currentMaxAutomaticAssociations;
            mask |= MAX_AUTO_ASSOCIATIONS_MASK;
            return this;
        }

        public Builder setCurrentCryptoAllowances(final Map<EntityNum, Long> currentCryptoAllowances) {
            this.currentCryptoAllowances = currentCryptoAllowances;
            mask |= CRYPTO_ALLOWANCES_MASK;
            return this;
        }

        public Builder setCurrentTokenAllowances(final Map<AllowanceId, Long> currentTokenAllowances) {
            this.currentTokenAllowances = currentTokenAllowances;
            mask |= TOKEN_ALLOWANCES_MASK;
            return this;
        }

        public Builder setCurrentApproveForAllNftAllowances(final Set<AllowanceId> currentApproveForAllNftAllowances) {
            this.currentApproveForAllNftAllowances = currentApproveForAllNftAllowances;
            mask |= NFT_ALLOWANCES_MASK;
            return this;
        }
    }
}
