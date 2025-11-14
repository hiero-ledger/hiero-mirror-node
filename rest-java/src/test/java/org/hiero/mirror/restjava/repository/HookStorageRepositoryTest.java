// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
public class HookStorageRepositoryTest extends RestJavaIntegrationTest {

    private final HookStorageRepository repository;

    private static final long OWNER_ID_1 = 1000L;
    private static final long OWNER_ID_2 = 9999L;

    private static final long HOOK_ID_1 = 1L;
    private static final long HOOK_ID_2 = 2L;

    private static final long TIMESTAMP_1 = 10000L;
    private static final long TIMESTAMP_2 = 20000L;
    private static final long TIMESTAMP_3 = 30000L;
    private static final long TIMESTAMP_4 = 30000L;

    private static final String KEY_1 = "00000000000000000000000000000000000000000000000000000000000aaa03";
    private static final String KEY_2 = "00000000000000000000000000000000000000000000000000000000000aaa04";
    private static final String KEY_3 = "00000000000000000000000000000000000000000000000000000000000aaa05";
    private static final String KEY_4 = "00000000000000000000000000000000000000000000000000000000000aaa06";
    private static final String KEY_5 = "00000000000000000000000000000000000000000000000000000000000aaa08";
    private static final String KEY_6 = "00000000000000000000000000000000000000000000000000000000000aaa09";
    private static final String KEY_NON_EXISTING = "00000000000000000000000000000000000000000000000000000000000aaa07";

    private static final byte[] KEY_1_BYTES = Numeric.hexStringToByteArray(KEY_1);
    private static final byte[] KEY_2_BYTES = Numeric.hexStringToByteArray(KEY_2);
    private static final byte[] KEY_3_BYTES = Numeric.hexStringToByteArray(KEY_3);
    private static final byte[] KEY_4_BYTES = Numeric.hexStringToByteArray(KEY_4);
    private static final byte[] KEY_5_BYTES = Numeric.hexStringToByteArray(KEY_5);
    private static final byte[] KEY_6_BYTES = Numeric.hexStringToByteArray(KEY_6);
    private static final byte[] KEY_NON_EXISTING_BYTES = Numeric.hexStringToByteArray(KEY_NON_EXISTING);

