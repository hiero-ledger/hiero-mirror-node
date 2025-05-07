// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StreamFileSignatureTest {

    @ParameterizedTest
    @CsvSource({
        "2020-06-03T16_45_00.100200345Z_Balances.csv_sig, 5, 2020-06-03T16_45_00.100200345Z_Balances.csv",
        "2020-06-03T16_45_00.100200345Z_Balances.pb_sig, 6, 2020-06-03T16_45_00.100200345Z_Balances.pb.gz",
        "2020-06-03T16_45_00.100200345Z_Balances.pb_sig.gz, 5, 2020-06-03T16_45_00.100200345Z_Balances.pb.gz",
        "2020-06-03T16_45_00.100200345Z.rcd_sig, 5, 2020-06-03T16_45_00.100200345Z.rcd",
        "2020-06-03T16_45_00.100200345Z.rcd_sig, 6, 2020-06-03T16_45_00.100200345Z.rcd.gz"
    })
    void getDataFilename(String filename, byte version, String expected) {
        var streamFileSignature = new StreamFileSignature();
        streamFileSignature.setFilename(StreamFilename.from(filename));
        streamFileSignature.setVersion(version);
        assertThat(streamFileSignature.getDataFilename().getFilename()).isEqualTo(expected);
    }
}
