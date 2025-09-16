// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.data.util.Version;

class HapiVersionConverterTest {

    private final HapiVersionConverter converter = new HapiVersionConverter();

    @ParameterizedTest
    @CsvSource({
        "0.66.0, 0.66.0",
        "0.66.1, 0.66.1",
        "0.67.0, 0.67.0",
        "0.66.0-rc.1, 0.66.0",
        "0.66.1-beta, 0.66.1",
        "0.67.0-SNAPSHOT, 0.67.0",
        "1.0.0-alpha.1, 1.0.0",
        "2.5.10-rc.2, 2.5.10",
        "0.66.0-rc.1-build.123, 0.66.0",
        "1.0.0-alpha-beta-gamma, 1.0.0"
    })
    void convertParameterizedTest(String input, String expectedVersion) {
        Version expected = Version.parse(expectedVersion);
        assertThat(converter.convert(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void convertNullAndEmptyInputs(String input) {
        assertThat(converter.convert(input)).isNull();
    }
}
