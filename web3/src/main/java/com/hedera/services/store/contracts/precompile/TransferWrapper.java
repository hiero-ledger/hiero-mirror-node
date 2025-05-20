// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;

public record TransferWrapper(List<HbarTransfer> hbarTransfers) {
    public TransferList.Builder asGrpcBuilder() {
        final var builder = TransferList.newBuilder();

        for (final var transfer : hbarTransfers) {
            if (transfer.sender() != null) {
                builder.addAccountAmounts(transfer.senderAdjustment());
            }
            if (transfer.receiver() != null) {
                builder.addAccountAmounts(transfer.receiverAdjustment());
            }
        }
        return builder;
    }
}
