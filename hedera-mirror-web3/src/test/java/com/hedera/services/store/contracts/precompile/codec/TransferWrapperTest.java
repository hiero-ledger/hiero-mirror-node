// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.utils.EntityIdUtils.toGrpcAccountId;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.store.contracts.precompile.HbarTransfer;
import com.hedera.services.store.contracts.precompile.TransferWrapper;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransferWrapperTest {

    @Test
    void translatesFungibleTransfersAsExpected() {
        final var inputTransfers = wellKnownTransfers();
        final var expectedAdjustments = TransferList.newBuilder()
                .addAccountAmounts(aaWith(anAccount, aChange))
                .addAccountAmounts(aaWith(otherAccount, bChange))
                .addAccountAmounts(aaWith(anotherAccount, cChange))
                .build();
        final var subject = new TransferWrapper(inputTransfers);

        final var builder = subject.asGrpcBuilder();
        assertEquals(expectedAdjustments, builder.build());
    }

    private AccountAmount aaWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(account)
                .setAmount(amount)
                .build();
    }

    private List<HbarTransfer> wellKnownTransfers() {
        return List.of(
                new HbarTransfer(Math.abs(aChange), false, anAccount, null),
                new HbarTransfer(Math.abs(bChange), false, null, otherAccount),
                new HbarTransfer(Math.abs(cChange), false, null, anotherAccount));
    }

    private final long aChange = -100L;
    private final long bChange = +75;
    private final long cChange = +25;
    private final AccountID anAccount = toGrpcAccountId(1234);
    private final AccountID otherAccount = toGrpcAccountId(2345);
    private final AccountID anotherAccount = toGrpcAccountId(3456);
}
