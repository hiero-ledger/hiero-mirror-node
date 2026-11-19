// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HookStorageMapperTest {
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private CommonMapper commonMapper;
    private HookStorageMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new HookStorageMapperImpl(commonMapper);
    }

    @Test
    void map() throws DecoderException {
        // given
        final byte[] key = Hex.decodeHex("03e7");
        final byte[] value = Hex.decodeHex("03e8");
        final var hookStorage = domainBuilder
                .hookStorage()
                .customize(
                        h -> h.key(key).modifiedTimestamp(1726874345123456789L).value(value))
                .get();

        // when
        final var result = mapper.map(hookStorage);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo("0x00000000000000000000000000000000000000000000000000000000000003e7");
        assertThat(result.getTimestamp()).isEqualTo("1726874345.123456789");
        assertThat(result.getValue()).isEqualTo("0x00000000000000000000000000000000000000000000000000000000000003e8");
    }

    @Test
    void mapNulls() {
        // given
        final var hookStorage = new HookStorage();

        // when
        final var result = mapper.map(hookStorage);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isNull();
        assertThat(result.getTimestamp()).isNull();
        assertThat(result.getValue()).isNull();
    }
}
