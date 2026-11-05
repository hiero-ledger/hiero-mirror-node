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

final class BlockNodeStatusTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            ERROR, false
            HEALTHY, false
            IGNORED, false
            NOT_AVAILABLE, false
            REJECTED, true
            TIMEOUT, false
            UNREACHABLE, true
            """)
    void isDeactivating(final BlockNodeStatus status, final boolean expected) {
        assertThat(status.isDeactivating()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("ofStatusCodeProvider")
    void ofStatusCode(final Status.Code code, final BlockNodeStatus expected) {
        final var status = Status.fromCode(code);
        assertThat(BlockNodeStatus.of(new StatusException(status))).isEqualTo(expected);
        assertThat(BlockNodeStatus.of(new StatusRuntimeException(status))).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("ofThrowableProvider")
    void ofThrowable(final Throwable throwable, final BlockNodeStatus expected) {
        assertThat(BlockNodeStatus.of(throwable)).isEqualTo(expected);
    }

    private static Stream<Arguments> ofStatusCodeProvider() {
        return Stream.of(Status.Code.values())
                .map(code -> Arguments.of(
                        code,
                        switch (code) {
                            case DEADLINE_EXCEEDED, UNAVAILABLE -> BlockNodeStatus.UNREACHABLE;
                            case PERMISSION_DENIED, UNAUTHENTICATED, UNIMPLEMENTED -> BlockNodeStatus.REJECTED;
                            default -> BlockNodeStatus.ERROR;
                        }));
    }

    private static Stream<Arguments> ofThrowableProvider() {
        return Stream.of(
                Arguments.of(new InterruptedException(), BlockNodeStatus.IGNORED),
                Arguments.of(
                        Status.CANCELLED.withCause(new InterruptedException()).asRuntimeException(),
                        BlockNodeStatus.IGNORED),
                Arguments.of(new TimeoutException(), BlockNodeStatus.TIMEOUT),
                Arguments.of(new BlockStreamException(new TimeoutException()), BlockNodeStatus.TIMEOUT),
                Arguments.of(
                        new BlockStreamException(new StatusException(Status.UNAVAILABLE)), BlockNodeStatus.UNREACHABLE),
                Arguments.of(
                        Status.UNKNOWN.withCause(new ClosedChannelException()).asException(),
                        BlockNodeStatus.UNREACHABLE),
                Arguments.of(
                        Status.UNKNOWN.withCause(new ConnectException()).asException(), BlockNodeStatus.UNREACHABLE),
                Arguments.of(
                        Status.UNKNOWN.withCause(new SSLException("handshake")).asException(),
                        BlockNodeStatus.UNREACHABLE),
                Arguments.of(Status.UNKNOWN.withCause(new IOException()).asException(), BlockNodeStatus.ERROR),
                Arguments.of(new BlockStreamException("bad block"), BlockNodeStatus.ERROR),
                Arguments.of(new IllegalStateException(), BlockNodeStatus.ERROR));
    }
}
