// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.common.RangeOperator.EQ;
import static org.hiero.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;

import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.restjava.dto.NftAllowanceRequest;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class NftAllowanceRepositoryCustomImpl implements NftAllowanceRepositoryCustom {

    private static final Condition APPROVAL_CONDITION = NFT_ALLOWANCE.APPROVED_FOR_ALL.isTrue();
    private static final Map<OrderSpec, List<SortField<?>>> SORT_ORDERS = Map.of(
            new OrderSpec(true, Direction.ASC), List.of(NFT_ALLOWANCE.SPENDER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(true, Direction.DESC), List.of(NFT_ALLOWANCE.SPENDER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()),
            new OrderSpec(false, Direction.ASC), List.of(NFT_ALLOWANCE.OWNER.asc(), NFT_ALLOWANCE.TOKEN_ID.asc()),
            new OrderSpec(false, Direction.DESC), List.of(NFT_ALLOWANCE.OWNER.desc(), NFT_ALLOWANCE.TOKEN_ID.desc()));

    private final DSLContext dslContext;

    @NotNull
    @Override
    public Collection<NftAllowance> findAll(NftAllowanceRequest request, EntityId accountId) {
        boolean byOwner = request.isOwner();
        var bounds = request.getBounds();
        var condition = getBaseCondition(accountId, byOwner).and(getBoundConditions(bounds));
        return dslContext
                .selectFrom(NFT_ALLOWANCE)
                .where(condition)
                .orderBy(SORT_ORDERS.get(new OrderSpec(byOwner, request.getOrder())))
                .limit(request.getLimit())
                .fetchInto(NftAllowance.class);
    }

    private Condition getBaseCondition(EntityId accountId, boolean byOwner) {
        return getCondition(byOwner ? NFT_ALLOWANCE.OWNER : NFT_ALLOWANCE.SPENDER, EQ, accountId.getId())
                .and(APPROVAL_CONDITION);
    }

    private record OrderSpec(boolean byOwner, Direction direction) {}
}
