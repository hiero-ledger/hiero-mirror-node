// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import java.nio.file.AccessDeniedException;
import java.util.Optional;
import java.util.stream.LongStream;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class DatabaseAccessorTest {

    @InjectSoftAssertions
    private SoftAssertions softly;

    static class DBAccessorTestImpl extends DatabaseAccessor<AccessDeniedException, LongStream> {
        @NonNull
        public Optional<LongStream> get(@NonNull final AccessDeniedException ignored, final Optional<Long> timestamp) {
            return Optional.empty();
        }
    }

    @Test
    void genericTypeParametersReflectionTest() {
        final var sut = new DBAccessorTestImpl();
        softly.assertThat(sut.getKeyClass()).isEqualTo(AccessDeniedException.class);
        softly.assertThat(sut.getValueClass()).isEqualTo(LongStream.class);
    }
}
