// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.io.BaseEncoding;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class EntityIdParameterTest {

    private static final String ALIAS = "HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA";
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();

    public static Stream<Arguments> parsableAliases() {
        return Stream.of(
                Arguments.of(ALIAS, COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), ALIAS),
                Arguments.of("1." + ALIAS, COMMON_PROPERTIES.getShard(), 1, ALIAS),
                Arguments.of("1.2." + ALIAS, 1, 2, ALIAS));
    }

    public static Stream<Arguments> parsableIds() {
        return Stream.of(
                Arguments.of("1.2.3", 1, 2, 3),
                Arguments.of("0", COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), 0L),
                Arguments.of("2.1", COMMON_PROPERTIES.getShard(), 2L, 1L),
                Arguments.of("0.0.4294967295", 0, 0L, 4294967295L),
                Arguments.of("65535.1", COMMON_PROPERTIES.getShard(), 65535L, 1L),
                Arguments.of("1023.65535.274877906943", 1023, 65535L, 274877906943L),
                Arguments.of("4294967295", COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), 4294967295L));
    }

    public static Stream<Arguments> parseableEvmAddresses() {

        return Stream.of(
                Arguments.of(
                        "0x0000000000000000000000000000000000000001",
                        COMMON_PROPERTIES.getShard(),
                        COMMON_PROPERTIES.getRealm(),
                        "0000000000000000000000000000000000000001"),
                Arguments.of(
                        "0000000000000000000000000000000000000001",
                        COMMON_PROPERTIES.getShard(),
                        COMMON_PROPERTIES.getRealm(),
                        "0000000000000000000000000000000000000001"),
                Arguments.of(
                        "0x0000000100000000000000020000000000000003",
                        COMMON_PROPERTIES.getShard(),
                        COMMON_PROPERTIES.getRealm(),
                        "0000000100000000000000020000000000000003"),
                Arguments.of(
                        "0000000100000000000000020000000000000003",
                        COMMON_PROPERTIES.getShard(),
                        COMMON_PROPERTIES.getRealm(),
                        "0000000100000000000000020000000000000003"),
                Arguments.of(
                        "1.2.0000000100000000000000020000000000000003",
                        1,
                        2,
                        "0000000100000000000000020000000000000003"),
                Arguments.of(
                        "0x00007fff000000000000ffff00000000ffffffff",
                        COMMON_PROPERTIES.getShard(),
                        COMMON_PROPERTIES.getRealm(),
                        "00007fff000000000000ffff00000000ffffffff"),
                Arguments.of(
                        "0.0.000000000000000000000000000000000186Fb1b",
                        0,
                        0,
                        "000000000000000000000000000000000186Fb1b"),
                Arguments.of(
                        "0.000000000000000000000000000000000186Fb1b",
                        COMMON_PROPERTIES.getShard(),
                        0,
                        "000000000000000000000000000000000186Fb1b"),
                Arguments.of(
                        "000000000000000000000000000000000186Fb1b",
                        COMMON_PROPERTIES.getShard(),
                        COMMON_PROPERTIES.getRealm(),
                        "000000000000000000000000000000000186Fb1b"),
                Arguments.of(
                        "0x000000000000000000000000000000000186Fb1b",
                        COMMON_PROPERTIES.getShard(),
                        COMMON_PROPERTIES.getRealm(),
                        "000000000000000000000000000000000186Fb1b"));
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
                "0.0.0x000000000000000000000000000000000186Fb1b",
                "0.0x000000000000000000000000000000000186Fb1b",
                "0x2540be3f6001fffffffffffff001fffffffffffff",
                "0x10000000000000000000000000000000000000000",
                "9223372036854775807",
                "AABBCC22",
                "1.2.AABBCC22"
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
    @MethodSource("parsableIds")
    void valueOfId(String givenEntityId, long expectedShard, long expectedRealm, long expectedNum) {
        assertThat(((EntityIdNumParameter) EntityIdParameter.valueOf(givenEntityId)).id())
                .isEqualTo(EntityId.of(expectedShard, expectedRealm, expectedNum));
    }

    @ParameterizedTest
    @MethodSource("parseableEvmAddresses")
    void valueOfEvmAddress(String givenEvmAddress, long expectedShard, long expectedRealm, String expectedEvmAddress) {
        var given = ((EntityIdEvmAddressParameter) EntityIdParameter.valueOf(givenEvmAddress));
        assertThat(Hex.decode(expectedEvmAddress)).isEqualTo(given.evmAddress());
        assertThat(expectedShard).isEqualTo(given.shard());
        assertThat(expectedRealm).isEqualTo(given.realm());
    }

    @ParameterizedTest
    @MethodSource("parsableAliases")
    void valueOfAlias(String givenAlias, long expectedShard, long expectedRealm, String expectedAlias) {
        var given = ((EntityIdAliasParameter) EntityIdParameter.valueOf(givenAlias));
        assertThat(BaseEncoding.base32().omitPadding().decode(expectedAlias)).isEqualTo(given.alias());
        assertThat(expectedShard).isEqualTo(given.shard());
        assertThat(expectedRealm).isEqualTo(given.realm());
    }
}
