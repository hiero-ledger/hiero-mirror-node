// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.util.CommonUtils;
import org.hiero.mirror.RestJavaIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
                order,
                10);

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

        final var expectedResponse =
                order.isAscending() ? List.of(storage1, storage2, storage4) : List.of(storage4, storage2, storage1);

        // when
        final var hookStorage = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                ownerId.getId(), HOOK_ID, storage1.getKey(), storage6.getKey(), order, 10);

        // then
        assertThat(hookStorage).isNotNull().containsExactlyElementsOf(expectedResponse);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalseRespectsOrderAndLimit(Direction order) {
        // given
        final var keys = generateKeys(4);
        final var ownerId = domainBuilder.entityId();

        final var storage1 = persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage2 = persistHookStorage(ownerId, HOOK_ID, keys.get(1));
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2));
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));

        final var hookStorage = order.isAscending()
                ? List.of(storage1, storage2, storage3, storage4)
                : List.of(storage4, storage3, storage2, storage1);

        final var expectedLimited = hookStorage.subList(0, LIMIT);

        // when
        final var limited = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                ownerId.getId(),
                HOOK_ID,
                List.of(storage1.getKey(), storage2.getKey(), storage3.getKey(), storage4.getKey()),
                order,
                LIMIT);

        final var all = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                ownerId.getId(),
                HOOK_ID,
                List.of(storage1.getKey(), storage2.getKey(), storage3.getKey(), storage4.getKey()),
                order,
                10);

        assertThat(limited).isNotNull().hasSize(expectedLimited.size()).containsExactlyElementsOf(expectedLimited);
        assertThat(all).isNotNull().hasSize(hookStorage.size()).containsExactlyElementsOf(hookStorage);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalseRespectsOrderAndLimit(Direction order) {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage2 = persistHookStorage(ownerId, HOOK_ID, keys.get(1));
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2));
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));
        persistHookStorage(ownerId, HOOK_ID, keys.get(4));

        final var hookStorage =
                order.isAscending() ? List.of(storage2, storage3, storage4) : List.of(storage4, storage3, storage2);

        final var expectedLimited = hookStorage.subList(0, LIMIT);

        // when
        final var limited = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                ownerId.getId(), HOOK_ID, storage2.getKey(), storage4.getKey(), order, LIMIT);
        final var all = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                ownerId.getId(), HOOK_ID, storage2.getKey(), storage4.getKey(), order, 10);

        // then
        assertThat(limited).isNotNull().hasSize(expectedLimited.size()).containsExactlyElementsOf(expectedLimited);
        assertThat(all).isNotNull().hasSize(hookStorage.size()).containsExactlyElementsOf(hookStorage);
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
