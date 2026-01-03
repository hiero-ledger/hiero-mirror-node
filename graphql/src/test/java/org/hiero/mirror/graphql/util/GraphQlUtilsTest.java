// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.commons.codec.binary.Base32;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.graphql.viewmodel.Account;
import org.hiero.mirror.graphql.viewmodel.EntityIdInput;
import org.hiero.mirror.graphql.viewmodel.HbarUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GraphQlUtilsTest {

    @CsvSource(nullValues = "null", textBlock = """
              null,     1,                         1
              HBAR,     null,                      null
              TINYBAR,  1,                         1
              MICROBAR, 100,                       1
              MILIBAR,  100_000,                   1
              HBAR,     100_000_000,               1
              KILOBAR,  100_000_000_000,           1
              MEGABAR,  100_000_000_000_000,       1
              GIGABAR,  100_000_000_000_000_000,   1
              TINYBAR,  5_000_000_000_000_000_000, 5_000_000_000_000_000_000
              MICROBAR, 5_000_000_000_000_000_000, 50_000_000_000_000_000
              MILIBAR,  5_000_000_000_000_000_000, 50_000_000_000_000
              HBAR,     5_000_000_000_000_000_000, 50_000_000_000
              KILOBAR,  5_000_000_000_000_000_000, 50_000_000
              MEGABAR,  5_000_000_000_000_000_000, 50_000
              GIGABAR,  5_000_000_000_000_000_000, 50
            """)
    @ParameterizedTest
    void convertCurrency(HbarUnit unit, Long input, Long output) {
        assertThat(GraphQlUtils.convertCurrency(unit, input)).isEqualTo(output);
    }

    @Test
    void getId() {
        var node = new Account();
        node.setId(Base64.getEncoder().encodeToString("Account:1".getBytes(StandardCharsets.UTF_8)));
        long value = GraphQlUtils.getId(node, i -> Long.parseLong(i.get(0)));
        assertThat(value).isEqualTo(1L);
    }

    @Test
    void toEntityId() {
        var input =
                EntityIdInput.builder().withShard(0L).withRealm(0L).withNum(3L).build();
        assertThat(GraphQlUtils.toEntityId(input))
                .isNotNull()
                .returns(input.getShard(), EntityId::getShard)
                .returns(input.getRealm(), EntityId::getRealm)
                .returns(input.getNum(), EntityId::getNum);
    }

    @Test
    void validateOneOf() {
        GraphQlUtils.validateOneOf("a");
        assertThatThrownBy(GraphQlUtils::validateOneOf).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphQlUtils.validateOneOf("a", "b")).isInstanceOf(IllegalArgumentException.class);
    }

    @NullAndEmptySource
    @ValueSource(
            strings = {
                "KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ",
                "KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ="
            })
    @ParameterizedTest
    void decodeBase32(String alias) {
        Base32 base32 = new Base32();
        var node = Account.builder()
                .withAlias(alias == null ? null : base32.encodeAsString(alias.getBytes()))
                .build();
        byte[] decodedAlias = GraphQlUtils.decodeBase32(node.getAlias());
        assertThat(decodedAlias).isEqualTo(alias == null ? null : alias.getBytes());
    }

    @CsvSource(textBlock = """
             '', 0, 0
             0000000000000000000000000000000000000001, 20, 1
             0000000000000000000000000000000000FAfAfA, 20, 16448250
             0000AaAaAa, 5, 11184810
             0x0000000000000000000000000000000000000001, 20, 1
             0x000000000000000000000000000000000000fafa, 20, 64250
             0x0000000000000000000000000000000000FafafA, 20, 16448250
             0x0000AaAaAa, 5, 11184810
            """)
    @ParameterizedTest
    void decodeEvmAddress(String evmAddress, int expectedLength, long output) {
        var decodedEvmAddress = GraphQlUtils.decodeEvmAddress(evmAddress);
        assertThat(decodedEvmAddress).hasSize(expectedLength).satisfies(e -> assertThat(
                        output > 0 ? new BigInteger(e).longValue() : output)
                .isEqualTo(output));
    }

    @NullAndEmptySource
    @ValueSource(
            strings = {"000000000000000000000000000000000000001", "f5a56e2d52c817161883f50c441c3228cfe54d9fa", "xyzabc"
            })
    @ParameterizedTest
    void invalidEvmAddress(String evmAddress) {
        assertThat(evmAddress)
                .satisfiesAnyOf(
                        e -> assertThatThrownBy(() -> GraphQlUtils.decodeEvmAddress(e))
                                .isInstanceOf(IllegalArgumentException.class),
                        e -> assertThat(GraphQlUtils.decodeEvmAddress(e)).isEmpty());
    }
}
