// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.common.RangeOperator.EQ;
import static org.hiero.mirror.restjava.jooq.domain.Tables.HOOK;

import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortOrder;

@Named
@RequiredArgsConstructor
public class HookRepositoryCustomImpl implements HookRepositoryCustom {

    private final DSLContext dsl;

    @Override
    public Collection<Hook> findAll(HooksRequest request, EntityId accountId) {

        final var hookTable = HOOK;

        final var ownerIdCondition = hookTable.OWNER_ID.eq(accountId.getNum());
        final var hookIdCondition = getBoundConditions(request.getBounds());
        final var orderBy = hookTable.HOOK_ID.sort(request.getOrder().isAscending() ? SortOrder.ASC : SortOrder.DESC);

        return dsl.selectFrom(hookTable)
                .where(ownerIdCondition.and(hookIdCondition))
                .orderBy(orderBy)
                .limit(request.getLimit())
                .fetchInto(Hook.class);
    }

    private Condition getBaseCondition(EntityId accountId, Field<Long> baseField) {
        return getCondition(baseField, EQ, accountId.getId());
    }
}
