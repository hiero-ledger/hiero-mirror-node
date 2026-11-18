// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.jooq.domain.Tables.HOOK;
import static org.hiero.mirror.restjava.jooq.domain.Tables.HOOK_STORAGE;
import static org.hiero.mirror.restjava.jooq.domain.Tables.HOOK_STORAGE_CHANGE;

import jakarta.inject.Named;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.SortField;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class HookRepositoryCustomImpl implements HookRepositoryCustom {

    private final DSLContext dslContext;

    @Override
    public List<HookStorageSlot> findHookStorage(HookStorageRequest request, long ownerId) {
        if (!request.getKeys().isEmpty() && !hasKeyInRange(request)) {
            return List.of();
        }

        if (request.isHistorical()) {
            return findHistoricalHookStorage(request, ownerId);
        }

        final var condition = HOOK_STORAGE
                .OWNER_ID
                .eq(ownerId)
                .and(HOOK_STORAGE.HOOK_ID.eq(request.getHookId()))
                .and(HOOK_STORAGE.DELETED.isFalse())
                .and(getKeyCondition(HOOK_STORAGE.KEY, request));

        return dslContext
                .select(HOOK_STORAGE.KEY, HOOK_STORAGE.MODIFIED_TIMESTAMP, HOOK_STORAGE.VALUE)
                .from(HOOK_STORAGE)
                .where(condition)
                .orderBy(getSortField(HOOK_STORAGE.KEY, request.getOrder()))
                .limit(request.getLimit())
                .fetch(Records.mapping(HookStorageSlot::new));
    }

    @Override
    public List<Hook> findHooks(HooksRequest request, long ownerId) {
        if (!request.getHookIds().isEmpty() && !hasHookIdInRange(request)) {
            return List.of();
        }

        var condition =
                HOOK.OWNER_ID.eq(ownerId).and(HOOK.HOOK_ID.between(request.getLowerBound(), request.getUpperBound()));

        final var hookIds = request.getHookIds();
        if (!hookIds.isEmpty()) {
            condition = condition.and(HOOK.HOOK_ID.in(hookIds));
        }

        return dslContext
                .selectFrom(HOOK)
                .where(condition)
                .orderBy(getSortField(HOOK.HOOK_ID, request.getOrder()))
                .limit(request.getLimit())
                .fetchInto(Hook.class);
    }

    private List<HookStorageSlot> findHistoricalHookStorage(HookStorageRequest request, long ownerId) {
        // Once any of the keys is within the key range, all the keys are matched regardless of the range
        final var key = HOOK_STORAGE_CHANGE.KEY;
        final var keys = request.getKeys();
        final var keyCondition =
                keys.isEmpty() ? key.between(request.getKeyLowerBound(), request.getKeyUpperBound()) : key.in(keys);
        final var condition = HOOK_STORAGE_CHANGE
                .OWNER_ID
                .eq(ownerId)
                .and(HOOK_STORAGE_CHANGE.HOOK_ID.eq(request.getHookId()))
                .and(keyCondition)
                .and(getBoundConditions(List.of(request.getTimestamp())));

        // A key can change multiple times within the timestamp range, so only return its latest change
        return dslContext
                .select(key, HOOK_STORAGE_CHANGE.CONSENSUS_TIMESTAMP, HOOK_STORAGE_CHANGE.VALUE_WRITTEN)
                .distinctOn(key)
                .from(HOOK_STORAGE_CHANGE)
                .where(condition)
                .orderBy(getSortField(key, request.getOrder()), HOOK_STORAGE_CHANGE.CONSENSUS_TIMESTAMP.desc())
                .limit(request.getLimit())
                .fetch(Records.mapping(HookStorageSlot::new));
    }

    private static Condition getKeyCondition(Field<byte[]> key, HookStorageRequest request) {
        final var condition = key.between(request.getKeyLowerBound(), request.getKeyUpperBound());
        final var keys = request.getKeys();
        return keys.isEmpty() ? condition : condition.and(key.in(keys));
    }

    private static <T> SortField<T> getSortField(Field<T> field, Direction order) {
        return order.isAscending() ? field.asc() : field.desc();
    }

    private static boolean hasHookIdInRange(HooksRequest request) {
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();

        for (final long hookId : request.getHookIds()) {
            if (hookId >= lowerBound && hookId <= upperBound) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasKeyInRange(HookStorageRequest request) {
        final var lowerBound = request.getKeyLowerBound();
        final var upperBound = request.getKeyUpperBound();

        for (final var key : request.getKeys()) {
            if (Arrays.compareUnsigned(key, lowerBound) >= 0 && Arrays.compareUnsigned(key, upperBound) <= 0) {
                return true;
            }
        }

        return false;
    }
}
