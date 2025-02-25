// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;

public class HbarTransfer {

    protected final long amount;
    protected final AccountID sender;
    protected final AccountID receiver;
    protected final boolean isApproval;

    public HbarTransfer(final long amount, final boolean isApproval, final AccountID sender, final AccountID receiver) {
        this.amount = amount;
        this.isApproval = isApproval;
        this.sender = sender;
        this.receiver = receiver;
    }

    public AccountAmount senderAdjustment() {
        return AccountAmount.newBuilder()
                .setAccountID(sender)
                .setAmount(-amount)
                .setIsApproval(isApproval)
                .build();
    }

    public AccountAmount receiverAdjustment() {
        return AccountAmount.newBuilder()
                .setAccountID(receiver)
                .setAmount(+amount)
                .setIsApproval(isApproval)
                .build();
    }

    public AccountID sender() {
        return sender;
    }

    public AccountID receiver() {
        return receiver;
    }

    public long amount() {
        return amount;
    }

    public boolean isApproval() {
        return isApproval;
    }
}
