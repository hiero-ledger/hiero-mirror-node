// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import static com.hedera.mirror.common.domain.entity.EntityId.NUM_BITS;
import static com.hedera.mirror.common.domain.entity.EntityId.REALM_BITS;
import static com.hedera.mirror.common.domain.entity.EntityId.SHARD_BITS;
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
        "10, 10, 10, 2814792716779530",
        "32767, 65535, 4294967295, 9223372036854775807", // max +ve for shard, max for realm, max for num = max
        // +ve long
        "32767, 0, 0, 9223090561878065152"
    })
    void testEntityEncoding(long shard, long realm, long num, long encodedId) {
        assertThat(EntityId.of(shard, realm, num).getId()).isEqualTo(encodedId);
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
        "2814792716779530, 10, 10, 10",
        "9223372036854775807, 32767, 65535, 4294967295", // max +ve for shard, max for realm, max for num = max
        // +ve long
        "9223090561878065152, 32767, 0, 0"
    })
    void testEntityDecoding(long encodedId, long shard, long realm, long num) {
        assertThat(EntityId.of(encodedId)).isEqualTo(EntityId.of(shard, realm, num));
    }

    @Test
    void throwsExceptionDecoding() {
        assertThatThrownBy(() -> EntityId.of(-1)).isInstanceOf(InvalidEntityException.class);
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
}
