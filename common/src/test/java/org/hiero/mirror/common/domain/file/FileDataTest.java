// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

final class FileDataTest {

    @Test
    void getDataSize() {
        var fileData = new FileData();
        assertThat(fileData.getDataSize()).isZero();

        fileData.setFileData(ArrayUtils.EMPTY_BYTE_ARRAY);
        assertThat(fileData.getDataSize()).isZero();

        fileData.setFileData(new byte[10]);
        assertThat(fileData.getDataSize()).isEqualTo(10);
    }
}
