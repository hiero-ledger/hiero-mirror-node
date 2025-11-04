// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.NftAllowanceRequest;
import org.hiero.mirror.restjava.repository.NftAllowanceRepository;

@Named
@RequiredArgsConstructor
final class NftAllowanceServiceImpl implements NftAllowanceService {

    private final NftAllowanceRepository repository;
    private final EntityService entityService;

    public Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request) {

        var ownerOrSpenderId = request.getOwnerOrSpenderIds();
        var token = request.getTokenIds();

        checkOwnerSpenderParamValidity(ownerOrSpenderId, token);

        var id = entityService.lookup(request.getAccountId());

        return repository.findAll(request, id);
    }

    private void checkOwnerSpenderParamValidity(Bound ownerOrSpenderParams, Bound tokenParams) {
        if (ownerOrSpenderParams.isEmpty() && !tokenParams.isEmpty()) {
            throw new IllegalArgumentException("token.id parameter must have account.id present");
        }

        verifyRangeId(ownerOrSpenderParams);
        verifyRangeId(tokenParams);

        if (!ownerOrSpenderParams.hasLowerAndUpper()
                && tokenParams.getAdjustedLowerRangeValue() > tokenParams.adjustUpperBound()) {
            throw new IllegalArgumentException("Invalid range provided for %s".formatted(Constants.TOKEN_ID));
        }

        if (tokenParams.getCardinality(RangeOperator.LT, RangeOperator.LTE) > 0
                && ownerOrSpenderParams.getCardinality(RangeOperator.EQ, RangeOperator.LTE) == 0) {
            throw new IllegalArgumentException("Requires the presence of an lte or eq account.id parameter");
        }
        if (tokenParams.getCardinality(RangeOperator.GT, RangeOperator.GTE) > 0
                && ownerOrSpenderParams.getCardinality(RangeOperator.EQ, RangeOperator.GTE) == 0) {
            throw new IllegalArgumentException("Requires the presence of an gte or eq account.id parameter");
        }
    }

    private void verifyRangeId(Bound ids) {
        ids.verifyUnsupported(RangeOperator.NE);
        ids.verifySingleOccurrence();
        ids.verifyEqualOrRange();
    }
}
