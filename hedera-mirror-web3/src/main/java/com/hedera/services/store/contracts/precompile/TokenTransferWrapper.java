// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.List;

public record TokenTransferWrapper(List<NftExchange> nftExchanges, List<FungibleTokenTransfer> fungibleTransfers) {
    public TokenTransferList.Builder asGrpcBuilder() {
        final var builder = TokenTransferList.newBuilder();

        /* The same token type cannot meaningfully have both NFT exchanges and fungible transfers,
         * so we arbitrarily give priority to the non-fungible exchange type, and let error codes be
         * assigned downstream. */
        if (!fungibleTransfers.isEmpty()) {
            final var type = fungibleTransfers.get(0).getDenomination();
            builder.setToken(type);
            for (final var transfer : fungibleTransfers) {
                if (transfer.sender() != null) {
                    builder.addTransfers(transfer.senderAdjustment());
                }
                if (transfer.receiver() != null) {
                    builder.addTransfers(transfer.receiverAdjustment());
                }
            }
        } else if (!nftExchanges.isEmpty()) {
            final var type = nftExchanges.get(0).getTokenType();
            builder.setToken(type);
            for (final var exchange : nftExchanges) {
                builder.addNftTransfers(exchange.asGrpc());
            }
        }
        return builder;
    }
}
