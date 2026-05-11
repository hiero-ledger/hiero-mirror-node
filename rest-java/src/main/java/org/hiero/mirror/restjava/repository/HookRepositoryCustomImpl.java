// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.jooq.impl.DSL.coalesce;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.converter.LongRangeConverter;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.jooq.domain.Tables;
import org.hiero.mirror.restjava.jooq.domain.tables.records.HookRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.SortField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class HookRepositoryCustomImpl implements HookRepositoryCustom, JooqRepository {

    private final DSLContext dsl;

    @Override
    public List<Hook> findHooks(HooksRequest request, long ownerId) {
        var h = Tables.HOOK;
        Condition condition = h.OWNER_ID.eq(ownerId).and(h.DELETED.eq(false));
        condition = condition.and(h.HOOK_ID.ge(request.getLowerBound())).and(h.HOOK_ID.le(request.getUpperBound()));
        if (!request.getHookIds().isEmpty()) {
            condition = condition.and(h.HOOK_ID.in(request.getHookIds()));
        }
        SortField<Long> sort = request.getOrder() == Direction.ASC ? h.HOOK_ID.asc() : h.HOOK_ID.desc();
        return dsl.selectFrom(h)
                .where(condition)
                .orderBy(sort)
                .limit(request.getLimit())
                .fetch(HookRepositoryCustomImpl::mapHook);
    }

    @Override
    public List<HookStorageSlot> findHookStorage(HookStorageRequest request, long ownerId) {
        if (request.getTimestamp().isEmpty()) {
            return findCurrentHookStorage(request, ownerId);
        }
        return findHistoricalHookStorage(request, ownerId);
    }

    private List<HookStorageSlot> findCurrentHookStorage(HookStorageRequest request, long ownerId) {
        var t = Tables.HOOK_STORAGE;
        Condition condition = baseStorageCondition(t, request, ownerId);
        SortField<byte[]> sort = request.getOrder() == Direction.ASC ? t.KEY.asc() : t.KEY.desc();
        return dsl.select(t.MODIFIED_TIMESTAMP, t.KEY, t.VALUE)
                .from(t)
                .where(condition)
                .orderBy(sort)
                .limit(request.getLimit())
                .fetch(this::mapStorageRow);
    }

    private List<HookStorageSlot> findHistoricalHookStorage(HookStorageRequest request, long ownerId) {
        var t = Tables.HOOK_STORAGE_CHANGE;
        Condition condition = baseStorageCondition(t, request, ownerId).and(conditionFromBound(request.getTimestamp()));
        SortField<byte[]> sort = request.getOrder() == Direction.ASC ? t.KEY.asc() : t.KEY.desc();
        return dsl.select(t.CONSENSUS_TIMESTAMP, t.KEY, coalesce(t.VALUE_WRITTEN, t.VALUE_READ))
                .from(t)
                .where(condition)
                .orderBy(sort)
                .limit(request.getLimit())
                .fetch(this::mapStorageRow);
    }

    private static Condition baseStorageCondition(
            org.hiero.mirror.restjava.jooq.domain.tables.HookStorage t, HookStorageRequest request, long ownerId) {
        Condition condition = t.OWNER_ID
                .eq(ownerId)
                .and(t.HOOK_ID.eq(request.getHookId()))
                .and(t.DELETED.eq(false))
                .and(t.KEY.ge(request.getKeyLowerBound()))
                .and(t.KEY.le(request.getKeyUpperBound()));
        if (!request.getKeys().isEmpty()) {
            condition = condition.and(t.KEY.in(request.getKeys()));
        }
        return condition;
    }

    private static Condition baseStorageCondition(
            org.hiero.mirror.restjava.jooq.domain.tables.HookStorageChange t,
            HookStorageRequest request,
            long ownerId) {
        Condition condition = t.OWNER_ID
                .eq(ownerId)
                .and(t.HOOK_ID.eq(request.getHookId()))
                .and(t.DELETED.eq(false))
                .and(t.KEY.ge(request.getKeyLowerBound()))
                .and(t.KEY.le(request.getKeyUpperBound()));
        if (!request.getKeys().isEmpty()) {
            condition = condition.and(t.KEY.in(request.getKeys()));
        }
        return condition;
    }

    private HookStorageSlot mapStorageRow(Record3<Long, byte[], byte[]> r) {
        byte[] value = r.value3();
        if (value == null) {
            value = new byte[0];
        }
        return new HookStorageSlot(r.value1(), r.value2(), value);
    }

    private static Hook mapHook(HookRecord r) {
        var h = Tables.HOOK;
        Long contractId = r.get(h.CONTRACT_ID);
        return Hook.builder()
                .ownerId(r.get(h.OWNER_ID))
                .hookId(r.get(h.HOOK_ID))
                .contractId(contractId != null ? EntityId.of(contractId) : null)
                .createdTimestamp(r.get(h.CREATED_TIMESTAMP))
                .adminKey(r.get(h.ADMIN_KEY))
                .deleted(r.get(h.DELETED))
                .extensionPoint(org.hiero.mirror.common.domain.hook.HookExtensionPoint.valueOf(
                        r.get(h.EXTENSION_POINT).name()))
                .type(org.hiero.mirror.common.domain.hook.HookType.valueOf(
                        r.get(h.TYPE).name()))
                .timestampRange(LongRangeConverter.INSTANCE.convert(r.get(h.TIMESTAMP_RANGE)))
                .build();
    }
}
