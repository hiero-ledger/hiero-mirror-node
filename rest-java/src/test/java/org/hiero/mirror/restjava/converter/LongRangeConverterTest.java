// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import org.jooq.postgres.extensions.types.LongRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LongRangeConverterTest {

    @CsvSource(delimiterString = "|", textBlock = """
            1| true  | 100 | false | [1,100)
            1| true  | 100 | true  | [1,100]
            1| true  |     | false | [1,)
             | false | 100 | true  | (,100]
             | false |     | false | (,)
            """)
    @ParameterizedTest
    void convert(Long lower, boolean lowerInclusive, Long upper, boolean upperInclusive, String expected) {
        var longRange = LongRange.longRange(lower, lowerInclusive, upper, upperInclusive);
        var expectedRange = PostgreSQLGuavaRangeType.longRange(expected);
        assertThat(LongRangeConverter.INSTANCE.convert(longRange)).isEqualTo(expectedRange);
    }
}
