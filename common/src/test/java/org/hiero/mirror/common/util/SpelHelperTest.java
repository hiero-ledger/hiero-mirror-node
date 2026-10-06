// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

final class SpelHelperTest {

    private final SpelHelper spelHelper = new SpelHelper();

    @Test
    void isNullOrEmptyNull() {
        assertThat(spelHelper.isNullOrEmpty(null)).isTrue();
    }

    @Test
    void isNullOrEmptyTrue() {
        final var values = List.of(
                Optional.empty(),
                List.of(),
                new ArrayList<>(),
                Set.of(),
                new HashSet<>(),
                new ArrayDeque<>(),
                Map.of(),
                new HashMap<>(),
                new TreeMap<>());
        assertThat(values)
                .allSatisfy(value -> assertThat(spelHelper.isNullOrEmpty(value))
                        .as("%s", value.getClass().getName())
                        .isTrue());
    }

    @Test
    void isNullOrEmptyFalse() {
        final var values = List.of(
                Optional.of("a"),
                Optional.of(List.of()), // not recursive
                List.of(1),
                Set.of(1),
                Map.of("a", 1));
        assertThat(values)
                .allSatisfy(value -> assertThat(spelHelper.isNullOrEmpty(value))
                        .as("%s", value)
                        .isFalse());
    }

    // Types without an explicit branch are never considered empty
    @Test
    void isNullOrEmptyUnsupportedTypes() {
        assertThat(List.of("", "a", new byte[0], new Object[0], 0, new Object()))
                .allSatisfy(value -> assertThat(spelHelper.isNullOrEmpty(value))
                        .as("%s", value.getClass().getSimpleName())
                        .isFalse());
    }

    @Test
    void getCacheKeyNull() {
        assertThat(spelHelper.getCacheKey(null)).isEqualTo(Bytes.EMPTY);
    }

    @Test
    void getCacheKeyEmptyArray() {
        assertThat(spelHelper.getCacheKey(new byte[0])).isEqualTo(Bytes.EMPTY);
    }

    @Test
    void getCacheKey() {
        var expected = Bytes.wrap(new byte[] {1, 2, 3});
        assertThat(spelHelper.getCacheKey(new byte[] {1, 2, 3}))
                .isEqualTo(expected)
                .hasSameHashCodeAs(expected);
    }

    @Test
    void getCacheKeyDifferentContent() {
        assertThat(spelHelper.getCacheKey(new byte[] {1, 2, 3}))
                .isNotEqualTo(spelHelper.getCacheKey(new byte[] {1, 2, 4}));
    }
}
