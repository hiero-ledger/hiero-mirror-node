// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.hiero.base.io.streams.SerializableDataInputStream;
import org.hiero.base.io.streams.SerializableDataOutputStream;

public class RichInstant implements Comparable<RichInstant> {
    public static final RichInstant MISSING_INSTANT = new RichInstant(0L, 0);

    private final int nanos;
    private final long seconds;

    public RichInstant() {
        nanos = 0;
        seconds = 0;
    }

    public RichInstant(final long seconds, final int nanos) {
        this.seconds = seconds;
        this.nanos = nanos;
    }

    public static RichInstant from(final SerializableDataInputStream in) throws IOException {
        return new RichInstant(in.readLong(), in.readInt());
    }

    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(seconds);
        out.writeInt(nanos);
    }

    /* --- Object --- */

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("seconds", seconds)
                .add("nanos", nanos)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || RichInstant.class != o.getClass()) {
            return false;
        }
        final var that = (RichInstant) o;
        return seconds == that.seconds && nanos == that.nanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seconds, nanos);
    }

    /* --- Bean --- */

    public long getSeconds() {
        return seconds;
    }

    public int getNanos() {
        return nanos;
    }

    /* --- Helpers --- */

    public static RichInstant fromGrpc(final Timestamp grpc) {
        return grpc.equals(Timestamp.getDefaultInstance())
                ? MISSING_INSTANT
                : new RichInstant(grpc.getSeconds(), grpc.getNanos());
    }

    public Timestamp toGrpc() {
        return isMissing()
                ? Timestamp.getDefaultInstance()
                : Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }

    public boolean isAfter(final RichInstant other) {
        return (seconds > other.seconds) || (seconds == other.seconds && (nanos > other.nanos));
    }

    public Instant toJava() {
        return Instant.ofEpochSecond(seconds, nanos);
    }

    public static RichInstant fromJava(final Instant when) {
        return Optional.ofNullable(when)
                .map(at -> new RichInstant(at.getEpochSecond(), at.getNano()))
                .orElse(null);
    }

    public boolean isMissing() {
        return this.equals(MISSING_INSTANT);
    }

    @Override
    public int compareTo(@NonNull final RichInstant o) {
        if (o.seconds == seconds) {
            return Integer.compare(nanos, o.nanos);
        }
        return Long.compare(seconds, o.seconds);
    }
}
