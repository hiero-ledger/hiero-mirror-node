// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.hiero.mirror.grpc.exception.EntityNotFoundException;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessException;
import reactor.core.Exceptions;

@CustomLog
@UtilityClass
public final class ProtoUtil {

    static final String DB_ERROR = "Error querying the data source. Please retry later";
    static final String OVERFLOW_ERROR = "Client lags too much behind. Please retry later";
    static final String UNKNOWN_ERROR = "Unknown error";

    public static Instant fromTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static ByteString toByteString(byte[] bytes) {
        if (bytes == null) {
            return ByteString.EMPTY;
        }
        return UnsafeByteOperations.unsafeWrap(bytes);
    }

    public static StatusRuntimeException toStatusRuntimeException(Throwable t) {
        if (Exceptions.isOverflow(t)) {
            return clientError(t, Status.DEADLINE_EXCEEDED, OVERFLOW_ERROR);
        } else if (t instanceof ConstraintViolationException
                || t instanceof IllegalArgumentException
                || t instanceof InvalidEntityException) {
            return clientError(t, Status.INVALID_ARGUMENT, t.getMessage());
        } else if (t instanceof EntityNotFoundException) {
            return clientError(t, Status.NOT_FOUND, t.getMessage());
        } else if (t instanceof TransientDataAccessException || t instanceof TimeoutException) {
            return serverError(t, Status.RESOURCE_EXHAUSTED, DB_ERROR);
        } else if (t instanceof NonTransientDataAccessResourceException) {
            return serverError(t, Status.UNAVAILABLE, DB_ERROR);
        } else {
            return serverError(t, Status.UNKNOWN, UNKNOWN_ERROR);
        }
    }

    private static StatusRuntimeException clientError(Throwable t, Status status, String message) {
        log.warn("Client error {}: {}", t.getClass().getSimpleName(), t.getMessage());
        return status.augmentDescription(message).asRuntimeException();
    }

    private static StatusRuntimeException serverError(Throwable t, Status status, String message) {
        log.error("Server error: ", t);
        return status.augmentDescription(message).asRuntimeException();
    }

    public static Timestamp toTimestamp(Long secondsNanos) {
        if (secondsNanos == null) {
            return null;
        }
        return toTimestamp(Instant.ofEpochSecond(0, secondsNanos));
    }

    public static Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
