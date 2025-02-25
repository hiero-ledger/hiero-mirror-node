// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.stream.Stream;

public class IdUtils {

    private IdUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static BalanceChange hbarChange(final AccountID account, final long amount) {
        return BalanceChange.changingHbar(adjustFrom(account, amount), null);
    }

    public static AccountAmount adjustFrom(AccountID account, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(account)
                .setAmount(amount)
                .build();
    }

    public static BalanceChange tokenChange(final Id token, final AccountID account, final long amount) {
        return BalanceChange.changingFtUnits(token, token.asGrpcToken(), adjustFrom(account, amount), null);
    }

    public static NftTransfer nftXfer(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .setSenderAccountID(from)
                .setReceiverAccountID(to)
                .setSerialNumber(serialNo)
                .build();
    }

    public static Id asModelId(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return new Id(nativeParts[0], nativeParts[1], nativeParts[2]);
    }

    public static ContractID asContract(final AccountID id) {
        return ContractID.newBuilder()
                .setRealmNum(id.getRealmNum())
                .setShardNum(id.getShardNum())
                .setContractNum(id.getAccountNum())
                .build();
    }

    public static AccountID asAccount(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return AccountID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setAccountNum(nativeParts[2])
                .build();
    }

    public static ContractID asContract(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return ContractID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setContractNum(nativeParts[2])
                .build();
    }

    public static TokenID asToken(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return TokenID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setTokenNum(nativeParts[2])
                .build();
    }

    static long[] asDotDelimitedLongArray(String s) {
        String[] parts = s.split("[.]");
        return Stream.of(parts).mapToLong(Long::valueOf).toArray();
    }

    public static AccountID asAccountWithAlias(String alias) {
        return AccountID.newBuilder().setAlias(ByteString.copyFromUtf8(alias)).build();
    }
}
