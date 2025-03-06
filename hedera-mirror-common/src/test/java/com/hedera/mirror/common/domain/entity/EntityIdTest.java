// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import static com.hedera.mirror.common.domain.entity.EntityId.NUM_BITS;
import static com.hedera.mirror.common.domain.entity.EntityId.REALM_BITS;
import static com.hedera.mirror.common.domain.entity.EntityId.SHARD_BITS;
import static com.hedera.mirror.common.domain.entity.EntityId.UNSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.exception.InvalidEntityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EntityIdTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0, 0",
        "0, 0, 10, 10",
        "0, 0, 4294967295, 4294967295",
        "10, 10, 10, 180146733873889290",
        "1023, 65535, 274877906943, -1",
        "1023, 0, 0, -18014398509481984"
    })
    void testEntityEncoding(long shard, long realm, long num, long encodedId) {
        assertThat(EntityId.of(shard, realm, num).getId()).isEqualTo(encodedId);
    }

    @Test
    void showValue() {
        System.out.println(EntityId.of(511, 65535, 274877906943L).getId());
        System.out.println(EntityId.of(512, 0, 0).getId());
    }

    @Test
    void throwsExceptionEncoding() {
        assertThatThrownBy(() -> EntityId.of(1L << SHARD_BITS, 0, 0)).isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> EntityId.of(0, 1L << REALM_BITS, 0)).isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> EntityId.of(0, 0, 1L << NUM_BITS)).isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> EntityId.of(-1, 0, 0)).isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> EntityId.of(0, -1, 0)).isInstanceOf(InvalidEntityException.class);
        assertThatThrownBy(() -> EntityId.of(0, 0, -1)).isInstanceOf(InvalidEntityException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0, 0",
        "10, 0, 0, 10",
        "4294967295, 0, 0, 4294967295",
        "180146733873889290, 10, 10, 10",
        "-1, 1023, 65535, 274877906943",
        "-18014398509481984, 1023, 0, 0"
    })
    void testEntityDecoding(long encodedId, long shard, long realm, long num) {
        assertThat(EntityId.of(encodedId)).isEqualTo(EntityId.of(shard, realm, num));
    }

    @Test
    void testToString() {
        assertThat(EntityId.of(0, 1, 2).toString()).isEqualTo("0.1.2");
        assertThat(EntityId.of(-1).toString()).isEqualTo("1023.65535.274877906943");
    }

    @CsvSource({"null", ".", "0..1", "0", "0.0", "0.0.0.1", "-1.-2.-3", "0.0.9223372036854775808", "foo.bar.baz"})
    @DisplayName("Convert String to EntityId and fail")
    @ParameterizedTest(name = "with {0}")
    void ofStringNegative(String string) {
        assertThatThrownBy(() -> EntityId.of(string)).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("Convert String to EntityId")
    @Test
    void ofStringPositive() {
        assertThat(EntityId.of("0.0.1")).isEqualTo(EntityId.of(0, 0, 1));
        assertThat(EntityId.of("0.0.0")).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void isUnset() {
        assertThat(EntityId.isUnset(UNSET)).isTrue();
        assertThat(EntityId.isUnset(EntityId.of(-1))).isFalse();
    }
}
