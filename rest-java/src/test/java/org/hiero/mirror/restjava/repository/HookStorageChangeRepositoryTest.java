// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.Constants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
final class HookStorageChangeRepositoryTest extends RestJavaIntegrationTest {

    private final HookStorageChangeRepository repository;

    private static final long OWNER_ID_1 = 1000L;
    private static final long HOOK_ID_1 = 1L;

    private static final long TIMESTAMP_1 = 10000L;
    private static final long TIMESTAMP_2 = 20000L;
    private static final long TIMESTAMP_3 = 30000L;
    private static final long TIMESTAMP_4 = 40000L;

    private static final String KEY_1 = "00000000000000000000000000000000000000000000000000000000000aaa01";
    private static final String KEY_2 = "00000000000000000000000000000000000000000000000000000000000aaa02";
    private static final String KEY_3 = "00000000000000000000000000000000000000000000000000000000000aaa03";
    private static final String KEY_4 = "00000000000000000000000000000000000000000000000000000000000aaa04";

    private static final byte[] KEY_1_BYTES = Numeric.hexStringToByteArray(KEY_1);
    private static final byte[] KEY_2_BYTES = Numeric.hexStringToByteArray(KEY_2);
    private static final byte[] KEY_3_BYTES = Numeric.hexStringToByteArray(KEY_3);
    private static final byte[] KEY_4_BYTES = Numeric.hexStringToByteArray(KEY_4);

    private static final int LIMIT = 2;

    public static final String ASC = "asc";
    public static final String DESC = "desc";
    public static final String KEY = "key";

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findLatestChangePerKeyInTimestampRangeForKeyRangeRespectsOrderLimitAndPagination(String order) {
        // given
        final var change1 = persistChange(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_1, KEY_1);
        final var change2 = persistChange(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_2, KEY_2);
        final var change3 = persistChange(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_3, KEY_3);
        final var change4 = persistChange(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_4, KEY_4);

        final var sort = ASC.equalsIgnoreCase(order)
                ? Sort.by(KEY).ascending()
                : Sort.by(KEY).descending();
        final var orderedAll = ASC.equalsIgnoreCase(order)
                ? List.of(change1, change2, change3, change4)
                : List.of(change4, change3, change2, change1);

        final var expectedPage0 = orderedAll.subList(0, LIMIT);
        final var expectedPage1 = orderedAll.subList(LIMIT, orderedAll.size());

        // when
        final var page0Result = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID_1,
                HOOK_ID_1,
                KEY_1_BYTES,
                KEY_4_BYTES,
                TIMESTAMP_1,
                TIMESTAMP_4,
                PageRequest.of(0, LIMIT, sort));

        final var page1Result = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID_1,
                HOOK_ID_1,
                KEY_1_BYTES,
                KEY_4_BYTES,
                TIMESTAMP_1,
                TIMESTAMP_4,
                PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0Result).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);
        assertThat(page1Result).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findLatestChangePerKeyInTimestampRangeForKeysRespectsOrderLimitAndPagination(String order) {
        // given
        final var change1 = persistChange(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_1, KEY_1);
        final var change2 = persistChange(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_2, KEY_2);
        final var change3 = persistChange(OWNER_ID_1, HOOK_ID_1, TIMESTAMP_3, KEY_3);

        final var keys = List.of(KEY_1_BYTES, KEY_2_BYTES, KEY_3_BYTES);

        final var orderedAll =
                ASC.equalsIgnoreCase(order) ? List.of(change1, change2, change3) : List.of(change3, change2, change1);
        final var expectedPage0 = orderedAll.subList(0, LIMIT);
        final var expectedPage1 = orderedAll.subList(LIMIT, orderedAll.size());

        final var sort = new Sort.Order(Direction.fromString(order), Constants.KEY);

        // when
        final var page0Result = repository.findByKeyInAndTimestampBetween(
                OWNER_ID_1,
                HOOK_ID_1,
                keys,
                TIMESTAMP_1,
                TIMESTAMP_3,
                PageRequest.of(0, LIMIT, sort.getDirection(), Constants.KEY));
        final var page1Result = repository.findByKeyInAndTimestampBetween(
                OWNER_ID_1,
                HOOK_ID_1,
                keys,
                TIMESTAMP_1,
                TIMESTAMP_3,
                PageRequest.of(1, LIMIT, sort.getDirection(), Constants.KEY));

        // then
        assertThat(page0Result).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);
        assertThat(page1Result).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    private HookStorageChange persistChange(long ownerId, long hookId, long timestamp, String keyHex) {
        return domainBuilder
                .hookStorageChange()
                .customize(change -> change.ownerId(ownerId)
                        .hookId(hookId)
                        .consensusTimestamp(timestamp)
                        .key(Numeric.hexStringToByteArray(keyHex)))
                .persist();
    }
}
