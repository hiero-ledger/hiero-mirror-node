// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Classifies a failed block node call by how the node should be treated.
 */
@NullMarked
public enum BlockNodeFailure {
    /** The node was reached but the call failed. */
    ERROR,
    /** The call was interrupted by the importer itself. */
    IGNORED,
    /** The node refused the call as unsupported or unauthorized, which retrying won't fix. */
    REJECTED,
    /** The response didn't arrive within the read timeout. */
    TIMEOUT,
    /** The node couldn't be reached. */
    UNREACHABLE;

    /** Whether the failure deactivates the node at once, since retrying soon won't help. */
    public boolean isDeactivating() {
        return this == REJECTED || this == UNREACHABLE;
    }

    public static BlockNodeFailure of(final @Nullable Throwable throwable) {
        for (var t = throwable; t != null; t = t.getCause()) {
            final var failure = switch (t) {
                case InterruptedException _ -> IGNORED;
                case StatusException e -> of(e.getStatus());
                case StatusRuntimeException e -> of(e.getStatus());
                case TimeoutException _ -> TIMEOUT;
                // Transport failures, report as UNREACHABLE
                case ClosedChannelException _,
                        SocketException _,
                        SSLException _,
                        UnknownHostException _,
                        UnresolvedAddressException _ -> UNREACHABLE;
                default -> null;
            };

            if (failure != null) {
                return failure;
            }
        }

        return ERROR;
    }

    private static BlockNodeFailure of(final Status status) {
        return switch (status.getCode()) {
            case CANCELLED -> status.getCause() instanceof InterruptedException ? IGNORED : ERROR;
            case DEADLINE_EXCEEDED, UNAVAILABLE -> UNREACHABLE;
            case PERMISSION_DENIED, UNAUTHENTICATED, UNIMPLEMENTED -> REJECTED;
            case UNKNOWN -> of(status.getCause());
            default -> ERROR;
        };
    }
}
