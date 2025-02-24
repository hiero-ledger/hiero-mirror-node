// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.io.BaseEncoding;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.restjava.RestJavaProperties;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdParameterTest {

    @Mock
    private RestJavaProperties properties;

    private MockedStatic<SpringApplicationContext> context;

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(SpringApplicationContext.class);
        when(SpringApplicationContext.getBean(RestJavaProperties.class)).thenReturn(properties);
    }

    @AfterEach
    void closeMocks() {
        context.close();
    }

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
        assertThrows(IllegalArgumentException.class, () -> EntityIdParameter.valueOf(inputId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.4294967296", "32768.65536.4294967296"})
    @DisplayName("EntityId parse from string tests, negative cases for ID having valid format")
    void testInvalidEntity(String input) {
        assertThrows(InvalidEntityException.class, () -> EntityIdParameter.valueOf(input));
    }

    @ParameterizedTest
    @CsvSource({
        "0.0.0,0,0,0",
        "0,0,0,0",
        "0.1,0,0,1",
        "0.0.4294967295,0,0,4294967295",
        "65535.000000001,0,65535,1",
        "32767.65535.4294967295,32767,65535,4294967295",
        "4294967295,0,0,4294967295"
    })
    void valueOfId(String givenEntityId, long expectedShard, long expectedRealm, long expectedNum) {
        assertThat(EntityId.of(expectedShard, expectedRealm, expectedNum))
                .isEqualTo(((EntityIdNumParameter) EntityIdParameter.valueOf(givenEntityId)).id());
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
