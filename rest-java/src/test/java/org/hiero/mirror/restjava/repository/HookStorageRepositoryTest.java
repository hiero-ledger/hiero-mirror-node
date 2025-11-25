// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.util.CommonUtils;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.util.BytesUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookStorageRepositoryTest extends RestJavaIntegrationTest {

    private static final EntityId OWNER_ID = EntityId.of(0, 0, 1000);
    private static final int LIMIT = 2;

    private final HookStorageRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(Direction order) {
        // given
        final var storage1 = persistHookStorage(OWNER_ID);
        final var hookId1 = storage1.getHookId();

        final var storage2 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage1.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage1.getKey())); // KEY_2 won't be passed to the method params
        final var storage3 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage2.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage2.getKey()),
                ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var storage4 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage3.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage3.getKey()));
        final var storage5 = persistHookStorage(
                EntityId.of(OWNER_ID.getId() + 1),
                hookId1,
                storage4.getModifiedTimestamp() + 1,
                storage4.getKey()); // different ownerId
        final var storage6 = persistHookStorage(
                OWNER_ID,
                hookId1 + 1,
                storage5.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage5.getKey())); // different hookId

        final var sort = Sort.by(order, Constants.KEY);

        final var expectedHookStorage = order.isAscending() ? List.of(storage1, storage4) : List.of(storage4, storage1);

        // when
        final var hookStorage = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                OWNER_ID.getId(),
                hookId1,
                List.of(
                        storage1.getKey(),
                        storage3.getKey(), // deleted because of empty key value
                        storage4.getKey(),
                        CommonUtils.nextBytes(32), // non-existing
                        storage5.getKey(), // existing, but for different ownerId
                        storage6.getKey() // existing, but for different hookId
                        ),
                PageRequest.of(0, 10, sort));

        // then
        assertThat(hookStorage).isNotNull().containsExactlyElementsOf(expectedHookStorage);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(Direction order) {
        // given
        final var storage1 = persistHookStorage(OWNER_ID);
        final var hookId1 = storage1.getHookId();

        final var storage2 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage1.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage1.getKey()));
        final var storage3 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage2.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage2.getKey()),
                ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var storage4 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage3.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage3.getKey()));
        final var storage5 = persistHookStorage(
                EntityId.of(OWNER_ID.getId() + 1),
                hookId1,
                storage4.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage4.getKey())); // different ownerId
        final var storage6 = persistHookStorage(
                OWNER_ID,
                hookId1 + 1,
                storage5.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage5.getKey())); // different hookId

        final var sort = Sort.by(order, Constants.KEY);

        final var expectedResponse =
                order.isAscending() ? List.of(storage1, storage2, storage4) : List.of(storage4, storage2, storage1);

        // when
        final var hookStorage = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                OWNER_ID.getId(), hookId1, storage1.getKey(), storage6.getKey(), PageRequest.of(0, 10, sort));

        // then
        assertThat(hookStorage).isNotNull().containsExactlyElementsOf(expectedResponse);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalseRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var storage1 = persistHookStorage(OWNER_ID);
        final var hookId1 = storage1.getHookId();
        final var storage2 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage1.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage1.getKey()));
        final var storage3 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage2.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage2.getKey()));
        final var storage4 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage3.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage3.getKey()));

        final var sort = Sort.by(order, Constants.KEY);

        final var hookStorage = order.isAscending()
                ? List.of(storage1, storage2, storage3, storage4)
                : List.of(storage4, storage3, storage2, storage1);

        final var expectedPage0 = hookStorage.subList(0, LIMIT);
        final var expectedPage1 = hookStorage.subList(LIMIT, hookStorage.size());

        // when
        final var page0 = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                OWNER_ID.getId(),
                hookId1,
                List.of(storage1.getKey(), storage2.getKey(), storage3.getKey(), storage4.getKey()),
                PageRequest.of(0, LIMIT, sort));

        final var page1 = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                OWNER_ID.getId(),
                hookId1,
                List.of(storage1.getKey(), storage2.getKey(), storage3.getKey(), storage4.getKey()),
                PageRequest.of(1, LIMIT, sort));

        assertThat(page0).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);

        assertThat(page1).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalseRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var storage1 = persistHookStorage(OWNER_ID);
        final var hookId1 = storage1.getHookId();

        final var storage2 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage1.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage1.getKey()));
        final var storage3 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage2.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage2.getKey()));
        final var storage4 = persistHookStorage(
                OWNER_ID,
                hookId1,
                storage3.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage3.getKey()));
        persistHookStorage(
                OWNER_ID,
                hookId1,
                storage4.getModifiedTimestamp() + 1,
                BytesUtil.incrementByteArray(storage4.getKey()));

        final var sort = Sort.by(order, Constants.KEY);

        final var hookStorage =
                order.isAscending() ? List.of(storage2, storage3, storage4) : List.of(storage4, storage3, storage2);

        final var expectedPage0 = hookStorage.subList(0, LIMIT);
        final var expectedPage1 = hookStorage.subList(LIMIT, hookStorage.size());

        // when
        final var page0 = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                OWNER_ID.getId(), hookId1, storage2.getKey(), storage4.getKey(), PageRequest.of(0, LIMIT, sort));

        final var page1 = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                OWNER_ID.getId(), hookId1, storage2.getKey(), storage4.getKey(), PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);

        assertThat(page1).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    private HookStorage persistHookStorage(EntityId ownerId) {
        return domainBuilder
                .hookStorage()
                .customize(hookStorage -> hookStorage.ownerId(ownerId.getId()))
                .persist();
    }

    private HookStorage persistHookStorage(EntityId ownerId, long hookId, long timestamp, byte[] key) {
        return domainBuilder
                .hookStorage()
                .customize(hookStorage -> hookStorage
                        .hookId(hookId)
                        .key(key)
                        .modifiedTimestamp(timestamp)
                        .ownerId(ownerId.getId()))
                .persist();
    }

    private HookStorage persistHookStorage(EntityId ownerId, long hookId, long timestamp, byte[] keyHex, byte[] value) {
        return domainBuilder
                .hookStorage()
                .customize(hookStorage -> hookStorage
                        .hookId(hookId)
                        .key(keyHex)
                        .modifiedTimestamp(timestamp)
                        .ownerId(ownerId.getId())
                        .value(value))
                .persist();
    }
}
