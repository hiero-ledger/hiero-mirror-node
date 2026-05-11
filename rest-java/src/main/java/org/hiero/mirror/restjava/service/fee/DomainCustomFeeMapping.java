// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;

/** Maps mirror domain custom fees to Hedera PBJ {@link CustomFee} messages. */
final class DomainCustomFeeMapping {

    private DomainCustomFeeMapping() {}

    static List<CustomFee> toPbjCustomFees(org.hiero.mirror.common.domain.token.CustomFee domain) {
        var out = new ArrayList<CustomFee>();
        if (domain.getFixedFees() != null) {
            for (var f : domain.getFixedFees()) {
                out.add(mapFixed(f));
            }
        }
        if (domain.getFractionalFees() != null) {
            for (var f : domain.getFractionalFees()) {
                out.add(mapFractional(f));
            }
        }
        if (domain.getRoyaltyFees() != null) {
            for (var f : domain.getRoyaltyFees()) {
                out.add(mapRoyalty(f));
            }
        }
        return out;
    }

    /** Topics only support fixed custom fees in consensus fee estimation. */
    static List<CustomFee> toPbjTopicCustomFees(org.hiero.mirror.common.domain.token.CustomFee domain) {
        var out = new ArrayList<CustomFee>();
        if (domain.getFixedFees() != null) {
            for (var f : domain.getFixedFees()) {
                out.add(mapFixed(f));
            }
        }
        return out;
    }

    private static CustomFee mapFixed(org.hiero.mirror.common.domain.token.FixedFee f) {
        return CustomFee.newBuilder()
                .fixedFee(new FixedFee(f.getAmount(), toTokenId(f.getDenominatingTokenId())))
                .feeCollectorAccountId(toAccountId(f.getCollectorAccountId()))
                .allCollectorsAreExempt(f.isAllCollectorsAreExempt())
                .build();
    }

    private static CustomFee mapFractional(org.hiero.mirror.common.domain.token.FractionalFee f) {
        var fraction = new Fraction(f.getNumerator(), f.getDenominator());
        long maxAmount = f.getMaximumAmount() != null ? f.getMaximumAmount() : 0L;
        var pbj = new FractionalFee(fraction, f.getMinimumAmount(), maxAmount, f.isNetOfTransfers());
        return CustomFee.newBuilder()
                .fractionalFee(pbj)
                .feeCollectorAccountId(toAccountId(f.getCollectorAccountId()))
                .allCollectorsAreExempt(f.isAllCollectorsAreExempt())
                .build();
    }

    private static CustomFee mapRoyalty(org.hiero.mirror.common.domain.token.RoyaltyFee f) {
        var exchange = new Fraction(f.getNumerator(), f.getDenominator());
        FixedFee fallbackPbj;
        var fb = f.getFallbackFee();
        if (fb == null) {
            fallbackPbj = FixedFee.DEFAULT;
        } else {
            fallbackPbj = new FixedFee(fb.getAmount(), toTokenId(fb.getDenominatingTokenId()));
        }
        var pbj = new RoyaltyFee(exchange, fallbackPbj);
        return CustomFee.newBuilder()
                .royaltyFee(pbj)
                .feeCollectorAccountId(toAccountId(f.getCollectorAccountId()))
                .allCollectorsAreExempt(f.isAllCollectorsAreExempt())
                .build();
    }

    private static AccountID toAccountId(EntityId id) {
        if (id == null || EntityId.isEmpty(id)) {
            return AccountID.DEFAULT;
        }
        return AccountID.newBuilder()
                .shardNum(id.getShard())
                .realmNum(id.getRealm())
                .accountNum(id.getNum())
                .build();
    }

    private static TokenID toTokenId(EntityId id) {
        if (id == null || EntityId.isEmpty(id)) {
            return TokenID.DEFAULT;
        }
        return TokenID.newBuilder()
                .shardNum(id.getShard())
                .realmNum(id.getRealm())
                .tokenNum(id.getNum())
                .build();
    }
}
