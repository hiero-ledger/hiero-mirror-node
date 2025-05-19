// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;

public class NftExchange {

    private final long serialNo;

    private final TokenID tokenType;
    private final AccountID sender;
    private final AccountID receiver;
    private final boolean isApproval;

    public NftExchange(final long serialNo, final TokenID tokenType, final AccountID sender, final AccountID receiver) {
        this(serialNo, tokenType, sender, receiver, false);
    }

    public static NftExchange fromApproval(
            final long serialNo, final TokenID tokenType, final AccountID sender, final AccountID receiver) {
        return new NftExchange(serialNo, tokenType, sender, receiver, true);
    }

    public NftExchange(
            final long serialNo,
            final TokenID tokenType,
            final AccountID sender,
            final AccountID receiver,
            final boolean isApproval) {
        this.serialNo = serialNo;
        this.tokenType = tokenType;
        this.sender = sender;
        this.receiver = receiver;
        this.isApproval = isApproval;
    }

    public NftTransfer asGrpc() {
        return NftTransfer.newBuilder()
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNo)
                .setIsApproval(isApproval)
                .build();
    }

    public TokenID getTokenType() {
        return tokenType;
    }

    public long getSerialNo() {
        return serialNo;
    }

    public boolean isApproval() {
        return isApproval;
    }
}
