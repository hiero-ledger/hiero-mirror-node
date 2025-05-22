// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.util.Version;

class RecordFileTest {

    @Test
    void testHapiVersion() {
        RecordFile recordFile = RecordFile.builder()
                .hapiVersionMajor(1)
                .hapiVersionMinor(23)
                .hapiVersionPatch(1)
                .build();
        assertThat(recordFile.getHapiVersion()).isEqualTo(new Version(1, 23, 1));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                ",,", ",1,1", "1,,1", "1,1,",
            })
    void testHapiVersionNotSet(Integer major, Integer minor, Integer patch) {
        RecordFile recordFile = RecordFile.builder()
                .hapiVersionMajor(major)
                .hapiVersionMinor(minor)
                .hapiVersionPatch(patch)
                .build();
        assertThat(recordFile.getHapiVersion()).isEqualTo(RecordFile.HAPI_VERSION_NOT_SET);
    }
}
