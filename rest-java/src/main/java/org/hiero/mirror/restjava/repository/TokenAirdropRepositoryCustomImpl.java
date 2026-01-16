// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.common.RangeOperator.EQ;
import static org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;
import static org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;
import static org.hiero.mirror.restjava.jooq.domain.Tables.TOKEN_AIRDROP;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import org.hiero.mirror.restjava.jooq.domain.enums.AirdropState;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
final class TokenAirdropRepositoryCustomImpl implements TokenAirdropRepositoryCustom {

    private static final Map<AirdropRequestType, Map<Direction, List<SortField<?>>>> SORT_ORDERS = Map.of(
            OUTSTANDING,
            Map.of(
                    Direction.ASC,
                    List.of(
                            OUTSTANDING.getPrimaryField().asc(),
                            TOKEN_AIRDROP.TOKEN_ID.asc(),
                            TOKEN_AIRDROP.SERIAL_NUMBER.asc()),
                    Direction.DESC,
                    List.of(
                            OUTSTANDING.getPrimaryField().desc(),
                            TOKEN_AIRDROP.TOKEN_ID.desc(),
                            TOKEN_AIRDROP.SERIAL_NUMBER.desc())),
            PENDING,
            Map.of(
                    Direction.ASC,
                    List.of(
                            PENDING.getPrimaryField().asc(),
                            TOKEN_AIRDROP.TOKEN_ID.asc(),
                            TOKEN_AIRDROP.SERIAL_NUMBER.asc()),
                    Direction.DESC,
                    List.of(
                            PENDING.getPrimaryField().desc(),
                            TOKEN_AIRDROP.TOKEN_ID.desc(),
                            TOKEN_AIRDROP.SERIAL_NUMBER.desc())));
    private final DSLContext dslContext;

    @Override
    public Collection<TokenAirdrop> findAll(TokenAirdropRequest request, EntityId accountId) {
        var type = request.getType();
        var bounds = request.getBounds();
        var condition = getBaseCondition(accountId, type.getBaseField())
                .and(getBoundConditions(bounds))
                .and(TOKEN_AIRDROP.STATE.eq(AirdropState.PENDING));

        var order = SORT_ORDERS.getOrDefault(type, Map.of()).get(request.getOrder());
        return dslContext
                .selectFrom(TOKEN_AIRDROP)
                .where(condition)
                .orderBy(order)
                .limit(request.getLimit())
                .fetchInto(TokenAirdrop.class);
    }

    private Condition getBaseCondition(EntityId accountId, Field<Long> baseField) {
        return getCondition(baseField, EQ, accountId.getId());
    }
}
