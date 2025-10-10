// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Bytes;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
final class ContractBytecodeServiceTest extends ImporterIntegrationTest {

    private final ContractBytecodeService service;

    @Test
    void empty() {
        assertThat(service.get(domainBuilder.entityId())).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validBytecodeFile(boolean withHexPrefix) {
        // given
        byte[] expected = domainBuilder.bytes(64);
        var fileData = domainBuilder
                .fileData()
                .customize(f -> f.fileData(TestUtils.toBytecodeFileContent(expected, withHexPrefix)))
                .persist();

        // when, then
        assertThat(service.get(fileData.getEntityId())).isEqualTo(expected);
    }

    @Test
    void invalidBytecodeFile() {
        // given
        var fileData = domainBuilder
                .fileData()
                .customize(f -> f.fileData(Bytes.concat(new byte[] {-100}, domainBuilder.bytes(63))))
                .persist();

        // when, then
        assertThat(service.get(fileData.getEntityId())).isNull();
    }
}
