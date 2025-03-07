// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Copied Id type from hedera-services.
 *
 * Represents the key for {@code nftAllowances} and {@code fungibleTokenAllowances} maps in State.
 * It consists of the information about the token for which allowance is granted to and the spender who is granted the
 * allowance.
 *
 * <p>Having allowance on a token will grant the spender to transfer fungible or non-fungible token
 * units from the owner's account.
 *
 * Differences with the original:
 *  1. Deleted unnecessary fields
 */
public class FcTokenAllowanceId implements Comparable<FcTokenAllowanceId> {

    private EntityNum tokenNum;
    private EntityNum spenderNum;

    public FcTokenAllowanceId() {
        /* RuntimeConstructable */
    }

    public FcTokenAllowanceId(final EntityNum tokenNum, final EntityNum spenderNum) {
        this.tokenNum = tokenNum;
        this.spenderNum = spenderNum;
    }

    public static FcTokenAllowanceId from(final EntityNum tokenNum, final EntityNum spenderNum) {
        return new FcTokenAllowanceId(tokenNum, spenderNum);
    }

    public static FcTokenAllowanceId from(final TokenID tokenId, final AccountID accountId) {
        return new FcTokenAllowanceId(EntityNum.fromTokenId(tokenId), EntityNum.fromAccountId(accountId));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(FcTokenAllowanceId.class)) {
            return false;
        }

        final var that = (FcTokenAllowanceId) obj;
        return new EqualsBuilder()
                .append(tokenNum, that.tokenNum)
                .append(spenderNum, that.spenderNum)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(tokenNum).append(spenderNum).toHashCode();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("tokenNum", tokenNum.longValue())
                .add("spenderNum", spenderNum.longValue())
                .toString();
    }

    public EntityNum getTokenNum() {
        return tokenNum;
    }

    public EntityNum getSpenderNum() {
        return spenderNum;
    }

    @Override
    public int compareTo(@NonNull final FcTokenAllowanceId that) {
        return new CompareToBuilder()
                .append(tokenNum, that.tokenNum)
                .append(spenderNum, that.spenderNum)
                .toComparison();
    }
}
