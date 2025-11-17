// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HookStorageMapperTest {
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
        final var source = new org.hiero.mirror.common.domain.hook.HookStorage();
        source.setCreatedTimestamp(1234567890000000000L);
        source.setDeleted(false);
        source.setHookId(10L);
        source.setOwnerId(200L);

        source.setKey(Hex.decodeHex("03e7"));
        source.setModifiedTimestamp(1726874345123456789L);
        source.setValue(Hex.decodeHex("00000000000000000000000000000000000000000000000000000000000003e8"));

        // when
        final var result = mapper.map(source);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo("00000000000000000000000000000000000000000000000000000000000003e7");
        assertThat(result.getTimestamp()).isEqualTo("1726874345.123456789");
        assertThat(result.getValue()).isEqualTo("03e8");
    }

    @Test
    void mapNulls() {
        // given
        final var source = new org.hiero.mirror.common.domain.hook.HookStorage();

        // when
        final var result = mapper.map(source);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isNull();
        assertThat(result.getTimestamp()).isEqualTo("0.0");
        assertThat(result.getValue()).isNull();
    }
}
