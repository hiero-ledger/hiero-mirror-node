// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.state.submerkle.RichInstant.from;
import static com.hedera.services.state.submerkle.RichInstant.fromGrpc;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;

import com.hederahashgraph.api.proto.java.Timestamp;
import java.io.IOException;
import java.time.Instant;
import org.hiero.base.io.streams.SerializableDataInputStream;
import org.hiero.base.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RichInstantTest {
    private static final long SECONDS = 1_234_567L;
    private static final int NANOS = 890;

    private RichInstant subject;

    @BeforeEach
    void setup() {
        subject = new RichInstant(SECONDS, NANOS);
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeLong(SECONDS);
        inOrder.verify(out).writeInt(NANOS);
    }

    @Test
    void factoryWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        given(in.readLong()).willReturn(SECONDS);
        given(in.readInt()).willReturn(NANOS);

        final var readSubject = from(in);

        assertEquals(subject, readSubject);
    }

    @Test
    void beanWorks() {
        assertEquals(subject, new RichInstant(subject.getSeconds(), subject.getNanos()));
    }

    @Test
    void viewWorks() {
        final var grpc =
                Timestamp.newBuilder().setSeconds(SECONDS).setNanos(NANOS).build();

        assertEquals(grpc, subject.toGrpc());
    }

    @Test
    void knowsIfMissing() {
        assertFalse(subject.isMissing());
        assertTrue(MISSING_INSTANT.isMissing());
    }

    @Test
    void toStringWorks() {
        assertEquals("RichInstant{seconds=" + SECONDS + ", nanos=" + NANOS + "}", subject.toString());
    }

    @Test
    void factoryWorksForMissing() {
        assertEquals(MISSING_INSTANT, fromGrpc(Timestamp.getDefaultInstance()));
        assertEquals(subject, fromGrpc(subject.toGrpc()));
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = new RichInstant(SECONDS - 1, NANOS - 1);
        final var three = new RichInstant(subject.getSeconds(), subject.getNanos());

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(one, two);
        assertEquals(one, three);

        assertEquals(one.hashCode(), three.hashCode());
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void orderingWorks() {
        assertTrue(subject.isAfter(new RichInstant(SECONDS - 1, NANOS)));
        assertTrue(subject.isAfter(new RichInstant(SECONDS, NANOS - 1)));
        assertFalse(subject.isAfter(new RichInstant(SECONDS, NANOS + 1)));
    }

    @Test
    void javaFactoryWorks() {
        assertEquals(subject, fromJava(Instant.ofEpochSecond(subject.getSeconds(), subject.getNanos())));
    }

    @Test
    void javaViewWorks() {
        assertEquals(Instant.ofEpochSecond(subject.getSeconds(), subject.getNanos()), subject.toJava());
    }

    @Test
    void nullEqualsWorks() {
        assertNotEquals(null, subject);
    }

    @Test
    void emptyConstructor() {
        RichInstant anotherSubject = new RichInstant();
        assertEquals(0, anotherSubject.getNanos());
        assertEquals(0, anotherSubject.getSeconds());
        assertEquals(MISSING_INSTANT, anotherSubject);
        assertEquals(MISSING_INSTANT, fromGrpc(Timestamp.getDefaultInstance()));
        assertEquals(Timestamp.getDefaultInstance(), anotherSubject.toGrpc());
    }

    @Test
    void compareToWorks() {
        assertEquals(0, new RichInstant(2, 2).compareTo(new RichInstant(2, 2)));

        assertTrue(new RichInstant(2, 3).compareTo(new RichInstant(2, 2)) > 0);
        assertTrue(new RichInstant(2, 3).compareTo(new RichInstant(2, 4)) < 0);

        assertTrue(new RichInstant(3, 2).compareTo(new RichInstant(2, 2)) > 0);
        assertTrue(new RichInstant(3, 2).compareTo(new RichInstant(4, 2)) < 0);

        assertTrue(new RichInstant(3, 1).compareTo(new RichInstant(2, 2)) > 0);
        assertTrue(new RichInstant(3, 2).compareTo(new RichInstant(4, 1)) < 0);
    }
}
