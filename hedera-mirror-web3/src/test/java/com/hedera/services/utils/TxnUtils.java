// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.jproto.JKey;
import com.hedera.services.jproto.JKeyList;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.UUID;

public class TxnUtils {

    public static JKey nestJKeys(int additionalKeysToNest) {
        if (additionalKeysToNest == 0) {
            final var bytes = new byte[33];
            bytes[0] = 0x02;
            return asFcKeyUnchecked(
                    Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build());
        } else {
            final var descendantKeys = nestJKeys(additionalKeysToNest - 1);
            return new JKeyList(List.of(descendantKeys));
        }
    }

    public static Key.Builder nestKeys(Key.Builder builder, int additionalKeysToNest) {
        if (additionalKeysToNest == 0) {
            final var bytes = new byte[32];
            bytes[0] = 0x02;
            final var key =
                    Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build();
            builder.setEd25519(key.getEd25519());
            return builder;
        } else {
            var nestedBuilder = Key.newBuilder();
            nestKeys(nestedBuilder, additionalKeysToNest - 1);
            builder.setKeyList(KeyList.newBuilder().addKeys(nestedBuilder));
            return builder;
        }
    }

    public static ByteString randomUtf8ByteString(int n) {
        return ByteString.copyFrom(randomUtf8Bytes(n));
    }

    public static byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }

    public static void assertFailsWith(final Runnable something, final ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }

    public static Transaction buildTransactionFrom(final TransactionBody transactionBody) {
        return buildTransactionFrom(signedTransactionFrom(transactionBody).toByteString());
    }

    public static Transaction buildTransactionFrom(final ByteString signedTransactionBytes) {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransactionBytes)
                .build();
    }

    private static SignedTransaction signedTransactionFrom(final TransactionBody txnBody) {
        return signedTransactionFrom(txnBody, SignatureMap.getDefaultInstance());
    }

    public static SignedTransaction signedTransactionFrom(final TransactionBody txnBody, final SignatureMap sigMap) {
        return SignedTransaction.newBuilder()
                .setBodyBytes(txnBody.toByteString())
                .setSigMap(sigMap)
                .build();
    }
}
