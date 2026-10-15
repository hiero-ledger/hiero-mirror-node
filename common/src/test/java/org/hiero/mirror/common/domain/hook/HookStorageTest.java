// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class HookStorageTest {

    @Test
    void builderWithoutValueIsDeleted() {
        final var hookStorage = HookStorage.builder().hookId(1L).ownerId(2L).build();

        assertThat(hookStorage.isDeleted()).isTrue();
        assertThat(hookStorage.getValue()).isNull();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            '', true
            00, true
            01, false
            """)
    void builderDerivesDeletedFromValue(final String hex, final boolean deleted) {
        final var value = HexFormat.of().parseHex(hex);
        final var hookStorage = HookStorage.builder().value(value).build();

        assertThat(hookStorage.isDeleted()).isEqualTo(deleted);
        assertThat(hookStorage.toBuilder().build().isDeleted()).isEqualTo(deleted);
    }
}
