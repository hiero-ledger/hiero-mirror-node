// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

final class BlockNodeFailureTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            ERROR, false
            IGNORED, false
            REJECTED, true
            TIMEOUT, false
            UNREACHABLE, true
            """)
    void isDeactivating(final BlockNodeFailure failure, final boolean expected) {
        assertThat(failure.isDeactivating()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("ofStatusCodeProvider")
    void ofStatusCode(final Status.Code code, final BlockNodeFailure expected) {
        final var status = Status.fromCode(code);
        assertThat(BlockNodeFailure.of(new StatusException(status))).isEqualTo(expected);
        assertThat(BlockNodeFailure.of(new StatusRuntimeException(status))).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("ofThrowableProvider")
    void ofThrowable(final Throwable throwable, final BlockNodeFailure expected) {
        assertThat(BlockNodeFailure.of(throwable)).isEqualTo(expected);
    }

    private static Stream<Arguments> ofStatusCodeProvider() {
        return Stream.of(Status.Code.values())
                .map(code -> Arguments.of(
                        code,
                        switch (code) {
                            case DEADLINE_EXCEEDED, UNAVAILABLE -> BlockNodeFailure.UNREACHABLE;
                            case PERMISSION_DENIED, UNAUTHENTICATED, UNIMPLEMENTED -> BlockNodeFailure.REJECTED;
                            default -> BlockNodeFailure.ERROR;
                        }));
    }

    private static Stream<Arguments> ofThrowableProvider() {
        return Stream.of(
                Arguments.of(new InterruptedException(), BlockNodeFailure.IGNORED),
                Arguments.of(
                        Status.CANCELLED.withCause(new InterruptedException()).asRuntimeException(),
                        BlockNodeFailure.IGNORED),
                Arguments.of(new TimeoutException(), BlockNodeFailure.TIMEOUT),
                Arguments.of(new BlockStreamException(new TimeoutException()), BlockNodeFailure.TIMEOUT),
                Arguments.of(
                        new BlockStreamException(new StatusException(Status.UNAVAILABLE)),
                        BlockNodeFailure.UNREACHABLE),
                Arguments.of(
                        Status.UNKNOWN.withCause(new ClosedChannelException()).asException(),
                        BlockNodeFailure.UNREACHABLE),
                Arguments.of(
                        Status.UNKNOWN.withCause(new ConnectException()).asException(), BlockNodeFailure.UNREACHABLE),
                Arguments.of(
                        Status.UNKNOWN.withCause(new SSLException("handshake")).asException(),
                        BlockNodeFailure.UNREACHABLE),
                Arguments.of(Status.UNKNOWN.withCause(new IOException()).asException(), BlockNodeFailure.ERROR),
                Arguments.of(new BlockStreamException("bad block"), BlockNodeFailure.ERROR),
                Arguments.of(new IllegalStateException(), BlockNodeFailure.ERROR));
    }
}
