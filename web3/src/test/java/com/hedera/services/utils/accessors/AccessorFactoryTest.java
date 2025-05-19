// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils.accessors;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessorFactoryTest {

    AccessorFactory subject;

    @BeforeEach
    void setUp() {
        subject = new AccessorFactory();
    }

    @Test
    void uncheckedSpecializedAccessorThrows() {
        final var invalidTxnBytes = "InvalidTxnBytes".getBytes();
        final var txn = Transaction.newBuilder()
                .setSignedTransactionBytes(ByteString.copyFrom(invalidTxnBytes))
                .build();
        assertThrows(IllegalArgumentException.class, () -> subject.uncheckedSpecializedAccessor(txn));
    }
}
