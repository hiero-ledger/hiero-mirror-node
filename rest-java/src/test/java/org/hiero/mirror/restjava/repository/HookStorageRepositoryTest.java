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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookStorageRepositoryTest extends RestJavaIntegrationTest {

    private static final long HOOK_ID = 2000;
    private static final int LIMIT = 2;

    private final HookStorageRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(Direction order) {
        // given
        final var keys = generateKeys(6);
        final var ownerId = domainBuilder.entityId();

        final var storage1 = persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        persistHookStorage(ownerId, HOOK_ID, keys.get(1)); //  won't be passed to the method params
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2), ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));
        final var storage5 =
                persistHookStorage(EntityId.of(ownerId.getId() + 1), HOOK_ID, keys.get(4)); // different ownerId
        final var storage6 = persistHookStorage(ownerId, HOOK_ID + 1, keys.get(5)); // different hookId

        final var sort = Sort.by(order, Constants.KEY);

        final var expectedHookStorage = order.isAscending() ? List.of(storage1, storage4) : List.of(storage4, storage1);

        // when
        final var hookStorage = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                ownerId.getId(),
                HOOK_ID,
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
        final var keys = generateKeys(6);
        final var ownerId = domainBuilder.entityId();

        final var storage1 = persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage2 = persistHookStorage(ownerId, HOOK_ID, keys.get(1));
        persistHookStorage(ownerId, HOOK_ID, keys.get(2), ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));
        persistHookStorage(EntityId.of(ownerId.getId() + 1), HOOK_ID, keys.get(4)); // different ownerId
        final var storage6 = persistHookStorage(ownerId, HOOK_ID + 1, keys.get(5)); // different hookId

        final var sort = Sort.by(order, Constants.KEY);

        final var expectedResponse =
                order.isAscending() ? List.of(storage1, storage2, storage4) : List.of(storage4, storage2, storage1);

        // when
        final var hookStorage = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                ownerId.getId(), HOOK_ID, storage1.getKey(), storage6.getKey(), PageRequest.of(0, 10, sort));

        // then
        assertThat(hookStorage).isNotNull().containsExactlyElementsOf(expectedResponse);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalseRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var keys = generateKeys(4);
        final var ownerId = domainBuilder.entityId();

        final var storage1 = persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage2 = persistHookStorage(ownerId, HOOK_ID, keys.get(1));
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2));
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));

        final var sort = Sort.by(order, Constants.KEY);

        final var hookStorage = order.isAscending()
                ? List.of(storage1, storage2, storage3, storage4)
                : List.of(storage4, storage3, storage2, storage1);

        final var expectedPage0 = hookStorage.subList(0, LIMIT);
        final var expectedPage1 = hookStorage.subList(LIMIT, hookStorage.size());

        // when
        final var page0 = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                ownerId.getId(),
                HOOK_ID,
                List.of(storage1.getKey(), storage2.getKey(), storage3.getKey(), storage4.getKey()),
                PageRequest.of(0, LIMIT, sort));

        final var page1 = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                ownerId.getId(),
                HOOK_ID,
                List.of(storage1.getKey(), storage2.getKey(), storage3.getKey(), storage4.getKey()),
                PageRequest.of(1, LIMIT, sort));

        assertThat(page0).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);
        assertThat(page1).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalseRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage2 = persistHookStorage(ownerId, HOOK_ID, keys.get(1));
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2));
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));
        persistHookStorage(ownerId, HOOK_ID, keys.get(4));

        final var sort = Sort.by(order, Constants.KEY);

        final var hookStorage =
                order.isAscending() ? List.of(storage2, storage3, storage4) : List.of(storage4, storage3, storage2);

        final var expectedPage0 = hookStorage.subList(0, LIMIT);
        final var expectedPage1 = hookStorage.subList(LIMIT, hookStorage.size());

        // when
        final var page0 = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                ownerId.getId(), HOOK_ID, storage2.getKey(), storage4.getKey(), PageRequest.of(0, LIMIT, sort));
        final var page1 = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                ownerId.getId(), HOOK_ID, storage2.getKey(), storage4.getKey(), PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);
        assertThat(page1).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    private HookStorage persistHookStorage(EntityId ownerId, long hookId, byte[] key) {
        return domainBuilder
                .hookStorage()
                .customize(hookStorage -> hookStorage.hookId(hookId).key(key).ownerId(ownerId.getId()))
                .persist();
    }

    private HookStorage persistHookStorage(EntityId ownerId, long hookId, byte[] keyHex, byte[] value) {
        return domainBuilder
                .hookStorage()
                .customize(hookStorage -> hookStorage
                        .hookId(hookId)
                        .key(keyHex)
                        .ownerId(ownerId.getId())
                        .value(value))
                .persist();
    }
}
