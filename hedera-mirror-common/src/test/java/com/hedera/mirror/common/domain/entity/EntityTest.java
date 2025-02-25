// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EntityTest {

    @ParameterizedTest
    @CsvSource({",,", ",1,1", "1,,1", "1,1,2"})
    void addBalance(Long base, Long delta, Long expected) {
        var entity = new Entity();
        entity.setBalance(base);
        entity.addBalance(delta);
        assertThat(entity.getBalance()).isEqualTo(expected);
    }

    @Test
    void nullCharacters() {
        Entity entity = new Entity();
        entity.setMemo("abc" + (char) 0);
        var actualBytes = entity.getMemo().getBytes(StandardCharsets.UTF_8);
        var expectedBytes = "abc�".getBytes(StandardCharsets.UTF_8);
        assertThat(actualBytes).isEqualTo(expectedBytes);
    }

    @Test
    void history() {
        Entity entity = new Entity();
        assertThat(entity.getTimestampRange()).isNull();
        assertThat(entity.getTimestampLower()).isNull();
        assertThat(entity.getTimestampUpper()).isNull();

        Range<Long> timestampRangeLower = Range.atLeast(1L);
        entity.setTimestampRange(timestampRangeLower);
        assertThat(entity.getTimestampRange()).isEqualTo(timestampRangeLower);
        assertThat(entity.getTimestampLower()).isEqualTo(timestampRangeLower.lowerEndpoint());
        assertThat(entity.getTimestampUpper()).isNull();

        entity.setTimestampUpper(2L);
        assertThat(entity.getTimestampUpper()).isEqualTo(2L);

        Range<Long> timestampRangeUpper = Range.atMost(1L);
        entity.setTimestampRange(timestampRangeUpper);
        assertThat(entity.getTimestampRange()).isEqualTo(timestampRangeUpper);
        assertThat(entity.getTimestampLower()).isNull();
        assertThat(entity.getTimestampUpper()).isEqualTo(timestampRangeUpper.upperEndpoint());

        entity.setTimestampLower(0L);
        assertThat(entity.getTimestampLower()).isZero();
    }
}
