// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.io.BaseEncoding;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdParameterTest {
    private final CommonProperties commonProperties = CommonProperties.getInstance();

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "0.1.x",
                "x.1",
                "0.1.2.3",
                "a",
                "a.b.c",
                "-1.-1.-1",
                "-1",
                "0.0.-1",
                "100000.65535.000000001",
                "100000.000000001",
                "0x",
                "0x00000001000000000000000200000000000000034",
                "0x0.0.00007fff000000000000ffff00000000ffffffff",
                "0x2540be3f6001fffffffffffff001fffffffffffff",
                "0x10000000000000000000000000000000000000000",
                "9223372036854775807"
            })
    @DisplayName("EntityId parse from string tests, negative cases")
    void entityParseFromStringFailure(String inputId) {
        assertThatThrownBy(() -> EntityIdParameter.valueOf(inputId)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.65536.1", "1024.1.1"})
    @DisplayName("EntityId parse from string tests, negative cases for ID having valid format")
    void testInvalidEntity(String input) {
        assertThatThrownBy(() -> EntityIdParameter.valueOf(input)).isInstanceOf(InvalidEntityException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0x0000000000000000000000000000000000000001", "AABBCC22"})
    void nonDefaultShardRealm(String value) {
        commonProperties.setRealm(1000L);
        commonProperties.setShard(1);
        assertThat(EntityIdParameter.valueOf(value))
                .returns(commonProperties.getRealm(), EntityIdParameter::realm)
                .returns(commonProperties.getShard(), EntityIdParameter::shard);
        commonProperties.setRealm(0);
        commonProperties.setShard(0);
    }

    @ParameterizedTest
    @CsvSource({
        "1.2.3,1,2,3",
        "0,0,0,0",
        "2.1,0,2,1",
        "0.0.4294967295,0,0,4294967295",
        "65535.1,0,65535,1",
        "1023.65535.274877906943,1023,65535,274877906943",
        "4294967295,0,0,4294967295"
    })
    void valueOfId(String givenEntityId, long expectedShard, long expectedRealm, long expectedNum) {
        assertThat(((EntityIdNumParameter) EntityIdParameter.valueOf(givenEntityId)).id())
                .isEqualTo(EntityId.of(expectedShard, expectedRealm, expectedNum));
    }

    @ParameterizedTest
    @CsvSource({
        "0x0000000000000000000000000000000000000001,0,0,0000000000000000000000000000000000000001",
        "0000000000000000000000000000000000000001,0,0,0000000000000000000000000000000000000001",
        "0x0000000100000000000000020000000000000003,0,0,0000000100000000000000020000000000000003",
        "0000000100000000000000020000000000000003,0,0,0000000100000000000000020000000000000003",
        "1.2.0000000100000000000000020000000000000003,1,2,0000000100000000000000020000000000000003",
        "0x00007fff000000000000ffff00000000ffffffff,0,0,00007fff000000000000ffff00000000ffffffff",
        "0.0.000000000000000000000000000000000186Fb1b,0,0,000000000000000000000000000000000186Fb1b",
        "0.0x000000000000000000000000000000000186Fb1b,0,0,000000000000000000000000000000000186Fb1b",
        "0.0.0x000000000000000000000000000000000186Fb1b,0,0,000000000000000000000000000000000186Fb1b",
        "0.000000000000000000000000000000000186Fb1b,0,0,000000000000000000000000000000000186Fb1b",
        "000000000000000000000000000000000186Fb1b,0,0,000000000000000000000000000000000186Fb1b",
        "0x000000000000000000000000000000000186Fb1b,0,0,000000000000000000000000000000000186Fb1b"
    })
    void valueOfEvmAddress(String givenEvmAddress, long expectedShard, long expectedRealm, String expectedEvmAddress) {
        var given = ((EntityIdEvmAddressParameter) EntityIdParameter.valueOf(givenEvmAddress));
        assertThat(Hex.decode(expectedEvmAddress)).isEqualTo(given.evmAddress());
        assertThat(expectedShard).isEqualTo(given.shard());
        assertThat(expectedRealm).isEqualTo(given.realm());
    }

    @ParameterizedTest
    @CsvSource({"AABBCC22,0,0,AABBCC22", "1.AABBCC22,0,1,AABBCC22", "1.2.AABBCC22,1,2,AABBCC22"})
    void valueOfAlias(String givenAlias, long expectedShard, long expectedRealm, String expectedAlias) {
        var given = ((EntityIdAliasParameter) EntityIdParameter.valueOf(givenAlias));
        assertThat(BaseEncoding.base32().omitPadding().decode(expectedAlias)).isEqualTo(given.alias());
        assertThat(expectedShard).isEqualTo(given.shard());
        assertThat(expectedRealm).isEqualTo(given.realm());
    }
}