    public static final String ASC = "asc";
    public static final String DESC = "desc";
    public static final String KEY = "key";

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(String order) {
        // given
        final var hookStorage1 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_1, KEY_1);
        persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_2, KEY_2); // KEY_2 won't be passed to the method params
        persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_3, KEY_3, ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var hookStorage4 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_4, KEY_4);
        persistHookStorage(OWNER_ID_2, HOOK_ID_1, TIMESTAMP_1, KEY_5); // different ownerId
        persistHookStorage(OWNER_ID_1, HOOK_ID_2, TIMESTAMP_1, KEY_6); // different hookId

        final var sort = ASC.equalsIgnoreCase(order)
                ? Sort.by(KEY).ascending()
                : Sort.by(KEY).descending();

        final var expectedResponse =
                ASC.equalsIgnoreCase(order) ? List.of(hookStorage1, hookStorage4) : List.of(hookStorage4, hookStorage1);

        // when
        final var result = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                OWNER_ID_1,
                HOOK_ID_1,
                List.of(
                        KEY_1_BYTES,
                        KEY_3_BYTES, // deleted because of empty key value
                        KEY_4_BYTES,
                        KEY_NON_EXISTING_BYTES, // non-existing
                        KEY_5_BYTES, // existing, but for different ownerId
                        KEY_6_BYTES // existing, but for different hookId
                        ),
                PageRequest.of(0, 10, sort));

        // then
        assertThat(result).isNotNull().containsExactlyElementsOf(expectedResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(String order) {
        // given
        final var hookStorage1 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_1, KEY_1);
        final var hookStorage2 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_2, KEY_2);
        persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_3, KEY_3, ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var hookStorage4 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_4, KEY_4);
        persistHookStorage(OWNER_ID_2, HOOK_ID_1, TIMESTAMP_1, KEY_5); // different ownerId
        persistHookStorage(OWNER_ID_1, HOOK_ID_2, TIMESTAMP_1, KEY_6); // different hookId

        final var sort = ASC.equalsIgnoreCase(order)
                ? Sort.by(KEY).ascending()
                : Sort.by(KEY).descending();

        final var expectedResponse = ASC.equalsIgnoreCase(order)
                ? List.of(hookStorage1, hookStorage2, hookStorage4)
                : List.of(hookStorage4, hookStorage2, hookStorage1);

        // when
        final var result = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                OWNER_ID_1, HOOK_ID_1, KEY_1_BYTES, KEY_6_BYTES, PageRequest.of(0, 10, sort));

        // then
        assertThat(result).isNotNull().containsExactlyElementsOf(expectedResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalseRespectsOrderLimitAndPagination(String order) {
        // given
        final var hookStorage1 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_1, KEY_1);
        final var hookStorage2 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_2, KEY_2);
        final var hookStorage3 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_3, KEY_3);
        final var hookStorage4 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_4, KEY_4);

        int limit = 2;

        final var sort = ASC.equalsIgnoreCase(order)
                ? Sort.by(KEY).ascending()
                : Sort.by(KEY).descending();

        final var orderedAll = ASC.equalsIgnoreCase(order)
                ? List.of(hookStorage1, hookStorage2, hookStorage3, hookStorage4)
                : List.of(hookStorage4, hookStorage3, hookStorage2, hookStorage1);

        final var expectedPage0 = orderedAll.subList(0, limit);
        final var expectedPage1 = orderedAll.subList(limit, orderedAll.size());

        // when
        final var page0Result = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                OWNER_ID_1,
                HOOK_ID_1,
                List.of(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES, KEY_4_BYTES),
                PageRequest.of(0, limit, sort));

        final var page1Result = repository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                OWNER_ID_1,
                HOOK_ID_1,
                List.of(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES, KEY_4_BYTES),
                PageRequest.of(1, limit, sort));

        assertThat(page0Result).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);

        assertThat(page1Result).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalseRespectsOrderLimitAndPagination(String order) {
        // given
        persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_1, KEY_1);
        final var hookStorage2 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_2, KEY_2);
        final var hookStorage3 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_3, KEY_3);
        final var hookStorage4 = persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_4, KEY_4);
        persistHookStorage(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_4, KEY_5);

        int limit = 2;

        final var sort = ASC.equalsIgnoreCase(order)
                ? Sort.by(KEY).ascending()
                : Sort.by(KEY).descending();

        final var orderedAll = ASC.equalsIgnoreCase(order)
                ? List.of(hookStorage2, hookStorage3, hookStorage4)
                : List.of(hookStorage4, hookStorage3, hookStorage2);

        final var expectedPage0 = orderedAll.subList(0, limit);
        final var expectedPage1 = orderedAll.subList(limit, orderedAll.size());

        // when
        final var page0Result = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                OWNER_ID_1, HOOK_ID_1, KEY_2_BYTES, KEY_4_BYTES, PageRequest.of(0, limit, sort));

        final var page1Result = repository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                OWNER_ID_1, HOOK_ID_1, KEY_2_BYTES, KEY_4_BYTES, PageRequest.of(1, limit, sort));

        // then
        assertThat(page0Result).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);

        assertThat(page1Result).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    private HookStorage persistHookStorage(long ownerId, long hookId, long timestamp, String keyHex) {
        return domainBuilder
                .hookStorage()
                .customize(hookStorage -> hookStorage
                        .hookId(hookId)
                        .key(Numeric.hexStringToByteArray(keyHex))
                        .modifiedTimestamp(timestamp)
                        .ownerId(ownerId))
                .persist();
    }

    private HookStorage persistHookStorage(long ownerId, long hookId, long timestamp, String keyHex, byte[] value) {
        return domainBuilder
                .hookStorage()
                .customize(hookStorage -> hookStorage
                        .hookId(hookId)
                        .key(Numeric.hexStringToByteArray(keyHex))
                        .modifiedTimestamp(timestamp)
                        .ownerId(ownerId)
                        .value(value))
                .persist();
    }
}
