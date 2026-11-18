// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.List;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.junit.jupiter.api.Test;

final class HookStorageMapperTest {

    private final HookStorageMapper mapper = new HookStorageMapperImpl(new CommonMapperImpl());

    @Test
    void map() {
        // given
        final var slot = new HookStorageSlot(
                HexFormat.of().parseHex("03e7"),
                1726874345123456789L,
                HexFormat.of().parseHex("03e8"));

        // when
        final var result = mapper.map(slot);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo("0x00000000000000000000000000000000000000000000000000000000000003e7");
        assertThat(result.getTimestamp()).isEqualTo("1726874345.123456789");
        assertThat(result.getValue()).isEqualTo("0x00000000000000000000000000000000000000000000000000000000000003e8");
    }

    @Test
    void mapEmptyValue() {
        // given
        final var slot = new HookStorageSlot(HexFormat.of().parseHex("03e7"), 1L, new byte[0]);

        // when
        final var result = mapper.map(slot);

        // then
        assertThat(result.getKey()).isEqualTo("0x00000000000000000000000000000000000000000000000000000000000003e7");
        assertThat(result.getTimestamp()).isEqualTo("0.000000001");
        assertThat(result.getValue()).isEqualTo("0x" + "0".repeat(64));
    }

    @Test
    void mapNullValue() {
        // given
        final var slot = new HookStorageSlot(HexFormat.of().parseHex("03e7"), 1L, null);

        // when
        final var result = mapper.map(slot);

        // then
        assertThat(result.getKey()).isEqualTo("0x00000000000000000000000000000000000000000000000000000000000003e7");
        assertThat(result.getValue()).isNull();
    }

    @Test
    void mapList() {
        // given
        final var slot1 = new HookStorageSlot(new byte[] {1}, 1L, new byte[] {2});
        final var slot2 = new HookStorageSlot(new byte[] {3}, 2L, new byte[] {4});

        // when, then
        assertThat(mapper.map(List.of(slot1, slot2))).hasSize(2);
        assertThat(mapper.map(List.of())).isEmpty();
    }
}
