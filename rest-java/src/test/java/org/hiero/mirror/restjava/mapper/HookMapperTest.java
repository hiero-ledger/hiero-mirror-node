// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import org.apache.commons.codec.DecoderException;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.rest.model.Key;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HookMapperTest {

    private HookMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new HookMapperImpl(new CommonMapperImpl());
    }

    @Test
    void map() throws DecoderException {
        // given
        final var source = new org.hiero.mirror.common.domain.hook.Hook();
        source.setContractId(EntityId.of(100L));
        source.setCreatedTimestamp(1234567890000000000L);
        source.setHookId(10L);
        source.setOwnerId(200L);
        source.setExtensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK);
        source.setTimestampRange(Range.closed(1L, 100000L));
        source.setType(HookType.EVM);
        source.setDeleted(false);

        final var ed25519Hex = "1220" + "a".repeat(64);
        source.setAdminKey(org.apache.commons.codec.binary.Hex.decodeHex(ed25519Hex));

        // when
        final var result = mapper.map(source);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContractId()).isEqualTo("0.0.100");
        assertThat(result.getOwnerId()).isEqualTo("0.0.200");
        assertThat(result.getCreatedTimestamp()).isEqualTo("1234567890.000000000");
        assertThat(result.getHookId()).isEqualTo(10L);
        assertThat(result.getDeleted()).isFalse();
        assertThat(result.getExtensionPoint()).isEqualTo(Hook.ExtensionPointEnum.ACCOUNT_ALLOWANCE_HOOK);
        assertThat(result.getTimestampRange())
                .isEqualTo(new TimestampRangeNullable().from("0.000000001").to("0.000100000"));
        assertThat(result.getType()).isEqualTo(Hook.TypeEnum.EVM);
        assertThat(result.getAdminKey().getType()).isEqualTo(Key.TypeEnum.ED25519);
        assertThat(result.getAdminKey().getKey()).hasSize(64);
        assertThat(result.getAdminKey().getKey())
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    void mapNulls() {
        // given
        final var source = new org.hiero.mirror.common.domain.hook.Hook();
        // when
        final var result = mapper.map(source);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAdminKey()).as("adminKey should be null").isNull();
        assertThat(result.getContractId()).as("contractId should be null").isNull();
        assertThat(result.getCreatedTimestamp())
                .as("createdTimestamp should be null")
                .isNull();
        assertThat(result.getDeleted()).as("deleted should be null").isNull();
        assertThat(result.getExtensionPoint())
                .as("extensionPoint should be null")
                .isNull();
        assertThat(result.getHookId()).as("hookId default for primitive long").isEqualTo(0L);
        assertThat(result.getOwnerId()).as("ownerId should be null").isNull();
        assertThat(result.getTimestampRange())
                .as("timestampRange should be null")
                .isNull();
        assertThat(result.getType()).as("type should be null").isNull();
    }
}
