// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.grpc.util.ProtoUtil.DB_ERROR;
import static org.hiero.mirror.grpc.util.ProtoUtil.OVERFLOW_ERROR;
import static org.hiero.mirror.grpc.util.ProtoUtil.UNKNOWN_ERROR;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.hiero.mirror.grpc.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.QueryTimeoutException;
import reactor.core.Exceptions;

class ProtoUtilTest {

    @DisplayName("Convert Timestamp to Instant")
    @ParameterizedTest(name = "with {0}s and {1}ns")
    @CsvSource({"0, 0", "0, 999999999", "10, 0", "31556889864403199, 999999999", "-31557014167219200, 0"})
    void fromTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        Timestamp timestamp =
                Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertThat(ProtoUtil.fromTimestamp(timestamp)).isEqualTo(instant);
    }

    @Test
    void toStatusRuntimeException() {
        var entityId = EntityId.of(1L);
        var message = "boom";

        assertException(Exceptions.failWithOverflow(message), Status.DEADLINE_EXCEEDED, OVERFLOW_ERROR);
        assertException(new ConstraintViolationException(message, null), Status.INVALID_ARGUMENT, message);
        assertException(new IllegalArgumentException(message), Status.INVALID_ARGUMENT, message);
        assertException(new InvalidEntityException(message), Status.INVALID_ARGUMENT, message);
        assertException(new EntityNotFoundException(entityId), Status.NOT_FOUND, "0.0.1 does not exist");
        assertException(new NonTransientDataAccessResourceException(message), Status.UNAVAILABLE, DB_ERROR);
        assertException(new QueryTimeoutException(message), Status.RESOURCE_EXHAUSTED, DB_ERROR);
        assertException(new TimeoutException(message), Status.RESOURCE_EXHAUSTED, DB_ERROR);
        assertException(new RuntimeException(message), Status.UNKNOWN, UNKNOWN_ERROR);
    }

    void assertException(Throwable t, Status status, String message) {
        assertThat(ProtoUtil.toStatusRuntimeException(t))
                .isNotNull()
                .hasMessageContaining(message)
                .extracting(StatusRuntimeException::getStatus)
                .extracting(Status::getCode)
                .isEqualTo(status.getCode());
    }

    @DisplayName("Convert Long to Timestamp")
    @ParameterizedTest(name = "with input {2} will return {0}s and {1}ns")
    @CsvSource({"0, 0, 0", "1574880387, 0, 1574880387000000000", "1574880387, 999999999, 1574880387999999999"})
    void toTimestamp(long timestampSeconds, int timestampNanos, long input) {
        var timestamp = Timestamp.newBuilder()
                .setSeconds(timestampSeconds)
                .setNanos(timestampNanos)
                .build();
        assertThat(ProtoUtil.toTimestamp(input)).isEqualTo(timestamp);
    }

    @DisplayName("Convert Instant to Timestamp")
    @ParameterizedTest(name = "with {0}s and {1}ns")
    @CsvSource({"0, 0", "0, 999999999", "10, 0", "31556889864403199, 999999999", "-31557014167219200, 0"})
    void toTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        Timestamp timestamp =
                Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
        assertThat(ProtoUtil.toTimestamp(instant)).isEqualTo(timestamp);
    }

    @Test
    void toAccountID() {
        assertThat(EntityId.of(0L, 0L, 5L).toAccountID())
                .returns(0L, AccountID::getShardNum)
                .returns(0L, AccountID::getRealmNum)
                .returns(5L, AccountID::getAccountNum);
        assertThat(EntityId.of(1L, 2L, 3L).toAccountID())
                .returns(1L, AccountID::getShardNum)
                .returns(2L, AccountID::getRealmNum)
                .returns(3L, AccountID::getAccountNum);
    }

    @Test
    void toByteString() {
        var bytes = new byte[] {0, 1, 2, 3};
        assertThat(ProtoUtil.toByteString(null)).isEqualTo(ByteString.EMPTY);
        assertThat(ProtoUtil.toByteString(new byte[] {})).isEqualTo(ByteString.EMPTY);
        assertThat(ProtoUtil.toByteString(bytes))
                .isEqualTo(ByteString.copyFrom(bytes))
                .isNotSameAs(ProtoUtil.toByteString(bytes));
    }
}
